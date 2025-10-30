"""
IPED task to detect Child Sexual Abuse Material (CSAM) using a TensorFlow or PyTorch based AI model.

The script must be enabled in IPED, and related PIP packages must be installed (tensorflow or torch, torchvision, timm, pillow).

On Linux, you need to install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.
see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
"""

__author__ = "Guilherme Dalpian"
__email__ = "gmdalpian@gmail.com"
__version__ = "0.8" # suporte ONNX

import traceback
import io
import os
import time
import sys
from java.lang import System
from iped.engine.task import HashDBLookupTask
from java.awt import Color
import numpy as np

# --- Placeholders for Late Loading ---
tf = None
tflite = None
keras = None
torch = None
nn = None
timm = None
transforms = None
Image = None
ort = None

# --- Global Configurations ---
PLUGIN_ENABLE_PROP = 'enableCSAMDetector'
CSAM_CONFIG_FILE = 'CSAMDetectorConfig.txt'
CSAM_SCORE = 'csamdetector:csam_score'
PORN_SCORE = 'csamdetector:porn_score'
OTHER_SCORE = 'csamdetector:other_score'
CSAMDETECTOR_CATEGORY = 'csamdetector:category'
MODEL_SEMAPHORE = None
CSAM_IMG_SIZE = 224

# Constantes de normalização do ImageNet (usadas por PyTorch-style ONNX)
IMG_MEAN_PYTORCH = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 1, 3)
IMG_STD_PYTORCH = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 1, 3)

# --- Global Control Variables ---
MOTOR_IA = None
MODELO_CARREGADO = None
DEVICE = None
CACHE = None
CLASS_NAMES = ['csam', 'porn', 'other']
NUM_CLASSES = len(CLASS_NAMES)
ONNX_MODEL_TYPE = None

# Configurable parameters defaults
PLUGIN_ENABLED = False
CSAM_MODELFILE = 'tensorflow_b0_v1.keras'
CSAM_BATCH_SIZE = 64
CSAM_MINIMUM_IMAGE_SIZE = 0  # in bytes
CSAM_SKIP_DIMENSION = 0  # in pixels
CSAM_SKIP_HASHDB_FILES = 'false'  # skip files with hits on IPED HashDB database
CSAM_CREATE_BOOKMARKS = 'false'

CSAM_MODELFILE_PROPERTY = 'CSAMModelFile'
CSAM_BATCH_SIZE_PROPERTY = 'CSAMBatchSize'
CSAM_MINIMUM_IMAGE_SIZE_PROPERTY = 'CSAMMinimumImageSize'
CSAM_SKIP_DIMENSION_PROPERTY = 'CSAMSkipDimension'
CSAM_SKIP_HASHDB_FILES_PROPERTY = 'CSAMSkipHashDBFiles'
CSAM_CREATE_BOOKMARKS_PROPERTY = 'CSAMCreateBookmarks'

# AI constants
AI_CLASSIFICATION_SKIP_ATTR = "AIClassificationSkip"
AI_CLASSIFICATION_SKIP_NO = "no"
AI_CLASSIFICATION_SKIP_SIZE = "size"
AI_CLASSIFICATION_SKIP_DIMENSION = "dimension"
AI_CLASSIFICATION_SKIP_HASHDB = "hashDB"
AI_CLASSIFICATION_SKIP_DUPLICATE = "duplicate"

# =============================================================================
# LOADING AND PREDICTION LOGIC (UNIFIED)
# =============================================================================

