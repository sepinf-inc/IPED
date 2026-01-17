"""
IPED task to detect Child Sexual Abuse Material (CSAM) using a TensorFlow or PyTorch based AI model.

The script must be enabled in IPED, and related PIP packages must be installed (tensorflow or torch, torchvision, timm, pillow).

On Linux, you need to install jep (pip install jep) and include jep.so in LD_LIBRARY_PATH.
see https://github.com/sepinf-inc/IPED/wiki/User-Manual#python-modules
"""

__author__ = "Guilherme Dalpian"
__email__ = "gmdalpian@gmail.com"
__version__ = "1.3" # Treats invalid dimensions in images/videos

import traceback
import io
import os
import time
import sys
from java.lang import System
from iped.engine.task import HashDBLookupTask
from java.awt import Color
from javax.imageio import ImageIO
from java.io import ByteArrayOutputStream
from iped.utils import ImageUtil
from iped.parsers.util import MetadataUtil
import math

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
np = None

# --- Global Configurations ---
PLUGIN_ENABLE_PROP = 'enableCSAMDetector'
CSAM_CONFIG_FILE = 'CSAMDetectorConfig.txt'
CSAM_SCORE = 'ai:csamDetector:csam'
PORN_SCORE = 'ai:csamDetector:porn'
OTHER_SCORE = 'ai:csamDetector:other'
CSAMDETECTOR_CATEGORY = 'ai:csamDetector:label'
IPED_GPU_GLOBAL_SEMAPHORE_STRING = 'IPED_GPU_GLOBAL_SEMAPHORE'
MODEL_SEMAPHORE = None
CSAM_IMG_SIZE = 224

# ImageNet normalization constants (used by PyTorch-style ONNX; set up during initialization)
IMG_MEAN_PYTORCH = None
IMG_STD_PYTORCH = None

# --- Global Control Variables ---
MOTOR_IA = None
MODELO_CARREGADO = None
DEVICE = None
CACHE = None
CLASS_NAMES = ['csam', 'porn', 'other']
NUM_CLASSES = len(CLASS_NAMES)
IS_IPED_422 = False

# --- ONNX Global Metadata ---
ONNX_MODEL_TYPE = None
ONNX_INPUT_NAME = None
ONNX_OUTPUT_NAME = None

# Configurable parameters defaults
CSAM_MODELFILE = 'tensorflow_B0_v3_1.keras'
CSAM_BATCH_SIZE = 64
CSAM_MINIMUM_IMAGE_SIZE = 0  # in bytes
CSAM_SKIP_DIMENSION = 0  # in pixels
CSAM_SKIP_HASHDB_FILES = 'false'  # skip files with hits on IPED HashDB database
CSAM_CREATE_BOOKMARKS = 'false'

# --- Video/Hierarchical Classification Configuration Defaults ---
CSAM_THRESHOLD = 0.60
PORN_THRESHOLD = 0.50
CSAM_MIN_FRAMES = 1
PORN_MIN_FRAMES = 1
CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE = 0.20 # 20%
CSAM_PORN_OVERRIDE_RATIO = 2.0 # 2.0x more porn frames

CSAM_MODELFILE_PROPERTY = 'ModelFile'
CSAM_BATCH_SIZE_PROPERTY = 'BatchSize'
CSAM_MINIMUM_IMAGE_SIZE_PROPERTY = 'MinimumImageSize'
CSAM_SKIP_DIMENSION_PROPERTY = 'SkipDimension'
CSAM_SKIP_HASHDB_FILES_PROPERTY = 'SkipHashDBFiles'
CSAM_CREATE_BOOKMARKS_PROPERTY = 'CreateBookmarks'

# --- NEW VIDEO PROPERTIES ---
CSAM_THRESHOLD_PROPERTY = 'CsamThreshold'
PORN_THRESHOLD_PROPERTY = 'PornThreshold'
CSAM_MIN_FRAMES_PROPERTY = 'CsamMinFrames'
PORN_MIN_FRAMES_PROPERTY = 'PornMinFrames'
CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE_PROPERTY = 'CsamAmbiguityMaxHitsPercentage'
CSAM_PORN_OVERRIDE_RATIO_PROPERTY = 'CsamPornOverrideRatio'

# AI constants
AI_CLASSIFICATION_STATUS_ATTR  = "ai:csamDetector:status"
AI_CLASSIFICATION_SUCCESS = "success";
AI_CLASSIFICATION_FAIL_NO_CLASS = "failNoClass";
AI_CLASSIFICATION_FAIL_NO_RESULTS = "failNoResults";
AI_CLASSIFICATION_SKIP_SIZE = "skippedSize";
AI_CLASSIFICATION_SKIP_DIMENSION = "skippedDimension";
AI_CLASSIFICATION_SKIP_HASHDB = "skippedHashDB";
AI_CLASSIFICATION_SKIP_DUPLICATE = "duplicate"

# =============================================================================
# LOADING AND PREDICTION LOGIC (UNIFIED)
# =============================================================================

