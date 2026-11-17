#!/usr/bin/env python3
"""One-worker shared-arena memory proxy on the actual GPU, not P620 emulation.

The arena constraint is not a whole-device allocation limit. CUDA contexts,
libraries and some reserved allocations are outside it. Sampled system GPU
memory can miss short peaks and includes unrelated programs. WDDM residency,
Pascal compatibility and P620 performance cannot be established on an RTX 4070.
"""
import argparse
from collections import Counter
import csv
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time

from compare_face_providers import ROOT, infer_image, manifest_hash, models, versions, worker
from benchmark_face_recognition import image_orientation
import onnxruntime as ort
import psutil
from PIL import Image

# This diagnostic runs in its own process. Retain native CUDA objects until the
# final forced exit, avoiding ONNX Runtime's potentially blocking GPU teardown.
_native_keepalive = []


def budget_status(samples, budget_mib):
    if not samples or not all(math.isfinite(value) for value in samples):
        return 'unobserved'
    return 'observed_budget_exceeded' if max(samples) > budget_mib else 'within_observed_budget'


def failure_code(error):
    message = str(error).lower()
    if any(value in message for value in ('out of memory', 'out_of_memory', 'bfcarena',
                                          'failed to allocate', 'available memory of', 'cudamalloc')):
        return 'allocation_failure'
    return 'setup_or_inference_failure'


class MemoryMonitor:
    """Sample the selected GPU every 100 ms using one nvidia-smi process."""
    def __init__(self, device):
        self.device = device
        self.gpu, self.rss = [], []
        self.stop_event = threading.Event()
        self.process = None
        self.threads = []
        self.hardware = {}

    def start(self):
        flags = subprocess.CREATE_NO_WINDOW if os.name == 'nt' else 0
        command = ['nvidia-smi', '--id=' + str(self.device), '--query-gpu=memory.used',
                   '--format=csv,noheader,nounits']
        initial = subprocess.check_output(
            ['nvidia-smi', '--id=' + str(self.device),
             '--query-gpu=name,driver_version,memory.total,memory.used', '--format=csv,noheader,nounits'],
            text=True, timeout=5, creationflags=flags)
        name, driver, total, used = next(csv.reader([initial.strip()], skipinitialspace=True))
        self.hardware = {'gpu_name': name, 'driver_version': driver, 'gpu_total_mib': float(total)}
        self.gpu.append(float(used))
        self.process = subprocess.Popen(command + ['--loop-ms=100'], stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, text=True, creationflags=flags)
        def gpu_samples():
            try:
                for line in self.process.stdout:
                    try:
                        value = float(line.strip())
                        if math.isfinite(value):
                            self.gpu.append(value)
                    except ValueError:
                        pass
            except (OSError, ValueError):
                pass
        def rss_samples():
            process = psutil.Process()
            while not self.stop_event.is_set():
                self.rss.append(process.memory_info().rss / 2**20)
                self.stop_event.wait(.1)
        self.threads = [threading.Thread(target=function, daemon=True)
                        for function in (gpu_samples, rss_samples)]
        for thread in self.threads:
            thread.start()

    def stop(self):
        self.stop_event.set()
        if self.process is not None:
            if self.process.poll() is None:
                self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait(timeout=3)
        for thread in self.threads:
            thread.join(timeout=3)
        if self.process is not None:
            self.process.stdout.close()


def shared_allocator(arena_mib):
    """Register one process-local CUDA allocator before creating either model."""
    if ort.__version__ != '1.22.0':
        raise ValueError('This shared-allocator proxy is validated for ONNX Runtime 1.22.0')
    worker.preload_runtime_dlls(ort, 'cuda')
    worker.requested_providers(ort, 'cuda')
    memory = ort.OrtMemoryInfo('Cuda', ort.OrtAllocatorType.ORT_ARENA_ALLOCATOR,
                               0, ort.OrtMemType.DEFAULT)
    arena = ort.OrtArenaCfg(int(arena_mib * 2**20), 1, -1, -1)
    ort.create_and_register_allocator_v2('CUDAExecutionProvider', memory, {}, arena)
    return arena


def add_session(sessions, model_dir, filename, label, arena_mib, profile_dir):
    path = model_dir / filename
    size, digest = models.MODEL_INTEGRITY[filename]
    if not models.valid_model_file(str(path), size, digest):
        raise ValueError('Pinned model verification failed')
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.inter_op_num_threads = 1
    options.add_session_config_entry('session.use_env_allocators', '1')
    options.enable_profiling = True
    options.profile_file_prefix = str(Path(profile_dir) / label)
    providers = ['CUDAExecutionProvider', 'CPUExecutionProvider']
    provider_options = worker.session_provider_options(providers)
    provider_options[0].update(device_id='0', gpu_mem_limit=str(int(arena_mib * 2**20)),
                               arena_extend_strategy='kSameAsRequested')
    session = ort.InferenceSession(str(path), sess_options=options,
                                   providers=providers, provider_options=provider_options)
    sessions.append(session)
    worker.verify_session_provider(session, 'cuda', label)