def carregar_e_configurar_modelo():
    """Central function that loads the correct model (TF, PyTorch) or checks metadata (TFLite, ONNX)."""
    global MODELO_CARREGADO, DEVICE, CACHE, CSAM_IMG_SIZE, ONNX_MODEL_TYPE
    
    MODELO_CARREGADO = caseData.getCaseObject('csam_model_unificado')
    
    if MODELO_CARREGADO is None:
        caminho_modelo = System.getProperty('iped.root') + '/models/' + CSAM_MODELFILE
        if not os.path.exists(caminho_modelo):
            logger.warn(f"FATAL ERROR: Model file not found: {caminho_modelo}")
            return None

        nome_modelo_lower = CSAM_MODELFILE.lower()
        if "_s_" in nome_modelo_lower:
            CSAM_IMG_SIZE = 384
        elif "_m_" in nome_modelo_lower or "_l_" in nome_modelo_lower:
            CSAM_IMG_SIZE = 480
        else: # Default for B0
            CSAM_IMG_SIZE = 224
        logger.info(f"Image size set to {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE} based on model name.")

        if MOTOR_IA == 'tensorflow':
            try:
                logger.info(f"Loading TensorFlow model from: {caminho_modelo}")
                MODELO_CARREGADO = keras.models.load_model(caminho_modelo)
                logger.info("TensorFlow model loaded successfully.")
            except Exception as e:
                logger.warn(f"FATAL ERROR loading TensorFlow model: {e}")
                return None
        
        elif MOTOR_IA == 'pytorch':
            try:
                logger.info(f"Loading PyTorch model from: {caminho_modelo}")
                DEVICE = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
                
                # 1. Carregar o checkpoint (dicionário) primeiro
                checkpoint = torch.load(caminho_modelo, map_location=DEVICE)
                
                # 2. Obter metadados de dentro do checkpoint
                # Usamos .get() para segurança, caso uma chave não exista
                model_name_key = checkpoint.get('model_name', 'B0') # Pega o nome 'S', 'B0', etc.
                num_classes_saved = checkpoint.get('num_classes', NUM_CLASSES)
                img_size_saved = checkpoint.get('img_size')

                # 3. Usar os metadados salvos para configurar o modelo
                if img_size_saved:
                    CSAM_IMG_SIZE = img_size_saved # Sobrepõe o valor baseado no nome do arquivo
                    logger.info(f"Image size set to {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE} from model checkpoint.")
                
                # Constrói o nome do modelo 'timm' com base na chave 'model_name'
                model_map = {
                    'B0': 'tf_efficientnetv2_b0.in1k',
                    'S': 'tf_efficientnetv2_s.in21k_ft_in1k',
                    'M': 'tf_efficientnetv2_m.in21k_ft_in1k',
                    'L': 'tf_efficientnetv2_l.in21k_ft_in1k'
                }
                # Se o nome não estiver no mapa, usa o B0 como padrão
                modelo_timm = model_map.get(model_name_key, 'tf_efficientnetv2_b0.in1k')
                logger.info(f"Building model architecture: {modelo_timm}")

                # 4. Criar o modelo com a arquitetura correta
                MODELO_CARREGADO = timm.create_model(modelo_timm, pretrained=False, num_classes=num_classes_saved)
                
                # 5. Carregar o state_dict CORRETO (aqui está a correção principal)
                if 'model_state_dict' in checkpoint:
                    MODELO_CARREGADO.load_state_dict(checkpoint['model_state_dict'])
                else:
                    # Tenta carregar como um checkpoint antigo (só por segurança)
                    MODELO_CARREGADO.load_state_dict(checkpoint)
                
                MODELO_CARREGADO.to(DEVICE)
                MODELO_CARREGADO.eval()
                
                logger.info(f"PyTorch model ({model_name_key}, {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}) loaded on {DEVICE}.")
            
            except Exception as e:
                logger.warn(f"FATAL ERROR loading PyTorch model: {e}")
                # Imprime o traceback completo no log do IPED para facilitar a depuração
                logger.warn(traceback.format_exc()) 
                return None
                
        elif MOTOR_IA == 'tflite': 
            try:
                logger.info(f"Loading TFLite model from: {caminho_modelo}")                
                MODELO_CARREGADO = tflite.Interpreter(model_path=caminho_modelo)
                input_details = MODELO_CARREGADO.get_input_details()[0]
                output_details = MODELO_CARREGADO.get_output_details()[0]
    
                input_details = MODELO_CARREGADO.get_input_details()[0]
                CSAM_IMG_SIZE = input_details['shape'][1]
                input_dtype = input_details['dtype']
                is_quantized = input_dtype == np.int8

                logger.info(f"Modelo espera imagens de tamanho: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                if is_quantized:
                    logger.info("Modelo quantizado (INT8) detectado.")                
                logger.info("TFLite model loaded successfully.")
                
            except Exception as e:
                logger.warn(f"FATAL ERROR loading TFLite model: {e}")
                return None            
        
        elif MOTOR_IA == 'onnx':
            try:
                logger.info(f"Reading ONNX metadata from: {caminho_modelo}")
                session_options = ort.SessionOptions()
                session_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_BASIC 
                
                MODELO_CARREGADO = ort.InferenceSession(
                    caminho_modelo, 
                    sess_options=session_options, 
                    providers=['CPUExecutionProvider'] 
                )
                
                input_details = MODELO_CARREGADO.get_inputs()[0]
                input_shape = input_details.shape
                
                # PyTorch: [batch, 3, height, width]
                if input_shape[1] == 3 and isinstance(input_shape[2], int) and isinstance(input_shape[3], int):
                    CSAM_IMG_SIZE = input_shape[2] 
                    ONNX_MODEL_TYPE = 'pytorch' # Define o tipo global
                    logger.info(f"ONNX (PyTorch-style, Channels-First) detected. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                # TensorFlow: [batch, height, width, 3]
                elif input_shape[3] == 3 and isinstance(input_shape[1], int) and isinstance(input_shape[2], int):
                    CSAM_IMG_SIZE = input_shape[1]
                    ONNX_MODEL_TYPE = 'tensorflow' # Define o tipo global
                    logger.info(f"ONNX (TensorFlow-style, Channels-Last) detected. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                else:
                    raise ValueError(f"Formato de entrada ONNX não reconhecido: {input_shape}")
                                
            except Exception as e:
                logger.warn(f"FATAL ERROR reading ONNX metadata: {e}")
                logger.warn(traceback.format_exc())
                return None                
        
        
        caseData.putCaseObject('csam_model_unificado', MODELO_CARREGADO)
        
    return MODELO_CARREGADO

def processar_imagem(item):
    """Loads and preprocesses the image to the correct format (tensor)."""
    global CSAM_IMG_SIZE, ONNX_MODEL_TYPE
    try:
        file_path = None
        if item.getViewFile() is not None and os.path.exists(item.getViewFile().getAbsolutePath()):
            file_path = item.getViewFile().getAbsolutePath()
        else:
            file_path = item.getTempFile().getAbsolutePath()  
            item.getTempFile().getAbsolutePath()

        if not os.path.exists(file_path):
            raise IOError("Temporary file not found")

        if MOTOR_IA == 'tensorflow' or MOTOR_IA == 'tflite':
            img = tf.io.read_file(file_path)
            img = tf.io.decode_image(img, channels=3, expand_animations=False)
            return tf.image.resize(img, [CSAM_IMG_SIZE, CSAM_IMG_SIZE])

        elif MOTOR_IA == 'pytorch':
            image = Image.open(file_path).convert('RGB')
            transform = transforms.Compose([
                transforms.Resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE)), transforms.ToTensor(),
                transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])])
            return transform(image)  

        elif MOTOR_IA == 'onnx':
            # ONNX usa PIL e Numpy para evitar dependência do torchvision
            image = Image.open(file_path).convert('RGB').resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE))
            
            # Pré-processamento estilo PyTorch (Channels-First, CHW)
            if ONNX_MODEL_TYPE == 'pytorch':
                img_array = np.array(image, dtype=np.float32) / 255.0
                img_array = (img_array - IMG_MEAN_PYTORCH) / IMG_STD_PYTORCH
                img_array = img_array.transpose(2, 0, 1) # HWC -> CHW
                return img_array.astype(np.float32) # Retorna np.ndarray (C, H, W)
            
            # Pré-processamento estilo TensorFlow (Channels-Last, HWC)
            elif ONNX_MODEL_TYPE == 'tensorflow':
                # Apenas converte para float32, normalização [0, 255] é embutida no modelo
                img_array = np.array(image, dtype=np.float32)
                return img_array # Retorna np.ndarray (H, W, C)           
            
    except Exception as e:
        logger.warn(f"Error processing image {item.getPath()}, trying thumbnail... {e}")
        try:
            image_bytes = bytes(b % 256 for b in item.getThumb())
            
            if MOTOR_IA == 'tensorflow' or MOTOR_IA == 'tflite':
                img = tf.io.decode_image(image_bytes, channels=3, expand_animations=False)
                return tf.image.resize(img, [CSAM_IMG_SIZE, CSAM_IMG_SIZE])
                
            elif MOTOR_IA == 'pytorch':
                image = Image.open(io.BytesIO(image_bytes)).convert('RGB')
                transform = transforms.Compose([
                    transforms.Resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE)), transforms.ToTensor(),
                    transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])])
                return transform(image)
                
            elif MOTOR_IA == 'onnx':
                # Fallback para thumbnail com ONNX (PIL/Numpy)
                image = Image.open(io.BytesIO(image_bytes)).convert('RGB').resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE))
                
                if ONNX_MODEL_TYPE == 'pytorch':
                    img_array = np.array(image, dtype=np.float32) / 255.0
                    img_array = (img_array - IMG_MEAN_PYTORCH) / IMG_STD_PYTORCH
                    img_array = img_array.transpose(2, 0, 1)
                    return img_array.astype(np.float32) 
                
                elif ONNX_MODEL_TYPE == 'tensorflow':
                    img_array = np.array(image, dtype=np.float32)
                    return img_array              
                
        except Exception as thumb_e:
            logger.error(f"Failed to process thumbnail for {item.getPath()}: {thumb_e}")
            return None

