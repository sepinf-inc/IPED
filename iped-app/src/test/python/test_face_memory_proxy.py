"""Memory-proxy result classification without starting CUDA or loading models."""
import sys
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import benchmark_face_memory_proxy as proxy


class MemoryProxyTests(unittest.TestCase):
    def test_memory_constraints_preserve_shared_production_precision(self):
        sessions = []
        provider_options = [{'use_tf32': '0'}, {}]
        with patch.object(proxy.models, 'valid_model_file', return_value=True), \
                patch.object(proxy.worker, 'session_provider_options', return_value=provider_options, create=True) as precision, \
                patch.object(proxy.worker, 'verify_session_provider'), \
                patch.object(proxy.ort, 'InferenceSession', return_value=Mock()) as create:
            proxy.add_session(sessions, Path('models'), proxy.models.RETINAFACE_R50_FILE,
                              'detection', 1536, 'profiles')
        precision.assert_called_once_with(['CUDAExecutionProvider', 'CPUExecutionProvider'])
        options = create.call_args.kwargs['provider_options']
        self.assertEqual(options[0]['use_tf32'], '0')
        self.assertEqual(options[0]['gpu_mem_limit'], str(1536 * 2**20))
        self.assertEqual(options[0]['arena_extend_strategy'], 'kSameAsRequested')
        self.assertEqual(options[1], {})

    def test_budget_uses_absolute_peak_including_baseline(self):
        self.assertEqual(proxy.budget_status([1900, 2048], 2048), 'within_observed_budget')
        self.assertEqual(proxy.budget_status([2049, 100], 2048), 'observed_budget_exceeded')
        self.assertEqual(proxy.budget_status([10, 2048.01], 2048), 'observed_budget_exceeded')

    def test_absent_or_invalid_measurements_never_prove_budget_fit(self):
        self.assertEqual(proxy.budget_status([], 2048), 'unobserved')
        self.assertEqual(proxy.budget_status([float('nan')], 2048), 'unobserved')

    def test_ort_arena_and_cuda_allocation_errors_are_classified(self):
        for message in ('Available memory of 1024 is smaller than requested bytes of 2048',
                        'CUDA out of memory', 'BFCArena failed to allocate memory',
                        'cudaMalloc failed'):
            self.assertEqual(proxy.failure_code(RuntimeError(message)), 'allocation_failure')
        self.assertEqual(proxy.failure_code(ValueError('Invalid input')), 'setup_or_inference_failure')


if __name__ == '__main__':
    unittest.main()
