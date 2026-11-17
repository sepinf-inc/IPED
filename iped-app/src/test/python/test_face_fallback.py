"""Provider failure handling with fake sessions; these tests run no model inference."""
import io
from pathlib import Path
import sys

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'resources/scripts/tasks'))
import FaceRecognitionProcess as worker


CUDA = ['CUDAExecutionProvider', 'CPUExecutionProvider']
CPU = ['CPUExecutionProvider']


class FakeSession:
    def __init__(self, model, providers, fail_cuda=False, fail_cpu=False, other_error=None):
        self.model = model
        self.providers = list(providers)
        self.fail_cuda = fail_cuda
        self.fail_cpu = fail_cpu
        self.other_error = other_error
        self.calls = []
        self.provider_changes = []
        self.fallback_disabled = False

    def get_providers(self):
        return list(self.providers)

    def set_providers(self, providers):
        self.providers = list(providers)
        self.provider_changes.append(list(providers))
        self.fallback_disabled = False

    def disable_fallback(self):
        self.fallback_disabled = True

    def run(self, outputs, inputs):
        self.calls.append(list(self.providers))
        if self.other_error is not None:
            raise self.other_error
        if self.fail_cuda and 'CUDAExecutionProvider' in self.providers:
            raise worker.ort_state.RuntimeException('simulated CUDA allocation failure')
        if self.fail_cpu and self.providers == CPU:
            raise worker.ort_state.Fail('simulated CPU runtime failure')
        return []


def factory_for(raw_sessions, vector=None):
    guarded = [worker.GuardedSession(session) for session in raw_sessions]
    vector = np.eye(1, 512, dtype=np.float32)[0] if vector is None else vector

    def factory(providers):
        def process(path, code):
            guarded[0].run(None, {'image': path})
            guarded[1].run(None, {'face': path})
            worker.write_response([(0, 10, 10, 0)], [vector])
        return process, list(zip(['RetinaFace', 'AuraFace'], guarded))
    return factory


@pytest.mark.parametrize('failing_session', [0, 1])
def test_auto_retries_current_image_once_and_remains_cpu(monkeypatch, failing_session):
    raw = [FakeSession('retinaface', CUDA), FakeSession('auraface', CUDA)]
    raw[failing_session].fail_cuda = True
    factory = factory_for(raw)
    pipeline = worker.initialize_pipeline(factory, 'auto', CUDA)
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    pipeline.process('same-image', '1')
    first_output = output.getvalue().splitlines()
    assert first_output[0] == '1' and len(first_output) == 3
    assert len(first_output[2].split()) == 512
    assert all(session.providers == CPU and session.fallback_disabled for session in raw)
    assert [session.model for session in raw] == ['retinaface', 'auraface']
    assert all(session.provider_changes == [CPU] for session in raw)
    pipeline.process('next-image', '1')
    assert len(output.getvalue().splitlines()) == 6
    assert all(session.calls[-1] == CPU for session in raw)
    assert all(session.calls.count(CUDA) == 1 for session in raw[:failing_session + 1])
    assert all(session.provider_changes == [CPU] for session in raw)


def test_explicit_cuda_error_never_retries_on_cpu(monkeypatch):
    raw = [FakeSession('retinaface', CUDA), FakeSession('auraface', CUDA, fail_cuda=True)]
    pipeline = worker.initialize_pipeline(factory_for(raw), 'cuda', CUDA)
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    with pytest.raises(RuntimeError, match='explicit CUDA mode cannot fall back'):
        pipeline.process('image', '1')
    assert output.getvalue() == ''
    assert all(not session.provider_changes for session in raw)


def test_cpu_retry_failure_propagates_without_image_error(monkeypatch):
    raw = [FakeSession('retinaface', CUDA, fail_cuda=True, fail_cpu=True), FakeSession('auraface', CUDA)]
    pipeline = worker.initialize_pipeline(factory_for(raw), 'auto', CUDA)
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    with pytest.raises(worker.ort_state.Fail, match='CPU runtime failure'):
        pipeline.process('image', '1')
    assert output.getvalue() == ''
    assert raw[0].calls == [CUDA, CPU]
    assert all(session.provider_changes == [CPU] for session in raw)


def test_output_validation_does_not_trigger_cpu_fallback(monkeypatch):
    raw = [FakeSession('retinaface', CUDA), FakeSession('auraface', CUDA)]
    pipeline = worker.initialize_pipeline(factory_for(raw, np.zeros(512)), 'auto', CUDA)
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    with pytest.raises(ValueError, match='L2-normalized'):
        pipeline.process('image', '1')
    assert output.getvalue() == ''
    assert all(not session.provider_changes for session in raw)