def carregar_e_configurar_modelo():
    """Central function that loads the correct model (TF, PyTorch) or checks metadata (TFLite, ONNX)."""
    global MODELO_CARREGADO, DEVICE, CACHE, CSAM_IMG_SIZE, ONNX_MODEL_TYPE, ONNX_INPUT_NAME, ONNX_OUTPUT_NAME
    
    MODELO_CARREGADO = caseData.getCaseObject('csam_model_unificado')
    
    if MODELO_CARREGADO is None:
        caminho_modelo = System.getProperty('iped.root') + '/models/' + CSAM_MODELFILE
        if not os.path.exists(caminho_modelo):
            logger.error(f"CSAMDetector: FATAL ERROR: Model file not found: {caminho_modelo}")
            return None

        if MOTOR_IA == 'tensorflow':
            try:
                logger.info(f"CSAMDetector: Loading TensorFlow model from: {caminho_modelo}")
                MODELO_CARREGADO = keras.models.load_model(caminho_modelo)
                logger.info("TensorFlow model loaded successfully.")
                
                nome_modelo_lower = CSAM_MODELFILE.lower()
                if "_s_" in nome_modelo_lower:
                    CSAM_IMG_SIZE = 384
                elif "_m_" in nome_modelo_lower or "_l_" in nome_modelo_lower:
                    CSAM_IMG_SIZE = 480
                else: # Default for B0
                    CSAM_IMG_SIZE = 224
                logger.info(f"CSAMDetector: Image size set to {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE} based on model name.")
                
            except Exception as e:
                logger.error(f"CSAMDetector: FATAL ERROR loading TensorFlow model: {e}")
                return None
        
        elif MOTOR_IA == 'pytorch':
            try:
                logger.info(f"CSAMDetector: Loading PyTorch model from: {caminho_modelo}")
                DEVICE = torch.device("cuda:0" if torch.cuda.is_available() else "cpu")
                
                # 1. Load the checkpoint (dictionary) first
                checkpoint = torch.load(caminho_modelo, map_location=DEVICE, weights_only=False)
                
                # 2. Get metadata from inside the checkpoint
                num_classes_saved = checkpoint.get('num_classes', NUM_CLASSES)
                img_size_saved = checkpoint.get('img_size')

                # 3. Use the saved metadata to configure the model
                if img_size_saved:
                    CSAM_IMG_SIZE = img_size_saved # Overrides the value based on the file name
                    logger.info(f"CSAMDetector: Image size set to {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE} from model checkpoint.")

                # We use .get() for safety, in case a key does not exist
                model_name_key = checkpoint.get('model_name', 'B0') # Gets the name 'S', 'B0', etc.
                timm_name = checkpoint.get('timm_model_name')
                
                model_name_to_load = timm_name if timm_name else model_name_key
                
                # Builds the 'timm' model name based on the 'model_name' key
                model_map = {
                    'B0': 'tf_efficientnetv2_b0.in1k',
                    'S': 'tf_efficientnetv2_s.in21k_ft_in1k',
                    'M': 'tf_efficientnetv2_m.in21k_ft_in1k',
                    'L': 'tf_efficientnetv2_l.in21k_ft_in1k',
                                       
                    # TinyViT - Tiny Vision Transformer
                    'TinyViT-5M': 'tiny_vit_5m_224.dist_in22k',
                    'TinyViT-11M': 'tiny_vit_11m_224.dist_in22k',
                    'TinyViT-21M': 'tiny_vit_21m_224.dist_in22k'
                }
                
                # Resolves timm model name if not present in model file
                if model_name_to_load in model_map:
                    model_timm_name = model_map[model_name_to_load]
                else:
                    model_timm_name = timm_name # Assumes it is already the technical name
                
                logger.info(f"CSAMDetector: Building model architecture: {model_timm_name}")

                # 4. Create the model with the correct architecture
                MODELO_CARREGADO = timm.create_model(model_timm_name, pretrained=False, num_classes=num_classes_saved)
                
                # 5. Load the CORRECT state_dict (here is the main fix)
                if 'model_state_dict' in checkpoint:
                    MODELO_CARREGADO.load_state_dict(checkpoint['model_state_dict'])
                else:
                    # Tries to load as an old checkpoint (just for safety)
                    MODELO_CARREGADO.load_state_dict(checkpoint)
                
                MODELO_CARREGADO.to(DEVICE)
                MODELO_CARREGADO.eval()
                
                logger.info(f"CSAMDetector: PyTorch model ({model_name_key}, {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}) loaded on {DEVICE}.")
            
            except Exception as e:
                logger.error(f"CSAMDetector: FATAL ERROR loading PyTorch model: {e}")
                # Prints the full traceback in the IPED log for easier debugging
                logger.warn(traceback.format_exc()) 
                return None
                
        elif MOTOR_IA == 'tflite': 
            try:
                logger.info(f"CSAMDetector: Loading TFLite model from: {caminho_modelo}")                
                MODELO_CARREGADO = tflite.Interpreter(model_path=caminho_modelo)
                input_details = MODELO_CARREGADO.get_input_details()[0]
                output_details = MODELO_CARREGADO.get_output_details()[0]
    
                input_details = MODELO_CARREGADO.get_input_details()[0]
                CSAM_IMG_SIZE = input_details['shape'][1]
                input_dtype = input_details['dtype']
                is_quantized = input_dtype == np.int8

                logger.info(f"CSAMDetector: Model expects image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                if is_quantized:
                    logger.info("CSAMDetector: Quantized model (INT8) detected.")                
                logger.info("CSAMDetector: TFLite model loaded successfully.")
                
            except Exception as e:
                logger.error(f"CSAMDetector: FATAL ERROR loading TFLite model: {e}")
                return None            
        
        elif MOTOR_IA == 'onnx':
            try:
                # --- MODIFIED: Loads the OPTIMIZED GLOBAL SESSION ---
                logger.info(f"CSAMDetector: Loading GLOBAL ONNX session from: {caminho_modelo}")
                session_options = ort.SessionOptions()
                
                # Optimization settings for CPU (external concurrency)
                session_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
                session_options.intra_op_num_threads = 1
                session_options.inter_op_num_threads = 1
                session_options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
                
                MODELO_CARREGADO = ort.InferenceSession(
                    caminho_modelo, 
                    sess_options=session_options, 
                    providers=['CPUExecutionProvider'] # Forces CPU
                )
                
                input_details = MODELO_CARREGADO.get_inputs()[0]
                output_details = MODELO_CARREGADO.get_outputs()[0]
                
                # Stores input/output names globally
                ONNX_INPUT_NAME = input_details.name
                ONNX_OUTPUT_NAME = output_details.name
                
                input_shape = input_details.shape
                
                # PyTorch: [batch, 3, height, width]
                if input_shape[1] == 3 and isinstance(input_shape[2], int) and isinstance(input_shape[3], int):
                    CSAM_IMG_SIZE = input_shape[2] 
                    ONNX_MODEL_TYPE = 'pytorch' # Defines the global type
                    logger.info(f"CSAMDetector: ONNX (PyTorch-style, Channels-First) detected. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                # TensorFlow: [batch, height, width, 3]
                elif input_shape[3] == 3 and isinstance(input_shape[1], int) and isinstance(input_shape[2], int):
                    CSAM_IMG_SIZE = input_shape[1]
                    ONNX_MODEL_TYPE = 'tensorflow' # Defines the global type
                    logger.info(f"CSAMDetector: ONNX (TensorFlow-style, Channels-Last) detected. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
                
                else:
                    raise ValueError(f"Unrecognized ONNX input format: {input_shape}")
                                
            except Exception as e:
                logger.error(f"CSAMDetector: FATAL ERROR reading ONNX metadata: {e}")
                logger.warn(traceback.format_exc())
                return None        
        
        caseData.putCaseObject('csam_model_unificado', MODELO_CARREGADO)
        
    return MODELO_CARREGADO

# Processes the images as an array of BufferedImage objects
def processFrameTensors(frames_from_video):
    tensors = []
    # converts the tensors to byte arrays
    for frame in frames_from_video:
        baos = ByteArrayOutputStream()
        ImageIO.write(frame, "jpeg", baos);
        frame_bytes = convertJavaByteArray(baos.toByteArray())
        tensor = get_tensor_from_path_or_bytes(None, frame_bytes)
        tensors.append(tensor)
        
    return tensors

def processar_imagem(item):
    """Loads and preprocesses the image to the correct format (tensor)."""
    global CSAM_IMG_SIZE, ONNX_MODEL_TYPE

    file_path = None
    
    try:  

        file_path = item.getTempFile().getAbsolutePath()

        return get_tensor_from_path_or_bytes(file_path, None)       
            
    except Exception as e:           
        logger.warn(f"CSAMDetector: Error processing image {item.getPath()}, trying thumbnail... {e}")
        
        try:
            image_bytes = convertJavaByteArray(item.getThumb())
            
            return get_tensor_from_path_or_bytes(None, image_bytes)                 
                
        except Exception as thumb_e:
            logger.error(f"CSAMDetector: Failed to process thumbnail for {item.getPath()}: {thumb_e}")
            return None

# Processes images from a path or byte array and returns the ready tensor
def get_tensor_from_path_or_bytes(file_path, file_bytes=None):
    """Loads and preprocesses the image to the correct format (tensor)."""
    global CSAM_IMG_SIZE, ONNX_MODEL_TYPE

    if(file_path is None):
        file_path = io.BytesIO(file_bytes)

    if MOTOR_IA == 'tensorflow' or MOTOR_IA == 'tflite':
        if(file_bytes is None):
            img = tf.io.read_file(file_path)
        else:
            img = file_bytes
        img = tf.io.decode_image(img, channels=3, expand_animations=False)
        return tf.image.resize(img, [CSAM_IMG_SIZE, CSAM_IMG_SIZE])

    elif MOTOR_IA == 'pytorch':
        image = Image.open(file_path).convert('RGB')
        transform = transforms.Compose([
            transforms.Resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE)), transforms.ToTensor(),
            transforms.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])])
        return transform(image)  

    elif MOTOR_IA == 'onnx':
        image_resample = Image.BILINEAR
        if ONNX_MODEL_TYPE == 'tensorflow':
            image_resample = Image.NEAREST
            
        # ONNX uses PIL and Numpy to avoid dependency on torchvision
        image = Image.open(file_path).convert('RGB').resize((CSAM_IMG_SIZE, CSAM_IMG_SIZE), image_resample)
        
        # PyTorch-style preprocessing (Channels-First, CHW)
        if ONNX_MODEL_TYPE == 'pytorch':
            img_array = np.array(image, dtype=np.float32) / 255.0
            img_array = (img_array - IMG_MEAN_PYTORCH) / IMG_STD_PYTORCH
            img_array = img_array.transpose(2, 0, 1) # HWC -> CHW
            return img_array.astype(np.float32) # Returns np.ndarray (C, H, W)
        
        # TensorFlow-style preprocessing (Channels-Last, HWC)
        elif ONNX_MODEL_TYPE == 'tensorflow':
            # Just converts to float32, [0, 255] normalization is built into the model
            img_array = np.array(image, dtype=np.float32)
            return img_array # Returns np.ndarray (H, W, C)

