"""Prove actual provider execution for both AuraFace models using synthetic inputs.

No photographs, embeddings, local model paths or raw profiles are saved. This is
a provider smoke test, not a performance or recognition-quality benchmark.
"""
import argparse
from collections import Counter
import importlib.metadata
import json
import pathlib
import sys
import tempfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
import FaceRecognitionModelConfig as models
import FaceRecognitionProcess as worker
import numpy as np
import onnxruntime as ort


def profile_model(path, label, provider, directory):
    size, digest = models.MODEL_INTEGRITY[path.name]
    if not models.valid_model_file(str(path), size, digest):
        raise ValueError(label + ' model is absent or failed size/SHA-256 verification')
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.enable_profiling = True
    options.profile_file_prefix = str(pathlib.Path(directory) / label)
    session = ort.InferenceSession(str(path), sess_options=options,
                                   providers=worker.requested_providers(ort, provider))
    worker.verify_session_provider(session, provider, label)
    input_name = session.get_inputs()[0].name
    try:
        if label == 'detection':
            # Exercise the production detector including its real model.
            worker.detect_faces_retinaface(session, input_name, np.zeros((640, 640, 3), dtype=np.uint8), 640, .5)
        else:
            # Run recognition even though a synthetic blank has no detected face.
            worker.get_auraface_embeddings_batch(session, input_name,
                                                [np.zeros((112, 112, 3), dtype=np.uint8)])
    finally:
        profile_path = session.end_profiling()
    events = json.loads(pathlib.Path(profile_path).read_text(encoding='utf-8'))
    providers = Counter(event['args']['provider'] for event in events
                        if event.get('cat') == 'Node' and event.get('args', {}).get('provider'))
    if provider == 'cuda' and not providers.get('CUDAExecutionProvider'):
        raise RuntimeError(label + ': no CUDA kernel execution was recorded')
    if provider == 'cpu' and (not providers.get('CPUExecutionProvider') or set(providers) != {'CPUExecutionProvider'}):
        raise RuntimeError(label + ': profiling did not confirm CPU-only kernel execution')
    return {'loaded_providers': session.get_providers(), 'kernel_events_by_provider': dict(providers),
            'model_sha256': digest, 'synthetic_samples': 1}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model-dir', required=True, help='Directory containing the two pinned AuraFace ONNX files')
    parser.add_argument('--execution-provider', choices=('cpu', 'cuda'), required=True)
    parser.add_argument('--output', required=True, help='Summary JSON destination under the repository target directory')
    args = parser.parse_args()
    target = pathlib.Path(__file__).resolve().parents[5] / 'target'
    output = pathlib.Path(args.output).resolve()
    if target.resolve() not in output.parents:
        parser.error('--output must be inside the repository target directory')
    output.parent.mkdir(parents=True, exist_ok=True)
    worker.preload_runtime_dlls(ort, args.execution_provider)
    model_dir = pathlib.Path(args.model_dir)
    with tempfile.TemporaryDirectory(dir=str(output.parent)) as directory:
        versions = {'onnxruntime': ort.__version__}
        for name in ('numpy', 'opencv-python', 'pillow', 'nvidia-cuda-runtime-cu12',
                     'nvidia-cuda-nvrtc-cu12', 'nvidia-cufft-cu12', 'nvidia-curand-cu12',
                     'nvidia-cudnn-cu12', 'nvidia-cublas-cu12'):
            try:
                versions[name] = importlib.metadata.version(name)
            except importlib.metadata.PackageNotFoundError:
                pass
        summary = {'requested_provider': args.execution_provider, 'input': 'synthetic_blank',
                   'versions': versions,
                   'models': {
                       'detection': profile_model(model_dir / models.RETINAFACE_R50_FILE, 'detection', args.execution_provider, directory),
                       'recognition': profile_model(model_dir / models.AURAFACE_REC_FILE, 'recognition', args.execution_provider, directory),
                   }}
    output.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(summary))


if __name__ == '__main__':
    main()
