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

SUPPORTED_MODELS = ('auraface', 'buffalo_l', 'buffalo_s')
EXECUTION_PROVIDERS = ('auto', 'cpu', 'cuda')

# Pin the distributed bytes as well as the download locations. A partial or
# replaced download must never be accepted as a model on the next startup.
MODEL_INTEGRITY = {
    RETINAFACE_R50_FILE: (109458296, '40f825cf7dd0a88b26fb61db9a3aaedc2cad35162091113f4017b3c26a4f792d'),
    AURAFACE_REC_FILE: (260694151, 'a7933ea5330113b01c9b60351d8f4c33003f145d8470ac5f0e52ee2effe25c60'),
}


def valid_model_file(path, expected_size, expected_sha256):
    """Check cached and newly downloaded bytes without importing ML libraries."""
    import hashlib
    import os
    try:
        if os.path.getsize(path) != expected_size:
            return False
        digest = hashlib.sha256()
        with open(path, 'rb') as stream:
            for block in iter(lambda: stream.read(1024 * 1024), b''):
                digest.update(block)
        return digest.hexdigest() == expected_sha256
    except FileNotFoundError:
        return False


def download_file(url, dest_path, label=None, expected_size=None, expected_sha256=None):
    """Atomically install a verified model, shared by online and offline setup."""
    import os
    import sys
    import tempfile
    import urllib.request

    if expected_size is None or expected_sha256 is None:
        expected_size, expected_sha256 = MODEL_INTEGRITY[os.path.basename(dest_path)]
    if valid_model_file(dest_path, expected_size, expected_sha256):
        return dest_path
    directory = os.path.dirname(os.path.abspath(dest_path))
    os.makedirs(directory, exist_ok=True)
    if label:
        print('Downloading ' + label + '...', file=sys.stderr, flush=True)
    fd, temporary = tempfile.mkstemp(dir=directory, suffix='.tmp')
    os.close(fd)
    try:
        urllib.request.urlretrieve(url, temporary)
        if not valid_model_file(temporary, expected_size, expected_sha256):
            raise ValueError('Downloaded model failed size/SHA-256 verification: ' + dest_path)
        try:
            os.replace(temporary, dest_path)
        except (FileExistsError, PermissionError):
            # A concurrent worker can finish first on Windows. Another reader
            # may hold its verified file without delete sharing, preventing a
            # replacement even though the desired bytes are already present.
            if not valid_model_file(dest_path, expected_size, expected_sha256):
                raise
        return dest_path
    finally:
        try:
            os.remove(temporary)
        except FileNotFoundError:
            pass
