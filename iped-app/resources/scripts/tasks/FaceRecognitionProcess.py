'''
# External process used by FaceRecognitionTask.py for face detection + recognition.
# Supports two modes:
#   - 'auraface': Standalone RetinaFace-R50 detection (MIT) + AuraFace recognition (Apache 2.0)
#   - 'buffalo_l'/'buffalo_s': InsightFace full pipeline (non-commercial license)
'''
import sys
stdout = sys.stdout
if __name__ == '__main__':
    sys.stdout = sys.stderr

import os
import math
from contextlib import redirect_stdout
from FaceRecognitionModelConfig import (
    AURAFACE_REC_URL, AURAFACE_REC_FILE, AURAFACE_SUBDIR,
    RETINAFACE_R50_URL, RETINAFACE_R50_FILE,
    SUPPORTED_MODELS, EXECUTION_PROVIDERS, download_file,
)

_dll_directory_handles = []
_dll_search_paths = set()


def expose_nvidia_dll_directories():
    """Make cuDNN's lazily loaded component DLLs discoverable on Windows."""
    if os.name != 'nt':
        return
    import importlib.util
    from pathlib import Path
    spec = importlib.util.find_spec('nvidia')
    if spec is None:
        return
    for root in spec.submodule_search_locations or []:
        for directory in Path(root).glob('*/bin'):
            path = str(directory.resolve())
            if path in _dll_search_paths:
                continue
            # cuDNN also uses LoadLibrary at inference time; ORT preload_dlls
            # alone does not preload every component in newer cuDNN releases.
            if hasattr(os, 'add_dll_directory'):
                _dll_directory_handles.append(os.add_dll_directory(path))
            os.environ['PATH'] = path + os.pathsep + os.environ.get('PATH', '')
            _dll_search_paths.add(path)


def preload_runtime_dlls(runtime, execution_provider):
    if execution_provider != 'cpu':
        expose_nvidia_dll_directories()
    if execution_provider != 'cpu' and hasattr(runtime, 'preload_dlls'):
        # ORT's diagnostic prints must not enter the subprocess response stream.
        with redirect_stdout(sys.stderr):
            runtime.preload_dlls()


def requested_providers(runtime, execution_provider):
    if execution_provider not in EXECUTION_PROVIDERS:
        raise ValueError('executionProvider must be auto, cpu or cuda')
    available = runtime.get_available_providers()
    if execution_provider == 'cpu':
        return ['CPUExecutionProvider']
    if 'CUDAExecutionProvider' in available:
        return ['CUDAExecutionProvider', 'CPUExecutionProvider']
    if execution_provider == 'cuda':
        raise RuntimeError('CUDA execution requested, but CUDAExecutionProvider is unavailable; install onnxruntime-gpu and its CUDA/cuDNN dependencies')
    print('CUDA unavailable; using CPUExecutionProvider.', file=sys.stderr, flush=True)
    return ['CPUExecutionProvider']


def session_provider_options(providers):
    """Keep float32 detector landmarks consistent across CPU and CUDA execution."""
    return [{'use_tf32': '0'} if provider == 'CUDAExecutionProvider' else {} for provider in providers]


def check_dependencies(model_name, execution_provider):
    import importlib
    if model_name not in SUPPORTED_MODELS:
        raise ValueError('Unsupported face model: ' + model_name)
    for module in ('numpy', 'cv2', 'PIL', 'onnxruntime'):
        importlib.import_module(module)
    if model_name != 'auraface':
        importlib.import_module('insightface.app')
    runtime = importlib.import_module('onnxruntime')
    preload_runtime_dlls(runtime, execution_provider)
    requested_providers(runtime, execution_provider)


