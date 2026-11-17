#!/usr/bin/env python3
"""Force a real shared-arena CUDA allocation failure and validate automatic CPU recovery.

This validates failure handling on the available GPU, not P620 performance.
Only aggregate results and manifest hashes are saved; protocol responses remain in memory.
Run this tool alone, after other GPU/CPU timing experiments have completed.
"""
import argparse
import ast
import hashlib
import io
import json
import math
import os
from pathlib import Path
import sys

from benchmark_face_memory_proxy import MemoryMonitor, failure_code, shared_allocator
from benchmark_face_recognition import image_orientation
from compare_face_providers import ROOT, manifest_hash, models, sample_indices, versions, worker
import onnxruntime as ort
from PIL import Image


CUDA = ['CUDAExecutionProvider', 'CPUExecutionProvider']
CPU = ['CPUExecutionProvider']
_native_keepalive = []


def response_count(response):
    """Validate the complete production protocol, retaining no vectors or boxes."""
    lines = response.splitlines()
    if not lines or lines[0] == worker.imgError:
        raise ValueError('missing_response_or_image_error')
    count = int(lines[0])
    if count < 0 or count > 100000 or len(lines) != 1 + 2 * count:
        raise ValueError('invalid_response_count')
    for line in lines[1:1 + count]:
        box = ast.literal_eval(line)
        if (not isinstance(box, (tuple, list)) or len(box) != 4 or
                any(type(value) is not int for value in box) or
                box[1] <= box[3] or box[2] <= box[0]):
            raise ValueError('invalid_response_box')
    for line in lines[1 + count:]:
        vector = [float(value) for value in line.split()]
        if len(vector) != 512 or not all(math.isfinite(value) for value in vector):
            raise ValueError('invalid_response_embedding')
        if abs(sum(value * value for value in vector) - 1) > 1e-3:
            raise ValueError('invalid_response_embedding_norm')
    return count


def provider_state(pipeline):
    return [session.get_providers() for _, session in pipeline.sessions]


