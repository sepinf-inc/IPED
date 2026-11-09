'''
# Finds images by text descriptions, using a local multilingual image-text model (SigLIP 2, https://huggingface.co/google/siglip2-base-patch16-256).
# ImageTextSearchTask.py
# Requirements: python with transformers, torch, pillow and numpy (the same modules used by AgeEstimationTask).
# Implementation notes:
#  - Image thumbnails are used, so 'enableImageThumbs' must be enabled.
#  - Categories are defined by text descriptions in conf/ImageTextSearchConfig.txt, each one with its own threshold.
#  - Stores results in items' extra attributes:
#    + 'ai:imageSearch:labels' stores the categories whose score reached their threshold
#    + 'ai:imageSearch:score:<category>' stores the score of each category (model probability, range: 0-100)
'''
import io, os, time
from java.lang import System

# configuration properties
enableProp = 'enableImageTextSearch'
configFile = 'ImageTextSearchConfig.txt'

# Maximum number of images per model run
batchSize = 50
batchSizeProp = 'batchSize'

# Device to use for model runs ('cpu' or 'gpu')
device = None
deviceProp = 'device'
deviceError = False

# Prefix of the properties that define the categories
categoryPrefix = 'category.'

modelName = 'google/siglip2-base-patch16-256'
labelsAttr = 'ai:imageSearch:labels'
scoreAttr = 'ai:imageSearch:score:'

# results cache (avoids running the model on duplicates)
from java.util.concurrent import ConcurrentHashMap
cache = ConcurrentHashMap()

IPED_GPU_GLOBAL_SEMAPHORE_STRING = 'IPED_GPU_GLOBAL_SEMAPHORE'

# variables related to statistics
imagesProcessed = 0
imagesFailed = 0
duplicatesCount = 0
predictTime = 0

# semaphore for concurrency control
semaphore = None

