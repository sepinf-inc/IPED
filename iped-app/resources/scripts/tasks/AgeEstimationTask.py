'''
# Age estimation feature based on Open Age Detection project (https://huggingface.co/prithivMLmods/open-age-detection)
# AgeEstimationTask.py - by Marcos Moura
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#AgeEstimation
'''

# configuration properties
enableProp = 'enableAgeEstimation'
configFile = 'AgeEstimationConfig.txt'

# Maximum number of items per classification run
batchSize = 50
batchSizeProp = 'batchSize'

# Threshold used to decide if a face is labeled in one category ('0' means the category with the highest score)
categorizationThreshold = 0
categorizationThresholdProp = 'categorizationThreshold'

# Skip age estimation for faces within images with hits on IPED hashesDB database (if 'hashesDB' is not configured in 'LocalConfig.txt' or 'false', do not skip)
skipHashDBFiles = True
skipHashDBFilesProp = 'skipHashDBFiles'

# Max number of threads allowed to enter code between semaphore.acquire() and semaphore.release()
# This can be set if your GPU does not have enough memory to use all threads with configured 'batchSize'
maxThreads = None
maxThreadsProp = 'maxThreads'

import io, os, time, sys
from java.lang import System

predictCount = 0
predictTime = 0
enabled = False
semaphore = None

'''
Load model and processor which perform age estimation for faces
'''
def loadModelAndProcessor():
    logger.debug('AgeEstimationTask: Loading Open Age Detection model')

    model = caseData.getCaseObject('open-age-detection_model')
    processor = caseData.getCaseObject('open-age-detection_processor')
    
    if model is None or processor is None:
        # Load model and processor
        model_name = "prithivMLmods/open-age-detection"
        model_path = System.getProperty('iped.root') + '/models/age_estimation'
        model_filename = 'model.safetensors'
        processor_filename = 'preprocessor_config.json'

        if not os.path.exists(model_path + '/' + model_filename) or not os.path.exists(model_path + '/' + processor_filename):
            # Create model files
            model = SiglipForImageClassification.from_pretrained(model_name)
            processor = AutoImageProcessor.from_pretrained(model_name)
            
            # Create the model path if it doesn't exist
            os.makedirs(model_path, exist_ok=True)
            
            # Save the model and processor to files
            logger.debug('AgeEstimationTask: Saving model to ' + model_path)
            model.save_pretrained(model_path)
            processor.save_pretrained(model_path)
        else:
            # Load model files
            logger.debug('AgeEstimationTask: Loading model from ' + model_path)
            model = SiglipForImageClassification.from_pretrained(model_path)
            processor = AutoImageProcessor.from_pretrained(model_path)
        
        # Store model in memory
        caseData.putCaseObject('open-age-detection_model', model)
        caseData.putCaseObject('open-age-detection_processor', processor)
        logger.debug('AgeEstimationTask: Open Age Detection model loaded')
        
        # Set up cache
        from java.util.concurrent import ConcurrentHashMap
        cache = ConcurrentHashMap()
        caseData.putCaseObject('age_estimation_cache', cache)    
        logger.debug('AgeEstimationTask: Cache ready')

    return [model, processor]

'''
Create semaphore for concurrency control
'''
def createSemaphore():
    if maxThreads is None:
        return
    global semaphore
    semaphore = caseData.getCaseObject('age_estimation_semaphore')
    
    if (semaphore is None):
        from java.util.concurrent import Semaphore
        semaphore = Semaphore(maxThreads)
        caseData.putCaseObject('age_estimation_semaphore', semaphore)
    return semaphore
   