def createSemaphore():
    global MODEL_SEMAPHORE
    MODEL_SEMAPHORE = caseData.getCaseObject('CSAM_SEMAPHORE')
    if(MODEL_SEMAPHORE is None):
        from java.util.concurrent import Semaphore
        MODEL_SEMAPHORE = Semaphore(1)
        caseData.putCaseObject('CSAM_SEMAPHORE', MODEL_SEMAPHORE)
    return MODEL_SEMAPHORE
    
def extrair_e_formatar_dois_digitos(score):
  numero = score*100
  if numero >= 100: return "99"      
  return f'{int(numero):02d}' 

def isImage(item):
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image')

def softmax(x, axis=-1):
    """Calcula softmax de forma estável (necessário para ONNX e TFLite)."""
    e_x = np.exp(x - np.max(x, axis=axis, keepdims=True))
    return e_x / e_x.sum(axis=axis, keepdims=True)

def supported(item):
    supported = (
        item.getLength() is not None and
        item.getLength() > 0 and
        isImage(item) and
        item.getExtraAttribute('hasThumb') and
        item.getHash() is not None
    )
    return supported
    
'''
Main class
'''
class CSAMDetector:
    def __init__(self):
        self.itemList = []
        self.imageBytes = []
        modelo_tflite = None
        
        # Variáveis de instância para o modelo ONNX (uma por thread)
        self.onnx_thread_session = None
        self.onnx_input_name = None
        self.onnx_output_name = None        

    def isEnabled(self):
        return PLUGIN_ENABLED

    def processQueueEnd(self):
        return True
       
    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(PLUGIN_ENABLE_PROP, CSAM_CONFIG_FILE)]        

    def init(self, configuration):
        global PLUGIN_ENABLED, MOTOR_IA, CSAM_MODELFILE, CACHE, CSAM_BATCH_SIZE, CSAM_MINIMUM_IMAGE_SIZE, CSAM_SKIP_DIMENSION, CSAM_SKIP_HASHDB_FILES, tf, keras, torch, nn, timm, transforms, Image, tflite, ort, CSAM_IMG_SIZE, ONNX_MODEL_TYPE
        
        taskConfig = configuration.getTaskConfigurable(CSAM_CONFIG_FILE)
        PLUGIN_ENABLED = taskConfig.isEnabled()

        if not PLUGIN_ENABLED:
            return
        
        extraProps = taskConfig.getConfiguration()
        
        if(extraProps):
            CSAM_MODELFILE = extraProps.getProperty(CSAM_MODELFILE_PROPERTY) if extraProps.getProperty(CSAM_MODELFILE_PROPERTY) is not None else CSAM_MODELFILE
            CSAM_BATCH_SIZE = int(extraProps.getProperty(CSAM_BATCH_SIZE_PROPERTY)) if extraProps.getProperty(CSAM_BATCH_SIZE_PROPERTY) is not None else CSAM_BATCH_SIZE
            CSAM_MINIMUM_IMAGE_SIZE = int(extraProps.getProperty(CSAM_MINIMUM_IMAGE_SIZE_PROPERTY)) if extraProps.getProperty(CSAM_MINIMUM_IMAGE_SIZE_PROPERTY) is not None else CSAM_MINIMUM_IMAGE_SIZE
            CSAM_SKIP_DIMENSION = int(extraProps.getProperty(CSAM_SKIP_DIMENSION_PROPERTY)) if extraProps.getProperty(CSAM_SKIP_DIMENSION_PROPERTY) is not None else CSAM_SKIP_DIMENSION
            skipDBFiles = extraProps.getProperty(CSAM_SKIP_HASHDB_FILES_PROPERTY) if extraProps.getProperty(CSAM_SKIP_HASHDB_FILES_PROPERTY) is not None else CSAM_SKIP_HASHDB_FILES
            CSAM_SKIP_HASHDB_FILES = True if skipDBFiles.lower() == 'true' else False
            createbookmarks = extraProps.getProperty(CSAM_CREATE_BOOKMARKS_PROPERTY) if extraProps.getProperty(CSAM_CREATE_BOOKMARKS_PROPERTY) is not None else CSAM_CREATE_BOOKMARKS
            CSAM_CREATE_BOOKMARKS = True if createbookmarks.lower() == 'true' else False
            
        
        logger.debug(f"CSAM configurations: {CSAM_MODELFILE} {CSAM_BATCH_SIZE} {CSAM_MINIMUM_IMAGE_SIZE} {CSAM_SKIP_DIMENSION} {CSAM_SKIP_HASHDB_FILES}")
        
        from iped.engine.config import HashTaskConfig
        from iped.engine.config import ImageThumbTaskConfig 
        from iped.engine.config import VideoThumbsConfig
        
        hashConfig = configuration.findObject(HashTaskConfig).isEnabled()
        imageThumbsConfig = configuration.findObject(ImageThumbTaskConfig).isEnabled()
        videoThumbsConfig = configuration.findObject(VideoThumbsConfig).isEnabled()
        videoThumbsSubitems = configuration.findObject(VideoThumbsConfig).getVideoThumbsSubitems()
        
        requiredTasks = (
            hashConfig and
            imageThumbsConfig and
            videoThumbsConfig and
            videoThumbsSubitems
        )

        if not requiredTasks:
            logger.warn(
                "To use CSAMDetector the following functions must also be enabled: "
                "enableHash enableImageThumbs enableVideoThumbs enableVideoThumbsSubitems"
            )
            PLUGIN_ENABLED = False
            return  
           
        model_name_lower = CSAM_MODELFILE.lower()           
        if model_name_lower.endswith('.keras'):
            MOTOR_IA = 'tensorflow'
            if tf is None:
                logger.info("TensorFlow engine detected. Loading libraries...")
                os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
                import tensorflow as tf_module
                from tensorflow import keras as keras_module
                tf = tf_module
                keras = keras_module
                logger.info("TensorFlow libraries loaded.")
        
        elif model_name_lower.endswith('.pth'):
            MOTOR_IA = 'pytorch'
            if torch is None:
                logger.info("PyTorch engine detected. Loading libraries...")
                import torch as torch_module
                import torch.nn as nn_module
                from torchvision import transforms as transforms_module
                import timm as timm_module
                from PIL import Image as Image_module
                torch = torch_module
                nn = nn_module
                timm = timm_module
                transforms = transforms_module
                Image = Image_module
                logger.info("PyTorch libraries loaded.")
                
        elif model_name_lower.endswith('.tflite'):      
            MOTOR_IA = 'tflite'
            if tflite is None:
                logger.info("TFLite engine detected. Loading libraries...")
                os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
                # Tenta importar o interpretador leve primeiro, se falhar, usa o do TF completo
                import tensorflow as tf_module
                tf = tf_module
                tflite = tf.lite
                logger.info("Using TFLite from full TensorFlow package.")
                
        elif model_name_lower.endswith('.onnx'):
            MOTOR_IA = 'onnx'
            if ort is None:
                logger.info("ONNX engine detected. Loading libraries...")
                import onnxruntime as ort_module
                ort = ort_module
                # ONNX preprocessing usa PIL (mas não torchvision)
                if Image is None:
                    from PIL import Image as Image_module
                    Image = Image_module
                logger.info("ONNX Runtime and PIL libraries loaded.")    
                
        else:
            logger.warn(f"ERROR: Could not determine AI engine from model name: {CSAM_MODELFILE}")
            PLUGIN_ENABLED = False
            return

        # Carrega o modelo global (TF/PyTorch) ou lê metadados (TFLite/ONNX)
        # Esta chamada agora é feita para todos os motores
        if not carregar_e_configurar_modelo():
             PLUGIN_ENABLED = False
             return
             
        if(not CACHE):
            from java.util.concurrent import ConcurrentHashMap
            CACHE = ConcurrentHashMap()
            caseData.putCaseObject('csam_cache_unificado', CACHE)             
    
        # Em caso de TFLite ou ONNX, cada thread deve ter seu próprio interpretador/sessão
        # A verificação de metadados já foi feita em carregar_e_configurar_modelo
        # Aqui nós carregamos a instância real da thread
        if MOTOR_IA == 'tflite':
            caminho_modelo = System.getProperty('iped.root') + '/models/' + CSAM_MODELFILE
            self.modelo_tflite = tf.lite.Interpreter(model_path=caminho_modelo)
            self.modelo_tflite.allocate_tensors()
            # O CSAM_IMG_SIZE já foi definido na verificação inicial
            CSAM_BATCH_SIZE = 1 # TFLite é processado 1 a 1
            logger.debug(f"TFLite interpreter created for thread. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
            
        elif MOTOR_IA == 'onnx':
            logger.debug("Creating thread-local ONNX session for CPU...")
            caminho_modelo = System.getProperty('iped.root') + '/models/' + CSAM_MODELFILE
            session_options = ort.SessionOptions()
            session_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
            
            self.onnx_thread_session = ort.InferenceSession(
                caminho_modelo, 
                sess_options=session_options, 
                providers=['CPUExecutionProvider'] # Força CPU
            )
            
            input_details = self.onnx_thread_session.get_inputs()[0]
            self.onnx_input_name = input_details.name
            self.onnx_output_name = self.onnx_thread_session.get_outputs()[0].name
            
            # Apenas verifica se o tamanho da imagem da thread bate com o global
            if ONNX_MODEL_TYPE == 'pytorch':
                img_size_check = input_details.shape[2]
            else: # tensorflow
                img_size_check = input_details.shape[1]
                
            if img_size_check != CSAM_IMG_SIZE:
                logger.warn(f"ONNX image size mismatch! Meta: {CSAM_IMG_SIZE}, Thread: {img_size_check}")
                CSAM_IMG_SIZE = img_size_check # Usa o tamanho da sessão da thread
            
            CSAM_BATCH_SIZE = 1 # Processa um por um
            logger.debug(f"ONNX session created for CPU ({ONNX_MODEL_TYPE}-style). Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
            
        else:
            # semaphore is only used when processing in batches, tflite is multithreaded
            createSemaphore()

    def process(self, item):
        
        logger.debug(f"CSAMDetector: called process for item {item.getPath()} {item.getId()}")
        
        if not item.isQueueEnd() and not supported(item):
            return

        # don't process it again (in the report generation for example)
        csamscore = item.getExtraAttribute(CSAM_SCORE)
        if csamscore is not None:
            return                      
        
        # Skip very small images in bytes
        if item.getLength() is not None and item.getLength() < CSAM_MINIMUM_IMAGE_SIZE:                
            logger.debug(f"CSAMDetector: skipping very small image {item.getName()} {item.getLength()} bytes")
            item.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_SIZE)                
            return

        # Skip very small dimensions
        if(CSAM_SKIP_DIMENSION>0):
            if(isImage(item)):
                width_meta = item.getMetadata().get("image:Width")
                height_meta = item.getMetadata().get("image:Height")
                width = int(width_meta) if width_meta is not None else None
                height = int(height_meta) if height_meta is not None else None
                if(width is not None and height is not None and (width<CSAM_SKIP_DIMENSION or height<CSAM_SKIP_DIMENSION)):
                    logger.debug(f"CSAMDetector: skipping very small image {item.getName()} {width}x{height}")
                    item.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_DIMENSION)
                    return

        # Skip classification of images/videos with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
        if (CSAM_SKIP_HASHDB_FILES and item.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) is not None):
            logger.debug(f"CSAMDetector: skipping item with HashDB hit {item.getName()}")
            item.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_HASHDB)
            return
        
        if item.getHash():
            cache = caseData.getCaseObject('csam_cache_unificado')
            scores = cache.get(item.getHash())
            if scores is not None:
                try:
                    csam_score, porn_score, other_score, csam_category = scores
                    logger.debug(f"CSAMDetector: Found cached scores for {item.getName()}: csam={csam_score}, porn={porn_score}")
                    item.setExtraAttribute(CSAM_SCORE, csam_score)
                    item.setExtraAttribute(PORN_SCORE, porn_score)
                    item.setExtraAttribute(OTHER_SCORE, other_score)
                    item.setExtraAttribute(CSAMDETECTOR_CATEGORY, csam_category)
                    item.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_DUPLICATE)
                    return
                except (TypeError, ValueError):
                     logger.warn(f"CSAMDetector: Outdated cache format for hash {item.getHash()}. Reprocessing.")

        img_tensor = None
        
        if(not item.isQueueEnd()):
            img_tensor = processar_imagem(item)            
            if img_tensor is None:    
                item.setExtraAttribute('csam_error', 1)
                logger.error(f"CSAMDetector: error processing image: {item.getName()}, id {item.getId()}")
                return
            
            self.itemList.append(item)
            self.imageBytes.append(img_tensor)

        # Check if the batch needs to be flushed.
        # This happens if the batch is full, or if the end of the queue is signaled.
        if (self.isToProcessBatch(item)):
            logger.debug(f"CSAMDetector: processing batch of {len(self.itemList)} items.")
            self.processar_lote_de_imagens(self.itemList, self.imageBytes)


    def sendToNextTask(self, item):       
       
        isItemOnList = False
        
        # Checks if the item is in the list to be processed (e.g., not an image or queueend)
        if item in self.itemList:
            isItemOnList = True
    
        # Now we check if we just processed a batch, to clear the list and send everything to the next task
        if self.isToProcessBatch(item):                 
            for i in self.itemList:
                javaTask.get().sendToNextTaskSuper(i) 
            self.itemList.clear()
            self.imageBytes.clear()
            
        # If the item is not on the list, send it to the next task.
        if(not isItemOnList):
            javaTask.get().sendToNextTaskSuper(item) 


    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return size >= CSAM_BATCH_SIZE or (size > 0 and item.isQueueEnd())    


    def finish(self):              
        global CSAM_CREATE_BOOKMARKS
        
        logger.debug("CSAMDetector: CSAM analysis finished.")                
        
        if not CSAM_CREATE_BOOKMARKS:
            return

        CSAM_CREATE_BOOKMARKS = False
        
        bookmarks_to_create = [
            {
                "query": "csamdetector\:csam_score:[85 TO *]",
                "name": "Possible CSAM IA - 1 - Higher Confidence",
                "comment": "Possible CSAM files, high confidence",
                "color": [220, 20, 60]
            },
            {
                "query": "csamdetector\:csam_score:[60 TO 84]",
                "name": "Possible CSAM IA - 2 - Medium Confidence",
                "comment": "Possible CSAM files, medium confidence",
                "color": [255, 165, 0]
            },
            {
                "query": "csamdetector\:csam_score:[40 TO 59]",
                "name": "Possible CSAM IA - 3 - Low Confidence",
                "comment": "Possible CSAM files, low confidence",
                "color": [255, 255, 0]
            },
            {
                "query": "csamdetector\:category=porn",
                "name": "Probable Adult Porn (IA)",
                "comment": "Probable Porn files, for manual review",
                "color": [255, 105, 180]
            },
            {
                "query": "hashDb\:status:pedo",
                "name": "Probable CSAM - Hash Hit",
                "comment": "Probable CSAM - hash hit",
                "color": [255, 0, 0]
            }
        ]  

        # Itera sobre a lista e chama a função para cada item
        for bookmark_data in bookmarks_to_create:
            self.create_bookmark_from_query(
                query=bookmark_data["query"], 
                bookmark_name=bookmark_data["name"], 
                bookmark_comment=bookmark_data["comment"],
                color=bookmark_data["color"]
            )      
        
        return True
        
    def create_bookmark_from_query(self, query, bookmark_name, bookmark_comment, color):
        """
        Executa uma busca e, se encontrar resultados, cria um bookmark com eles.

        Args:
            query (str): A string de consulta para a busca.
            bookmark_name (str): O nome do bookmark a ser criado.
            bookmark_comment (str): O comentário para o bookmark.
        """
        
        # Define e executa a consulta
        searcher.setQuery(query)
        ids = searcher.search().getIds()
        
        # Cria o bookmark mesmo que vazio
        bookmarks = ipedCase.getBookmarks()
        bookmark_id = bookmarks.newBookmark(bookmark_name)
        bookmarks.setBookmarkComment(bookmark_id, bookmark_comment)
        if(color):
            bookmarks.setBookmarkColor(bookmark_id, Color(color[0], color[1], color[2]))        
        
        # Verifica se houve resultados
        if ids and len(ids) > 0:
            # Cria e configura o novo bookmark
            bookmarks.addBookmark(ids, bookmark_id)

        # Salva as alterações
        bookmarks.saveState(True)       
        
       
    def fazer_predicao(self, tensores):
        """Runs batch prediction, returning the full probability array."""
        global MODEL_SEMAPHORE, MOTOR_IA, DEVICE, MODELO_CARREGADO
        
        try:
            if MOTOR_IA == 'tensorflow' or MOTOR_IA == 'pytorch':
                if MODEL_SEMAPHORE is not None:
                    MODEL_SEMAPHORE.acquire()
            
            if MOTOR_IA == 'tensorflow':
                return MODELO_CARREGADO.predict(tf.stack(tensores), verbose=0)
            
            elif MOTOR_IA == 'pytorch':
                with torch.no_grad():
                    outputs = MODELO_CARREGADO(torch.stack(tensores).to(DEVICE))
                    return torch.nn.functional.softmax(outputs, dim=1).cpu().numpy()
            
            elif MOTOR_IA == 'tflite':                
                interpreter = self.modelo_tflite
                input_details = interpreter.get_input_details()[0]
                output_details = interpreter.get_output_details()[0]
                is_quantized = input_details['dtype'] == np.int8
                
                predictions = []
                for tensor in tensores: # Lote sempre será 1
                    input_tensor = np.expand_dims(tensor.numpy(), axis=0)
                    
                    if is_quantized:
                        input_tensor = (input_tensor.astype(np.float32) - 128).astype(np.int8)

                    interpreter.set_tensor(input_details['index'], input_tensor)
                    interpreter.invoke()
                    output_data = interpreter.get_tensor(output_details['index'])
                    
                    if is_quantized:
                        # 1. Dequantiza os logits
                        scale, zero_point = output_details['quantization']
                        dequantized_logits = (output_data.astype(np.float32) - zero_point) * scale
                        # 2. Aplica softmax nos logits dequantizados
                        output_probabilities = softmax(dequantized_logits, axis=1) 
                    else:
                        # Modelo Float32 (do Keras) já contém softmax, a saída são probabilidades
                        output_probabilities = output_data

                    predictions.append(output_probabilities[0])
                
                return np.array(predictions)
                
            elif MOTOR_IA == 'onnx':
                session = self.onnx_thread_session
                input_name = self.onnx_input_name
                output_name = self.onnx_output_name
                
                outputs_list = []
                for tensor in tensores: # Lote sempre será 1
                    input_tensor = np.expand_dims(tensor, axis=0) 
                    output = session.run([output_name], {input_name: input_tensor})[0]
                    outputs_list.append(output[0])
                
                stacked_outputs = np.array(outputs_list)
                
                # Usa a variável global para decidir o pós-processamento
                if ONNX_MODEL_TYPE == 'pytorch':
                    return softmax(stacked_outputs, axis=1) # Aplica softmax
                elif ONNX_MODEL_TYPE == 'tensorflow':
                    return stacked_outputs # Retorna probabilidades diretas

        finally:
            if MOTOR_IA == 'tensorflow' or MOTOR_IA == 'pytorch':
                if MODEL_SEMAPHORE is not None:
                    MODEL_SEMAPHORE.release()

    def processar_lote_de_imagens(self, items, tensores):
        global CLASS_NAMES, CSAM_SCORE, PORN_SCORE, OTHER_SCORE, AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_NO, CSAMDETECTOR_CATEGORY
        
        """Processa um lote e atribui os scores de csam e porn, salvando ambos no cache."""
        predicoes_lote = self.fazer_predicao(tensores)
        
        # Para TFLite/ONNX, predicoes_lote pode ser (1, 3). Para TF/PyTorch, (N, 3)
        # O código abaixo lida com ambos os casos
        
        csam_idx = CLASS_NAMES.index('csam')
        porn_idx = CLASS_NAMES.index('porn')
        other_idx = CLASS_NAMES.index('other')
        cache = caseData.getCaseObject('csam_cache_unificado')
        indices = np.argmax(predicoes_lote, axis=1)

        for i, item in enumerate(items):            
            predicoes_item = predicoes_lote[i]
            pred_index = indices[i]
            
            csam_score_float = predicoes_item[csam_idx]
            porn_score_float = predicoes_item[porn_idx]
            other_score_float = predicoes_item[other_idx]
            
            csam_score_formatado = int(csam_score_float*100)
            porn_score_formatado = int(porn_score_float*100)
            other_score_formatado = int(other_score_float*100)
            
            csam_category = CLASS_NAMES[pred_index]
            
            item.setExtraAttribute(CSAM_SCORE, csam_score_formatado)
            item.setExtraAttribute(PORN_SCORE, porn_score_formatado)
            item.setExtraAttribute(OTHER_SCORE, other_score_formatado)
            item.setExtraAttribute(CSAMDETECTOR_CATEGORY, csam_category)
            item.setExtraAttribute(AI_CLASSIFICATION_SKIP_ATTR, AI_CLASSIFICATION_SKIP_NO)
            
            cache.put(item.getHash(), (csam_score_formatado, porn_score_formatado, other_score_formatado, csam_category))

