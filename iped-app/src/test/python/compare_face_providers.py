#!/usr/bin/env python3
"""Compare CPU/CUDA numerical consistency, without measuring identity accuracy.

Select sixteen evenly spaced images from the sorted corpus. Save only aggregate
statistics, content-manifest hashes, settings and versions; never persist inputs,
filenames, bounding boxes or embeddings.
"""
import argparse
from collections import Counter
import hashlib
import importlib.metadata
import json
from pathlib import Path
import platform
import sys

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / 'iped-app/resources/scripts/tasks'))
import FaceRecognitionModelConfig as models
import FaceRecognitionProcess as worker
from benchmark_face_recognition import image_orientation
import numpy as np
import onnxruntime as ort
from PIL import Image

BOX_IOU_THRESHOLD = .99
EMBEDDING_DISTANCE_THRESHOLD = 1e-4


def sample_indices(count, limit=16):
    if count < 1 or limit < 1:
        raise ValueError('A nonempty corpus and positive sample limit are required')
    if count <= limit:
        return list(range(count))
    if limit == 1:
        return [0]
    return [round(index * (count - 1) / (limit - 1)) for index in range(limit)]


def box_iou(first, second):
    """Intersection over union for x1,y1,x2,y2 coordinates."""
    a = np.asarray(first, dtype=np.float64)
    b = np.asarray(second, dtype=np.float64)
    if a.shape != (4,) or b.shape != (4,) or not np.isfinite(a).all() or not np.isfinite(b).all():
        raise ValueError('Invalid face bounding box')
    if np.any(a[2:] <= a[:2]) or np.any(b[2:] <= b[:2]):
        raise ValueError('Degenerate face bounding box')
    intersection = np.maximum(0, np.minimum(a[2:], b[2:]) - np.maximum(a[:2], b[:2])).prod()
    union = (a[2:] - a[:2]).prod() + (b[2:] - b[:2]).prod() - intersection
    return float(intersection / union)


def match_boxes(cpu_boxes, cuda_boxes, threshold=BOX_IOU_THRESHOLD):
    """Require exactly one sufficiently overlapping match on each side."""
    if len(cpu_boxes) != len(cuda_boxes):
        raise ValueError('face_count_mismatch')
    if not cpu_boxes:
        return []
    overlaps = np.array([[box_iou(a, b) for b in cuda_boxes] for a in cpu_boxes])
    eligible = overlaps >= threshold
    if not np.all(eligible.sum(axis=0) == 1) or not np.all(eligible.sum(axis=1) == 1):
        raise ValueError('ambiguous_or_missing_box_match')
    return [(index, int(np.argmax(row)), float(overlaps[index, np.argmax(row)]))
            for index, row in enumerate(eligible)]


def embedding_distance(first, second):
    vectors = [np.asarray(value, dtype=np.float64) for value in (first, second)]
    norms = []
    for vector in vectors:
        if vector.shape != (512,) or not np.isfinite(vector).all():
            raise ValueError('invalid_embedding')
        norm = float(np.linalg.norm(vector))
        if abs(norm - 1) > 1e-4:
            raise ValueError('invalid_embedding')
        norms.append(norm)
    return float(np.clip(1 - np.dot(*vectors) / (norms[0] * norms[1]), 0, 2))


def create_sessions(model_dir, provider):
    worker.preload_runtime_dlls(ort, provider)
    sessions = []
    for filename, label in ((models.RETINAFACE_R50_FILE, 'detection'), (models.AURAFACE_REC_FILE, 'recognition')):
        path = model_dir / filename
        size, digest = models.MODEL_INTEGRITY[filename]
        if not models.valid_model_file(str(path), size, digest):
            raise ValueError('Pinned model verification failed')
        options = ort.SessionOptions()
        options.intra_op_num_threads = 2
        options.inter_op_num_threads = 1
        session = ort.InferenceSession(str(path), sess_options=options,
                                       providers=worker.requested_providers(ort, provider))
        worker.verify_session_provider(session, provider, label)
        sessions.append(session)
    return sessions


def infer_image(path, orientation, sessions):
    """Use the production preprocessing, detection, alignment and recognition."""
    detector, recognizer = sessions
    image, scale = worker.load_and_preprocess(str(path), orientation, 1024)
    detected = worker.detect_faces_retinaface(detector, detector.get_inputs()[0].name,
                                              image[:, :, ::-1], 640, .5)
    boxes, aligned = [], []
    for bbox, landmarks, _ in detected:
        face = worker.align_face_5pt(image, landmarks)
        if face is None:
            continue
        x1, y1, x2, y2 = [int(value) for value in bbox]
        if scale != 1:
            x1, y1, x2, y2 = worker.rescale_bbox(x1, y1, x2, y2, scale)
        box = (x1, y1, x2, y2)
        box_iou(box, box)  # Validate the same geometry required by the protocol.
        boxes.append(box)
        aligned.append(face)
    embeddings = worker.get_auraface_embeddings_batch(recognizer, recognizer.get_inputs()[0].name, aligned)
    if len(boxes) != len(embeddings):
        raise ValueError('Recognition face count differs from detection')
    for embedding in embeddings:
        embedding_distance(embedding, embedding)
    return boxes, embeddings