if __name__ == '__main__' and '--check-dependencies' in sys.argv:
    import argparse
    parser = argparse.ArgumentParser(description='Check face worker dependencies without loading models.')
    parser.add_argument('--check-dependencies', action='store_true')
    parser.add_argument('--model', choices=SUPPORTED_MODELS, default='auraface')
    parser.add_argument('--execution-provider', choices=EXECUTION_PROVIDERS, default='auto')
    args = parser.parse_args()
    try:
        check_dependencies(args.model, args.execution_provider)
    except Exception as error:
        package = 'onnxruntime' if args.execution_provider == 'cpu' else 'onnxruntime-gpu'
        extra = ' insightface' if args.model != 'auraface' else ''
        print('Face recognition dependencies unavailable: ' + str(error), file=sys.stderr)
        print('Install into this Python: python -m pip install numpy opencv-python pillow ' + package + extra, file=sys.stderr)
        sys.exit(1)
    sys.exit(0)

import onnxruntime
from onnxruntime.capi import _pybind_state as ort_state
import cv2
import PIL
import PIL.ImageFile
from PIL import Image
import numpy as np

PIL.ImageFile.LOAD_TRUNCATED_IMAGES = True

terminate = 'terminate_process'
imgError = "image_error"
ping = "ping"
video = "video"

max_files = 2000
processed_files = 0

# Standard ArcFace 5-point reference landmarks for 112x112 alignment
ARCFACE_REF = np.array([
    [38.2946, 51.6963],   # left eye
    [73.5318, 51.5014],   # right eye
    [56.0252, 71.7366],   # nose tip
    [41.5493, 92.3655],   # left mouth corner
    [70.7299, 92.2041],   # right mouth corner
], dtype=np.float32)

RETINAFACE_MIN_SIZES = [[16, 32], [64, 128], [256, 512]]
RETINAFACE_STEPS = [8, 16, 32]
RETINAFACE_VARIANCE = [0.1, 0.2]
RETINAFACE_NMS_THRESH = 0.4
RETINAFACE_MEAN = np.array([104.0, 117.0, 123.0], dtype=np.float32)

# Cache for prior boxes keyed by (height, width)
_prior_box_cache = {}


def generate_prior_boxes(h, w):
    """Generate anchor/prior boxes for RetinaFace. Cached per (h, w)."""
    key = (h, w)
    if key in _prior_box_cache:
        return _prior_box_cache[key]

    anchors = []
    for k, step in enumerate(RETINAFACE_STEPS):
        feat_h = (h + step - 1) // step
        feat_w = (w + step - 1) // step
        min_sizes = RETINAFACE_MIN_SIZES[k]
        for i in range(feat_h):
            for j in range(feat_w):
                for min_size in min_sizes:
                    s_kx = min_size / w
                    s_ky = min_size / h
                    cx = (j + 0.5) * step / w
                    cy = (i + 0.5) * step / h
                    anchors.append([cx, cy, s_kx, s_ky])

    priors = np.array(anchors, dtype=np.float32)
    _prior_box_cache[key] = priors
    return priors


def decode_bboxes(loc, priors, variances):
    """Decode RetinaFace bbox regressions to [x1, y1, x2, y2]."""
    boxes = np.concatenate([
        priors[:, :2] + loc[:, :2] * variances[0] * priors[:, 2:],
        priors[:, 2:] * np.exp(loc[:, 2:] * variances[1]),
    ], axis=1)
    # center-size to corner form
    boxes[:, :2] -= boxes[:, 2:] / 2
    boxes[:, 2:] += boxes[:, :2]
    return boxes


def decode_landmarks(landms, priors, variances):
    """Decode RetinaFace 5-point landmark regressions."""
    decoded = np.zeros_like(landms)
    for i in range(5):
        decoded[:, i * 2] = priors[:, 0] + landms[:, i * 2] * variances[0] * priors[:, 2]
        decoded[:, i * 2 + 1] = priors[:, 1] + landms[:, i * 2 + 1] * variances[0] * priors[:, 3]
    return decoded