def isImage(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')
       
def supported(item):
    return isImage(item) and item.getLength() is not None and item.getLength() > 0

'''
Main class
'''
class AgeEstimationTask:

    enabled = None

    def __init__(self):
        self.itemList = []
        self.faceItems = []
        self.faceImages = []
        self.queued = False

    def isEnabled(self):
        return False if AgeEstimationTask.enabled is None else AgeEstimationTask.enabled
        
    def processQueueEnd(self):
        return True
        
    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(enableProp, configFile)]
    
    def init(self, configuration):
        # check if age estimation task is enabled
        taskConfig = configuration.getTaskConfigurable(configFile)
        if AgeEstimationTask.enabled is None:
            AgeEstimationTask.enabled = taskConfig.isEnabled()
        
        # Check if required age estimation modules are properly installed
        try:
            if not AgeEstimationTask.enabled:
                return

            # default help and error messages
            msg_see_manual = 'See AgeEstimation task setup information at <https://github.com/sepinf-inc/IPED/wiki/User-Manual#AgeEstimation>.'
            msg_task_init_error = 'AgeEstimation task could not be initialized and was disabled'

            # chek if 'transformers' module is installed
            module_name = 'transformers'
            global AutoImageProcessor, SiglipForImageClassification
            from transformers import AutoImageProcessor, SiglipForImageClassification

            # chek if 'torch' module is installed
            module_name = 'torch'
            global torch
            import torch

            # chek if 'pillow' module is installed
            module_name = 'pillow'
            global PilImage
            from PIL import Image as PilImage
            
            # chek if 'numpy' module is installed
            module_name = 'numpy'
            global np
            import numpy as np
            
        except ModuleNotFoundError:
            # required module not installed
            msg = msg_task_init_error + f': \'{module_name}\' module is missing.'
            logger.error(msg + ' ' + msg_see_manual)
            AgeEstimationTask.enabled = False
            return

        # load configuration properties
        extraProps = taskConfig.getConfiguration()
        if extraProps.getProperty(batchSizeProp) is not None:
            try:
                batchSize = int(extraProps.getProperty(batchSizeProp))
                if batchSize < 1:
                    raise ValueError("AgeEstimationTask: Value for property 'batchSize' must be >0")
            except ValueError:
                logger.warn("AgeEstimationTask: Invalid value for property 'batchSize': " + extraProps.getProperty(batchSizeProp))
                logger.warn("AgeEstimationTask: Using default value for property 'batchSize': " + str(batchSize))
        if extraProps.getProperty(categorizationThresholdProp) is not None:
            try:
                categorizationThreshold = int(extraProps.getProperty(categorizationThresholdProp))
                if categorizationThreshold < 0 or categorizationThreshold > 100:
                    raise ValueError("AgeEstimationTask: Value for property 'categorizationThreshold' must be in range 0-100")
            except ValueError:
                logger.warn("AgeEstimationTask: Invalid value for property 'categorizationThreshold': " + extraProps.getProperty(categorizationThresholdProp))
                logger.warn("AgeEstimationTask: Using default value for property 'categorizationThreshold': " + str(categorizationThreshold))
        if extraProps.getProperty(skipHashDBFilesProp) is not None:
            if extraProps.getProperty(skipHashDBFilesProp) in ('true', 'True'):
                skipHashDBFiles = True
            elif extraProps.getProperty(skipHashDBFilesProp) in ('false', 'False'):
                skipHashDBFiles = False
            else:
                logger.warn("AgeEstimationTask: Invalid value for property 'skipHashDBFiles': " + extraProps.getProperty(skipHashDBFilesProp) + " - value must be 'true' or 'false'")
                logger.warn("AgeEstimationTask: Using default value for property 'skipHashDBFiles': " + str(skipHashDBFiles))
        if extraProps.getProperty(maxThreadsProp) is not None:
            try:
                maxThreads = int(extraProps.getProperty(maxThreadsProp))
                if maxThreads < 1:
                    raise ValueError("AgeEstimationTask: Value for property 'maxThreads' must be >0")
            except ValueError:
                logger.warn("AgeEstimationTask: Invalid value for property 'maxThreads': " + extraProps.getProperty(maxThreadsProp))
                logger.warn("AgeEstimationTask: Using default value for property 'maxThreads': " + str(maxThreads))

        # load model and processor
        loadModelAndProcessor()
        
        # create semaphore
        createSemaphore()
    

    def finish(self):
        num_finishes = caseData.getCaseObject('num_finishes')
        if num_finishes is None:
            num_finishes = 0
        num_finishes += 1
        caseData.putCaseObject('num_finishes', num_finishes)
        
        age_estimation_time = caseData.getCaseObject('age_estimation_time')
        if age_estimation_time is None:
            age_estimation_time = 0
        age_estimation_time += predictTime / numThreads
        caseData.putCaseObject('age_estimation_time', age_estimation_time)
        
        if num_finishes == numThreads:
            logger.info('AgeEstimationTask: Total time to perform age estimation for faces (s): ' + str(round(age_estimation_time, 2)))
            logger.info('AgeEstimationTask: Count of faces with age estimation performed: ' + str(predictCount))
            logger.info('AgeEstimationTask: Average time to perform age estimation for faces (faces/s): ' + str(round(predictCount / age_estimation_time, 2)))
    

    def sendToNextTask(self, item):        
        if not item.isQueueEnd() and not self.queued:
            javaTask.get().sendToNextTaskSuper(item)
            return
        
        if self.isToProcessBatch(item):        
            for i in self.itemList:
                javaTask.get().sendToNextTaskSuper(i)            
            self.itemList.clear()
            self.faceItems.clear()
            self.faceImages.clear()           
            
        if item.isQueueEnd():
            javaTask.get().sendToNextTaskSuper(item)
    

    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return (size >= batchSize) or (size > 0 and item.isQueueEnd())
    
    
    def process(self, item):        
        self.queued = False
    
        if (not item.isQueueEnd() and not supported(item)) or (not item.isToAddToCase()):
            return
            
        try:
            # skip age estimation for faces within images with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
            global skipHashDBFiles
            from iped.engine.task import HashDBLookupTask
            if (skipHashDBFiles and item.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) is not None):
                # add skip age estimation info
                item.setExtraAttribute('faceAgeEstimationSkip', 'hashDB')
                return

            # skip age estimation for faces within images duplicates if age estimation data exists in cache
            # retrieve age estimation data from cache
            if item.getHash() is not None:
                cache = caseData.getCaseObject('age_estimation_cache')
                if cache is not None:
                    age_estimation_data = cache.get(item.getHash())
                    if age_estimation_data is not None:
                        # age estimation data exists in cache
                        # add age estimation scores and labels
                        item.setExtraAttribute('faceAgeClassScores', age_estimation_data['faceAgeClassScores'])
                        item.setExtraAttribute('faceAgeClassLabels', age_estimation_data['faceAgeClassLabels'])
                        # add label for each type of face found in image ('Child', 'Teenager', 'Adult')
                        for face_of in age_estimation_data['faceOf']:
                            item.setExtraAttribute('faceOf' + face_of, True)
                        # add age estimation status
                        item.setExtraAttribute('faceAgeEstimationStatus', 'success')
                        # add skip age estimation info
                        item.setExtraAttribute('faceAgeEstimationSkip', 'duplicate')
                        return
            
            img = None            
            if isImage(item) and item.getTempFile() is not None:
                # check if item has faces
                from iped.properties import ExtraProperties
                face_count = item.getExtraAttribute(ExtraProperties.FACE_COUNT)
                if face_count is not None and face_count > 0:
                    logger.debug('AgeEstimationTask: Processing ' + item.getPath())
                    logger.debug('AgeEstimationTask: face_count: ' + str(face_count))

                    # save image (for test purposes only; to be removed before feature release)
                    img_save_path = System.getProperty('iped.root') + '/models/age_estimation/img_save'
                    os.makedirs(img_save_path, exist_ok=True)
                    img_path = item.getTempFile().getAbsolutePath()
                    img = PilImage.open(img_path)
                    img = img.convert("RGB")                    
                    img.save(img_save_path + '/' + os.path.basename(img_path))
                    
                    # get face_locations
                    face_locations = item.getExtraAttribute(ExtraProperties.FACE_LOCATIONS)
                    if face_locations is not None and len(face_locations) > 0:
                        # iterate through face_locations
                        i = 1
                        for face_location in face_locations:
                            logger.debug('AgeEstimationTask: face_location: ' + str(face_location))

                            # extract the portion of the image corresponding to the face
                            y1, x2, y2, x1 = face_location                            
                            face_pos = str(x1) + str(y1) + str(x2) + str(y2)
                            face_img = img.crop((x1, y1, x2, y2))

                            # save face image (for test purposes only; to be removed before feature release)
                            face_img_path = os.path.splitext(os.path.basename(img_path))[0] + '_face_' + str(i) + '.' + os.path.splitext(os.path.basename(img_path))[1]
                            face_img.save(img_save_path + '/' + os.path.basename(face_img_path))
                            
                            # add face item and face image to the corresponding list
                            self.faceItems.append(item)
                            self.faceImages.append(face_img)
                            i += 1
                
            if not item.isQueueEnd():
                if img is None:    
                    # add age estimation status
                    item.setExtraAttribute('faceAgeEstimationStatus', 'fail:NoImage')
                    return
                self.itemList.append(item)
                self.queued = True
            
        except Exception as e:
            # add age estimation status
            item.setExtraAttribute('faceAgeEstimationStatus', 'fail:Exception')
            raise e
        
        # process faces for age estimation
        if self.isToProcessBatch(item):
            processImages(self.faceItems, self.faceImages)
    