def createSemaphore():
    global MODEL_SEMAPHORE, IPED_GPU_GLOBAL_SEMAPHORE_STRING
    MODEL_SEMAPHORE = caseData.getCaseObject(IPED_GPU_GLOBAL_SEMAPHORE_STRING)
    if(MODEL_SEMAPHORE is None):
        from java.util.concurrent import Semaphore
        MODEL_SEMAPHORE = Semaphore(1)
        caseData.putCaseObject(IPED_GPU_GLOBAL_SEMAPHORE_STRING, MODEL_SEMAPHORE)
    return MODEL_SEMAPHORE
    
def extrair_e_formatar_dois_digitos(score):
  # Function unused, keeping name/content as is (was in portuguese but not used)
  numero = score*100
  if numero >= 100: return "99"      
  return f'{int(numero):02d}' 

def isItemImage(item):
    global IS_IPED_422
    try:
        if(not IS_IPED_422):
            return MetadataUtil.isImageType(item.getMediaType()) 
    except AttributeError:
        IS_IPED_422 = True        
    # Compatible with IPED 4.2.2
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('image') 
    
def isItemVideo(item):
    global IS_IPED_422
    try:
        if(not IS_IPED_422):            
            return MetadataUtil.isVideoType(item.getMediaType())
    except AttributeError:
        IS_IPED_422 = True 
    # Compatible with IPED 4.2.2
    return item.getMediaType() is not None and item.getMediaType().toString().startswith('video')  

def isItemAnimatedImage(item):
    global IS_IPED_422
    try:
        if(not IS_IPED_422):
            return MetadataUtil.isAnimationImage(item)
    except AttributeError:
        IS_IPED_422 = True 
    # Compatible with IPED 4.2.2
    return item.getMediaType() is not None and (item.getMediaType().equals("image/heic-sequence") or item.getMediaType().equals("image/heif-sequence"))   

def softmax(x, axis=-1):
    """Calculates softmax in a stable way (needed for ONNX and TFLite)."""
    e_x = np.exp(x - np.max(x, axis=axis, keepdims=True))
    return e_x / e_x.sum(axis=axis, keepdims=True)
    
def convertJavaByteArray(byteArray):
    return bytes(b % 256 for b in byteArray)    

def supported(item):
    supported = (
        item.getLength() is not None and
        item.getLength() > 0 and
        (isItemImage(item) or isItemVideo(item)) and
        item.getExtraAttribute('hasThumb') and
        item.getHash() is not None
    )
    return supported

def get_scores_from_prediction(scores):
    global CLASS_NAMES

    csam_idx = CLASS_NAMES.index('csam')
    porn_idx = CLASS_NAMES.index('porn')
    other_idx = CLASS_NAMES.index('other')
    
    csam_score_formatado = int(scores[csam_idx]*100)
    porn_score_formatado = int(scores[porn_idx]*100)
    other_score_formatado = int(scores[other_idx]*100)
    
    category_index = np.argmax(scores)
    csam_category = CLASS_NAMES[category_index]


    results =  { 
        'csam_score_formatado' : csam_score_formatado,
        'porn_score_formatado' : porn_score_formatado,
        'other_score_formatado': other_score_formatado,
        'csam_category': csam_category 
    }
    
    return results

def md5_bytes_para_hex_maiusculo(bytes_data: bytes) -> str:
    # Function unused, keeping name/content as is (was in portuguese but not used)
    hash_md5_objeto = hashlib.md5(bytes_data)  
    hash_md5_hex = hash_md5_objeto.hexdigest()   
    hash_md5_hex_maiusculo = hash_md5_hex.upper()
    return hash_md5_hex_maiusculo

