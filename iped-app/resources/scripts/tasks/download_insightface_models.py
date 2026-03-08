#!/usr/bin/env python3
"""
Download face recognition models for offline/portable use with IPED.

Run this script once (with internet access) before using IPED in
offline or portable/removable-drive scenarios.

Usage:
    python download_insightface_models.py [--model MODEL] [--dest DIR]

Options:
    --model MODEL   Model name to download (default: auraface).
                    Options:
                      auraface  - RetinaFace-R50 (MIT) + AuraFace-v1 (Apache 2.0, ~365MB)
                      buffalo_l - InsightFace (non-commercial, ~300MB) — more accurate
                      buffalo_s - InsightFace (non-commercial, smaller/faster)
    --dest DIR      Destination directory for model files.
                    Default: ../../../models/insightface (relative to this script).

The downloaded models will be stored in the destination directory
and used automatically by FaceRecognitionProcess.py.
"""

import argparse
import os
import sys
import urllib.request

AURAFACE_REC_URL = 'https://huggingface.co/fal/AuraFace-v1/resolve/main/glintr100.onnx'
RETINAFACE_R50_URL = 'https://github.com/Zeyi-Lin/HivisionIDPhotos/releases/download/pretrained-model/retinaface-resnet50.onnx'


def _download_file(url, fpath, label):
    """Download a file if it doesn't already exist."""
    fname = os.path.basename(fpath)
    if os.path.exists(fpath):
        print(f'  {fname} already exists, skipping.')
    else:
        print(f'  Downloading {label}...')
        urllib.request.urlretrieve(url, fpath)
        size_mb = os.path.getsize(fpath) / (1024 * 1024)
        print(f'  Saved {fname} ({size_mb:.1f} MB)')


def download_auraface(dest):
    """Download AuraFace-v1 models: RetinaFace-R50 (~104 MB) + AuraFace recognition (~261 MB)."""
    model_dir = os.path.join(dest, 'models', 'auraface')
    os.makedirs(model_dir, exist_ok=True)
    _download_file(RETINAFACE_R50_URL, os.path.join(model_dir, 'retinaface-resnet50.onnx'),
                   'RetinaFace-R50 detection model (~104 MB)')
    _download_file(AURAFACE_REC_URL, os.path.join(model_dir, 'glintr100.onnx'),
                   'AuraFace-v1 recognition model (~261 MB)')
    return model_dir


def download_insightface_model(dest, model_name):
    """Download an InsightFace model pack (buffalo_l, buffalo_s, etc.)."""
    os.environ['INSIGHTFACE_HOME'] = dest
    try:
        from insightface.app import FaceAnalysis
    except ImportError:
        print('Error: insightface is not installed.', file=sys.stderr)
        print('Install it with: pip install insightface onnxruntime-gpu', file=sys.stderr)
        sys.exit(1)
    app = FaceAnalysis(name=model_name, root=dest,
                       providers=['CPUExecutionProvider'])
    app.prepare(ctx_id=-1, det_size=(640, 640))


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_dest = os.path.normpath(os.path.join(script_dir, '..', '..', '..', 'models', 'insightface'))

    parser = argparse.ArgumentParser(description='Download face recognition models for IPED offline use.')
    parser.add_argument('--model', default='auraface',
                        help='Model name to download (default: auraface)')
    parser.add_argument('--dest', default=default_dest,
                        help=f'Destination directory (default: {default_dest})')
    args = parser.parse_args()

    dest = os.path.abspath(args.dest)
    os.makedirs(dest, exist_ok=True)

    print(f'Downloading model "{args.model}" to {dest} ...')

    if args.model == 'auraface':
        print('RetinaFace-R50 (MIT) + AuraFace-v1 (Apache 2.0), ~365 MB total')
        download_auraface(dest)
    else:
        print(f'InsightFace {args.model} (non-commercial license)')
        download_insightface_model(dest, args.model)

    print(f'\nModel "{args.model}" is ready at: {dest}')
    print('IPED can now run face recognition without internet access.')


if __name__ == '__main__':
    main()
