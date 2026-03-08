'''
# External process used by FaceRecognitionTask.py to do the hard work to bypass python GIL and allow multiprocess parallelization.
# Supports two modes:
#   - 'auraface': MediaPipe detection + AuraFace recognition (fully Apache 2.0)
#   - 'buffalo_l'/'buffalo_s': InsightFace pipeline (non-commercial license, more accurate)
'''
import sys
stdout = sys.stdout
sys.stdout = sys.stderr

import os
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

# ArcFace standard reference landmarks for 112x112 aligned face
ARCFACE_REF = np.array([
    [38.2946, 51.6963],   # left eye
    [73.5318, 51.5014],   # right eye
    [56.0252, 71.7366],   # nose tip
    [41.5493, 92.3655],   # left mouth corner
    [70.7299, 92.2041],   # right mouth corner
], dtype=np.float32)

# MediaPipe face_mesh landmark indices for the 5 ArcFace reference points
MP_LEFT_EYE_INNER = 133
MP_LEFT_EYE_OUTER = 33
MP_RIGHT_EYE_INNER = 362
MP_RIGHT_EYE_OUTER = 263
MP_NOSE_TIP = 1
MP_LEFT_MOUTH = 61
MP_RIGHT_MOUTH = 291

AURAFACE_REC_URL = 'https://huggingface.co/fal/AuraFace-v1/resolve/main/glintr100.onnx'
MEDIAPIPE_LANDMARKER_URL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task'


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
    img = PIL.Image.open(img_path)
    img = convertToRGB(img)

    if not is_video:
        size = img.size
        if max(size[0], size[1]) > max_size:
            scale = max_size / max(size[0], size[1])
            if size[0] > size[1]:
                new_size = (max_size, int(size[1] * scale))
            else:
                new_size = (int(size[0] * scale), max_size)
            img = img.resize(new_size, resample=Image.Resampling.BILINEAR)

    img = np.array(img)
    img = rotateImg(img, tiff_orient)
    return img, scale


def download_file(url, dest_path, label=None):
    """Download a file if it doesn't already exist."""
    if os.path.exists(dest_path):
        return dest_path
    import urllib.request
    if label:
        sys.stderr.write(f"Downloading {label}...\n")
        sys.stderr.flush()
    urllib.request.urlretrieve(url, dest_path)
    size_mb = os.path.getsize(dest_path) / (1024 * 1024)
    sys.stderr.write(f"  Saved to {dest_path} ({size_mb:.1f} MB)\n")
    sys.stderr.flush()
    return dest_path


def align_face_arcface(img_rgb, landmarks, h, w):
    """Align a face to 112x112 using 5 MediaPipe landmarks and ArcFace reference points.
    Returns (aligned_face, (x1, y1, x2, y2)) or (None, None) on failure."""
    src_pts = np.array([
        [
            (landmarks[MP_LEFT_EYE_INNER].x + landmarks[MP_LEFT_EYE_OUTER].x) / 2 * w,
            (landmarks[MP_LEFT_EYE_INNER].y + landmarks[MP_LEFT_EYE_OUTER].y) / 2 * h,
        ],
        [
            (landmarks[MP_RIGHT_EYE_INNER].x + landmarks[MP_RIGHT_EYE_OUTER].x) / 2 * w,
            (landmarks[MP_RIGHT_EYE_INNER].y + landmarks[MP_RIGHT_EYE_OUTER].y) / 2 * h,
        ],
        [landmarks[MP_NOSE_TIP].x * w, landmarks[MP_NOSE_TIP].y * h],
        [landmarks[MP_LEFT_MOUTH].x * w, landmarks[MP_LEFT_MOUTH].y * h],
        [landmarks[MP_RIGHT_MOUTH].x * w, landmarks[MP_RIGHT_MOUTH].y * h],
    ], dtype=np.float32)

    M, _ = cv2.estimateAffinePartial2D(src_pts, ARCFACE_REF, method=cv2.LMEDS)
    if M is None:
        return None, None
    aligned = cv2.warpAffine(img_rgb, M, (112, 112))
    # Derive bbox from the 5 key points (avoids iterating all 478 landmarks)
    xs = src_pts[:, 0]
    ys = src_pts[:, 1]
    bbox = (int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max()))
    return aligned, bbox


def get_auraface_embedding(session, input_name, aligned_face_rgb):
    """Run AuraFace ONNX model on a 112x112 aligned RGB face. Returns L2-normalized embedding."""
    face = aligned_face_rgb.astype(np.float32)
    face = (face - 127.5) / 127.5  # normalize to [-1, 1]
    face = face.transpose(2, 0, 1)  # HWC -> CHW
    face = face[np.newaxis, ...]    # add batch dim

    embedding = session.run(None, {input_name: face})[0][0]

    # L2 normalize
    norm = np.linalg.norm(embedding)
    if norm > 0:
        embedding = embedding / norm
    return embedding


