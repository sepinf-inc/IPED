'''
# External process used by FaceRecognitionTask.py for face detection + recognition.
# Supports two modes:
#   - 'auraface': InsightFace RetinaFace detection + AuraFace recognition
#   - 'buffalo_l'/'buffalo_s': InsightFace full pipeline (non-commercial license)
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

# Standard ArcFace 5-point reference landmarks for 112x112 alignment
ARCFACE_REF = np.array([
    [38.2946, 51.6963],   # left eye
    [73.5318, 51.5014],   # right eye
    [56.0252, 71.7366],   # nose tip
    [41.5493, 92.3655],   # left mouth corner
    [70.7299, 92.2041],   # right mouth corner
], dtype=np.float32)

AURAFACE_REC_URL = 'https://huggingface.co/fal/AuraFace-v1/resolve/main/glintr100.onnx'


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

    # OpenCV IMREAD_COLOR auto-applies EXIF rotation (OpenCV 4.5.2+),
    # avoiding coordinate mismatch on portrait images.
    img = cv2.imread(img_path, cv2.IMREAD_COLOR)
    if img is not None:
        img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)
    else:
        # PIL fallback for formats OpenCV can't handle
        pil_img = PIL.Image.open(img_path)
        pil_img = convertToRGB(pil_img)
        img = np.array(pil_img)
        img = rotateImg(img, tiff_orient)

    if not is_video:
        h, w = img.shape[:2]
        if max(w, h) > max_size:
            scale = max_size / max(w, h)
            new_w, new_h = int(w * scale), int(h * scale)
            img = cv2.resize(img, (new_w, new_h), interpolation=cv2.INTER_LINEAR)

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

    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    norms = np.maximum(norms, 1e-10)
    embeddings = embeddings / norms
    return list(embeddings)


def rescale_bbox(x1, y1, x2, y2, scale):
    """Rescale bounding box coordinates back to original image size."""
    return int(x1 / scale), int(y1 / scale), int(x2 / scale), int(y2 / scale)


def process_one_image_auraface(img_path, code, app, rec_session, rec_input_name,
                               max_size, min_det_score):
    """Process one image using InsightFace detection + AuraFace recognition."""
    try:
        img_rgb, scale = load_and_preprocess(img_path, code, max_size)
    except Exception:
        print(imgError, file=stdout, flush=True)
        return

    img_bgr = img_rgb[:, :, ::-1]
    faces = app.get(img_bgr)
    faces = [f for f in faces if f.det_score >= min_det_score]

    if not faces:
        print('0', file=stdout, flush=True)
        return

    aligned_faces = []
    locations = []
    for face in faces:
        aligned = align_face_5pt(img_rgb, face.kps.astype(np.float32))
        if aligned is None:
            continue
        x1, y1, x2, y2 = face.bbox.astype(int)
        if scale != 1:
            x1, y1, x2, y2 = rescale_bbox(x1, y1, x2, y2, scale)
        aligned_faces.append(aligned)
        locations.append((y1, x2, y2, x1))

    if not aligned_faces:
        print('0', file=stdout, flush=True)
        return

    embeddings = get_auraface_embeddings_batch(rec_session, rec_input_name, aligned_faces)

    num_faces = len(embeddings)
    print(str(num_faces), file=stdout, flush=True)
    for location in locations:
        print(str(location), file=stdout, flush=True)
    for embedding in embeddings:
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
        # InsightFace detection (RetinaFace) + AuraFace recognition
        from insightface.app import FaceAnalysis
        import onnxruntime

        # Load InsightFace detection model only (skip recognition model)
        det_kwargs = {}
        if model_dir:
            det_kwargs['root'] = model_dir
        app = FaceAnalysis(name='buffalo_l', allowed_modules=['detection'],
                           providers=['CPUExecutionProvider'], **det_kwargs)
        app.prepare(ctx_id=0, det_size=(det_size, det_size))

        # Download AuraFace recognition model on first use
        models_dest = os.path.join(model_dir, 'models', 'auraface') if model_dir else '.'
        os.makedirs(models_dest, exist_ok=True)

        rec_path = download_file(
            AURAFACE_REC_URL,
            os.path.join(models_dest, 'glintr100.onnx'),
            'AuraFace-v1 recognition model (~261 MB)')

        sess_opts = onnxruntime.SessionOptions()
        sess_opts.intra_op_num_threads = 2
        sess_opts.inter_op_num_threads = 1
        rec_session = onnxruntime.InferenceSession(
            rec_path, sess_options=sess_opts, providers=['CPUExecutionProvider'])
        rec_input_name = rec_session.get_inputs()[0].name

        det_providers = [m.session.get_providers() for m in app.models.values()]
        rec_providers = rec_session.get_providers()
        sys.stderr.write(f"AuraFace mode: InsightFace RetinaFace detection + AuraFace recognition\n")
        sys.stderr.write(f"Providers: det={det_providers}, rec={rec_providers}\n")
        sys.stderr.flush()

        def process_fn(img_path, code):
            process_one_image_auraface(img_path, code, app, rec_session, rec_input_name,
                                       max_size, min_det_score)
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