def nms(dets, thresh):
    """Greedy non-maximum suppression. dets: (N, 5) with [x1,y1,x2,y2,score]."""
    x1 = dets[:, 0]
    y1 = dets[:, 1]
    x2 = dets[:, 2]
    y2 = dets[:, 3]
    scores = dets[:, 4]

    areas = (x2 - x1) * (y2 - y1)
    order = scores.argsort()[::-1]
    keep = []

    while order.size > 0:
        i = order[0]
        keep.append(i)
        xx1 = np.maximum(x1[i], x1[order[1:]])
        yy1 = np.maximum(y1[i], y1[order[1:]])
        xx2 = np.minimum(x2[i], x2[order[1:]])
        yy2 = np.minimum(y2[i], y2[order[1:]])
        inter = np.maximum(0.0, xx2 - xx1) * np.maximum(0.0, yy2 - yy1)
        iou = inter / (areas[i] + areas[order[1:]] - inter)
        inds = np.where(iou <= thresh)[0]
        order = order[inds + 1]

    return keep


def detect_faces_retinaface(session, input_name, img_bgr, det_size, score_thresh):
    """Run standalone RetinaFace-R50 detection.
    Returns list of (bbox_xyxy, landmarks_5x2, score) tuples."""
    orig_h, orig_w = img_bgr.shape[:2]

    # Letterbox resize to det_size x det_size
    scale_ratio = min(det_size / orig_w, det_size / orig_h)
    new_w = max(1, int(orig_w * scale_ratio))
    new_h = max(1, int(orig_h * scale_ratio))
    resized = cv2.resize(img_bgr, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    # Pad to det_size x det_size
    padded = np.zeros((det_size, det_size, 3), dtype=np.float32)
    padded[:new_h, :new_w, :] = resized.astype(np.float32)

    # Normalize: subtract mean
    padded -= RETINAFACE_MEAN

    # HWC -> NCHW
    blob = padded.transpose(2, 0, 1)[np.newaxis, ...]

    # Inference
    outputs = session.run(None, {input_name: blob})
    loc, conf, landms = outputs[0][0], outputs[1][0], outputs[2][0]

    # Generate priors for the padded size
    priors = generate_prior_boxes(det_size, det_size)

    # Decode
    boxes = decode_bboxes(loc, priors, RETINAFACE_VARIANCE)
    scores = conf[:, 1]
    landmarks = decode_landmarks(landms, priors, RETINAFACE_VARIANCE)

    # Scale boxes and landmarks to padded image coords
    boxes[:, 0::2] *= det_size
    boxes[:, 1::2] *= det_size
    landmarks[:, 0::2] *= det_size
    landmarks[:, 1::2] *= det_size

    # Filter by score
    mask = scores > score_thresh
    boxes = boxes[mask]
    scores = scores[mask]
    landmarks = landmarks[mask]

    if len(boxes) == 0:
        return []

    # NMS
    dets = np.hstack([boxes, scores[:, np.newaxis]])
    keep = nms(dets, RETINAFACE_NMS_THRESH)
    boxes = boxes[keep]
    scores = scores[keep]
    landmarks = landmarks[keep]

    # Rescale from padded coords to original image coords
    results = []
    for i in range(len(boxes)):
        bbox = boxes[i] / scale_ratio
        kps = landmarks[i].reshape(5, 2) / scale_ratio
        results.append((bbox.astype(np.int32), kps.astype(np.float32), float(scores[i])))

    return results


# Image rotation, when necessary
def rotateImg(img, tiff_orient):
    if tiff_orient == 8 or tiff_orient == 5:
        img = np.rot90(img, 1)
    elif tiff_orient == 3 or tiff_orient == 4:
        img = np.rot90(img, 2)
    elif tiff_orient == 6 or tiff_orient == 7:
        img = np.rot90(img, 3)
    if tiff_orient == 5 or tiff_orient == 7:
        img = np.flipud(img)
    elif tiff_orient == 2 or tiff_orient == 4:
        img = np.fliplr(img)
    return img

# handles Palette images with Transparency expressed in bytes
def convertToRGB(image):
    if image.mode in ("L", "RGB", "P"):
        t = image.info.get("transparency")
        if isinstance(t, bytes):
            image = image.convert('RGBA')
    return image.convert('RGB')

def load_and_preprocess(img_path, code, max_size):
    """Load image, convert to RGB numpy array, apply rotation and resizing."""
    if code == video:
        tiff_orient = 1
    else:
        tiff_orient = int(code)

    scale = 1
    is_video = (code == video)

    # The caller supplies the orientation for originals and 1 for an already
    # oriented preview. Ignore embedded EXIF so that it cannot rotate twice.
    img = cv2.imread(img_path, cv2.IMREAD_COLOR | cv2.IMREAD_IGNORE_ORIENTATION)
    if img is not None:
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    else:
        # PIL fallback for formats OpenCV can't handle
        with PIL.Image.open(img_path) as pil_img:
            img = np.array(convertToRGB(pil_img))

    img = np.ascontiguousarray(rotateImg(img, tiff_orient))

    if not is_video:
        h, w = img.shape[:2]
        if max(w, h) > max_size:
            scale = max_size / max(w, h)
            new_w, new_h = max(1, int(w * scale)), max(1, int(h * scale))
            img = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

    return img, scale


def align_face_5pt(img_rgb, landmarks_5pt):
    """Align face to 112x112 using 5-point landmarks and similarity transform."""
    M, _ = cv2.estimateAffinePartial2D(landmarks_5pt, ARCFACE_REF)
    if M is None:
        return None
    return cv2.warpAffine(img_rgb, M, (112, 112))


def get_auraface_embeddings_batch(session, input_name, aligned_faces_rgb):
    """Run AuraFace ONNX model on a batch of 112x112 aligned RGB faces.
    Returns list of L2-normalized embeddings."""
    if len(aligned_faces_rgb) == 0:
        return []

    batch = np.stack([
        ((face.astype(np.float32) - 127.5) / 127.5).transpose(2, 0, 1)
        for face in aligned_faces_rgb
    ])  # (N, 3, 112, 112)

    embeddings = session.run(None, {input_name: batch})[0]  # (N, 512)

    embeddings = np.asarray(embeddings, dtype=np.float32)
    if embeddings.shape != (len(aligned_faces_rgb), 512) or not np.isfinite(embeddings).all():
        raise ValueError('Recognition model must return one finite 512-dimensional embedding per face')
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    if not np.isfinite(norms).all() or np.any(norms <= 1e-10):
        raise ValueError('Recognition model returned an invalid embedding norm')
    embeddings = embeddings / norms
    return list(embeddings)


def write_response(locations, embeddings):
    """Validate the complete response before writing any protocol bytes."""
    if len(locations) != len(embeddings):
        raise ValueError('Face locations and embeddings must have equal lengths')
    lines = [str(len(locations))]
    for location in locations:
        if len(location) != 4:
            raise ValueError('Face locations must contain four coordinates')
        # NumPy 2 scalar repr includes np.int32(...), which Java cannot parse.
        coordinates = tuple(int(value) for value in location)
        if coordinates[2] <= coordinates[0] or coordinates[1] <= coordinates[3]:
            raise ValueError('Face location must have positive width and height')
        lines.append(str(coordinates))
    for embedding in embeddings:
        vector = np.asarray(embedding, dtype=np.float32)
        if vector.shape != (512,) or not np.isfinite(vector).all():
            raise ValueError('Face embedding must contain 512 finite values')
        norm = float(np.linalg.norm(vector))
        if not math.isclose(norm, 1.0, rel_tol=1e-4, abs_tol=1e-4):
            raise ValueError('Face embedding must be L2-normalized')
        lines.append(' '.join(f'{float(value):.8g}' for value in vector))
    print('\n'.join(lines), file=stdout, flush=True)


def rescale_bbox(x1, y1, x2, y2, scale):
    """Rescale bounding box coordinates back to original image size."""
    return int(x1 / scale), int(y1 / scale), int(x2 / scale), int(y2 / scale)


def process_one_image_auraface(img_path, code, det_session, det_input_name, det_size,
                               rec_session, rec_input_name, max_size, min_det_score):
    """Process one image using standalone RetinaFace detection + AuraFace recognition."""
    try:
        img_rgb, scale = load_and_preprocess(img_path, code, max_size)
    except Exception:
        print(imgError, file=stdout, flush=True)
        return

    img_bgr = img_rgb[:, :, ::-1]
    faces = detect_faces_retinaface(det_session, det_input_name, img_bgr, det_size, min_det_score)

    if not faces:
        write_response([], [])
        return

    aligned_faces = []
    locations = []
    for bbox, kps, score in faces:
        aligned = align_face_5pt(img_rgb, kps)
        if aligned is None:
            continue
        x1, y1, x2, y2 = bbox
        if scale != 1:
            x1, y1, x2, y2 = rescale_bbox(x1, y1, x2, y2, scale)
        aligned_faces.append(aligned)
        locations.append((y1, x2, y2, x1))

    if not aligned_faces:
        write_response([], [])
        return

    embeddings = get_auraface_embeddings_batch(rec_session, rec_input_name, aligned_faces)

    write_response(locations, embeddings)


def process_one_image_insightface(img_path, code, app, max_size, min_det_score):
    """Process one image using InsightFace pipeline (buffalo_l/buffalo_s)."""
    try:
        img_rgb, scale = load_and_preprocess(img_path, code, max_size)
    except Exception:
        print(imgError, file=stdout, flush=True)
        return

    img_bgr = img_rgb[:, :, ::-1]
    faces = app.get(img_bgr)
    faces = [f for f in faces if f.det_score >= min_det_score]

    locations = []
    embeddings = []
    for face in faces:
        x1, y1, x2, y2 = face.bbox.astype(int)
        if scale != 1:
            x1, y1, x2, y2 = rescale_bbox(x1, y1, x2, y2, scale)
        location = (y1, x2, y2, x1)
        locations.append(location)
        embeddings.append(face.normed_embedding)
    write_response(locations, embeddings)


def validate_options(max_size, model_name, det_size, min_det_score, execution_provider):
    if model_name not in SUPPORTED_MODELS:
        raise ValueError('Unsupported face model: ' + model_name)
    if max_size <= 0 or det_size <= 0:
        raise ValueError('maxResolution and detSize must be positive')
    if not math.isfinite(min_det_score) or not 0 <= min_det_score <= 1:
        raise ValueError('minDetScore must be finite and between 0 and 1')
    if execution_provider not in EXECUTION_PROVIDERS:
        raise ValueError('executionProvider must be auto, cpu or cuda')


def verify_session_provider(session, execution_provider, label):
    active = session.get_providers()
    print(f'{label} providers: {active}', file=sys.stderr, flush=True)
    if execution_provider == 'cuda':
        if 'CUDAExecutionProvider' not in active:
            raise RuntimeError(label + ': CUDA provider failed to load; check CUDA/cuDNN dependencies')
    elif execution_provider == 'cpu' and active != ['CPUExecutionProvider']:
        raise RuntimeError(label + ': explicit CPU mode loaded an unexpected provider')
    elif execution_provider == 'auto' and 'CUDAExecutionProvider' not in active:
        print(label + ': automatic CPU fallback is active.', file=sys.stderr, flush=True)
    # We control runtime fallback so it also retries the current image and keeps
    # the detector and recognizer on the same effective execution provider.
    session.disable_fallback()


ORT_RUNTIME_ERRORS = (ort_state.Fail, ort_state.RuntimeException, ort_state.EPFail)


class CudaInferenceError(RuntimeError):
    """An ORT execution failure from a session with an active CUDA provider."""


class GuardedSession:
    def __init__(self, session):
        self.session = session

    def __getattr__(self, name):
        return getattr(self.session, name)

    def run(self, *args, **kwargs):
        try:
            return self.session.run(*args, **kwargs)
        except ORT_RUNTIME_ERRORS as error:
            if 'CUDAExecutionProvider' in self.session.get_providers():
                raise CudaInferenceError('CUDA inference failed: ' + str(error)) from error
            raise


def switch_sessions_to_cpu(sessions):
    for label, session in sessions:
        session.set_providers(['CPUExecutionProvider'])
        verify_session_provider(session, 'cpu', label)


class ProcessingPipeline:
    def __init__(self, process, sessions, execution_provider):
        self.process_image = process
        self.sessions = sessions
        self.execution_provider = execution_provider

    def process(self, img_path, code):
        try:
            self.process_image(img_path, code)
        except CudaInferenceError as error:
            if self.execution_provider != 'auto':
                raise RuntimeError(str(error) + '; explicit CUDA mode cannot fall back. '
                                   'Use executionProvider=cpu or fix the GPU environment.') from error
            print(str(error) + '; switching this worker to CPU and retrying the image once.',
                  file=sys.stderr, flush=True)
            switch_sessions_to_cpu(self.sessions)
            # A retry failure propagates: it is a runtime failure, not an unreadable image.
            self.process_image(img_path, code)


def initialize_pipeline(factory, execution_provider, providers):
    """Create the complete pipeline, with one fresh CPU retry for auto CUDA failures."""
    try:
        process, sessions = factory(providers)
    except ORT_RUNTIME_ERRORS as error:
        if execution_provider != 'auto' or 'CUDAExecutionProvider' not in providers:
            raise
        print('CUDA initialization failed: ' + str(error) + '; initializing both models on CPU.',
              file=sys.stderr, flush=True)
        process, sessions = factory(['CPUExecutionProvider'])
    for label, session in sessions:
        verify_session_provider(session, execution_provider, label)
    cuda_sessions = ['CUDAExecutionProvider' in session.get_providers() for _, session in sessions]
    if execution_provider == 'auto' and any(cuda_sessions) and not all(cuda_sessions):
        print('Mixed CUDA/CPU initialization; switching both models to CPU.', file=sys.stderr, flush=True)
        switch_sessions_to_cpu(sessions)
    return ProcessingPipeline(process, sessions, execution_provider)


'''
Main function of external process which will detect and encode faces.
It is executed out of process to workaround python GIL bottleneck.
Multiprocessing module does not work with jep-3.9.1.
'''
def main():
    max_size = int(sys.argv[1])
    model_name = sys.argv[2]
    det_size = int(sys.argv[3])
    min_det_score = float(sys.argv[4])
    model_dir = sys.argv[5] if len(sys.argv) > 5 else None
    execution_provider = sys.argv[6] if len(sys.argv) > 6 else 'auto'
    validate_options(max_size, model_name, det_size, min_det_score, execution_provider)
    preload_runtime_dlls(onnxruntime, execution_provider)
    providers = requested_providers(onnxruntime, execution_provider)

    use_auraface = (model_name == 'auraface')

    if use_auraface:
        # Standalone RetinaFace-R50 (MIT) + AuraFace recognition (Apache 2.0)
        models_dest = os.path.join(model_dir, 'models', AURAFACE_SUBDIR) if model_dir else '.'
        os.makedirs(models_dest, exist_ok=True)

        # Download RetinaFace-R50 detection model on first use
        det_path = download_file(
            RETINAFACE_R50_URL,
            os.path.join(models_dest, RETINAFACE_R50_FILE),
            'RetinaFace-R50 detection model (~104 MB)')

        # Download AuraFace recognition model on first use
        rec_path = download_file(
            AURAFACE_REC_URL,
            os.path.join(models_dest, AURAFACE_REC_FILE),
            'AuraFace-v1 recognition model (~261 MB)')

        sess_opts = onnxruntime.SessionOptions()
        sess_opts.intra_op_num_threads = 2
        sess_opts.inter_op_num_threads = 1

        def factory(selected_providers):
            det_session = GuardedSession(onnxruntime.InferenceSession(
                det_path, sess_options=sess_opts, providers=selected_providers,
                provider_options=session_provider_options(selected_providers)))
            rec_session = GuardedSession(onnxruntime.InferenceSession(
                rec_path, sess_options=sess_opts, providers=selected_providers,
                provider_options=session_provider_options(selected_providers)))
            det_input_name = det_session.get_inputs()[0].name
            rec_input_name = rec_session.get_inputs()[0].name

            def process_image(img_path, code):
                process_one_image_auraface(img_path, code, det_session, det_input_name, det_size,
                                           rec_session, rec_input_name, max_size, min_det_score)
            return process_image, [('RetinaFace', det_session), ('AuraFace', rec_session)]
    else:
        # InsightFace mode (buffalo_l, buffalo_s, etc.)
        from insightface.app import FaceAnalysis
        kwargs = {}
        if model_dir:
            kwargs['root'] = model_dir

        def factory(selected_providers):
            app = FaceAnalysis(name=model_name, providers=selected_providers,
                               provider_options=session_provider_options(selected_providers),
                               allowed_modules=['detection', 'recognition'], **kwargs)
            app.prepare(ctx_id=-1 if selected_providers == ['CPUExecutionProvider'] else 0,
                        det_size=(det_size, det_size))
            sessions = []
            for name, model in app.models.items():
                model.session = GuardedSession(model.session)
                sessions.append(('InsightFace ' + name, model.session))

            def process_image(img_path, code):
                process_one_image_insightface(img_path, code, app, max_size, min_det_score)
            return process_image, sessions

    pipeline = initialize_pipeline(factory, execution_provider, providers)
    if use_auraface:
        det_providers = pipeline.sessions[0][1].get_providers()
        rec_providers = pipeline.sessions[1][1].get_providers()
        sys.stderr.write('AuraFace mode: RetinaFace-R50 (MIT) + AuraFace (Apache 2.0)\n')
        sys.stderr.write(f'Providers: det={det_providers}, rec={rec_providers}\n')
    else:
        providers_used = [session.get_providers() for _, session in pipeline.sessions]
        sys.stderr.write(f'InsightFace mode: {model_name}\n')
        sys.stderr.write(f'InsightFace providers: {providers_used}\n')
    sys.stderr.flush()

    def process_safely(img_path, code):
        try:
            pipeline.process(img_path, code)
        except Exception as error:
            print('Face processing failed: ' + str(error), file=sys.stderr, flush=True)
            raise

    while True:
        global processed_files
        if processed_files >= max_files:
            break

        try:
            line = input()
        except EOFError:
            break
        if line == terminate:
            break
        if line == ping:
            print(ping, file=stdout, flush=True)
            continue

        if line.startswith('batch:'):
            n = int(line[6:])
            for _ in range(n):
                img_path = input()
                code = input()
                process_safely(img_path, code)
                processed_files += 1
            continue

        processed_files += 1
        code = input()
        process_safely(line, code)

    return

def run_worker():
    try:
        main()
    except Exception:
        import traceback
        try:
            traceback.print_exc(file=sys.stderr)
            sys.stderr.flush()
        finally:
            # A failed CUDA context can also hang during interpreter cleanup.
            # Exit immediately so the task observes EOF and discards this worker.
            os._exit(1)
    # Force exit to avoid ONNX Runtime GPU session hanging on cleanup.
    os._exit(0)


if __name__ == "__main__":
    run_worker()