def rescale_bbox(x1, y1, x2, y2, scale):
    """Rescale bounding box coordinates back to original image size."""
    return int(x1 / scale), int(y1 / scale), int(x2 / scale), int(y2 / scale)


def process_one_image_auraface(img_path, code, landmarker, mp_module, rec_session, rec_input_name, max_size):
    """Process one image using MediaPipe + AuraFace (fully Apache 2.0)."""
    try:
        img_rgb, scale = load_and_preprocess(img_path, code, max_size)
    except Exception:
        print(imgError, file=stdout, flush=True)
        return

    h, w = img_rgb.shape[:2]
    mp_image = mp_module.Image(image_format=mp_module.ImageFormat.SRGB, data=np.ascontiguousarray(img_rgb))
    result = landmarker.detect(mp_image)

    if not result.face_landmarks:
        print('0', file=stdout, flush=True)
        return

    face_data = []
    for face_lms in result.face_landmarks:
        aligned, bbox = align_face_arcface(img_rgb, face_lms, h, w)
        if aligned is None:
            continue
        x1, y1, x2, y2 = bbox
        if scale != 1:
            x1, y1, x2, y2 = rescale_bbox(x1, y1, x2, y2, scale)
        embedding = get_auraface_embedding(rec_session, rec_input_name, aligned)
        face_data.append(((y1, x2, y2, x1), embedding))

    num_faces = len(face_data)
    print(str(num_faces), file=stdout, flush=True)
    if num_faces == 0:
        return

    for location, _ in face_data:
        print(str(location), file=stdout, flush=True)
    for _, embedding in face_data:
        print(' '.join(f'{float(v):.8g}' for v in embedding), file=stdout, flush=True)


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

    num_faces = len(faces)
    print(str(num_faces), file=stdout, flush=True)
    if num_faces == 0:
        return

    for face in faces:
        x1, y1, x2, y2 = face.bbox.astype(int)
        if scale != 1:
            x1, y1, x2, y2 = rescale_bbox(x1, y1, x2, y2, scale)
        location = (y1, x2, y2, x1)
        print(str(location), file=stdout, flush=True)

    for face in faces:
        embedding = face.normed_embedding
        print(' '.join(f'{float(v):.8g}' for v in embedding), file=stdout, flush=True)


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

    use_auraface = (model_name == 'auraface')

    if use_auraface:
        # MediaPipe + AuraFace mode (fully Apache 2.0 licensed)
        import mediapipe as mp
        import onnxruntime

        # Download models on first use
        models_dest = os.path.join(model_dir, 'models', 'auraface') if model_dir else '.'
        os.makedirs(models_dest, exist_ok=True)

        landmarker_path = download_file(
            MEDIAPIPE_LANDMARKER_URL,
            os.path.join(models_dest, 'face_landmarker.task'),
            'MediaPipe face_landmarker model (~3.6 MB)')
        rec_path = download_file(
            AURAFACE_REC_URL,
            os.path.join(models_dest, 'glintr100.onnx'),
            'AuraFace-v1 recognition model (~261 MB)')

        options = mp.tasks.vision.FaceLandmarkerOptions(
            base_options=mp.tasks.BaseOptions(model_asset_path=landmarker_path),
            num_faces=50,
            min_face_detection_confidence=min_det_score,
            min_face_presence_confidence=min_det_score,
        )
        landmarker = mp.tasks.vision.FaceLandmarker.create_from_options(options)

        rec_session = onnxruntime.InferenceSession(
            rec_path, providers=['CPUExecutionProvider']
        )
        rec_input_name = rec_session.get_inputs()[0].name
        providers_used = rec_session.get_providers()
        sys.stderr.write(f"AuraFace mode: MediaPipe detection + AuraFace recognition\n")
        sys.stderr.write(f"ONNX Runtime providers: {providers_used}\n")
        sys.stderr.flush()

        def process_fn(img_path, code):
            process_one_image_auraface(img_path, code, landmarker, mp, rec_session, rec_input_name, max_size)
    else:
        # InsightFace mode (buffalo_l, buffalo_s, etc.)
        from insightface.app import FaceAnalysis
        kwargs = {}
        if model_dir:
            kwargs['root'] = model_dir
        app = FaceAnalysis(name=model_name, providers=['CPUExecutionProvider'], **kwargs)
        app.prepare(ctx_id=0, det_size=(det_size, det_size))
        providers_used = [m.session.get_providers() for m in app.models.values()]
        sys.stderr.write(f"InsightFace mode: {model_name}\n")
        sys.stderr.write(f"InsightFace providers: {providers_used}\n")
        sys.stderr.flush()

        def process_fn(img_path, code):
            process_one_image_insightface(img_path, code, app, max_size, min_det_score)

    while True:
        global processed_files
        if processed_files >= max_files:
            break

        line = input()
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
                process_fn(img_path, code)
                processed_files += 1
            continue

        processed_files += 1
        code = input()
        process_fn(line, code)
    return

if __name__ == "__main__":
    main()
    # Force exit to avoid ONNX Runtime GPU session hanging on cleanup
    os._exit(0)
