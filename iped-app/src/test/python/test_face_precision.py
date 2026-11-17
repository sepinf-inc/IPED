"""Verify full float32 precision reaches every model without running inference."""
from pathlib import Path
import sys
from types import ModuleType, SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'resources/scripts/tasks'))
import FaceRecognitionProcess as worker


@pytest.mark.parametrize('providers,expected', [
    (['CUDAExecutionProvider', 'CPUExecutionProvider'], [{'use_tf32': '0'}, {}]),
    (['CPUExecutionProvider'], [{}]),
    (['CPUExecutionProvider', 'CUDAExecutionProvider'], [{}, {'use_tf32': '0'}]),
])
def test_precision_options_follow_provider_order_without_changing_requests(providers, expected):
    original = list(providers)
    assert worker.session_provider_options(providers) == expected
    assert providers == original
    first = worker.session_provider_options(providers)
    first[0]['diagnostic_only'] = 'value'
    assert worker.session_provider_options(providers) == expected


@pytest.mark.parametrize('model', ['auraface', 'buffalo_l', 'buffalo_s'])
@pytest.mark.parametrize('provider', ['cpu', 'cuda'])
def test_all_model_factories_pass_precision_to_runtime(model, provider, monkeypatch):
    providers = ['CPUExecutionProvider'] if provider == 'cpu' else ['CUDAExecutionProvider', 'CPUExecutionProvider']
    expected_options = worker.session_provider_options(providers)
    calls = []

    def session():
        return SimpleNamespace(get_providers=lambda: list(providers), disable_fallback=lambda: None,
                               get_inputs=lambda: [SimpleNamespace(name='input')])

    def create_session(path, **kwargs):
        calls.append(kwargs)
        return session()

    def create_analysis(**kwargs):
        calls.append(kwargs)
        return SimpleNamespace(models={'detection': SimpleNamespace(session=session()),
                                       'recognition': SimpleNamespace(session=session())},
                               prepare=lambda **options: None)

    insightface = ModuleType('insightface')
    app = ModuleType('insightface.app')
    app.FaceAnalysis = create_analysis
    insightface.app = app
    monkeypatch.setitem(sys.modules, 'insightface', insightface)
    monkeypatch.setitem(sys.modules, 'insightface.app', app)
    monkeypatch.setattr(worker.sys, 'argv', ['worker', '1024', model, '640', '.5', 'unused-model-dir', provider])
    monkeypatch.setattr('builtins.input', lambda: worker.terminate)
    monkeypatch.setattr(worker, 'preload_runtime_dlls', lambda *args: None)
    monkeypatch.setattr(worker, 'requested_providers', lambda *args: list(providers))
    monkeypatch.setattr(worker, 'download_file', lambda url, destination, label: destination)
    monkeypatch.setattr(worker.os, 'makedirs', lambda *args, **kwargs: None)
    monkeypatch.setattr(worker.onnxruntime, 'SessionOptions', lambda: SimpleNamespace())
    monkeypatch.setattr(worker.onnxruntime, 'InferenceSession', create_session)
    worker.main()

    assert len(calls) == (2 if model == 'auraface' else 1)
    for call in calls:
        assert call['providers'] == providers
        assert call['provider_options'] == expected_options
