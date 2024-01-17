"""
A Python implementation of Google's Encoded Polyline Algorithm Format.
"""
import io
import itertools
import math
from typing import List, Tuple


def _pcitr(iterable):
    return zip(iterable, itertools.islice(iterable, 1, None))


def _py2_round(x):
    # The polyline algorithm uses Python 2's way of rounding
    return int(math.copysign(math.floor(math.fabs(x) + 0.5), x))


def _write(output, curr_value, prev_value, factor):
    curr_value = _py2_round(curr_value * factor)
    prev_value = _py2_round(prev_value * factor)
    coord = curr_value - prev_value
    coord <<= 1
    coord = coord if coord >= 0 else ~coord

    while coord >= 0x20:
        output.write(chr((0x20 | (coord & 0x1f)) + 63))
        coord >>= 5

    output.write(chr(coord + 63))


def _trans(value, index):
    byte, result, shift = None, 0, 0

    comp = None
    while byte is None or byte >= 0x20:
        byte = ord(value[index]) - 63
        index += 1
        result |= (byte & 0x1f) << shift
        shift += 5
        comp = result & 1

    return ~(result >> 1) if comp else (result >> 1), index


def decode(expression: str, precision: int = 5, geojson: bool = False) -> List[Tuple[float, float]]:
    """
    Decode a polyline string into a set of coordinates.

    :param expression: Polyline string, e.g. 'u{~vFvyys@fS]'.
    :param precision: Precision of the encoded coordinates. Google Maps uses 5, OpenStreetMap uses 6.
        The default value is 5.
    :param geojson: Set output of tuples to (lon, lat), as per https://tools.ietf.org/html/rfc7946#section-3.1.1
    :return: List of coordinate tuples in (lat, lon) order, unless geojson is set to True.
    """
    coordinates, index, lat, lng, length, factor = [], 0, 0, 0, len(expression), float(10 ** precision)

    while index < length:
        lat_change, index = _trans(expression, index)
        lng_change, index = _trans(expression, index)
        lat += lat_change
        lng += lng_change
        coordinates.append((lat / factor, lng / factor))

    if geojson is True:
        coordinates = [t[::-1] for t in coordinates]

    return coordinates


def encode(coordinates: List[Tuple[float, float]], precision: int = 5, geojson: bool = False) -> str:
    """
    Encode a set of coordinates in a polyline string.

    :param coordinates: List of coordinate tuples, e.g. [(0, 0), (1, 0)]. Unless geojson is set to True, the order
        is expected to be (lat, lon).
    :param precision: Precision of the coordinates to encode. Google Maps uses 5, OpenStreetMap uses 6.
        The default value is 5.
    :param geojson: Set to True in order to encode (lon, lat) tuples.
    :return: The encoded polyline string.
    """
    if geojson is True:
        coordinates = [t[::-1] for t in coordinates]

    output, factor = io.StringIO(), int(10 ** precision)

    _write(output, coordinates[0][0], 0, factor)
    _write(output, coordinates[0][1], 0, factor)

    for prev, curr in _pcitr(coordinates):
        _write(output, curr[0], prev[0], factor)
        _write(output, curr[1], prev[1], factor)

    return output.getvalue()
