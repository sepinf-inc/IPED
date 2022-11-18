﻿# Python nudity detection based on yahoo open_nsfw algorithm.
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
        self.itemList = []
        self.imageList = []
        self.queued = False

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
        
        if not item.isQueueEnd() and not self.queued:
            javaTask.get().sendToNextTaskSuper(item)
            return
        
        if self.isToProcessBatch(item):
        
            for i in self.itemList:
                javaTask.get().sendToNextTaskSuper(i)
            
            self.itemList.clear()
            self.imageList.clear()
            
        if item.isQueueEnd():
            javaTask.get().sendToNextTaskSuper(item)
    
    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return size >= batchSize or (size > 0 and item.isQueueEnd())
    
    
    def process(self, item):
        
        self.queued = False
    
        if not item.isQueueEnd() and not supported(item):
            return
            
        try:
            if item.getHash() is not None:
                cache = caseData.getCaseObject('nsfw_score_cache')
                score = cache.get(item.getHash())
                if score is not None:
                    item.setExtraAttribute('nsfw_nudity_score', score)
                    return
            
            #print('Processing ' + item.getPath())
            img = None
            
            if isSupportedVideo(item):
                processVideoFrames(item)
                return
                
            from keras.preprocessing import image
                
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
                
                x = image.img_to_array(img)
                self.imageList.append(x)
                self.itemList.append(item)
                self.queued = True
            
        except Exception as e:
            item.setExtraAttribute('nsfw_error', 2)
            raise e
            
        if self.isToProcessBatch(item):
            processImages(self.imageList, self.itemList)
    
    
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