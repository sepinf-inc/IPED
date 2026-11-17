"""Check the fallback probe's protocol validation without creating ORT sessions."""
from pathlib import Path
import sys

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import probe_face_fallback as probe


def embedding(first='1', dimensions=512):
    return ' '.join([first] + ['0'] * (dimensions - 1))


def test_complete_valid_response_returns_only_aggregate_count():
    assert probe.response_count('1\n(0, 10, 10, 0)\n' + embedding() + '\n') == 1
    assert probe.response_count('0\n') == 0


@pytest.mark.parametrize('response', [
    '', 'image_error\n', '-1\n', '2\n(0, 10, 10, 0)\n' + embedding(),
    '0\nextra\n', '1\n(0, 10, 10, 0)\n' + embedding(dimensions=128),
    '1\n(0, 10, 10, 0)\n' + embedding('nan'),
    '1\n(0, 10, 10, 0)\n' + embedding('0'),
    '1\n(0, 0, 10, 0)\n' + embedding(),
    '1\n(0.0, 10, 10, 0)\n' + embedding(),
])
def test_incomplete_or_invalid_responses_cannot_pass_the_probe(response):
    with pytest.raises((ValueError, SyntaxError)):
        probe.response_count(response)