'''
Main class: ImageTextSearchTask
'''
class ImageTextSearchTask:

    enabled = None

    def __init__(self):
        self.itemList = []
        self.nextTaskList = []
        self.imageList = []

    def isEnabled(self):
        return False if ImageTextSearchTask.enabled is None else ImageTextSearchTask.enabled

    def processQueueEnd(self):
        return True

    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]

    def init(self, configuration):
        taskConfig = configuration.getTaskConfigurable(configFile)
        if ImageTextSearchTask.enabled is None:
            ImageTextSearchTask.enabled = taskConfig.isEnabled()

        # disable task during report generation
        if caseData.isIpedReport():
            ImageTextSearchTask.enabled = False
            return

        if not ImageTextSearchTask.enabled:
            return

        from iped.engine.config import ImageThumbTaskConfig
        if not configuration.findObject(ImageThumbTaskConfig).isEnabled():
            logger.error('To use the ImageTextSearch task, you must enable image thumbnails by setting enableImageThumbs = true. ImageTextSearch has been disabled.')
            ImageTextSearchTask.enabled = False
            return

        # check if required modules are installed
        try:
            module_name = 'transformers'
            global AutoModel, AutoProcessor
            from transformers import AutoModel, AutoProcessor

            module_name = 'torch'
            global torch
            import torch

            module_name = 'pillow'
            global PilImage
            from PIL import Image as PilImage

            module_name = 'numpy'
            global np
            import numpy as np

        except ModuleNotFoundError as e:
            logger.error(f"ImageTextSearchTask could not be initialized and was disabled: '{module_name}' module could not be imported: {e}")
            ImageTextSearchTask.enabled = False
            return

        # load task configuration properties
        extraProps = taskConfig.getConfiguration()
        global batchSize, device, deviceError

        if extraProps.getProperty(batchSizeProp) is not None:
            try:
                batchSize = int(extraProps.getProperty(batchSizeProp))
                if batchSize < 1:
                    raise ValueError()
            except ValueError:
                batchSize = 50
                logger.warn("ImageTextSearchTask: Invalid value for property 'batchSize': " + extraProps.getProperty(batchSizeProp) + ". Using " + str(batchSize))

        if device is None:
            value = extraProps.getProperty(deviceProp)
            if value == 'gpu':
                if torch.cuda.is_available():
                    device = torch.device('cuda')
                else:
                    error_msg = "ImageTextSearchTask: Device 'gpu' is not available or properly configured on your system."
                    if not deviceError:
                        deviceError = True
                        logger.error(error_msg)
                    from java.lang import RuntimeException
                    self.worker.exception = RuntimeException(error_msg)
                    return
            else:
                if value is not None and value != 'cpu':
                    logger.warn("ImageTextSearchTask: Invalid value for property 'device': " + value + " - value must be 'cpu' or 'gpu'. Using 'cpu'")
                device = torch.device('cpu')
            logger.info('ImageTextSearchTask: Device to use is ' + str(device))

        loadModel()
        loadCategories(extraProps)

        if torch.cuda.is_available():
            createSemaphore()

    def finish(self):
        num_finishes = caseData.getCaseObject('image_text_search_num_finishes')
        if num_finishes is None:
            num_finishes = 0
        num_finishes += 1
        caseData.putCaseObject('image_text_search_num_finishes', num_finishes)

        if num_finishes == numThreads:
            cache.clear()
            logger.info('ImageTextSearchTask: Images processed: ' + str(imagesProcessed))
            logger.info('ImageTextSearchTask: Duplicates taken from cache: ' + str(duplicatesCount))
            logger.info('ImageTextSearchTask: Images that failed: ' + str(imagesFailed))
            if imagesProcessed > duplicatesCount:
                total = predictTime / numThreads
                logger.info('ImageTextSearchTask: Time to run the model (s): ' + str(round(total, 3)))
                logger.info('ImageTextSearchTask: Average time (ms/image): ' + str(round(total * 1000 / (imagesProcessed - duplicatesCount), 3)))

    def sendToNextTask(self, item):
        if not item.isQueueEnd() and item not in self.itemList and item not in self.nextTaskList:
            self.javaTask.sendToNextTaskSuper(item)

        if len(self.nextTaskList) > 0:
            localList = list(self.nextTaskList)
            self.nextTaskList.clear()
            for i in localList:
                self.javaTask.sendToNextTaskSuper(i)

        if item.isQueueEnd():
            self.javaTask.sendToNextTaskSuper(item)

    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return (size >= batchSize) or (size > 0 and item.isQueueEnd())

    def process(self, item):

        if not self.isEnabled() or (not item.isQueueEnd() and not supported(item)):
            return

        global imagesProcessed, imagesFailed, duplicatesCount

        if not item.isQueueEnd():
            scores = cache.get(item.getHashValue())
            if scores is not None:
                setResults(item, scores)
                imagesProcessed += 1
                duplicatesCount += 1
                return

            try:
                img = PilImage.open(io.BytesIO(convertJavaByteArray(item.getThumb()))).convert('RGB')
            except Exception as e:
                imagesFailed += 1
                logger.warn('ImageTextSearchTask: Could not load the thumbnail of ' + item.getPath() + ': ' + str(e))
                return

            self.imageList.append(img)
            self.itemList.append(item)

        if self.isToProcessBatch(item):
            processImages(self.itemList, self.imageList)
            self.nextTaskList.extend(self.itemList)
            self.itemList.clear()
            self.imageList.clear()