'''
Process faces for age estimation
'''
def processImages(itemList, imageList):
    logger.debug('AgeEstimationTask: Processing batch of ' + str(len(itemList)) + ' faces.')

    # perform age estimation for faces
    preds = makePrediction(imageList)
    
    # store faces count and labels
    item_faces_count = 0
    item_faces_labels = set()

    for i in range(len(itemList)):
        # add age estimation scores and labels: class '0' (child: 0-12); class '1' (teenager: 13-20); class '2+' (adult: 21+)
        labels = {0: 'child', 1: 'teenager', 2: 'adult'}
        prob_class0 = round(preds[i][0] * 100)
        prob_class1 = round(preds[i][1] * 100)
        prob_class2Plus = 100 - (prob_class0 + prob_class1)
        scores = [prob_class0, prob_class1, prob_class2Plus]
        max_val = max(scores)
        # set the label of the face, according to the 'categorizationThreshold'
        label = None
        if max_val >= categorizationThreshold:
            max_val_idx = scores.index(max_val)
            label = [labels[max_val_idx]]
        scores = [scores]
        age_class_scores = itemList[i].getExtraAttribute('faceAgeClassScores')
        age_class_labels = itemList[i].getExtraAttribute('faceAgeClassLabels')
        if age_class_scores is None:
            itemList[i].setExtraAttribute('faceAgeClassScores', scores)
            itemList[i].setExtraAttribute('faceAgeClassLabels', label)
        else:
            itemList[i].setExtraAttribute('faceAgeClassScores', age_class_scores + scores)
            itemList[i].setExtraAttribute('faceAgeClassLabels', age_class_labels + label)

        # add label for each type of face found in image ('Child', 'Teenager', 'Adult'), according to the 'categorizationThreshold'
        if label is not None:
            itemList[i].setExtraAttribute('faceOf' + labels[max_val_idx].capitalize(), True)
        
        logger.debug('AgeEstimationTask: Age estimation scores for face: ' + str(scores))
        
        # add age estimation status
        itemList[i].setExtraAttribute('faceAgeEstimationStatus', 'success')
        # add skip age estimation info
        itemList[i].setExtraAttribute('faceAgeEstimationSkip', 'no')

        # update faces count and labels
        item_faces_count += 1
        item_faces_labels.add(labels[max_val_idx].capitalize())

        # cache age estimation data for faces within the image
        if (i+1) == len(itemList) or itemList[i].getHash() != itemList[i+1].getHash():
            cache = caseData.getCaseObject('age_estimation_cache')
            age_estimation_data = {'faceAgeClassScores': itemList[i].getExtraAttribute('faceAgeClassScores'),
                                   'faceAgeClassLabels': itemList[i].getExtraAttribute('faceAgeClassLabels'),
                                   'faceOf': item_faces_labels }
            cache.put(itemList[i].getHash(), age_estimation_data)
            caseData.putCaseObject('age_estimation_cache', cache)
            logger.info("AgeEstimationTask: Cache store for item with hash '" + itemList[i].getHash() + 
                        "': faces_count: " + str(item_faces_count) + "; faces_age_data: " + str(cache.get(itemList[i].getHash())))
            # reset faces count and labels
            item_faces_count = 0
            item_faces_labels = set()

'''
Perform age estimation for faces
'''
def makePrediction(imageList):
    logger.debug('AgeEstimationTask: Making predictions for batch of ' + str(len(imageList)) + ' faces.')

    global predictCount, predictTime 
    t = time.time()
    # load model and processor
    [model, processor] = loadModelAndProcessor()
    inputs = processor(images=imageList, return_tensors="pt")
    try:
        if semaphore is not None:
            semaphore.acquire()
        with torch.no_grad():
            # run the classification model
            outputs = model(**inputs)
            logits = outputs.logits
            # store probabilities associated to each age class for the face
            preds = torch.nn.functional.softmax(logits, dim=1).tolist()
            predictCount += len(imageList)
        predictTime += time.time() - t
    finally:
        if semaphore is not None:
            semaphore.release()
    return preds
