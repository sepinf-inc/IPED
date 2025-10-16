'''
# Age estimation feature based on Open Age Detection project (https://huggingface.co/prithivMLmods/open-age-detection)
# AgeEstimationTask.py - by Marcos Moura
# Requirements: See https://github.com/sepinf-inc/IPED/wiki/User-Manual#AgeEstimation
# Implementation notes:
#  - Images' faces are extracted using 'face_locations' extra attributes ('FaceRecognitionTask' must be enabled!).
#  - Processes files in batches for improved performance (see 'batchSize' config property).
#  - Stores age estimation results in items' extra attributes.
#    + 'faceAge:scores' stores age estimation scores (values order is the same as 'face_locations')
#      - scores (range: 0-100)
#    + 'faceAge:labels' stores age estimation labels (values order is the same as 'face_locations')
#      - labels (Child: 0-12; Teenager: 13-20; Adult: 21-44; MiddleAge: 45-64; Aged: 65+; Unlabeled: all scores are below 'categorizationThreshold' config property)
#    + 'faceAge:count:<label>' stores the count of faces with the corresponding label
#    + 'faceAge:maxScore:<label>' stores the highest score for the corresponding label across faces
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

# age estimation cache (avoids age estimation of duplicates)
from java.util.concurrent import ConcurrentHashMap
cache = ConcurrentHashMap()

# Margins proportions added to the face rectangle before submitting to the age estimation model.
topMargin = 0.50
bottomMargin = 0.20
sidesMargin = 0.05

# variables related to statistics
classificationSuccess = 0
classificationFail = 0
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
    videoSubitems = False

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
        
        faceRecognitionTaskEnabled = configuration.getEnableTaskProperty('enableFaceRecognition')
        if AgeEstimationTask.enabled and not faceRecognitionTaskEnabled:
            logger.error('To use the AgeEstimation task, you must enable the FaceRecognition task by setting enableFaceRecognition = true. AgeEstimation has been disabled.')
            AgeEstimationTask.enabled = False

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

        # load 'VideoThumbsSubitems' configuration property from 'VideoThumbsConfig'
        from iped.engine.config import VideoThumbsConfig
        videoConfig = configuration.findObject(VideoThumbsConfig)
        AgeEstimationTask.videoSubitems = videoConfig.getVideoThumbsSubitems()

        # load task configuration properties
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
            # clear age estimation cache
            cache.clear()

            # total time to perform age estimation for faces
            age_estimation_time = predictTime / numThreads

            # summary statistics
            totClassifications = classificationSuccess + classificationFail
            totSkipCount = skipHashDBFilesCount + skipDuplicatesCount
            logger.info('AgeEstimationTask: Total count of files processed: ' + str(totClassifications + totSkipCount - skipDuplicatesCount))

            # statistics for files for age estimation
            if totClassifications > 0:
                logger.info('AgeEstimationTask:  Files for age estimation: ' + str(totClassifications - skipDuplicatesCount))
                logger.info('AgeEstimationTask:   Successful age estimation: ' + str(classificationSuccess - skipDuplicatesCount))
                logger.info('AgeEstimationTask:   Failed age estimation: ' + str(classificationFail))
                if predictCount > 0:
                    logger.info('AgeEstimationTask:   Total time to perform age estimation for faces (s): ' + str(round(age_estimation_time, 3)))
                    logger.info('AgeEstimationTask:   Faces with age estimation performed: ' + str(predictCount))
                    logger.info('AgeEstimationTask:   Average age estimation time (ms/face): ' + str(round(age_estimation_time * 1000 / predictCount, 3)))
                    logger.info('AgeEstimationTask:   Average age estimation throughput (faces/s): ' + str(round(predictCount / age_estimation_time)))

            # statistics for files with skipped age estimation
            if totSkipCount > 0:
                logger.info('AgeEstimationTask:  Files with skipped age estimation: ' + str(totSkipCount))
                logger.info('AgeEstimationTask:   Skipped age estimation by hashDBFiles: ' + str(skipHashDBFilesCount))
                logger.info('AgeEstimationTask:   Skipped age estimation by duplicates: ' + str(skipDuplicatesCount))
            

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
    
        # does not process item if any condition is met
        if (not item.isQueueEnd() and not supported(item)) or (not item.isToAddToCase()):
            return

        if not item.isQueueEnd():
            # does not process image without faces
            from iped.properties import ExtraProperties
            face_count = item.getExtraAttribute(ExtraProperties.FACE_COUNT)
            if face_count is None or face_count == 0:
                return

            img = None
            global classificationSuccess, classificationFail, skipHashDBFilesCount, skipDuplicatesCount

            try:
                # skip age estimation for faces within images with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
                from iped.engine.task import HashDBLookupTask
                if (skipHashDBFiles and item.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) is not None):
                    # add skip age estimation info
                    item.setExtraAttribute('faceAge:estimationStatus', 'skipped_hashdb')
                    skipHashDBFilesCount += 1
                    return

                # skip age estimation for faces within images duplicates if age estimation data exists in cache
                # retrieve age estimation data from cache
                age_estimation_data = cache.get(item.getHashValue())
                if age_estimation_data is not None:
                    # age estimation data exists in cache
                    # add age estimation scores and labels
                    item.setExtraAttribute('faceAge:scores', age_estimation_data['faceAgeScores'])
                    item.setExtraAttribute('faceAge:labels', age_estimation_data['faceAgeLabels'])
                    # add face labels counts ('faceAge:count:<label>')
                    for label in age_estimation_data['faceAgeLabelsCounts']:
                        item.setExtraAttribute('faceAge:count:' + uncapitalize(label), age_estimation_data['faceAgeLabelsCounts'][label])
                    # add the highest score for each label across faces ('faceAge:maxScore:<label>')
                    for label in age_estimation_data['faceAgeLabelsScores']:
                        item.setExtraAttribute('faceAge:maxScore:' + uncapitalize(label), age_estimation_data['faceAgeLabelsScores'][label])
                    # add age estimation status
                    item.setExtraAttribute('faceAge:estimationStatus', 'success')
                    classificationSuccess += 1
                    skipDuplicatesCount += 1
                    return
                
                logger.debug('AgeEstimationTask: Processing item: ' + item.getPath())
                logger.debug('AgeEstimationTask: face_count: ' + str(face_count))

                # handle tiff:Orientation attribute
                try:
                    # get tiff:Orientation attribute
                    tiff_orient = int(item.getMetadata().get("image:tiff:Orientation"))
                except:
                    # if item has no tiff:Orientation attribute
                    tiff_orient = 1

                # get absolute path for image or video or quit processing if another media type
                mediaType = item.getMediaType().toString()
                if mediaType.startswith('image'):
                    if item.getViewFile() is not None and os.path.exists(item.getViewFile().getAbsolutePath()):
                        img_path = item.getViewFile().getAbsolutePath()
                        tiff_orient = 1
                    else:
                        img_path = item.getTempFile().getAbsolutePath()
                elif mediaType.startswith('video') and not AgeEstimationTask.videoSubitems:
                    img_path = item.getViewFile().getAbsolutePath()
                else:
                    return

                # allows usage of functions defined in 'FaceRecognitionProcess'
                import FaceRecognitionProcess

                # load image
                img = PilImage.open(img_path)
                img = FaceRecognitionProcess.convertToRGB(img)
                
                # image rotation, when necessary
                img = np.array(img)
                img = FaceRecognitionProcess.rotateImg(img, tiff_orient)
                img = PilImage.fromarray(img)

                # get face_locations
                face_locations = item.getExtraAttribute(ExtraProperties.FACE_LOCATIONS)
                if face_locations is not None and len(face_locations) > 0:
                    # iterate through face_locations
                    for face_location in face_locations:
                        logger.debug('AgeEstimationTask: face_location: ' + str(face_location))

                        # get face locaction and image dimensions
                        top, right, bottom, left = face_location
                        width, height = img.size

                        # calculate margins, as proportions of the face rectangle
                        mTop = int(topMargin * (bottom - top))
                        mBottom = int(bottomMargin * (bottom - top))
                        mSides = int(sidesMargin * (right - left))

                        # add margins, trying to include the whole person's head
                        top = max(0, top - mTop)
                        bottom = min(img.height, bottom + mBottom)
                        left = max(0, left - mSides)
                        right = min(img.width, right + mSides)

                        # extract the portion of the image corresponding to the face + border
                        face_img = img.crop((left, top, right, bottom))
                        
                        # add face item and face image to the corresponding lists
                        self.faceItems.append(item)
                        self.faceImages.append(face_img)
                
                self.itemList.append(item)
                self.queued = True
                
            except Exception as e:
                classificationFail += 1
                if img is None:
                    # load image problem
                    # add age estimation status
                    item.setExtraAttribute('faceAge:estimationStatus', 'failed_invalid_image')
                    logger.warn("AgeEstimationTask: 'faceAge:estimationStatus -> failed_invalid_image' for item: " + item.getPath())
                else:
                    # other problem
                    # add age estimation status
                    item.setExtraAttribute('faceAge:estimationStatus', 'failed_preprocessing')
                    logger.warn("AgeEstimationTask: 'faceAge:estimationStatus -> failed_preprocessing' for item: " + item.getPath())
                raise e
        
        # process faces for age estimation
        if self.isToProcessBatch(item):
            processImages(self.faceItems, self.faceImages)
    
'''
Check if item is supported
'''
def supported(item):
    return item.getHashValue() is not None and item.getExtraAttribute('hasThumb')

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
    
    # store counts for faces, count of faces for each label, and highest score for each label across faces
    item_faces_count = 0
    item_faces_labels_counts = {'Child': 0, 'Teenager': 0, 'Adult': 0, 'MiddleAge': 0, 'Aged': 0}
    item_faces_labels_max_scores = [0.0, 0.0, 0.0, 0.0, 0.0]

    for i in range(len(itemList)):
        item_faces_count += 1

        # get age estimation scores (range: 0-100) and labels for classes
        # class -> label: '0' (Child: 0-12); '1' (Teenager: 13-20); '2' (Adult: 21-44); '3' (MiddleAge: 45-64); class '4' (Aged: 65+)
        labels = {0: 'Child', 1: 'Teenager', 2: 'Adult', 3: 'MiddleAge', 4: 'Aged'}
        prob_class0 = round(preds[i][0] * 100, 1)
        prob_class1 = round(preds[i][1] * 100, 1)
        prob_class2 = round(preds[i][2] * 100, 1)
        prob_class3 = round(preds[i][3] * 100, 1)
        prob_class4 = abs(round(100 - (prob_class0 + prob_class1 + prob_class2 + prob_class3), 1))
        scores = [prob_class0, prob_class1, prob_class2, prob_class3, prob_class4]

        # set face label according to the 'categorizationThreshold' ('Unlabeled' if all scores are below the threshold)
        max_val = max(scores)
        if max_val >= categorizationThreshold:
            max_val_idx = scores.index(max_val)
            label = [labels[max_val_idx]]
            item_faces_labels_counts[labels[max_val_idx]] += 1
        else:
            label = ['Unlabeled']

        # update the highest score for each label across faces
        item_faces_labels_max_scores = [max(a, b) for a, b in zip(item_faces_labels_max_scores, scores)]

        # set face scores
        scores = [scores]
        age_class_scores = itemList[i].getExtraAttribute('faceAge:scores')
        age_class_labels = itemList[i].getExtraAttribute('faceAge:labels')
        if age_class_scores is None:
            itemList[i].setExtraAttribute('faceAge:scores', scores)
            itemList[i].setExtraAttribute('faceAge:labels', label)
        else:
            itemList[i].setExtraAttribute('faceAge:scores', age_class_scores + scores)
            itemList[i].setExtraAttribute('faceAge:labels', age_class_labels + label)
        
        logger.debug('AgeEstimationTask: Age estimation for face - scores:' + str(scores) + '; label:' + str(label))

        # add age estimation status
        itemList[i].setExtraAttribute('faceAge:estimationStatus', 'success')

        # check if this is the last face
        if (i+1) == len(itemList) or itemList[i].getId() != itemList[i+1].getId():
            # add face labels counts ('faceAge:count:<label>')
            for label in item_faces_labels_counts:
                itemList[i].setExtraAttribute('faceAge:count:' + uncapitalize(label), item_faces_labels_counts[label])

            # add the highest score for each label across faces ('faceAge:maxScore:<label>')
            item_faces_labels_max_scores_dict = dict(zip(list(labels.values()), item_faces_labels_max_scores))
            for label in item_faces_labels_max_scores_dict:
                itemList[i].setExtraAttribute('faceAge:maxScore:' + uncapitalize(label), item_faces_labels_max_scores_dict[label])

            global classificationSuccess
            classificationSuccess += 1

            # store age estimation data in cache
            age_estimation_data = {'faceAgeScores': itemList[i].getExtraAttribute('faceAge:scores'),
                                   'faceAgeLabels': itemList[i].getExtraAttribute('faceAge:labels'),
                                   'faceAgeLabelsCounts': item_faces_labels_counts,
                                   'faceAgeLabelsScores': item_faces_labels_max_scores_dict }
            cache.put(itemList[i].getHashValue(), age_estimation_data)
            logger.debug("AgeEstimationTask: Cache store for item with hash '" + itemList[i].getHashValue().toString() + 
                         "': faces_count: " + str(item_faces_count) + "; faces_age_data: " + str(cache.get(itemList[i].getHashValue())))
            
            # reset counts for faces, count of faces for each label, and highest score for each label across faces
            item_faces_count = 0
            item_faces_labels_counts = {'Child': 0, 'Teenager': 0, 'Adult': 0, 'MiddleAge': 0, 'Aged': 0}
            item_faces_labels_max_scores = [0.0, 0.0, 0.0, 0.0, 0.0]

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

def uncapitalize(s: str) -> str:
    return s[:1].lower() + s[1:]