@pytest.mark.parametrize('error', [ValueError('alignment failed'), worker.ort_state.InvalidArgument('bad model input')])
def test_non_runtime_errors_do_not_trigger_cpu_fallback(error, monkeypatch):
    raw = [FakeSession('retinaface', CUDA, other_error=error), FakeSession('auraface', CUDA)]
    pipeline = worker.initialize_pipeline(factory_for(raw), 'auto', CUDA)
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    with pytest.raises(type(error)):
        pipeline.process('image', '1')
    assert output.getvalue() == ''
    assert all(not session.provider_changes for session in raw)


def test_auto_initialization_failure_creates_fresh_cpu_pipeline():
    calls = []
    raw = [FakeSession('retinaface', CPU), FakeSession('auraface', CPU)]

    def factory(providers):
        calls.append(list(providers))
        if providers == CUDA:
            raise worker.ort_state.Fail('unsupported CUDA architecture')
        return factory_for(raw)(providers)

    pipeline = worker.initialize_pipeline(factory, 'auto', CUDA)
    assert calls == [CUDA, CPU]
    assert all(session.get_providers() == CPU for _, session in pipeline.sessions)
    assert all(session.fallback_disabled for session in raw)


@pytest.mark.parametrize('mode', ['cpu', 'cuda'])
def test_explicit_provider_initialization_failure_has_no_retry(mode):
    calls = []

    def factory(providers):
        calls.append(list(providers))
        raise worker.ort_state.Fail('initialization failed')

    providers = CPU if mode == 'cpu' else CUDA
    with pytest.raises(worker.ort_state.Fail):
        worker.initialize_pipeline(factory, mode, providers)
    assert calls == [providers]


def test_cpu_initialization_retry_failure_is_not_retried_again():
    calls = []

    def factory(providers):
        calls.append(list(providers))
        raise worker.ort_state.Fail('initialization failed')

    with pytest.raises(worker.ort_state.Fail):
        worker.initialize_pipeline(factory, 'auto', CUDA)
    assert calls == [CUDA, CPU]


def test_mixed_automatic_providers_are_moved_to_cpu_together():
    raw = [FakeSession('retinaface', CPU), FakeSession('auraface', CUDA)]
    pipeline = worker.initialize_pipeline(factory_for(raw), 'auto', CUDA)
    assert all(session.get_providers() == CPU for _, session in pipeline.sessions)
    assert all(session.provider_changes == [CPU] and session.fallback_disabled for session in raw)


def test_image_load_failure_is_the_only_image_error(monkeypatch):
    raw = [FakeSession('retinaface', CUDA), FakeSession('auraface', CUDA)]
    guarded = [worker.GuardedSession(session) for session in raw]
    output = io.StringIO()
    monkeypatch.setattr(worker, 'stdout', output)
    monkeypatch.setattr(worker, 'load_and_preprocess', lambda *args: (_ for _ in ()).throw(OSError('bad image')))

    def process(path, code):
        worker.process_one_image_auraface(path, code, guarded[0], 'input', 640,
                                         guarded[1], 'input', 1024, .5)

    pipeline = worker.ProcessingPipeline(process, list(zip(['det', 'rec'], guarded)), 'auto')
    pipeline.process('image', '1')
    assert output.getvalue() == 'image_error\n'
    assert all(not session.calls and not session.provider_changes for session in raw)


def test_fatal_runtime_error_flushes_diagnostics_and_forces_failed_exit(monkeypatch):
    class Diagnostics(io.StringIO):
        flushed = False

        def flush(self):
            self.flushed = True
            super().flush()

    errors = Diagnostics()
    output = io.StringIO()
    monkeypatch.setattr(worker.sys, 'stderr', errors)
    monkeypatch.setattr(worker, 'stdout', output)
    monkeypatch.setattr(worker, 'main', lambda: (_ for _ in ()).throw(worker.ort_state.Fail('runtime failure')))

    def exit_immediately(code):
        raise SystemExit(code)

    monkeypatch.setattr(worker.os, '_exit', exit_immediately)
    with pytest.raises(SystemExit) as exit_result:
        worker.run_worker()
    assert exit_result.value.code == 1
    assert errors.flushed and 'runtime failure' in errors.getvalue()
    assert output.getvalue() == ''
