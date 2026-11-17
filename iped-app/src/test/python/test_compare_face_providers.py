"""Synthetic consistency checks; no models are loaded and no inference runs."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import compare_face_providers as compare
import numpy as np


def vector(index=0):
    value = np.zeros(512, dtype=np.float32)
    value[index] = 1
    return value


class ProviderComparisonTests(unittest.TestCase):
    def test_sixteen_samples_span_the_fixed_corpus(self):
        selected = compare.sample_indices(377)
        self.assertEqual(len(selected), 16)
        self.assertEqual(selected[0], 0)
        self.assertEqual(selected[-1], 376)
        self.assertEqual(len(set(selected)), 16)
        self.assertTrue(all(gap in (25, 26) for gap in np.diff(selected)))
        self.assertEqual(compare.sample_indices(3), [0, 1, 2])

    def test_real_metadata_undefined_orientation_means_no_rotation(self):
        class Image:
            def getexif(self):
                return {274: 0}
        self.assertEqual(compare.image_orientation(Image()), '1')

    def test_shuffled_faces_match_by_box_and_compare_the_correct_embedding(self):
        first, second = (0, 0, 100, 100), (200, 200, 300, 300)
        cpu = [([first, second], [vector(0), vector(1)])]
        cuda = [([second, first], [vector(1), vector(0)])]
        summary = compare.summarize(cpu, cuda)
        self.assertEqual(summary['status'], 'passed')
        self.assertEqual(summary['matched_faces'], 2)
        self.assertEqual(summary['minimum_box_iou'], 1)
        self.assertEqual(summary['maximum_embedding_cosine_distance'], 0)

    def test_missing_and_duplicate_face_matches_fail(self):
        box = (0, 0, 100, 100)
        for first, second in (([box], []), ([box, box], [box, box]),
                              ([box], [(1, 1, 101, 101)])):
            with self.subTest(first=first, second=second), self.assertRaises(ValueError):
                compare.match_boxes(first, second)

    def test_embedding_validation_and_tolerance_are_enforced(self):
        for invalid in (np.zeros(512), np.ones(128), np.full(512, np.nan)):
            with self.assertRaises(ValueError):
                compare.embedding_distance(vector(), invalid)
        box = (0, 0, 100, 100)
        summary = compare.summarize([([box], [vector(0)])], [([box], [vector(1)])])
        self.assertEqual(summary['status'], 'failed')
        self.assertEqual(summary['failures'], {'embedding_tolerance_exceeded': 1})

    def test_small_rounding_difference_is_consistent(self):
        changed = vector().astype(np.float64)
        changed[1] = 1e-4
        changed /= np.linalg.norm(changed)
        self.assertLess(compare.embedding_distance(vector(), changed), 1e-4)

    def test_failed_images_and_an_all_empty_sample_cannot_pass(self):
        failed = compare.summarize([None], [([], [])])
        empty = compare.summarize([([], [])], [([], [])])
        self.assertEqual(failed['status'], 'failed')
        self.assertEqual(failed['failures']['processing_error'], 1)
        self.assertEqual(empty['status'], 'failed')
        self.assertEqual(empty['failures'], {'no_faces_compared': 1})


if __name__ == '__main__':
    unittest.main()
