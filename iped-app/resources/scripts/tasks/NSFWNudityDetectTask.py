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
from collections import deque

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
        # List to accumulate items until a batch is formed
        self.itemList = []
        self.imageBytes = []
        # Queue for ALL items (single or batched) ready to be sent
        self.itemsToSend = deque()
        self.BATCH_LOCK = threading.Lock()

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
        """
        Acts as a simple sender. It drains the itemsToSend queue and sends
        everything that the process() method has prepared. The 'item'

        parameter is no longer needed for the logic itself.
        """
        items_to_send_now = deque()

        with self.BATCH_LOCK:
            # Drains the output queue to a local variable for sending.
            while self.itemsToSend:
                items_to_send_now.append(self.itemsToSend.popleft())

        # Sends all items that were ready.
        # This is done outside the lock.
        while items_to_send_now:
            self.javaTask.sendToNextTaskSuper(items_to_send_now.popleft())
    
    
    def process(self, item):                    
        try:
            # Stores the logic to determine if this item should be added to processing batch
            add_to_batch = True

            # Pass non-supported items through immediately
            if not item.isQueueEnd() and not supported(item):
                add_to_batch = False
            
            if(item.isQueueEnd()):
                add_to_batch = False
        
            # Check cache first
            if item.getHash() is not None:
                cache = caseData.getCaseObject('nsfw_score_cache')
                score = cache.get(item.getHash())
                if score is not None:
                    item.setExtraAttribute('nsfw_nudity_score', score)
                    add_to_batch = False
            
            # Videos are processed individually, not batched with images
            if isSupportedVideo(item):
                processVideoFrames(item)
                add_to_batch = False

            # --- Start of new batching logic for images ---
            img_bytes = None

            # Tries to load image bytes
            if(add_to_batch):
                from keras.preprocessing import image
                img = None
                if isImage(item) and not useImageThumbs and item.getTempFile() is not None:
                    img_path = item.getTempFile().getAbsolutePath()
                    img = image.load_img(img_path, target_size=targetSize)
                
                if isImage(item) and useImageThumbs and item.getExtraAttribute('hasThumb'):
                    input = convertJavaByteArray(item.getThumb())
                    img = loadRawImage(input)

                if not item.isQueueEnd():
                    if(img is not None):                        
                        img_bytes = image.img_to_array(img)

                    if img_bytes is None:    
                        item.setExtraAttribute('nsfw_error', 1)
                        add_to_batch = False                        

            with self.BATCH_LOCK:
                # Step 1: Classify the incoming item.
                # If supported, add to the batch list. Otherwise, add directly to the send queue.
                if add_to_batch:
                    self.itemList.append(item)
                    self.imageBytes.append(img_bytes)
                else:
                    self.itemsToSend.append(item)

                # Check if the batch is ready for processing
                size = len(self.itemList)
                is_batch_ready = size >= batchSize or (size > 0 and item.isQueueEnd())

                if is_batch_ready:
                    processImages(self.imageBytes, self.itemList)

                    # Move the now-processed items to the sending queue.
                    self.itemsToSend.extend(self.itemList)
                    self.itemList.clear()
                    self.imageBytes.clear()

        except Exception as e:
            # SAFETY NET: If any unexpected error occurs, log it and queue the
            # original item to be sent to the next task, ensuring it is not lost.
            with self.BATCH_LOCK:
                self.itemsToSend.append(item)
                        
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