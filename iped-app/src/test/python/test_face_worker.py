"""Worker protocol, provider selection, image orientation and model downloads."""
import ast
import contextlib
import hashlib
import io
import pathlib
import sys
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[3] / 'resources/scripts/tasks'))
import FaceRecognitionModelConfig as models
import FaceRecognitionProcess as worker
import numpy as np
from PIL import Image


class WorkerTests(unittest.TestCase):
    def test_explicit_cpu_never_requests_cuda(self):
        runtime = SimpleNamespace(get_available_providers=lambda: ['CUDAExecutionProvider', 'CPUExecutionProvider'])
        self.assertEqual(worker.requested_providers(runtime, 'cpu'), ['CPUExecutionProvider'])

    def test_cuda_requires_available_and_loaded_provider(self):
        with self.assertRaises(RuntimeError):
            worker.requested_providers(SimpleNamespace(get_available_providers=lambda: ['CPUExecutionProvider']), 'cuda')
        session = Mock()
        session.get_providers.return_value = ['CPUExecutionProvider']
        with self.assertRaises(RuntimeError):
            worker.verify_session_provider(session, 'cuda', 'recognition')
        session.get_providers.return_value = ['CUDAExecutionProvider', 'CPUExecutionProvider']
        worker.verify_session_provider(session, 'cuda', 'recognition')
        session.disable_fallback.assert_called_once_with()

    def test_preload_diagnostics_do_not_use_protocol_stdout(self):
        runtime = SimpleNamespace(preload_dlls=lambda: print('DLL warning'))
        output, errors = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            worker.preload_runtime_dlls(runtime, 'auto')
        self.assertEqual(output.getvalue(), '')
        self.assertIn('DLL warning', errors.getvalue())

    def test_cpu_does_not_preload_or_change_dll_search_paths(self):
        runtime = Mock()
        with patch.object(worker, 'expose_nvidia_dll_directories') as expose:
            worker.preload_runtime_dlls(runtime, 'cpu')
        expose.assert_not_called()
        runtime.preload_dlls.assert_not_called()

    def test_invalid_options(self):
        for options in [(0, 'auraface', 640, .5, 'cpu'), (1024, 'typo', 640, .5, 'cpu'),
                        (1024, 'auraface', -1, .5, 'cpu'), (1024, 'auraface', 640, float('nan'), 'cpu'),
                        (1024, 'auraface', 640, 1.1, 'cpu'), (1024, 'auraface', 640, .5, 'typo')]:
            with self.subTest(options=options), self.assertRaises(ValueError):
                worker.validate_options(*options)

    def test_response_has_plain_integer_coordinates_and_complete_embeddings(self):
        output = io.StringIO()
        vector = np.zeros(512, dtype=np.float32)
        vector[0] = 1
        with patch.object(worker, 'stdout', output):
            worker.write_response([(np.int32(1), np.int64(4), np.int32(3), np.int64(2))], [vector])
        lines = output.getvalue().splitlines()
        self.assertEqual(lines[0], '1')
        self.assertEqual(ast.literal_eval(lines[1]), (1, 4, 3, 2))
        self.assertEqual(len(lines[2].split()), 512)

    def test_invalid_embedding_never_writes_partial_response(self):
        for vector in (np.zeros(512), np.ones(128), np.full(512, np.nan), np.ones(512)):
            output = io.StringIO()
            with patch.object(worker, 'stdout', output), self.assertRaises(ValueError):
                worker.write_response([(1, 4, 3, 2)], [vector])
            self.assertEqual(output.getvalue(), '')

    def test_degenerate_boxes_never_write_partial_response(self):
        vector = np.zeros(512, dtype=np.float32)
        vector[0] = 1
        for location in [(1, 2, 3, 4), (1, 4, 1, 2), (1, 2, 3, 2)]:
            output = io.StringIO()
            with patch.object(worker, 'stdout', output), self.assertRaises(ValueError):
                worker.write_response([location], [vector])
            self.assertEqual(output.getvalue(), '')

    def test_recognition_rejects_zero_nan_wrong_dimension_and_count(self):
        session = Mock()
        face = np.zeros((112, 112, 3), dtype=np.uint8)
        for result in (np.zeros((1, 512)), np.full((1, 512), np.nan), np.ones((1, 128)), np.ones((2, 512))):
            session.run.return_value = [result]
            with self.assertRaises(ValueError):
                worker.get_auraface_embeddings_batch(session, 'input', [face])

    def test_all_passed_orientations_match_pillow_on_both_loaders(self):
        pixels = np.arange(3 * 5 * 3, dtype=np.uint8).reshape((3, 5, 3))
        transposes = [None, Image.Transpose.FLIP_LEFT_RIGHT, Image.Transpose.ROTATE_180,
                      Image.Transpose.FLIP_TOP_BOTTOM, Image.Transpose.TRANSPOSE,
                      Image.Transpose.ROTATE_270, Image.Transpose.TRANSVERSE, Image.Transpose.ROTATE_90]
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'oriented.png'
            image = Image.fromarray(pixels)
            exif = image.getexif()
            exif[274] = 6  # The explicitly passed orientation takes precedence.
            image.save(path, exif=exif)
            for orientation, transpose in enumerate(transposes, 1):
                expected = np.array(image if transpose is None else image.transpose(transpose))
                with self.subTest(orientation=orientation):
                    actual, scale = worker.load_and_preprocess(str(path), str(orientation), 1024)
                    np.testing.assert_array_equal(actual, expected)
                    self.assertEqual(scale, 1)
                    self.assertTrue(actual.flags.c_contiguous)
                    with patch.object(worker.cv2, 'imread', return_value=None):
                        fallback, _ = worker.load_and_preprocess(str(path), str(orientation), 1024)
                    np.testing.assert_array_equal(fallback, expected)


