'''
# Age estimation feature based on Open Age Detection project (https://huggingface.co/prithivMLmods/open-age-detection)
# AgeEstimationTask.py - by Marcos Moura
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#AgeEstimation
'''
import io, os, time, sys
from java.lang import System

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

# variables related to statistics
classificationSuccess = 0
classificationFail = 0
imageNoFacesCount = 0
skipHashDBFilesCount = 0
skipDuplicatesCount = 0
predictCount = 0
predictTime = 0

# semaphore for concurrency control
semaphore = None

'''
Main class: AgeEstimationTask
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
        global batchSize, categorizationThreshold, skipHashDBFiles, maxThreads

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
        num_finishes = caseData.getCaseObject('age_estimation_num_finishes')
        if num_finishes is None:
            num_finishes = 0
        num_finishes += 1
        caseData.putCaseObject('age_estimation_num_finishes', num_finishes)
        
        if num_finishes == numThreads:
            # total time to perform age estimation for faces
            age_estimation_time = predictTime / numThreads
            caseData.putCaseObject('age_estimation_time', age_estimation_time)

            # summary statistics
            totClassifications = classificationSuccess + classificationFail
            totSkipCount = skipHashDBFilesCount + skipDuplicatesCount
            logger.info('AgeEstimationTask: Total count of files processed: ' + str(totClassifications + totSkipCount - skipDuplicatesCount + imageNoFacesCount))

            # statistics for files for age estimation
            if totClassifications > 0:
                logger.info('AgeEstimationTask:  Files for age estimation: ' + str(totClassifications))
                logger.info('AgeEstimationTask:   Successful age estimation: ' + str(classificationSuccess))
                logger.info('AgeEstimationTask:   Failed age estimation: ' + str(classificationFail))
                if predictCount > 0:
                    logger.info('AgeEstimationTask:   Total time to perform age estimation for faces (s): ' + str(round(age_estimation_time, 3)))
                    logger.info('AgeEstimationTask:   Faces with age estimation performed: ' + str(predictCount))
                    logger.info('AgeEstimationTask:   Average age estimation time (ms/face): ' + str(round(age_estimation_time * 1000 / predictCount, 3)))
                    logger.info('AgeEstimationTask:   Average age estimation throughput (faces/s): ' + str(round(predictCount / age_estimation_time)))

            # statistics for files with skipped age estimation
            if totSkipCount > 0:
                logger.info("AgeEstimationTask:  Files with skipped age estimation: {}", totSkipCount)
                logger.info("AgeEstimationTask:   Skipped age estimation by hashDBFiles: {}", skipHashDBFilesCount)
                logger.info("AgeEstimationTask:   Skipped age estimation by duplicates: {}", skipDuplicatesCount)

            # statistics for files not for age estimation
            logger.info('AgeEstimationTask:  Files not for age estimation (no faces found): ' + str(imageNoFacesCount))


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

        global classificationSuccess, classificationFail, imageNoFacesCount, skipHashDBFilesCount, skipDuplicatesCount
        
        try:
            # skip age estimation for faces within images with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
            from iped.engine.task import HashDBLookupTask
            if (skipHashDBFiles and item.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) is not None):
                # add skip age estimation info
                item.setExtraAttribute('faceAgeEstimationSkip', 'hashDB')
                skipHashDBFilesCount += 1
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
                        item.setExtraAttribute('faceAgeScores', age_estimation_data['faceAgeScores'])
                        item.setExtraAttribute('faceAgeLabels', age_estimation_data['faceAgeLabels'])                        
                        # add face labels count
                        for label in age_estimation_data['faceAgeLabelsCount']:
                            count = age_estimation_data['faceAgeLabelsCount'][label]
                            if count > 0:
                                item.setExtraAttribute('faceAgeIs' + label.capitalize(), count)
                        # add age estimation status
                        item.setExtraAttribute('faceAgeEstimationStatus', 'success')
                        # add skip age estimation info
                        item.setExtraAttribute('faceAgeEstimationSkip', 'duplicate')                
                        classificationSuccess += 1
                        skipDuplicatesCount += 1
                        return
            
            img = None
            # flags if item contains face
            item_with_face = False
            if isImage(item):
                # check if item has faces
                from iped.properties import ExtraProperties
                face_count = item.getExtraAttribute(ExtraProperties.FACE_COUNT)
                if face_count is not None and face_count > 0:
                    # item has at least one face
                    item_with_face = True
                    
                    logger.debug('AgeEstimationTask: Processing item: ' + item.getPath())
                    logger.debug('AgeEstimationTask: face_count: ' + str(face_count))

                    ######################################
                    ## TEST CODE -> REMOVE BEFORE RELEASE
                    #
                    # # save image (for test purposes only; to be removed before feature release)
                    # img_save_path = System.getProperty('iped.root') + '/models/age_estimation/img_save'
                    # os.makedirs(img_save_path, exist_ok=True)
                    if item.getViewFile() is not None and os.path.exists(item.getViewFile().getAbsolutePath()):
                        img_path = item.getViewFile().getAbsolutePath()
                    else:
                        img_path = item.getTempFile().getAbsolutePath()
                    img = PilImage.open(img_path)
                    img = img.convert("RGB")
                    # img.save(img_save_path + '/' + os.path.basename(img_path))
                    #
                    ##
                    ######################################
                    
                    # get face_locations
                    face_locations = item.getExtraAttribute(ExtraProperties.FACE_LOCATIONS)
                    if face_locations is not None and len(face_locations) > 0:
                        # iterate through face_locations
                        i = 1
                        for face_location in face_locations:
                            logger.debug('AgeEstimationTask: face_location: ' + str(face_location))

                            # extract the portion of the image corresponding to the face
                            top, right, bottom, left = face_location                            
                            face_img = img.crop((left, top, right, bottom))

                            ######################################
                            ## TEST CODE -> REMOVE BEFORE RELEASE
                            #
                            # # save face image (for test purposes only; to be removed before feature release)
                            # face_img_path = os.path.splitext(os.path.basename(img_path))[0] + '_face_' + str(i) + '.' + os.path.splitext(os.path.basename(img_path))[1]
                            # face_img.save(img_save_path + '/' + os.path.basename(face_img_path))
                            #
                            ##
                            ######################################
                            
                            # add face item and face image to the corresponding list
                            self.faceItems.append(item)
                            self.faceImages.append(face_img)
                            i += 1
                
            if not item.isQueueEnd():
                if img is None:
                    if item_with_face:
                        # add age estimation status
                        item.setExtraAttribute('faceAgeEstimationStatus', 'fail:NoImage')
                        classificationFail += 1
                    else:
                        # add skip age estimation info
                        item.setExtraAttribute('faceAgeEstimationSkip', 'noFace')
                        imageNoFacesCount += 1
                    return
                self.itemList.append(item)
                self.queued = True
            
        except Exception as e:
            # add age estimation status
            item.setExtraAttribute('faceAgeEstimationStatus', 'fail:Exception')
            classificationFail += 1
            raise e
        
        # process faces for age estimation
        if self.isToProcessBatch(item):
            processImages(self.faceItems, self.faceImages)
    
'''
Checks if item is an image
'''
def isImage(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')
       
'''
Checks if item is supported
'''
def supported(item):
    return isImage(item) and item.getLength() is not None and item.getLength() > 0

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
    if semaphore is None:
        from java.util.concurrent import Semaphore
        semaphore = Semaphore(maxThreads)
        caseData.putCaseObject('age_estimation_semaphore', semaphore)
   
'''
Process faces for age estimation
'''
def processImages(itemList, imageList):
    logger.debug('AgeEstimationTask: Processing batch of ' + str(len(itemList)) + ' faces.')

    # perform age estimation for faces
    preds = makePrediction(imageList)
    
    # store counts for faces and labels
    item_faces_count = 0
    item_faces_labels_count = {'child': 0, 'teenager': 0, 'adult': 0}

    for i in range(len(itemList)):
        item_faces_count += 1

        # get age estimation scores and labels: class '0' (child: 0-12); class '1' (teenager: 13-20); class '2+' (adult: 21+)
        labels = {0: 'child', 1: 'teenager', 2: 'adult'}
        prob_class0 = round(preds[i][0] * 100)
        prob_class1 = round(preds[i][1] * 100)
        prob_class2Plus = 100 - (prob_class0 + prob_class1)
        scores = [prob_class0, prob_class1, prob_class2Plus]

        # set face label ('child', 'teenager', or 'adult'), according to the 'categorizationThreshold' ('nolabel' is set if scores are below the threshold)
        max_val = max(scores)
        if max_val >= categorizationThreshold:
            max_val_idx = scores.index(max_val)
            label = [labels[max_val_idx]]
            item_faces_labels_count[labels[max_val_idx]] += 1
        else:
            label = ['nolabel']

        # set face scores
        scores = [scores]
        age_class_scores = itemList[i].getExtraAttribute('faceAgeScores')
        age_class_labels = itemList[i].getExtraAttribute('faceAgeLabels')
        if age_class_scores is None:
            itemList[i].setExtraAttribute('faceAgeScores', scores)
            itemList[i].setExtraAttribute('faceAgeLabels', label)
        else:
            itemList[i].setExtraAttribute('faceAgeScores', age_class_scores + scores)
            itemList[i].setExtraAttribute('faceAgeLabels', age_class_labels + label)
        
        logger.debug('AgeEstimationTask: Age estimation for face - scores:' + str(scores) + '; label:' + str(label))

        # add age estimation status
        itemList[i].setExtraAttribute('faceAgeEstimationStatus', 'success')
        # add skip age estimation info
        itemList[i].setExtraAttribute('faceAgeEstimationSkip', 'no')

        # check if this is the last face
        if (i+1) == len(itemList) or itemList[i].getHash() != itemList[i+1].getHash():
            # add face labels count
            for label, count in item_faces_labels_count.items():
                if count > 0:
                    itemList[i].setExtraAttribute('faceAgeIs' + label.capitalize(), count)

            global classificationSuccess
            classificationSuccess += 1

            # store age estimation data in cache
            cache = caseData.getCaseObject('age_estimation_cache')
            age_estimation_data = {'faceAgeScores': itemList[i].getExtraAttribute('faceAgeScores'),
                                   'faceAgeLabels': itemList[i].getExtraAttribute('faceAgeLabels'),
                                   'faceAgeLabelsCount': item_faces_labels_count }
            cache.put(itemList[i].getHash(), age_estimation_data)
            caseData.putCaseObject('age_estimation_cache', cache)
            logger.info("AgeEstimationTask: Cache store for item with hash '" + itemList[i].getHash() + 
                        "': faces_count: " + str(item_faces_count) + "; faces_age_data: " + str(cache.get(itemList[i].getHash())))
            
            # reset counts for faces and labels
            item_faces_count = 0
            item_faces_labels_count = {'child': 0, 'teenager': 0, 'adult': 0}

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
            # store probabilities associated with each age class for the face
            preds = torch.nn.functional.softmax(logits, dim=1).tolist()
            predictCount += len(imageList)
        predictTime += time.time() - t
    finally:
        if semaphore is not None:
            semaphore.release()
    return preds