'''
Main class
'''
class CSAMDetectorTask:
    
    # Sets enable variable in class scope
    enabled = None
    
    def __init__(self):
        self.itemList = []
        self.nextTaskList = []
        self.imageBytes = []
        modelo_tflite = None               

    def isEnabled(self):
        return False if CSAMDetectorTask.enabled is None else CSAMDetectorTask.enabled

    def processQueueEnd(self):
        return True
       
    def getConfigurables(self):
        from iped.engine.config import DefaultTaskPropertiesConfig
        return [DefaultTaskPropertiesConfig(PLUGIN_ENABLE_PROP, CSAM_CONFIG_FILE)]        

    def init(self, configuration):
        global MOTOR_IA, CSAM_MODELFILE, CACHE, CSAM_BATCH_SIZE, CSAM_MINIMUM_IMAGE_SIZE, CSAM_SKIP_DIMENSION, CSAM_SKIP_HASHDB_FILES 
        global tf, keras, torch, nn, timm, transforms, Image, tflite, ort, np, CSAM_IMG_SIZE, ONNX_MODEL_TYPE, CSAM_CREATE_BOOKMARKS, CSAM_SKIP_HASHDB_FILES_PROPERTY
        # --- NEW VIDEO GLOBALS ---
        global CSAM_THRESHOLD, PORN_THRESHOLD, CSAM_MIN_FRAMES, PORN_MIN_FRAMES, CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE, CSAM_PORN_OVERRIDE_RATIO
        
        taskConfig = configuration.getTaskConfigurable(CSAM_CONFIG_FILE)
        
        if CSAMDetectorTask.enabled is None:
            CSAMDetectorTask.enabled = taskConfig.isEnabled()

        if not CSAMDetectorTask.enabled:
            return
            
        # Disable task during report generation
        if (caseData.isIpedReport()):
            CSAMDetectorTask.enabled = False
            return

        extraProps = taskConfig.getConfiguration()
        
        if(extraProps):
            CSAM_MODELFILE = extraProps.getProperty(CSAM_MODELFILE_PROPERTY, str(CSAM_MODELFILE))
            CSAM_BATCH_SIZE = int(extraProps.getProperty(CSAM_BATCH_SIZE_PROPERTY, str(CSAM_BATCH_SIZE)))
            CSAM_MINIMUM_IMAGE_SIZE = int(extraProps.getProperty(CSAM_MINIMUM_IMAGE_SIZE_PROPERTY, str(CSAM_MINIMUM_IMAGE_SIZE)))
            CSAM_SKIP_DIMENSION = int(extraProps.getProperty(CSAM_SKIP_DIMENSION_PROPERTY, str(CSAM_SKIP_DIMENSION)))
            skipDBFiles = extraProps.getProperty(CSAM_SKIP_HASHDB_FILES_PROPERTY, str(CSAM_SKIP_HASHDB_FILES))
            CSAM_SKIP_HASHDB_FILES = True if skipDBFiles.lower() == 'true' else False
            createbookmarks = extraProps.getProperty(CSAM_CREATE_BOOKMARKS_PROPERTY, str(CSAM_CREATE_BOOKMARKS))
            CSAM_CREATE_BOOKMARKS = True if createbookmarks.lower() == 'true' else False
            
            # --- LOADING NEW VIDEO CONFIGURATIONS ---
            CSAM_THRESHOLD = float(extraProps.getProperty(CSAM_THRESHOLD_PROPERTY, str(CSAM_THRESHOLD)))
            PORN_THRESHOLD = float(extraProps.getProperty(PORN_THRESHOLD_PROPERTY, str(PORN_THRESHOLD)))
            CSAM_MIN_FRAMES = int(extraProps.getProperty(CSAM_MIN_FRAMES_PROPERTY, str(CSAM_MIN_FRAMES)))
            PORN_MIN_FRAMES = int(extraProps.getProperty(PORN_MIN_FRAMES_PROPERTY, str(PORN_MIN_FRAMES)))
            CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE = float(extraProps.getProperty(CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE_PROPERTY, str(CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE)))
            CSAM_PORN_OVERRIDE_RATIO = float(extraProps.getProperty(CSAM_PORN_OVERRIDE_RATIO_PROPERTY, str(CSAM_PORN_OVERRIDE_RATIO)))
            
        
        logger.debug(f"CSAMDetector: CSAM configurations {CSAM_MODELFILE=} {CSAM_BATCH_SIZE=} {CSAM_MINIMUM_IMAGE_SIZE=} {CSAM_SKIP_DIMENSION=} {CSAM_SKIP_HASHDB_FILES=} {CSAM_CREATE_BOOKMARKS=}")
        logger.debug(f"CSAMDetector: Video classification configurations {CSAM_THRESHOLD=} {PORN_THRESHOLD=} {CSAM_MIN_FRAMES=} {PORN_MIN_FRAMES=} {CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE=} {CSAM_PORN_OVERRIDE_RATIO=}")
        
        from iped.engine.config import HashTaskConfig
        from iped.engine.config import ImageThumbTaskConfig 
        from iped.engine.config import VideoThumbsConfig
        
        hashConfig = configuration.findObject(HashTaskConfig).isEnabled()
        imageThumbsConfig = configuration.findObject(ImageThumbTaskConfig).isEnabled()
        videoThumbsConfig = configuration.findObject(VideoThumbsConfig).isEnabled()
        
        requiredTasks = (
            hashConfig and
            imageThumbsConfig and
            videoThumbsConfig
        )

        if not requiredTasks:
            logger.error(
                "CSAMDetector: To use CSAMDetector the following functions must also be enabled: "
                "enableHash enableImageThumbs enableVideoThumbs"
            )
            CSAMDetectorTask.enabled = False
            return  
           
        try:
            model_name_lower = CSAM_MODELFILE.lower()           
            
            if(np is None):
                module_name = 'numpy'
                import numpy as np_module
                np = np_module

            # Determine AI engine from model name
            if model_name_lower.endswith('.keras'):
                MOTOR_IA = 'tensorflow'
                if tf is None:
                    logger.info("CSAMDetector: TensorFlow engine detected. Loading libraries...")
                    os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
                    module_name = 'tensorflow'
                    import tensorflow as tf_module
                    from tensorflow import keras as keras_module
                    tf = tf_module
                    keras = keras_module
                    logger.info("CSAMDetector: TensorFlow libraries loaded.")
            
            elif model_name_lower.endswith('.pth'):
                MOTOR_IA = 'pytorch'
                if torch is None:
                    logger.info("CSAMDetector: PyTorch engine detected. Loading libraries...")
                    module_name = 'torch'
                    import torch as torch_module
                    import torch.nn as nn_module
                    module_name = 'torchvision'
                    from torchvision import transforms as transforms_module
                    module_name = 'timm'
                    import timm as timm_module
                    module_name = 'pillow'
                    from PIL import Image as Image_module
                    torch = torch_module
                    nn = nn_module
                    timm = timm_module
                    transforms = transforms_module
                    Image = Image_module
                    logger.info("CSAMDetector: PyTorch libraries loaded.")
                    
            elif model_name_lower.endswith('.tflite'):      
                MOTOR_IA = 'tflite'
                if tflite is None:
                    logger.info("CSAMDetector: TFLite engine detected. Loading libraries...")
                    os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'
                    module_name = 'tensorflow'
                    # Tries to import the lite interpreter first, if it fails, uses the full TF one
                    import tensorflow as tf_module
                    tf = tf_module
                    tflite = tf.lite
                    logger.info("CSAMDetector: Using TFLite from full TensorFlow package.")
                    
            elif model_name_lower.endswith('.onnx'):
                MOTOR_IA = 'onnx'
                if ort is None:
                    logger.info("CSAMDetector: ONNX engine detected. Loading libraries...")
                    module_name = 'onnxruntime'
                    import onnxruntime as ort_module
                    ort = ort_module
                    # ONNX preprocessing uses PIL (but not torchvision)
                    if Image is None:
                        module_name = 'pillow'
                        from PIL import Image as Image_module
                        Image = Image_module
                    logger.info("CSAMDetector: ONNX Runtime and PIL libraries loaded.")    
                # ImageNet normalization constants (used by PyTorch-style ONNX)
                global IMG_MEAN_PYTORCH, IMG_STD_PYTORCH
                if IMG_MEAN_PYTORCH is None:
                    IMG_MEAN_PYTORCH = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 1, 3)
                if IMG_STD_PYTORCH is None:
                    IMG_STD_PYTORCH = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 1, 3)
                    
            else:
                logger.error(f"CSAMDetector: Could not determine AI engine from model name: {CSAM_MODELFILE}")
                CSAMDetectorTask.enabled  = False
                return

        except ModuleNotFoundError as e:
            logger.error(f"CSAMDetector: Task could not be initialized and was disabled, '{module_name}' is missing. See CSAMDetector task setup information at <https://github.com/sepinf-inc/IPED/wiki/User-Manual#csamdetector>. {e}")
            CSAMDetectorTask.enabled = False
            return
            
        # Loads the global model (TF/PyTorch) or reads metadata (TFLite/ONNX)
        # This call is now done for all engines
        if not carregar_e_configurar_modelo():
             logger.error(f"CSAMDetector: Task was disabled.")
             CSAMDetectorTask.enabled  = False
             return
             
        CACHE = caseData.getCaseObject('csam_cache_unificado')  
        if(not CACHE):
            from java.util.concurrent import ConcurrentHashMap
            CACHE = ConcurrentHashMap()
            caseData.putCaseObject('csam_cache_unificado', CACHE)             
    
        # In case of TFLite, each thread must have its own interpreter
        if MOTOR_IA == 'tflite':
            caminho_modelo = System.getProperty('iped.root') + '/models/' + CSAM_MODELFILE
            self.modelo_tflite = tf.lite.Interpreter(model_path=caminho_modelo)
            self.modelo_tflite.allocate_tensors()
            # CSAM_IMG_SIZE was already set in the initial check
            CSAM_BATCH_SIZE = 1 # TFLite is processed 1 by 1
            logger.debug(f"CSAMDetector: TFLite interpreter created for thread. Image size: {CSAM_IMG_SIZE}x{CSAM_IMG_SIZE}")
            
        elif MOTOR_IA == 'onnx':            
            CSAM_BATCH_SIZE = 1 # Processes one by one

        else:            
            # semaphore is only used when processing in batches, tflite and onnx are multithreaded
            createSemaphore()
        

    def process(self, item):
        
        logger.debug(f"CSAMDetector: called process for item {item.getPath()} {item.getId()}")
        
        if not item.isQueueEnd() and not supported(item):
            logger.debug(f"CSAMDetector: Item not supported: {item.getPath()} {item.getId()}")
            return

        try:
            # don't process it again (in the report generation for example)
            csamscore = item.getExtraAttribute(CSAM_SCORE)
            if csamscore is not None:
                logger.debug(f"CSAMDetector: Item has already csam score: {item.getPath()} {item.getId()}")
                return                      
            
            isAnimationImage = False
            isImage = False
            isVideo = False
            
            if(not item.isQueueEnd()):
                isAnimationImage = isItemAnimatedImage(item)
                isImage = isItemImage(item)
                isVideo = isItemVideo(item)
        
            # Skip very small images in bytes
            if item.getLength() is not None and item.getLength() < CSAM_MINIMUM_IMAGE_SIZE:                
                logger.debug(f"CSAMDetector: skipping very small image {item.getName()} {item.getLength()} bytes")
                item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_SIZE)                
                return

            # Skip very small dimensions
            if(CSAM_SKIP_DIMENSION>0):
                try:                
                    width = None
                    height = None
                    if(isImage):
                        width_meta = item.getMetadata().get("image:Width")
                        height_meta = item.getMetadata().get("image:Height")
                        width = int(width_meta) if width_meta is not None else None
                        height = int(height_meta) if height_meta is not None else None
                    elif(isVideo):
                        width_meta = item.getMetadata().get("video:Width")
                        height_meta = item.getMetadata().get("video:Height")
                        width = int(width_meta) if width_meta is not None else None
                        height = int(height_meta) if height_meta is not None else None
                    
                    if(width is not None and height is not None and (width<CSAM_SKIP_DIMENSION or height<CSAM_SKIP_DIMENSION)):
                        logger.debug(f"CSAMDetector: skipping very small image {item.getName()} {width}x{height}")
                        item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_DIMENSION)
                        return
                except ValueError:
                    logger.warn(f"CSAMDetector: invalid dimensions for item {item.getName()} {width_meta}x{height_meta}")                        

            # Skip classification of images/videos with hits on IPED hashesDB database (see 'skipHashDBFiles' config property)
            if (CSAM_SKIP_HASHDB_FILES and item.getExtraAttribute(HashDBLookupTask.STATUS_ATTRIBUTE) is not None):
                logger.debug(f"CSAMDetector: skipping item with HashDB hit {item.getName()}")
                item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SKIP_HASHDB)
                return
            
            
            if item.getHash():                
                scores = CACHE.get(item.getHash())
                if scores is not None:
                    try:
                        if(isImage and not isAnimationImage):
                            csam_score, porn_score, other_score, csam_category = scores
                        elif(isVideo or isAnimationImage):
                            csam_score, porn_score, other_score, csam_category, trigger_frame_index, hit_perc_formatted, avg_conf_formatted = scores
                            
                        logger.debug(f"CSAMDetector: Found cached scores for {item.getName()}: csam={csam_score}, porn={porn_score}")
                        item.setExtraAttribute(CSAM_SCORE, csam_score)
                        item.setExtraAttribute(PORN_SCORE, porn_score)
                        item.setExtraAttribute(OTHER_SCORE, other_score)
                        item.setExtraAttribute(CSAMDETECTOR_CATEGORY, csam_category)
                        if(isVideo or isAnimationImage):
                            item.setExtraAttribute('ai:csamDetector:triggerFrame', trigger_frame_index)
                            item.setExtraAttribute('ai:csamDetector:hitPercentage', hit_perc_formatted)
                            item.setExtraAttribute('ai:csamDetector:avgConfidence', avg_conf_formatted)
                            
                        item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS)
                        return
                    except (TypeError, ValueError) as err:
                        logger.info(f"CSAMDetector: Outdated cache format for hash {item.getHash()}. Reprocessing. {err}")

            img_tensor = None
            
            if(not item.isQueueEnd()):
                # Process the images normally, adding to batch
                if(isImage and not isAnimationImage):
                    img_tensor = processar_imagem(item)            
                    if img_tensor is None:    
                        item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL_NO_RESULTS)
                        logger.error(f"CSAMDetector: error processing image: {item.getName()}, id {item.getId()}")
                        return
                    
                    self.itemList.append(item)
                    self.imageBytes.append(img_tensor)
                    
                elif(isVideo or isAnimationImage):
                    # Processes videos and animated images frames immediately                
                    frames = None

                    viewFile = item.getViewFile()
                    if (viewFile is not None and viewFile.exists()):
                        frames = ImageUtil.getFrames(viewFile)
                    elif item.hasPreview():
                        logger.debug(f"CSAMDetector: no view file for video/animation {item.getPath()}")
                        from iped.engine.preview import PreviewRepositoryManager
                        stream = PreviewRepositoryManager.get(moduleDir).readPreview(item, False)
                        frames = ImageUtil.getFrames(stream)

                    if(frames is None):
                        logger.warn(f"CSAMDetector: no frames extracted for video/animation {item.getPath()}")
                    else:                
                        logger.debug(f"CSAMDetector: Processing {len(frames)} frames from video file {item.getPath()}")

                        predictions_array = []
                        # Processes the frames respecting batch size
                        for i in range(0, len(frames), CSAM_BATCH_SIZE):
                            current_batch = frames[i : i + CSAM_BATCH_SIZE]
                            tensores = processFrameTensors(current_batch)
                            predictions = self.fazer_predicao(tensores)
                            predictions_array.extend(predictions)
                        
                        # 1. Calls the new function, which returns a rich object
                        video_result = self.classify_video_with_full_scores(predictions_array)
                        
                        # 2. Extracts classification and risk data
                        class_info = video_result['classification']
                        risk_meta = video_result['risk_metadata']
                        
                        # 3. Uses the winning frame's probability vector for get_scores_from_prediction
                        # This fills csam_score_formatado, porn_score_formatado, etc.
                        results = get_scores_from_prediction(class_info['probabilities'])
                            
                        # 4. Sets the old attributes (scores)
                        item.setExtraAttribute(CSAM_SCORE, results['csam_score_formatado'])
                        item.setExtraAttribute(PORN_SCORE, results['porn_score_formatado'])
                        item.setExtraAttribute(OTHER_SCORE, results['other_score_formatado'])
                        
                        # 5. Sets the category based on the hierarchical class (more reliable)
                        item.setExtraAttribute(CSAMDETECTOR_CATEGORY, class_info['class'])
                        
                        # 6. Sets the NEW risk metadata attributes
                        item.setExtraAttribute('ai:csamDetector:triggerFrame', class_info['trigger_frame_index'])
                        
                        # These two properties are not essential, as hitPercentage already provides what is needed
                        #item.setExtraAttribute('ai:csamDetector:totalFrames', risk_meta['total_frames'])
                        #item.setExtraAttribute('ai:csamDetector:hitCount', risk_meta['hit_count'])
                        
                        # Formats to integer percentage (0 to 100)
                        hit_perc_formatted = int(risk_meta['hit_percentage']*100)
                        avg_conf_formatted = int(risk_meta['avg_confidence']*100)
                        
                        item.setExtraAttribute('ai:csamDetector:hitPercentage', hit_perc_formatted)
                        item.setExtraAttribute('ai:csamDetector:avgConfidence', avg_conf_formatted)

                        # 7. Sets the success status
                        item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS)
                        
                        # 8. Updates the cache (Using the correct hierarchical class)
                        CACHE.put(item.getHash(), (results['csam_score_formatado'], results['porn_score_formatado'], results['other_score_formatado'], class_info['class'], class_info['trigger_frame_index'], hit_perc_formatted, avg_conf_formatted))

        except Exception as e:
            logger.error(f"CSAMDetector: exception processing item {item.getPath()} id {item.getId()}: {e}")
            item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_FAIL_NO_RESULTS)
            raise e

        # Check if the batch needs to be flushed.
        # This happens if the batch is full, or if the end of the queue is signaled.
        if (self.isToProcessBatch(item)):
            logger.debug(f"CSAMDetector: processing batch of {len(self.itemList)} items.")
            self.processar_lote_de_imagens(self.itemList, self.imageBytes)
            self.nextTaskList.extend(self.itemList)
            self.itemList.clear()
            self.imageBytes.clear()


    def sendToNextTask(self, item):
        if not item.isQueueEnd() and item not in self.itemList and item not in self.nextTaskList:
            self.javaTask.sendToNextTaskSuper(item)
        
        if len(self.nextTaskList) > 0:
            localList = list(self.nextTaskList)
            self.nextTaskList.clear()
            for i in localList:
                self.javaTask.sendToNextTaskSuper(i)
            
        if item.isQueueEnd():
            self.javaTask.sendToNextTaskSuper(item)


    def isToProcessBatch(self, item):
        size = len(self.itemList)
        return size >= CSAM_BATCH_SIZE or (size > 0 and item.isQueueEnd())    


    def finish(self):              
        global CSAM_CREATE_BOOKMARKS, CSAM_SCORE, CSAMDETECTOR_CATEGORY
        
        logger.debug("CSAMDetector: CSAM analysis finished.")                
        
        if not CSAM_CREATE_BOOKMARKS:
            return

        CSAM_CREATE_BOOKMARKS = False
        
        csam_score_query = CSAM_SCORE.replace(":", r"\:")
        category = CSAMDETECTOR_CATEGORY.replace(":", r"\:")
        
        bookmarks_to_create = [
            {
                "query": f"{csam_score_query}:[85 TO *]",
                "name": "Possible CSAM IA - 1 - Higher Confidence",
                "comment": "Possible CSAM files, high confidence",
                "color": [220, 20, 60]
            },
            {
                "query": f"{csam_score_query}:[60 TO 84]",
                "name": "Possible CSAM IA - 2 - Medium Confidence",
                "comment": "Possible CSAM files, medium confidence",
                "color": [255, 165, 0]
            },
            {
                "query": f"{csam_score_query}:[40 TO 59]",
                "name": "Possible CSAM IA - 3 - Low Confidence",
                "comment": "Possible CSAM files, low confidence",
                "color": [255, 255, 0]
            },
            {
                "query": f"{category}=porn",
                "name": "Probable Adult Porn (IA)",
                "comment": "Probable Porn files, for manual review",
                "color": [255, 105, 180]
            },
            {
                "query": "hashDb:status=pedo".replace(":", r"\:"),
                "name": "Probable CSAM - Hash Hit",
                "comment": "Probable CSAM - hash hit",
                "color": [255, 0, 0]
            }
        ]  

        # Iterates over the list and calls the function for each item
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
        Executes a search and, if results are found, creates a bookmark with them.

        Args:
            query (str): The query string for the search.
            bookmark_name (str): The name of the bookmark to be created.
            bookmark_comment (str): The comment for the bookmark.
        """
        
        # Defines and executes the query
        searcher.setQuery(query)
        ids = searcher.search().getIds()
        
        # Creates the bookmark even if empty
        bookmarks = ipedCase.getBookmarks()
        bookmark_id = bookmarks.newBookmark(bookmark_name)
        bookmarks.setBookmarkComment(bookmark_id, bookmark_comment)
        if(color):
            bookmarks.setBookmarkColor(bookmark_id, Color(color[0], color[1], color[2]))        
        
        # Checks if there were results
        if ids and len(ids) > 0:
            # Creates and configures the new bookmark
            bookmarks.addBookmark(ids, bookmark_id)

        # Saves the changes
        bookmarks.saveState(True)       
        
       
    def fazer_predicao(self, tensores):
        """Runs batch prediction, returning the full probability array."""
        global MODEL_SEMAPHORE, MOTOR_IA, DEVICE, MODELO_CARREGADO,  ONNX_INPUT_NAME, ONNX_OUTPUT_NAME
        
        try:
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
                for tensor in tensores: # Batch size will always be 1
                    input_tensor = np.expand_dims(tensor.numpy(), axis=0)
                    
                    if is_quantized:
                        input_tensor = (input_tensor.astype(np.float32) - 128).astype(np.int8)

                    interpreter.set_tensor(input_details['index'], input_tensor)
                    interpreter.invoke()
                    output_data = interpreter.get_tensor(output_details['index'])
                    
                    if is_quantized:
                        # 1. Dequantizes the logits
                        scale, zero_point = output_details['quantization']
                        dequantized_logits = (output_data.astype(np.float32) - zero_point) * scale
                        # 2. Applies softmax to the dequantized logits
                        output_probabilities = softmax(dequantized_logits, axis=1) 
                    else:
                        # Float32 Model (from Keras) already contains softmax, the output is probabilities
                        output_probabilities = output_data

                    predictions.append(output_probabilities[0])
                
                return np.array(predictions)
                
            elif MOTOR_IA == 'onnx':
                # --- MODIFIED: USES GLOBAL SESSION AND BATCH ---
                session = MODELO_CARREGADO # Global session
                input_name = ONNX_INPUT_NAME  # Global name
                output_name = ONNX_OUTPUT_NAME # Global name
                
                # Stacks the tensors (list of np.ndarray) into a single batch
                input_tensor = np.stack(tensores, axis=0)
                
                # Executes inference on the entire batch at once
                stacked_outputs = session.run([output_name], {input_name: input_tensor})[0]
                
                # Uses the global variable to decide post-processing
                if ONNX_MODEL_TYPE == 'pytorch':
                    return softmax(stacked_outputs, axis=1) # Applies softmax
                elif ONNX_MODEL_TYPE == 'tensorflow':
                    return stacked_outputs # Returns direct probabilities

        finally:
            if MODEL_SEMAPHORE is not None:
                MODEL_SEMAPHORE.release()

    def processar_lote_de_imagens(self, items, tensores):
        global CLASS_NAMES, CSAM_SCORE, PORN_SCORE, OTHER_SCORE, AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS, CSAMDETECTOR_CATEGORY
        
        """Processes a batch and assigns csam and porn scores, saving both to cache."""
        predicoes_lote = self.fazer_predicao(tensores)              

        for i, item in enumerate(items):            
            predicoes_item = predicoes_lote[i]
            
            results = get_scores_from_prediction(predicoes_item)
                        
            item.setExtraAttribute(CSAM_SCORE, results['csam_score_formatado'])
            item.setExtraAttribute(PORN_SCORE, results['porn_score_formatado'])
            item.setExtraAttribute(OTHER_SCORE, results['other_score_formatado'])
            item.setExtraAttribute(CSAMDETECTOR_CATEGORY, results['csam_category'])
            item.setExtraAttribute(AI_CLASSIFICATION_STATUS_ATTR, AI_CLASSIFICATION_SUCCESS)
            
            CACHE.put(item.getHash(), (results['csam_score_formatado'], results['porn_score_formatado'], results['other_score_formatado'], results['csam_category']))  
                    
    def classify_video_with_full_scores(self, frame_predictions):
        """
        Classifies a video using a robust hierarchical logic with an
        "Override by Ratio Rule" to disambiguate CSAM vs Porn.

        The logic is:
        1. Count "hits" for CSAM and Porn (based on Threshold and Min_Frames).
        2. OVERRIDE RULE:
           If (csam_hits > MIN_FRAMES) AND (csam_hits <= AMBIGUITY_MAX) AND (porn_hits > csam_hits * OVERRIDE_RATIO):
              -> Classify as PORN (assumes False Positive)
        3. STANDARD HIERARCHICAL RULE:
           If (csam_hits > MIN_FRAMES) -> Classify as CSAM.
           If (porn_hits > MIN_FRAMES) -> Classify as PORN.
           Else -> Classify as OTHER.
        """
        global CLASS_NAMES, np
        # Add 'global' for the new variables here
        global CSAM_THRESHOLD, PORN_THRESHOLD, CSAM_MIN_FRAMES, PORN_MIN_FRAMES, CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE, CSAM_PORN_OVERRIDE_RATIO

        # --- CRITICAL TUNING PARAMETERS (NOW GLOBALS) ---
        THRESHOLDS = {
            'csam': CSAM_THRESHOLD, 
            'porn': PORN_THRESHOLD
        }
        MIN_FRAMES = {
            'csam': CSAM_MIN_FRAMES, 
            'porn': PORN_MIN_FRAMES
        }
        
        csam_idx = CLASS_NAMES.index('csam')
        porn_idx = CLASS_NAMES.index('porn')
        other_idx = CLASS_NAMES.index('other')

        total_frames = len(frame_predictions)
        
        # --- EXCEPTION PARAMETERS ---
        # Override rule only applies if CSAM hits are <= this number
        # Uses the global variable CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE
        CSAM_AMBIGUITY_MAX_HITS = int(total_frames * CSAM_AMBIGUITY_MAX_HITS_PERCENTAGE)
        # How many times PORN hits must be greater than CSAM hits for the override
        CSAM_PORN_OVERRIDE_RATIO_VAR = CSAM_PORN_OVERRIDE_RATIO
        # ------------------------------------       
        
        DEFAULT_OTHER_FRAME = [0.0] * len(CLASS_NAMES)
        DEFAULT_OTHER_FRAME[other_idx] = 1.0

        if total_frames == 0:
            return {
                'classification': {
                    'class': 'other',
                    'probabilities': DEFAULT_OTHER_FRAME,
                    'trigger_frame_index': -1
                },
                'risk_metadata': {
                    'hit_count': 0,
                    'hit_percentage': 0.0,
                    'avg_confidence': 0.0,
                    'total_frames': 0
                }
            }

        # --- Step 1: Maximum Evidence (For reporting) ---
        best_csam_frame = {'score': -1.0, 'vector': DEFAULT_OTHER_FRAME, 'index': -1}
        best_porn_frame = {'score': -1.0, 'vector': DEFAULT_OTHER_FRAME, 'index': -1}
        best_other_frame = {'score': -1.0, 'vector': DEFAULT_OTHER_FRAME, 'index': -1}

        # --- Step 2: "Hit" Count (Based on Threshold) ---
        csam_hits_count = 0
        porn_hits_count = 0
        
        total_csam_score_sum = 0.0
        total_porn_score_sum = 0.0
        total_other_score_sum = 0.0

        for i, frame_vector in enumerate(frame_predictions):
            frame_vector = list(frame_vector) 
            
            csam_score = frame_vector[csam_idx]
            porn_score = frame_vector[porn_idx]
            other_score = frame_vector[other_idx]

            if csam_score > best_csam_frame['score']:
                best_csam_frame = {'score': csam_score, 'vector': frame_vector, 'index': i}
            if porn_score > best_porn_frame['score']:
                best_porn_frame = {'score': porn_score, 'vector': frame_vector, 'index': i}
            if other_score > best_other_frame['score']:
                best_other_frame = {'score': other_score, 'vector': frame_vector, 'index': i}

            total_csam_score_sum += csam_score
            total_porn_score_sum += porn_score
            total_other_score_sum += other_score

            if csam_score >= THRESHOLDS['csam']:
                csam_hits_count += 1
            elif porn_score >= THRESHOLDS['porn']:
                porn_hits_count += 1

        # --- Step 3: Hierarchical Decision (with Exception Logic) ---
        final_classification = {}
        hit_count = 0
        total_confidence_sum_for_avg = 0.0 

        # 1. Checks the "hit" conditions
        is_csam_candidate = (csam_hits_count >= MIN_FRAMES['csam'])
        is_porn_candidate = (porn_hits_count >= MIN_FRAMES['porn'])

        # 2. Exception Logic: Is it a probable CSAM False Positive?
        is_false_positive_override = (
            is_csam_candidate and
            csam_hits_count <= CSAM_AMBIGUITY_MAX_HITS and
            csam_hits_count > 0 and # Avoids division by zero
            # Uses the local variable CSAM_PORN_OVERRIDE_RATIO_VAR
            porn_hits_count > (csam_hits_count * CSAM_PORN_OVERRIDE_RATIO_VAR) 
        )
        
        # 3. Defines the final class
        
        if is_false_positive_override:
            # EXCEPTION: Treats as PORN despite CSAM hits
            final_classification = {
                'class': 'porn',
                'probabilities': best_porn_frame['vector'],
                'trigger_frame_index': best_porn_frame['index']
            }
            hit_count = porn_hits_count
            total_confidence_sum_for_avg = total_porn_score_sum
            
        elif is_csam_candidate:
            # STANDARD RULE 1: CSAM
            final_classification = {
                'class': 'csam',
                'probabilities': best_csam_frame['vector'], 
                'trigger_frame_index': best_csam_frame['index']
            }
            hit_count = csam_hits_count
            total_confidence_sum_for_avg = total_csam_score_sum
            
        elif is_porn_candidate:
            # STANDARD RULE 2: Porn
            final_classification = {
                'class': 'porn',
                'probabilities': best_porn_frame['vector'],
                'trigger_frame_index': best_porn_frame['index']
            }
            hit_count = porn_hits_count
            total_confidence_sum_for_avg = total_porn_score_sum
            
        else:
            # STANDARD RULE 3: Other
            final_classification = {
                'class': 'other',
                'probabilities': best_other_frame['vector'],
                'trigger_frame_index': best_other_frame['index']
            }
            hit_count = 0
            total_confidence_sum_for_avg = total_other_score_sum

        # --- Step 4: Calculate Final Metadata ---
        hit_percentage = (hit_count / total_frames) if total_frames > 0 else 0.0
        avg_confidence = (total_confidence_sum_for_avg / total_frames) if total_frames > 0 else 0.0

        final_risk_metadata = {
            'hit_count': hit_count,
            'hit_percentage': hit_percentage,
            'avg_confidence': avg_confidence,
            'total_frames': total_frames
        }
        
        return {
            'classification': final_classification,
            'risk_metadata': final_risk_metadata
        }