# Python nudity detection based on yahoo open_nsfw algorithm.
# You need to install jep (pip install jep==3.9.1) and include jep.dll(.so) in PATH or LD_LIBRARY_PATH (Linux)
# You also need to install keras and tensorflow backend.
# This must be installed in TaskInstaller.xml to be executed.
      
import traceback
import keras
from keras.models import Model
from keras import layers
import keras.backend as K
from keras.preprocessing import image
from keras.applications.imagenet_utils import preprocess_input
from keras.models import load_model
from PIL import Image as PilImage
import numpy as np
import io
import time
import sys
from java.lang import System

useImageThumbs = True

itemList = []
imageList = []
batchSize = 50
targetSize = (224, 224)
videoFramesTime = 0
arrayConvTime = 0
predictTime = 0
loadImgTime = 0
queued = False

def __flatten(name, input):
    if input.shape.ndims > 2: return layers.Flatten(name = name)(input)
    else: return input


def getName():
	return ("NSFWNudityDetectTask")
	
def processQueueEnd():
	return True

def loadModel():
	model = caseData.getCaseObject('nsfw_model')
	if model is None:
		file = System.getProperty('iped.root') + '/models/nsfw-keras-1.0.0.h5'
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

def init(confProps, configFolder):
	loadModel()
	return

def finish():
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

def isSupportedVideo(item):
	return item.getMediaType() is not None and item.getMediaType().toString().startswith('video') and item.getViewFile() is not None
	
def isImage(item):
	return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')

def supported(item):
	return item.getLength() > 0 and (isImage(item) or isSupportedVideo(item))

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
	
def sendToNextTask(item):
	
	if not item.isQueueEnd() and not queued:
		javaTask.sendToNextTaskSuper(item)
		return
	
	if isToProcessBatch(item):
		for i in itemList:
			javaTask.sendToNextTaskSuper(i)
		
		itemList.clear()
		imageList.clear()
		
	if item.isQueueEnd():
		javaTask.sendToNextTaskSuper(item)
		

def isToProcessBatch(item):
	size = len(itemList)
	return size >= batchSize or (size > 0 and item.isQueueEnd())

def process(item):
	
	global queued
	queued = False

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
			imageList.append(x)
			itemList.append(item)
			queued = True
		
	except Exception as e:
		item.setExtraAttribute('nsfw_error', 2)
		raise e
		
	if isToProcessBatch(item):
		processImages(imageList, itemList)


def processVideoFrames(item):
	global videoFramesTime
	t = time.time()
	frames = ImageUtil.getBmpFrames(item.getViewFile())
	videoFramesTime += time.time() - t 
	list = []
	for i in range(frames.size()):
		input = convertJavaByteArray(frames.get(i))
		img = loadRawImage(input)
		x = image.img_to_array(img)
		list.append(x)
		
	preds = makePrediction(list)
	
	scores = []
	for i in range(len(list)):
		scores.append(preds[i][1])
	
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
	logger.info('Processing batch of ' + str(len(imageList)) + " images.")
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
	x = preprocess_input(x)
	model = loadModel()
	preds = model.predict(x)
	predictTime += time.time() - t
	return preds