def create_factory(model_dir, arena_mib, events, attempts):
    class ObservedSession(worker.GuardedSession):
        def run(self, *args, **kwargs):
            try:
                return super().run(*args, **kwargs)
            except worker.CudaInferenceError as error:
                events.append({'phase': 'inference', 'failure': failure_code(error.__cause__ or error),
                               'error_type': type(error.__cause__ or error).__name__})
                raise

    def factory(selected_providers):
        attempts.append(list(selected_providers))
        sessions = []
        _native_keepalive.append(sessions)
        for filename, label in ((models.RETINAFACE_R50_FILE, 'RetinaFace'),
                                (models.AURAFACE_REC_FILE, 'AuraFace')):
            path = model_dir / filename
            size, digest = models.MODEL_INTEGRITY[filename]
            if not models.valid_model_file(str(path), size, digest):
                raise ValueError('pinned_model_verification_failed')
            options = ort.SessionOptions()
            options.intra_op_num_threads = 2
            options.inter_op_num_threads = 1
            options.add_session_config_entry('session.use_env_allocators', '1')
            provider_options = worker.session_provider_options(selected_providers)
            if 'CUDAExecutionProvider' in selected_providers:
                provider_options[selected_providers.index('CUDAExecutionProvider')].update(
                    device_id='0', gpu_mem_limit=str(int(arena_mib * 2**20)),
                    arena_extend_strategy='kSameAsRequested')
            try:
                session = ort.InferenceSession(str(path), sess_options=options,
                                               providers=selected_providers,
                                               provider_options=provider_options)
            except worker.ORT_RUNTIME_ERRORS as error:
                if 'CUDAExecutionProvider' in selected_providers:
                    events.append({'phase': 'initialization', 'failure': failure_code(error),
                                   'error_type': type(error).__name__})
                raise
            sessions.append((label, ObservedSession(session)))
        detector, recognizer = [session for _, session in sessions]
        det_input = detector.get_inputs()[0].name
        rec_input = recognizer.get_inputs()[0].name

        def process(path, orientation):
            worker.process_one_image_auraface(str(path), orientation, detector, det_input, 640,
                                             recognizer, rec_input, 1024, .5)
        return process, sessions
    return factory


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--images', type=Path, required=True)
    parser.add_argument('--model-dir', type=Path, required=True,
                        help='Directory directly containing the pinned detector and recognizer')
    parser.add_argument('--arena-mib', type=float, default=32)
    parser.add_argument('--expected-corpus-images', type=int, default=377)
    parser.add_argument('--expected-faces', type=int, default=62,
                        help='Previously measured CPU face total for these sixteen sampled images')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if (ROOT / 'target').resolve() not in output.parents:
        parser.error('--output must be inside the repository target directory')
    if not args.images.is_dir():
        parser.error('--images must be a corpus directory')
    if not math.isfinite(args.arena_mib) or args.arena_mib <= 0 or args.expected_faces < 0:
        parser.error('Use a finite positive arena size and a nonnegative expected face count')
    paths = [path for path in sorted(args.images.iterdir()) if path.is_file() and
             path.suffix.lower() in ('.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff', '.webp')]
    if len(paths) != args.expected_corpus_images or len(paths) < 16:
        parser.error('Corpus size differs from the supplied CPU reference corpus')
    selected = [paths[index] for index in sample_indices(len(paths), 16)]
    events, attempts, counts = [], [], []
    report = {'purpose': 'real_allocation_failure_and_cpu_recovery_not_hardware_emulation',
              'status': 'failed', 'workers': 1, 'model_id': 'auraface',
              'corpus_images': len(paths), 'selected_images': len(selected), 'images_processed': 0,
              'expected_cpu_faces': args.expected_faces, 'faces': 0,
              'corpus_manifest_sha256': manifest_hash(paths),
              'sample_manifest_sha256': manifest_hash(selected),
              'versions': versions(), 'settings': {'shared_arena_mib': args.arena_mib,
                  'session.use_env_allocators': '1', 'use_tf32': '0',
                  'max_resolution': 1024, 'det_size': 640, 'min_det_score': .5},
              'model_sha256': {name: value[1] for name, value in models.MODEL_INTEGRITY.items()},
              'source_sha256': hashlib.sha256((ROOT / 'iped-app/resources/scripts/tasks/FaceRecognitionProcess.py').read_bytes()).hexdigest(),
              'limitations': ['The arena is not a hard whole-device memory limit.',
                              'This does not establish P620 compatibility or performance.',
                              'Sampled GPU memory is system-wide and may miss brief peaks.']}
    monitor = MemoryMonitor(0)
    runtime_transitions = 0
    cpu_seen = False
    cpu_persistent = True
    try:
        images = []
        for path in selected:
            with Image.open(path) as image:
                images.append((path, image_orientation(image)))
        monitor.start()
        _native_keepalive.append(shared_allocator(args.arena_mib))
        pipeline = worker.initialize_pipeline(create_factory(args.model_dir, args.arena_mib, events, attempts),
                                               'auto', CUDA)
        _native_keepalive.append(pipeline)
        state = provider_state(pipeline)
        report['providers_after_initialization'] = state
        cpu_seen = all(providers == CPU for providers in state)
        for path, orientation in images:
            before = provider_state(pipeline)
            response = io.StringIO()
            original_output = worker.stdout
            try:
                worker.stdout = response
                pipeline.process(path, orientation)
            finally:
                worker.stdout = original_output
            counts.append(response_count(response.getvalue()))
            state = provider_state(pipeline)
            is_cpu = all(providers == CPU for providers in state)
            runtime_transitions += any('CUDAExecutionProvider' in providers for providers in before) and is_cpu
            if cpu_seen and not is_cpu:
                cpu_persistent = False
            cpu_seen = cpu_seen or is_cpu
        report['providers_after_processing'] = provider_state(pipeline)
        report['cpu_persistent_after_fallback'] = cpu_seen and cpu_persistent
        allocation_failure = any(event['failure'] == 'allocation_failure' for event in events)
        report['observed_allocation_failure'] = allocation_failure
        report['fallback_phase'] = ('initialization' if len(attempts) > 1 else
                                    'inference' if runtime_transitions else 'not_observed')
        report['fallback_count'] = (len(attempts) - 1) + int(runtime_transitions)
        if (allocation_failure and report['fallback_count'] == 1 and cpu_seen and cpu_persistent
                and sum(counts) == args.expected_faces and len(counts) == 16):
            report['status'] = 'passed'
    except Exception as error:
        report['failure'] = failure_code(error)
        report['failure_type'] = type(error).__name__
    finally:
        try:
            monitor.stop()
        except Exception as error:
            report['monitor_cleanup_failure'] = type(error).__name__
            report['status'] = 'failed'
        report['images_processed'] = len(counts)
        report['faces'] = sum(counts)
        report['counts_sha256'] = hashlib.sha256(','.join(str(value) for value in counts).encode()).hexdigest()
        report['failure_events'] = events
        report['initialization_attempts'] = attempts
        report['system_gpu_baseline_mib'] = monitor.gpu[0] if monitor.gpu else None
        report['peak_system_gpu_mib'] = max(monitor.gpu, default=None)
        report['peak_process_rss_mib'] = max(monitor.rss, default=None)
        report['hardware'] = monitor.hardware
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('PASS' if report['status'] == 'passed' else 'FAIL', json.dumps(report), flush=True)
    return 0 if report['status'] == 'passed' else 1


if __name__ == '__main__':
    exit_code = main()
    sys.stdout.flush()
    sys.stderr.flush()
    os._exit(exit_code)
