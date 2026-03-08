#!/usr/bin/env python3
"""
Download InsightFace models for offline/portable use with IPED.

Run this script once (with internet access) before using IPED in
offline or portable/removable-drive scenarios.

Usage:
    python download_insightface_models.py [--model MODEL] [--dest DIR]

Options:
    --model MODEL   Model name to download (default: buffalo_l).
                    Options: buffalo_l (more accurate), buffalo_s (smaller/faster).
    --dest DIR      Destination directory for model files.
                    Default: ../../../models/insightface (relative to this script).

The downloaded models will be stored in the destination directory
and used automatically by FaceRecognitionProcess.py via the
INSIGHTFACE_HOME environment variable.
"""

import argparse
import os
import sys


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    default_dest = os.path.normpath(os.path.join(script_dir, '..', '..', '..', 'models', 'insightface'))

    parser = argparse.ArgumentParser(description='Download InsightFace models for IPED offline use.')
    parser.add_argument('--model', default='buffalo_l',
                        help='Model name to download (default: buffalo_l)')
    parser.add_argument('--dest', default=default_dest,
                        help=f'Destination directory (default: {default_dest})')
    args = parser.parse_args()

    dest = os.path.abspath(args.dest)
    os.makedirs(dest, exist_ok=True)

    # Point InsightFace storage to the desired directory
    os.environ['INSIGHTFACE_HOME'] = dest

    try:
        from insightface.app import FaceAnalysis
    except ImportError:
        print('Error: insightface is not installed.', file=sys.stderr)
        print('Install it with: pip install insightface onnxruntime opencv-python numpy', file=sys.stderr)
        sys.exit(1)

    print(f'Downloading InsightFace model "{args.model}" to {dest} ...')
    print('This may take a few minutes on the first run (~300 MB for buffalo_l).')

    # Initializing FaceAnalysis triggers the model download if not present
    app = FaceAnalysis(name=args.model, root=dest,
                       providers=['CPUExecutionProvider'])
    app.prepare(ctx_id=-1, det_size=(640, 640))

    print(f'Model "{args.model}" is ready at: {dest}')
    print('IPED can now run face recognition without internet access.')


if __name__ == '__main__':
    main()