class DownloadTests(unittest.TestCase):
    payload = b'verified model bytes'

    def download(self, path):
        return models.download_file('https://example.invalid/model', str(path),
                                    expected_size=len(self.payload),
                                    expected_sha256=hashlib.sha256(self.payload).hexdigest())

    def retrieve(self, url, path):
        pathlib.Path(path).write_bytes(self.payload)

    def test_valid_cache_is_reused_and_corrupt_cache_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'model.onnx'
            path.write_bytes(b'corrupt')
            with patch('urllib.request.urlretrieve', side_effect=self.retrieve) as retrieve:
                self.download(path)
                self.download(path)
            retrieve.assert_called_once()
            self.assertEqual(path.read_bytes(), self.payload)

    def test_interruption_and_bad_bytes_do_not_publish_partial_model(self):
        for failure in ('interrupt', 'bad_bytes'):
            with tempfile.TemporaryDirectory() as directory:
                path = pathlib.Path(directory) / 'model.onnx'
                def retrieve(url, temporary):
                    pathlib.Path(temporary).write_bytes(b'partial')
                    if failure == 'interrupt':
                        raise OSError('connection lost')
                with patch('urllib.request.urlretrieve', side_effect=retrieve), self.assertRaises((ValueError, OSError)):
                    self.download(path)
                self.assertFalse(path.exists())
                self.assertEqual(list(path.parent.iterdir()), [])

    def test_permission_error_is_propagated_and_temporary_removed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'model.onnx'
            with patch('urllib.request.urlretrieve', side_effect=self.retrieve), patch('os.replace', side_effect=PermissionError('denied')):
                with self.assertRaises(PermissionError):
                    self.download(path)
            self.assertEqual(list(path.parent.iterdir()), [])

    def test_parallel_downloads_publish_only_verified_bytes(self):
        barrier = threading.Barrier(2)
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / 'model.onnx'
            def retrieve(url, temporary):
                self.retrieve(url, temporary)
                barrier.wait(timeout=5)
            with patch('urllib.request.urlretrieve', side_effect=retrieve), ThreadPoolExecutor(max_workers=2) as pool:
                results = list(pool.map(lambda _: self.download(path), range(2)))
            self.assertEqual(results, [str(path), str(path)])
            self.assertEqual(path.read_bytes(), self.payload)
            self.assertEqual(list(path.parent.iterdir()), [path])


if __name__ == '__main__':
    unittest.main()
