# Tarefa de processamento em Python que calcula score nudez baseada no algoritmo deeplearning open NSFW do yahoo adaptado
# Necessário instalar o keras e tensorflow, além do jep (https://github.com/ninia/jep)
# E incluir a jep.dll(.so) no PATH (win) ou LD_LIBRARY_PATH (Linux)

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

# Retorna o nome da tarefa.
def getName():
	return ("NSFWNudityDetectTask")

# Realiza alguma inicialização da tarefa, como acessar opções e arquivos de configuração.
# É executado antes de iniciar o processamento dos itens do caso.
# @Params
# confProps: arquivo java Properties com opções gerais do processamento
# configFolder: diretório de configurações extras, onde a tarefa pode criar um arquivo de configuração próprio
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


# Finaliza a tarefa, podendo limpar recursos. É executado após o término do processamento de todos os itens do caso.
# São disponibilizados os objetos ipedCase e searcher, podendo ser realizadas consultas no caso e criados bookmarks, por exemplo.
# TODO: documentar métodos desses objetos.
def finish():
	return

# Realiza o processamento do objeto "item" da classe EvidenceFile. Esta função é executada sobre todos os itens do caso.
# Pode utilizar qualquer método da classe EvidenceFile:
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