"""Validate benchmark labels and failure cleanup without model inference."""
import importlib.util
import os
from pathlib import Path
import sys
import threading
import time
import types

import pytest


@pytest.fixture
def benchmark_module():
    path = Path(__file__).with_name('benchmark_face_recognition.py')
    spec = importlib.util.spec_from_file_location('face_benchmark_under_test', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def worker_script(tmp_path, provider):
    script = tmp_path / 'fake_worker.py'
    script.write_text(
        'import sys\n'
        'print("Providers: det=[\\\'' + provider + '\\\'], rec=[\\\'' + provider + '\\\']", file=sys.stderr, flush=True)\n'
        'for line in sys.stdin:\n'
        '    if line.strip() == "ping": print("ping", flush=True)\n'
        '    elif line.strip() == "terminate_process": break\n', encoding='utf-8')
    return script


def test_undefined_exif_orientation_is_not_a_rotation(benchmark_module):
    image = types.SimpleNamespace(getexif=lambda: {274: 0})
    assert benchmark_module.image_orientation(image) == '1'
    image = types.SimpleNamespace(getexif=lambda: {274: 6})
    assert benchmark_module.image_orientation(image) == '6'


def test_cpu_worker_requires_cpu_evidence(benchmark_module, tmp_path):
    script = worker_script(tmp_path, 'CPUExecutionProvider')
    worker = benchmark_module.Worker(sys.executable, script, ['cpu'], time.monotonic() + 10)
    assert worker.stderr == ["Providers: det=['CPUExecutionProvider'], rec=['CPUExecutionProvider']"]
    worker.close()
    worker.close()
    assert worker.proc.returncode == 0


@pytest.mark.parametrize('requested, actual', [
    ('cpu', 'CUDAExecutionProvider'), ('cuda', 'CPUExecutionProvider'),
])
def test_mislabeled_provider_is_rejected(benchmark_module, tmp_path, requested, actual):
    script = worker_script(tmp_path, actual)
    with pytest.raises(RuntimeError, match='unexpectedly activated|fell back'):
        benchmark_module.Worker(sys.executable, script, [requested], time.monotonic() + 10)


def test_cuda_dlib_is_rejected_even_with_python_optimization(benchmark_module, tmp_path, monkeypatch):
    (tmp_path / 'dlib.py').write_text('DLIB_USE_CUDA = True\n__version__ = "test"\n', encoding='utf-8')
    script = worker_script(tmp_path, 'CPUExecutionProvider')
    monkeypatch.setenv('PYTHONPATH', str(tmp_path))
    monkeypatch.setenv('PYTHONOPTIMIZE', '1')
    with pytest.raises(RuntimeError, match='Worker exited'):
        benchmark_module.Worker(sys.executable, script, ['cpu'], time.monotonic() + 10, dlib=True)


def test_failed_configuration_unblocks_and_closes_peer_workers(benchmark_module, monkeypatch, tmp_path):
    module = benchmark_module
    created = []

    class FakeWorker:
        def __init__(self, *args, **kwargs):
            self.index = len(created)
            self.closed = threading.Event()
            self.calls = 0
            self.stderr = []
            self.proc = types.SimpleNamespace(pid=os.getpid())
            created.append(self)

        def image(self, image, legacy):
            self.calls += 1
            if self.calls > 1:
                if self.index == 0:
                    raise RuntimeError('Invalid worker output')
                assert self.closed.wait(2), 'Peer remained blocked after the other worker failed'
                raise RuntimeError('Peer closed')
            return {'faces': 0, 'image_errors': 0, 'invalid_boxes': 0, 'invalid_embeddings': 0}

        def close(self):
            self.closed.set()

    class FakeMonitor:
        @staticmethod
        def system_vram():
            return None

        def __init__(self, workers):
            self.rss = []
            self.vram = []
            self.thread = types.SimpleNamespace(start=lambda: None)

        def stop(self):
            pass

    monkeypatch.setattr(module, 'Worker', FakeWorker)
    monkeypatch.setattr(module, 'Monitor', FakeMonitor)
    args = types.SimpleNamespace(timeout=10, max_resolution=1024, det_size=640,
                                 min_det_score=0.5, model_dir=tmp_path, python=sys.executable,
                                 repeats=1)
    result = module.benchmark(args, [(tmp_path / 'a.jpg', '1'), (tmp_path / 'b.jpg', '1')], 'cpu', 2)
    assert result['status'] == 'failed'
    assert all(worker.closed.is_set() for worker in created)
