# Python nudity detection based on yahoo open_nsfw algorithm.
# You need to install pillow, keras, and tensorflow backend.
# Tested on Windows with python 3.8.6, pillow 8.1.0, keras 2.4.3, tensorflow 2.4.1.
# On Linux, you must also install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.

# If computed thumbnail will be reused or computed again
useImageThumbs = True

# Number of images or video frames to be processed at the same time
batchSize = 50

# Max number of threads allowed to enter code between semaphore.acquire() and semaphore.release()
# This can be set if your GPU does not have enough memory to use all threads with configured 'batchSize'
maxThreads = None

import traceback
import io
import time
import sys
from java.lang import System
import threading

# Dictionary to accumulate batches before processing
NSFW_BATCHES = {}
# Dictionary to queue items that have been processed and are ready to be sent
NSFW_PROCESSED_ITEMS = {}
# Unified lock to protect access to both dictionaries
BATCH_LOCK = threading.Lock()


enableProp = 'enableYahooNSFWDetection'
targetSize = (224, 224)
videoFramesTime = 0
arrayConvTime = 0
predictTime = 0
loadImgTime = 0
enabled = False
semaphore = None

def loadModel():
    model = caseData.getCaseObject('nsfw_model')
    if model is None:
        file = System.getProperty('iped.root') + '/models/nsfw-keras-1.0.0.h5'
        from keras.models import load_model
        model = load_model(file)
        x = np.zeros((1, 224, 224, 3))
        #compile predict function to be used by multiple threads
        model.predict(x)
        caseData.putCaseObject('nsfw_model', model)
        logger.info('Loaded NSFW model ' + file)
        from java.util.concurrent import ConcurrentHashMap
        cache = ConcurrentHashMap()
        caseData.putCaseObject('nsfw_score_cache', cache)
    
    return model

def createSemaphore():
    if maxThreads is None:
        return
    global semaphore
    semaphore = caseData.getCaseObject('nsfw_semaphore')
    if(semaphore is None):
        from java.util.concurrent import Semaphore
        semaphore = Semaphore(maxThreads)
        caseData.putCaseObject('nsfw_semaphore', semaphore)
    return semaphore
   
