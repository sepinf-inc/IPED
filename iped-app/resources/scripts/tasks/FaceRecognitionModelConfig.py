"""
Face recognition model URLs and file paths.

This file centralizes all model download URLs and expected file names
so they can be updated in one place. Used by FaceRecognitionProcess.py
and download_insightface_models.py.
"""

# ── AuraFace mode (default — fully open-source) ──────────────────────────

# RetinaFace-R50 detection model (MIT license)
# Source: biubug6/Pytorch_Retinaface, ONNX export hosted by HivisionIDPhotos
RETINAFACE_R50_URL = 'https://github.com/Zeyi-Lin/HivisionIDPhotos/releases/download/pretrained-model/retinaface-resnet50.onnx'
RETINAFACE_R50_FILE = 'retinaface-resnet50.onnx'

# AuraFace-v1 recognition model (Apache 2.0 license)
# Source: https://huggingface.co/fal/AuraFace-v1
AURAFACE_REC_URL = 'https://huggingface.co/fal/AuraFace-v1/resolve/main/glintr100.onnx'
AURAFACE_REC_FILE = 'glintr100.onnx'

# Subdirectory under model_dir/models/ for auraface models
AURAFACE_SUBDIR = 'auraface'