def provider_results(images, model_dir, provider):
    sessions = create_sessions(model_dir, provider)
    results = []
    for path, orientation in images:
        try:
            results.append(infer_image(path, orientation, sessions))
        except Exception:
            # Keep only an aggregate failure marker, never a private path or data.
            results.append(None)
    return results


def summarize(cpu_results, cuda_results):
    if len(cpu_results) != len(cuda_results):
        raise ValueError('Provider image counts differ')
    failures = Counter()
    cpu_faces = cuda_faces = matched_faces = images_with_faces = images_matched = 0
    overlaps, distances = [], []
    for cpu, cuda in zip(cpu_results, cuda_results):
        if cpu is None or cuda is None:
            failures['processing_error'] += 1
            continue
        cpu_boxes, cpu_embeddings = cpu
        cuda_boxes, cuda_embeddings = cuda
        cpu_faces += len(cpu_boxes)
        cuda_faces += len(cuda_boxes)
        images_with_faces += bool(cpu_boxes or cuda_boxes)
        try:
            pairs = match_boxes(cpu_boxes, cuda_boxes)
        except ValueError as error:
            reason = str(error)
            failures[reason if reason in ('face_count_mismatch', 'ambiguous_or_missing_box_match') else 'invalid_box'] += 1
            continue
        images_matched += 1
        for cpu_index, cuda_index, overlap in pairs:
            try:
                distance = embedding_distance(cpu_embeddings[cpu_index], cuda_embeddings[cuda_index])
            except (ValueError, IndexError):
                failures['invalid_embedding'] += 1
                continue
            overlaps.append(overlap)
            distances.append(distance)
            matched_faces += 1
            if distance > EMBEDDING_DISTANCE_THRESHOLD:
                failures['embedding_tolerance_exceeded'] += 1
    if matched_faces == 0:
        failures['no_faces_compared'] += 1
    return {'status': 'passed' if not failures else 'failed', 'images_compared': len(cpu_results),
            'images_with_faces': images_with_faces, 'images_with_matching_face_sets': images_matched,
            'cpu_faces': cpu_faces, 'cuda_faces': cuda_faces, 'matched_faces': matched_faces,
            'minimum_box_iou': min(overlaps, default=None),
            'maximum_embedding_cosine_distance': max(distances, default=None),
            'failures': dict(failures)}


def versions():
    result = {'python': platform.python_version(), 'onnxruntime': ort.__version__}
    for package in ('numpy', 'opencv-python', 'pillow', 'nvidia-cuda-runtime-cu12', 'nvidia-cuda-nvrtc-cu12',
                    'nvidia-cufft-cu12', 'nvidia-curand-cu12', 'nvidia-cudnn-cu12', 'nvidia-cublas-cu12'):
        try:
            result[package] = importlib.metadata.version(package)
        except importlib.metadata.PackageNotFoundError:
            pass
    return result


def manifest_hash(paths):
    hashes = [hashlib.sha256(path.read_bytes()).hexdigest() for path in paths]
    return hashlib.sha256('\n'.join(hashes).encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--images', type=Path, required=True)
    parser.add_argument('--model-dir', type=Path, required=True, help='Directory directly containing both pinned AuraFace ONNX files')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if (ROOT / 'target').resolve() not in output.parents:
        parser.error('--output must be inside the repository target directory')
    if not args.images.is_dir():
        parser.error('--images must be a corpus directory')
    paths = [path for path in sorted(args.images.iterdir()) if path.is_file() and
             path.suffix.lower() in ('.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff', '.webp')]
    if not paths:
        parser.error('No supported images found')
    selected = [paths[index] for index in sample_indices(len(paths))]
    summary = {'purpose': 'numerical_provider_consistency_not_identity_accuracy',
               'corpus_images': len(paths), 'selected_images': len(selected),
               'corpus_manifest_sha256': manifest_hash(paths), 'sample_manifest_sha256': manifest_hash(selected),
               'versions': versions(), 'settings': {'max_resolution': 1024, 'det_size': 640,
               'min_det_score': .5, 'minimum_pair_iou': BOX_IOU_THRESHOLD,
               'maximum_embedding_cosine_distance': EMBEDDING_DISTANCE_THRESHOLD},
               'model_sha256': {filename: values[1] for filename, values in models.MODEL_INTEGRITY.items()}}
    try:
        images = []
        for path in selected:
            with Image.open(path) as image:
                images.append((path, image_orientation(image)))
        summary.update(summarize(provider_results(images, args.model_dir, 'cpu'),
                                 provider_results(images, args.model_dir, 'cuda')))
    except Exception as error:
        summary.update(status='failed', failures={'setup_error': 1}, setup_error_type=type(error).__name__)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print('PASS' if summary['status'] == 'passed' else 'FAIL', json.dumps(summary), flush=True)
    return 0 if summary['status'] == 'passed' else 1


if __name__ == '__main__':
    sys.exit(main())