def isImage(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')
       
def isSupportedVideo(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('video') and item.getViewFile() is not None
    
def supported(item):
    return item.getLength() is not None and item.getLength() > 0 and (isImage(item) or isSupportedVideo(item))

def convertJavaByteArray(byteArray):
    global arrayConvTime
    t = time.time()
    result =  bytes(b % 256 for b in byteArray)
    arrayConvTime += time.time() - t
    return result
    
def loadRawImage(input):
    global loadImgTime 
    t = time.time()
    img = PilImage.open(io.BytesIO(input))
    img = img.convert('RGB')
    img = img.resize(targetSize, PilImage.NEAREST)
    loadImgTime += time.time() - t
    return img

'''
Main class
'''
class NSFWNudityDetectTask:
    
    def __init__(self):
        pass

    def isEnabled(self):
        return enabled
        
    def processQueueEnd(self):
        return True
        
    def getConfigurables(self):
        from iped.engine.config import EnableTaskProperty
        return [EnableTaskProperty(enableProp)]
    
    def init(self, configuration):
        global enabled
        enabled = configuration.getEnableTaskProperty(enableProp)
        if not enabled:
            return
        global PilImage, np
        from PIL import Image as PilImage
        import numpy as np
        loadModel()
        createSemaphore()
    
    def finish(self):
        num_finishes = caseData.getCaseObject('num_finishes')
        if num_finishes is None:
            num_finishes = 0;
        num_finishes += 1
        caseData.putCaseObject('num_finishes', num_finishes)
        
        times = caseData.getCaseObject('nsfw_times')
        if times is None:
            times = [0, 0, 0, 0]
            
        times[0] += videoFramesTime / numThreads
        times[1] += arrayConvTime / numThreads
        times[2] += predictTime / numThreads
        times[3] += loadImgTime / numThreads
        caseData.putCaseObject('nsfw_times', times)
        
        if num_finishes == numThreads:
            logger.info('Time(s) to get video frames: ' + str(times[0]))
            logger.info('Time(s) to convert java arrays: ' + str(times[1]))
            logger.info('Time(s) to NSFW prediction: ' + str(times[2]))
            logger.info('Time(s) to load images: ' + str(times[3]))
    
    
    def sendToNextTask(self, item):
        worker_key = javaTask.get()
        items_to_send = None

        with BATCH_LOCK:
            # Atomically remove items ready for sending to avoid blocking other threads
            if worker_key in NSFW_PROCESSED_ITEMS and len(NSFW_PROCESSED_ITEMS[worker_key]) > 0:
                items_to_send = NSFW_PROCESSED_ITEMS.pop(worker_key)

        if items_to_send:
            # Send the batch of already processed items
            item_was_in_batch = False
            for item_to_send in items_to_send:
                if item_to_send.getId() == item.getId():
                    item_was_in_batch = True
                javaTask.get().sendToNextTaskSuper(item_to_send)
            
            # If the current item triggered the send and was in the batch, we are done with it.
            if item_was_in_batch:
                return

        with BATCH_LOCK:
            # If the current item is still waiting in an accumulation batch, hold it back.
            if worker_key in NSFW_BATCHES and item.getId() in NSFW_BATCHES[worker_key]['item_ids']:
                return

        # If the item is not part of any batch (e.g., unsupported, video), send it.
        javaTask.get().sendToNextTaskSuper(item)
    
    
    def process(self, item):
        # Pass non-supported items through immediately
        if not item.isQueueEnd() and not supported(item):
            return
            
        try:
            # Check cache first
            if item.getHash() is not None:
                cache = caseData.getCaseObject('nsfw_score_cache')
                score = cache.get(item.getHash())
                if score is not None:
                    item.setExtraAttribute('nsfw_nudity_score', score)
                    return
            
            # Videos are processed individually, not batched with images
            if isSupportedVideo(item):
                processVideoFrames(item)
                return

            # --- Start of new batching logic for images ---
            worker_key = javaTask.get()
            batch_to_process = None
            img = None

            if isImage(item) and not useImageThumbs and item.getTempFile() is not None:
                img_path = item.getTempFile().getAbsolutePath()
                img = image.load_img(img_path, target_size=targetSize)
                
            if isImage(item) and useImageThumbs and item.getExtraAttribute('hasThumb'):
                input = convertJavaByteArray(item.getThumb())
                img = loadRawImage(input)

            if not item.isQueueEnd():
                if img is None:    
                    item.setExtraAttribute('nsfw_error', 1)
                    return

                from keras.preprocessing import image
                x = image.img_to_array(img)

            with BATCH_LOCK:
                # Initialize batch storage for this worker if it's the first time
                if worker_key not in NSFW_BATCHES:
                    NSFW_BATCHES[worker_key] = {'items': [], 'images': [], 'item_ids': set()}

                batch_data = NSFW_BATCHES[worker_key]

                # For non-queueEnd items, prepare image and add to batch
                if not item.isQueueEnd():
                    batch_data['images'].append(x)
                    batch_data['items'].append(item)
                    batch_data['item_ids'].add(item.getId())

                # Check if the batch is ready for processing
                size = len(batch_data['items'])
                is_batch_ready = size >= batchSize or (size > 0 and item.isQueueEnd())

                if is_batch_ready:
                    # Move the batch out of the accumulation dict to be processed
                    batch_to_process = NSFW_BATCHES.pop(worker_key)

            # --- Processing happens outside the lock to avoid long holds ---
            if batch_to_process:
                # 1. This function processes the images and adds the 'nsfw_nudity_score' attribute
                processImages(batch_to_process['images'], batch_to_process['items'])

                # 2. After processing, add the completed items to the send queue
                with BATCH_LOCK:
                    if worker_key not in NSFW_PROCESSED_ITEMS:
                        NSFW_PROCESSED_ITEMS[worker_key] = []
                    NSFW_PROCESSED_ITEMS[worker_key].extend(batch_to_process['items'])
            
        except Exception as e:
            item.setExtraAttribute('nsfw_error', 2)
            # Log the full traceback for better debugging
            logger.warn(f"Error processing item {item.getPath()}: {traceback.format_exc()}")
            raise e
    
    
def processVideoFrames(item):
    global videoFramesTime
    t = time.time()
    frames = ImageUtil.getBmpFrames(item.getViewFile())
    videoFramesTime += time.time() - t 
    list = []
    scores = []
    numFrames = frames.size()
    for i in range(numFrames):
        input = convertJavaByteArray(frames.get(i))
        img = loadRawImage(input)
        from keras.preprocessing import image
        x = image.img_to_array(img)
        list.append(x)
        if batchSize == 1 or (i > 0 and i % batchSize == 0) or i == (numFrames - 1):
            preds = makePrediction(list)
            for i in range(len(list)):
                scores.append(preds[i][1])
            list.clear()
    
    finalScore = videoScore(scores)
    item.setExtraAttribute('nsfw_nudity_score', finalScore)
    cache = caseData.getCaseObject('nsfw_score_cache')
    cache.put(item.getHash(), finalScore)
    
def videoScore(scores):
    scores.sort(reverse=True)
    weight = 1
    mult = 0.7
    div = 0
    sum = 0
    for v in scores:
        div += weight
        sum += v * weight
        weight *= mult
    if div > 0:
        sum /= div
    return sum * 100

def processImages(imageList, itemList):
    if not imageList:
        return
    logger.debug('Processing batch of ' + str(len(imageList)) + " images.")
    preds = makePrediction(imageList)
    cache = caseData.getCaseObject('nsfw_score_cache')
    for i in range(len(itemList)):
        score = preds[i][1] * 100
        itemList[i].setExtraAttribute('nsfw_nudity_score', score)
        cache.put(itemList[i].getHash(), score)

def makePrediction(list):
    global predictTime 
    t = time.time()
    x = np.stack(list, axis=0)
    from keras.applications.imagenet_utils import preprocess_input
    x = preprocess_input(x)
    model = loadModel()
    try:
        if semaphore is not None:
            semaphore.acquire()

        preds = model.predict(x)
        predictTime += time.time() - t
        return preds
    finally:
        if semaphore is not None:
            semaphore.release()