def end_profiles(sessions):
    proof = {}
    for label, session in zip(('detection', 'recognition'), sessions):
        profile = session.end_profiling()
        events = json.loads(Path(profile).read_text(encoding='utf-8'))
        counts = Counter(event['args']['provider'] for event in events
                         if event.get('cat') == 'Node' and event.get('args', {}).get('provider'))
        proof[label] = {'loaded_providers': session.get_providers(),
                        'use_tf32': session.get_provider_options().get('CUDAExecutionProvider', {}).get('use_tf32'),
                        'kernel_events_by_provider': dict(counts)}
    return proof


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--images', type=Path, required=True)
    parser.add_argument('--model-dir', type=Path, required=True, help='Directory directly containing the two AuraFace ONNX files')
    parser.add_argument('--arena-mib', type=float, default=1536)
    parser.add_argument('--gpu-budget-mib', type=float, default=2048)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    if (ROOT / 'target').resolve() not in output.parents:
        parser.error('--output must be under the repository target directory')
    if not args.images.is_dir():
        parser.error('--images must be a corpus directory')
    if any(not math.isfinite(value) or value <= 0 for value in (args.arena_mib, args.gpu_budget_mib)):
        parser.error('Memory settings must be finite and positive')
    paths = [path for path in sorted(args.images.iterdir()) if path.is_file() and
             path.suffix.lower() in ('.jpg', '.jpeg', '.png', '.bmp', '.tif', '.tiff', '.webp')]
    if not paths:
        parser.error('No supported corpus images found')
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'purpose': 'shared_arena_memory_proxy_not_hardware_emulation', 'workers': 1,
              'versions': versions(), 'status': 'failed', 'image_count': len(paths),
              'images_processed': 0, 'faces': 0, 'no_face_images': 0,
              'manifest_sha256': manifest_hash(paths),
              'model_sha256': {name: values[1] for name, values in models.MODEL_INTEGRITY.items()},
              'settings': {'shared_arena_mib': args.arena_mib, 'observed_gpu_budget_mib': args.gpu_budget_mib,
                           'session.use_env_allocators': '1', 'arena_extend_strategy': 'kSameAsRequested',
                           'max_resolution': 1024, 'det_size': 640, 'min_det_score': .5,
                           'sample_interval_ms': 100, 'recognition_batching': 'unchanged_production'},
              'limitations': [
                  'Arena constraint is not a hard limit on total GPU allocations.',
                  'System-wide sampled GPU memory includes other programs and may miss short peaks.',
                  'RTX 4070 results do not establish P620/Pascal compatibility or performance.',
                  'WDDM residency/paging cannot emulate a physically smaller graphics card.',
                  'An observed peak above the budget invalidates the requested memory proxy.',
                  'Profiling of initial face-bearing images adds overhead; this is not a throughput comparison.',
              ]}
    monitor, sessions = MemoryMonitor(0), []
    _native_keepalive.append(sessions)
    profiling = False
    with tempfile.TemporaryDirectory(dir=str(output.parent)) as profile_dir:
        try:
            monitor.start()
            start = time.perf_counter()
            arena = shared_allocator(args.arena_mib)  # Keep arena configuration alive through both sessions.
            _native_keepalive.append(arena)
            add_session(sessions, args.model_dir, models.RETINAFACE_R50_FILE, 'detection', args.arena_mib, profile_dir)
            profiling = True
            add_session(sessions, args.model_dir, models.AURAFACE_REC_FILE, 'recognition', args.arena_mib, profile_dir)
            report['startup_seconds'] = time.perf_counter() - start
            start = time.perf_counter()
            for path in paths:
                with Image.open(path) as image:
                    orientation = image_orientation(image)
                boxes, embeddings = infer_image(path, orientation, sessions)
                report['images_processed'] += 1
                report['faces'] += len(embeddings)
                report['no_face_images'] += not boxes
                if profiling and embeddings:
                    # End profiling once both real models executed. Keep the
                    # remainder of the full-corpus pass free of profile growth.
                    report['provider_proof'] = end_profiles(sessions)
                    profiling = False
            report['processing_seconds'] = time.perf_counter() - start
            report['status'] = 'completed'
        except Exception as error:
            report['failure'] = failure_code(error)
            report['failure_type'] = type(error).__name__
        finally:
            if profiling or (sessions and 'provider_proof' not in report):
                try:
                    report['provider_proof'] = end_profiles(sessions)
                except Exception:
                    report['profile_error'] = True
            try:
                monitor.stop()
            except Exception as error:
                report['monitor_cleanup_failure'] = type(error).__name__
            report['system_gpu_baseline_mib'] = monitor.gpu[0] if monitor.gpu else None
            report['hardware'] = monitor.hardware
            report['peak_system_gpu_mib'] = max(monitor.gpu, default=None)
            report['peak_process_rss_mib'] = max(monitor.rss, default=None)
            report['gpu_memory_samples'] = len(monitor.gpu)
            # An isolated pre-start sample cannot establish the run's peak.
            report['observed_budget_status'] = budget_status(
                monitor.gpu if len(monitor.gpu) > 1 else [], args.gpu_budget_mib)
            proof = report.get('provider_proof', {})
            report['both_models_executed_cuda'] = all(
                proof.get(label, {}).get('kernel_events_by_provider', {}).get('CUDAExecutionProvider', 0) > 0
                for label in ('detection', 'recognition'))
            report['proxy_passed'] = (report['status'] == 'completed' and report['both_models_executed_cuda']
                                      and 'monitor_cleanup_failure' not in report
                                      and report['observed_budget_status'] == 'within_observed_budget')
            output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('PASS' if report['proxy_passed'] else 'FAIL', json.dumps(report), flush=True)
    return 0 if report['proxy_passed'] else 1


if __name__ == '__main__':
    exit_code = main()
    sys.stdout.flush()
    sys.stderr.flush()
    os._exit(exit_code)
