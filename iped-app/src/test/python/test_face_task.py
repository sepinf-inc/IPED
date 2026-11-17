"""Subprocess protocol and shared Jep-task lifecycle tests without a JVM."""
import importlib.util
import io
import subprocess
import sys
import types
from pathlib import Path
from unittest.mock import Mock

import pytest


@pytest.fixture
def task_module(monkeypatch):
    java_lang = types.ModuleType('java.lang')
    java_lang.Runtime = types.SimpleNamespace(getRuntime=lambda: types.SimpleNamespace(availableProcessors=lambda: 8))
    java_lang.System = types.SimpleNamespace(getProperty=lambda name: 'test-iped')
    config_module = types.ModuleType('iped.engine.config')
    config_module.VideoThumbsConfig = object()
    properties = types.ModuleType('iped.properties')
    properties.ExtraProperties = types.SimpleNamespace(
        FACE_COUNT='face_count', FACE_MODEL='face_model', FACE_LOCATIONS='face_locations',
        FACE_ENCODINGS='face_encodings', IMAGE_META_PREFIX='image:')
    monkeypatch.setitem(sys.modules, 'java.lang', java_lang)
    monkeypatch.setitem(sys.modules, 'iped.engine.config', config_module)
    monkeypatch.setitem(sys.modules, 'iped.properties', properties)
    path = Path(__file__).resolve().parents[3] / 'resources/scripts/tasks/FaceRecognitionTask.py'
    spec = importlib.util.spec_from_file_location('face_task_under_test', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.logger = Mock()
    module.javaConverter = types.SimpleNamespace(toKnnVector=lambda values: values)
    monkeypatch.setattr(module, 'checkDependencies', Mock())
    return module


def configuration(values=None, enabled=True):
    props = types.SimpleNamespace(getProperty=(values or {}).get)
    task = types.SimpleNamespace(isEnabled=lambda: enabled, getConfiguration=lambda: props)
    return types.SimpleNamespace(
        getTaskConfigurable=lambda name: task,
        findObject=lambda kind: types.SimpleNamespace(getVideoThumbsSubitems=lambda: False))


class Worker:
    def __init__(self, response=''):
        self.stdin = io.StringIO()
        self.stdout = io.StringIO(response)
        self.stderr = io.StringIO()
        self.status = None
        self.killed = False
        self.waited = False

    def poll(self):
        return self.status

    def kill(self):
        self.killed = True
        self.status = -9

    def wait(self, timeout=None):
        self.waited = True
        self.status = 0 if self.status is None else self.status
        return self.status


class Item:
    def __init__(self, hash_value='image'):
        self.attributes = {'hasThumb': True}
        self.hash = hash_value

    def getHash(self):
        return self.hash

    def getExtraAttribute(self, name):
        return self.attributes.get(name)

    def setExtraAttribute(self, name, value):
        self.attributes[name] = value

    def getMetadata(self):
        return {'image:Width': '100', 'image:Height': '100', 'image:tiff:Orientation': '1'}

    def getMediaType(self):
        return types.SimpleNamespace(toString=lambda: 'image/jpeg')

    def getViewFile(self):
        return None

    def hasPreview(self):
        return False

    def getTempFile(self):
        return types.SimpleNamespace(getAbsolutePath=lambda: 'image.jpg')

    def getPath(self):
        return 'image.jpg'


def embedding(first='1', rest='0', size=512):
    return ' '.join([first] + [rest] * (size - 1)) + '\n'


def test_last_initialized_instance_drains_every_worker_and_resets(task_module):
    module = task_module
    tasks = [module.FaceRecognitionTask() for _ in range(3)]
    for task in tasks:
        task.init(configuration({'maxResolution': '2048', 'insightFaceModel': 'buffalo_s'}))
    assert module.checkDependencies.call_count == 1
    workers = [Worker(), Worker(), Worker()]
    for worker in workers:
        module.processQueue.put(worker)
    module.numCreatedProcs = 3
    tasks[0].finish()
    tasks[0].finish()  # Idempotent even when a framework calls finish twice.
    tasks[1].finish()
    assert not any(worker.waited for worker in workers)
    tasks[2].finish()
    assert all(worker.waited and worker.stdout.closed for worker in workers)
    assert module.processQueue is None and module.numCreatedProcs == 0
    assert module.FaceRecognitionTask.enabled is None
    gui_task = module.FaceRecognitionTask()
    gui_task.init(configuration({'numFaceRecognitionProcesses': '1'}))
    assert module.max_size == 1024 and module.model_name == 'auraface'
    assert module.checkDependencies.call_count == 2
    worker = Worker()
    module.processQueue.put(worker)
    module.numCreatedProcs = 1
    gui_task.finish()
    assert worker.waited and module.activeInstances == 0


@pytest.mark.parametrize('values', [
    {'numFaceRecognitionProcesses': '0'}, {'numFaceRecognitionProcesses': '-1'},
    {'insightFaceModel': 'unknown'}, {'executionProvider': 'gpu'},
    {'minDetScore': 'nan'}, {'minDetScore': '1.1'}, {'detSize': '0'},
])
def test_invalid_configuration_fails_before_dependencies(task_module, values):
    with pytest.raises(ValueError):
        task_module.FaceRecognitionTask().init(configuration(values))
    task_module.checkDependencies.assert_not_called()


@pytest.mark.parametrize('response', [
    '', '-1\n', '100001\n', 'not-a-count\n',
    '1\n(1, 2, 3)\n', '1\n(3, 2, 1, 0)\n',
    '1\n(0, 2, 2, 0)\n' + embedding(size=128),
    '1\n(0, 2, 2, 0)\n' + embedding(first='nan'),
    '1\n(0, 2, 2, 0)\n' + embedding(first='inf'),
    '1\n(0, 2, 2, 0)\n' + embedding(first='0'),
    '1\n(0, 2, 2, 0)\n' + embedding(first='2'),
])
def test_bad_protocol_discards_worker_and_next_item_recovers(task_module, monkeypatch, response):
    module = task_module
    task = module.FaceRecognitionTask()
    task.init(configuration({'numFaceRecognitionProcesses': '1'}))
    broken, healthy = Worker('ping\n' + response), Worker('ping\n0\n')
    monkeypatch.setattr(module, 'createExternalProcess', Mock(side_effect=[broken, healthy]))
    failed = Item('bad')
    task.process(failed)
    assert 'face_count' not in failed.attributes and 'bad_count' not in module.cache
    assert broken.killed and broken.waited and module.numCreatedProcs == 0
    assert module.processQueue.empty()
    successful = Item('good')
    task.process(successful)
    assert successful.attributes['face_count'] == 0
    assert successful.attributes['face_model'] == 'auraface'
    assert module.numCreatedProcs == 1 and module.processQueue.qsize() == 1
    task.finish()


def test_valid_embeddings_and_cached_results_keep_model_tag(task_module, monkeypatch):
    module = task_module
    task = module.FaceRecognitionTask()
    task.init(configuration())
    worker = Worker('ping\n1\n(0, 10, 10, 0)\n' + embedding())
    monkeypatch.setattr(module, 'createExternalProcess', Mock(return_value=worker))
    original, duplicate = Item(), Item()
    task.process(original)
    task.process(duplicate)
    assert original.attributes == duplicate.attributes
    assert original.attributes['face_model'] == 'auraface'
    assert original.attributes['face_locations'] == [[0, 10, 10, 0]]
    assert len(original.attributes['face_encodings'][0]) == 512
    assert module.createExternalProcess.call_count == 1
    task.finish()


def test_image_load_error_keeps_worker_and_caches_failure(task_module, monkeypatch):
    module = task_module
    task = module.FaceRecognitionTask()
    task.init(configuration())
    worker = Worker('ping\nimage_error\n')
    monkeypatch.setattr(module, 'createExternalProcess', Mock(return_value=worker))
    item = Item()
    task.process(item)
    assert 'face_count' not in item.attributes
    assert module.cache['image_count'] == -1
    assert not worker.killed and module.processQueue.qsize() == 1
    task.finish()


def test_failed_worker_creation_releases_reserved_slot(task_module, monkeypatch):
    module = task_module
    task = module.FaceRecognitionTask()
    task.init(configuration({'numFaceRecognitionProcesses': '1'}))
    monkeypatch.setattr(module, 'createExternalProcess', Mock(side_effect=OSError('cannot launch python')))
    item = Item()
    task.process(item)
    assert 'face_count' not in item.attributes
    assert module.numCreatedProcs == 0 and module.processQueue.empty()
    task.finish()


def test_disabled_then_enabled_lifecycle_reads_current_configuration(task_module):
    module = task_module
    task = module.FaceRecognitionTask()
    task.init(configuration(enabled=False))
    task.finish()
    module.checkDependencies.assert_not_called()
    task.init(configuration(enabled=True))
    assert task.isEnabled() and module.activeInstances == 1
    task.finish()


def test_dependency_check_command_timeout_and_actionable_failure(task_module, monkeypatch):
    module = task_module
    # Restore the real function, which the standard fixture stubs for lifecycle tests.
    source = Path(__file__).resolve().parents[3] / 'resources/scripts/tasks/FaceRecognitionTask.py'
    namespace = {}
    import ast
    tree = ast.parse(source.read_text(encoding='utf-8'))
    function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == 'checkDependencies')
    exec(compile(ast.Module(body=[function], type_ignores=[]), str(source), 'exec'), module.__dict__, namespace)
    check = namespace['checkDependencies']
    run = Mock(return_value=types.SimpleNamespace(returncode=0, stdout='', stderr=''))
    monkeypatch.setattr(module.subprocess, 'run', run)
    module.execution_provider = 'cpu'
    check()
    command = run.call_args.args[0]
    assert command[-4:] == ['--model', 'auraface', '--execution-provider', 'cpu']
    assert run.call_args.kwargs['timeout'] == 60
    assert run.call_args.kwargs['stdin'] == subprocess.DEVNULL
    run.return_value = types.SimpleNamespace(returncode=1, stdout='', stderr='missing onnxruntime')
    with pytest.raises(RuntimeError, match='pythonPath.*missing onnxruntime'):
        check()


def test_process_arguments_always_reserve_model_directory(task_module, monkeypatch):
    module = task_module
    module.model_dir = None
    module.execution_provider = 'cuda'
    worker = Worker('ping\n')
    popen = Mock(return_value=worker)
    monkeypatch.setattr(module.subprocess, 'Popen', popen)
    monkeypatch.setattr(module.threading.Thread, 'start', lambda self: None)
    assert module.createExternalProcess() is worker
    assert popen.call_args.args[0][-2:] == ['', 'cuda']