'''
Check if item is supported
'''
def supported(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image') \
        and item.getHashValue() is not None and item.getExtraAttribute('hasThumb') and item.isToAddToCase()

def convertJavaByteArray(byteArray):
    return bytes(b % 256 for b in byteArray)

'''
Load the model and processor, shared by all threads
'''
def loadModel():
    model = caseData.getCaseObject('image_text_search_model')
    if model is not None:
        return model, caseData.getCaseObject('image_text_search_processor')

    model_path = System.getProperty('iped.root') + '/models/image_text_search'
    if not os.path.exists(model_path + '/model.safetensors'):
        logger.info('ImageTextSearchTask: Downloading model ' + modelName + ' to ' + model_path)
        model = AutoModel.from_pretrained(modelName)
        processor = AutoProcessor.from_pretrained(modelName)
        os.makedirs(model_path, exist_ok=True)
        model.save_pretrained(model_path)
        processor.save_pretrained(model_path)
    else:
        model = AutoModel.from_pretrained(model_path)
        processor = AutoProcessor.from_pretrained(model_path)

    model.to(device).eval()
    caseData.putCaseObject('image_text_search_model', model)
    caseData.putCaseObject('image_text_search_processor', processor)
    logger.info('ImageTextSearchTask: Model loaded from ' + model_path)
    return model, processor

'''
Read the categories from the configuration and turn their descriptions into vectors.
Each category is 'category.<name> = <threshold> ; <description> ; <description> ...', where the
threshold is a score from 0 to 100 and the vectors of all descriptions are averaged.
'''
def loadCategories(extraProps):
    if caseData.getCaseObject('image_text_search_names') is not None:
        return

    names, thresholds, descriptions = [], [], []
    for key in sorted(extraProps.stringPropertyNames()):
        if not key.startswith(categoryPrefix):
            continue
        parts = [p.strip() for p in extraProps.getProperty(key).split(';')]
        try:
            threshold = float(parts[0])
        except ValueError:
            logger.warn('ImageTextSearchTask: Invalid threshold in ' + key + ', category ignored')
            continue
        texts = [p for p in parts[1:] if p]
        if not texts:
            logger.warn('ImageTextSearchTask: No description in ' + key + ', category ignored')
            continue
        names.append(key[len(categoryPrefix):])
        thresholds.append(threshold)
        descriptions.append(texts)

    model, processor = loadModel()
    vectors = []
    with torch.no_grad():
        for texts in descriptions:
            # SigLIP 2 was trained with lowercase texts padded to 64 tokens
            inputs = processor(text=[t.lower() for t in texts], padding='max_length', max_length=64,
                               truncation=True, return_tensors='pt').to(device)
            v = normalize(model.get_text_features(**inputs)).sum(dim=0)
            vectors.append(v / v.norm())

    caseData.putCaseObject('image_text_search_names', names)
    caseData.putCaseObject('image_text_search_thresholds', thresholds)
    caseData.putCaseObject('image_text_search_vectors', torch.stack(vectors) if vectors else None)
    logger.info('ImageTextSearchTask: Categories loaded: ' + ', '.join(names))

def normalize(features):
    if not torch.is_tensor(features):  # some transformers versions return a model output
        features = features.pooler_output
    return torch.nn.functional.normalize(features.float(), dim=-1)

'''
Create semaphore for concurrency control
'''
def createSemaphore():
    global semaphore
    semaphore = caseData.getCaseObject(IPED_GPU_GLOBAL_SEMAPHORE_STRING)
    if semaphore is None:
        from java.util.concurrent import Semaphore
        semaphore = Semaphore(1)
        caseData.putCaseObject(IPED_GPU_GLOBAL_SEMAPHORE_STRING, semaphore)

'''
Run the model on a batch of images and store the scores
'''
def processImages(itemList, imageList):
    global predictTime, imagesProcessed
    vectors = caseData.getCaseObject('image_text_search_vectors')
    if vectors is None:
        return
    model, processor = loadModel()

    t = time.time()
    try:
        if semaphore is not None:
            semaphore.acquire()
        # one thread per call on cpu, as parallelism already comes from the workers
        if device.type == 'cpu':
            torch.set_num_threads(1)
        with torch.no_grad():
            inputs = processor(images=imageList, return_tensors='pt').to(device)
            images = normalize(model.get_image_features(**inputs))
            logits = images @ vectors.T * model.logit_scale.exp() + model.logit_bias
            probs = torch.sigmoid(logits).cpu().numpy()
    finally:
        if semaphore is not None:
            semaphore.release()
    predictTime += time.time() - t

    for i, item in enumerate(itemList):
        scores = [float(p) * 100 for p in probs[i]]
        setResults(item, scores)
        cache.put(item.getHashValue(), scores)
        imagesProcessed += 1

def setResults(item, scores):
    names = caseData.getCaseObject('image_text_search_names')
    thresholds = caseData.getCaseObject('image_text_search_thresholds')
    labels = []
    for name, threshold, score in zip(names, thresholds, scores):
        item.setExtraAttribute(scoreAttr + name, score)
        if score >= threshold:
            labels.append(name)
    if labels:
        item.setExtraAttribute(labelsAttr, labels)
