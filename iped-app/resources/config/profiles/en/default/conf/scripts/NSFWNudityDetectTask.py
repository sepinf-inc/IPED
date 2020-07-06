# Python nudity detection based on yahoo open_nsfw algorithm.
# You need to install jep (pip install jep==3.8.2) and include jep.dll(.so) in PATH or LD_LIBRARY_PATH (Linux)
# You also need to install keras with tensorflow backend.
# This must be installed in TaskInstaller.xml to be executed.

import traceback
import keras
from keras.models import Model
from keras import layers
import keras.backend as K
from keras.preprocessing import image
from keras.applications.imagenet_utils import preprocess_input
from keras.models import load_model

import numpy as np
import sys

def __flatten(name, input):
    if input.shape.ndims > 2: return layers.Flatten(name = name)(input)
    else: return input


def getName():
	return ("NSFWNudityDetectTask")


def init(confProps, configFolder):
	if caseData.getCaseObject('nsfw_model') is None:
		from java.lang import System
		file = System.getProperty('iped.root') + '/models/nsfw-keras-1.0.0.h5'
		model = load_model(file)
		#compile predict function to be used by multiple threads
		model._make_predict_function()
		caseData.putCaseObject('nsfw_model', model)
		logger.info('Loaded NSFW model ' + file)
	return


def finish():
	return


def process(item):

	if not item.getMediaType().toString().startswith('image'):
		return

	model = caseData.getCaseObject('nsfw_model')
	
	#print('Processing ' + item.getPath())
	img_path = item.getTempFile().getAbsolutePath()
	img = image.load_img(img_path, target_size=(224, 224))
	x = image.img_to_array(img)
	x = np.expand_dims(x, axis=0)
	x = preprocess_input(x)
	preds = model.predict(x)
	#print('Predicted:', preds)
	item.setExtraAttribute('nsfw_nudity_score', preds[0][1])