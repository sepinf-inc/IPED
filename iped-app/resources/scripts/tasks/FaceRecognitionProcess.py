'''
# External process used by FaceRecognitionTask.py to do the hard work to bypass python GIL and allow multiprocess parallelization.
'''
import sys
stdout = sys.stdout
sys.stdout = sys.stderr

import os
import PIL
from PIL import Image
import numpy as np
import traceback
from insightface.app import FaceAnalysis

PIL.ImageFile.LOAD_TRUNCATED_IMAGES = True

terminate = 'terminate_process'
imgError = "image_error"
ping = "ping"
video = "video"

max_files = 2000
processed_files = 0

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

def process_one_image(img_path, code, app, max_size, min_det_score):
    if code == video:
        isVideo = True
        tiff_orient = 1
    else:
        isVideo = False
        tiff_orient = int(code)

    scale = 1
    try:
        img = PIL.Image.open(img_path)
        img = convertToRGB(img)

        if not isVideo:
            size = img.size
            if max(size[0], size[1]) > max_size:
                scale = max_size / max(size[0], size[1])
                if size[0] > size[1]:
                    new_size = (max_size, int(size[1] * scale))
                else:
                    new_size = (int(size[0] * scale), max_size)
                img = img.resize(new_size, resample=Image.Resampling.BILINEAR)

    except Exception:
        print(imgError, file=stdout, flush=True)
        return

    img = np.array(img)
    img = rotateImg(img, tiff_orient)

    # Convert RGB to BGR for InsightFace
    img_bgr = img[:, :, ::-1]

    faces = app.get(img_bgr)

    # Filter by detection confidence
    faces = [f for f in faces if f.det_score >= min_det_score]

    num_faces = len(faces)
    print(str(num_faces), file=stdout, flush=True)
    if num_faces == 0:
        return

    for face in faces:
        x1, y1, x2, y2 = face.bbox.astype(int)
        if scale != 1:
            x1 = int(x1 / scale)
            y1 = int(y1 / scale)
            x2 = int(x2 / scale)
            y2 = int(y2 / scale)
        # Convert to (top, right, bottom, left) format for compatibility
        location = (y1, x2, y2, x1)
        print(str(location), file=stdout, flush=True)

    for face in faces:
        embedding = face.normed_embedding  # L2-normalized, required for cosine distance
        print(' '.join(map(repr, embedding)), file=stdout, flush=True)

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

    # Initialize InsightFace model once; root= controls where models are stored/loaded
    kwargs = {}
    if model_dir:
        kwargs['root'] = model_dir
    app = FaceAnalysis(name=model_name, providers=['CUDAExecutionProvider', 'CPUExecutionProvider'], **kwargs)
    app.prepare(ctx_id=0, det_size=(det_size, det_size))
    providers_used = [m.session.get_providers() for m in app.models.values()]
    sys.stderr.write(f"InsightFace providers: {providers_used}\n")
    sys.stderr.flush()

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
                process_one_image(img_path, code, app, max_size, min_det_score)
                processed_files += 1
            continue

        processed_files += 1
        code = input()
        process_one_image(line, code, app, max_size, min_det_score)
    return

if __name__ == "__main__":
    main()
    # Force exit to avoid ONNX Runtime GPU session hanging on cleanup
    os._exit(0)
