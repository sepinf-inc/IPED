"""
Utilities
-------

Utility module for Folium helper functions.

"""

import base64
import json
import math
import os
import re
import struct
import typing
import zlib
from typing import Any, Callable, Union

from jinja2 import Environment, PackageLoader

try:
    import pandas as pd
except ImportError:
    pd = None

try:
    import numpy as np
except ImportError:
    np = None

if typing.TYPE_CHECKING:
    from branca.colormap import ColorMap


rootpath = os.path.abspath(os.path.dirname(__file__))


def get_templates():
    """Get Jinja templates."""
    return Environment(loader=PackageLoader("branca", "templates"))


def legend_scaler(legend_values, max_labels=10.0):
    """
    Downsamples the number of legend values so that there isn't a collision
    of text on the legend colorbar (within reason). The colorbar seems to
    support ~10 entries as a maximum.

    """
    if len(legend_values) < max_labels:
        legend_ticks = legend_values
    else:
        spacer = int(math.ceil(len(legend_values) / max_labels))
        legend_ticks = []
        for i in legend_values[::spacer]:
            legend_ticks += [i]
            legend_ticks += [""] * (spacer - 1)
    return legend_ticks


def linear_gradient(hexList, nColors):
    """
    Given a list of hexcode values, will return a list of length
    nColors where the colors are linearly interpolated between the
    (r, g, b) tuples that are given.

    Examples
    --------
    >>> linear_gradient([(0, 0, 0), (255, 0, 0), (255, 255, 0)], 100)

    """

    def _scale(start, finish, length, i):
        """
        Return the value correct value of a number that is in between start
        and finish, for use in a loop of length *length*.

        """
        base = 16

        fraction = float(i) / (length - 1)
        raynge = int(finish, base) - int(start, base)
        thex = hex(int(int(start, base) + fraction * raynge)).split("x")[-1]
        if len(thex) != 2:
            thex = "0" + thex
        return thex

    allColors = []
    # Separate (R, G, B) pairs.
    for start, end in zip(hexList[:-1], hexList[1:]):
        # Linearly interpolate between pair of hex ###### values and
        # add to list.
        nInterpolate = 765
        for index in range(nInterpolate):
            r = _scale(start[1:3], end[1:3], nInterpolate, index)
            g = _scale(start[3:5], end[3:5], nInterpolate, index)
            b = _scale(start[5:7], end[5:7], nInterpolate, index)
            allColors.append("".join(["#", r, g, b]))

    # Pick only nColors colors from the total list.
    result = []
    for counter in range(nColors):
        fraction = float(counter) / (nColors - 1)
        index = int(fraction * (len(allColors) - 1))
        result.append(allColors[index])
    return result


def color_brewer(color_code, n=6):
    """
    Generate a colorbrewer color scheme of length 'len', type 'scheme.
    Live examples can be seen at http://colorbrewer2.org/

    """
    maximum_n = 253
    minimum_n = 3

    if not isinstance(n, int):
        raise TypeError("n has to be an int, not a %s" % type(n))

    # Raise an error if the n requested is greater than the maximum.
    if n > maximum_n:
        raise ValueError(
            "The maximum number of colors in a"
            " ColorBrewer sequential color series is 253",
        )
    if n < minimum_n:
        raise ValueError(
            "The minimum number of colors in a"
            " ColorBrewer sequential color series is 3",
        )

    if not isinstance(color_code, str):
        raise ValueError(f"color should be a string, not a {type(color_code)}.")
    if color_code[-2:] == "_r":
        base_code = color_code[:-2]
        core_color_code = base_code + "_" + str(n).zfill(2)
        color_reverse = True
    else:
        base_code = color_code
        core_color_code = base_code + "_" + str(n).zfill(2)
        color_reverse = False

    with open(os.path.join(rootpath, "_schemes.json")) as f:
        schemes = json.loads(f.read())

    with open(os.path.join(rootpath, "scheme_info.json")) as f:
        scheme_info = json.loads(f.read())

    with open(os.path.join(rootpath, "scheme_base_codes.json")) as f:
        core_schemes = json.loads(f.read())["codes"]

    if base_code not in core_schemes:
        raise ValueError(base_code + " is not a valid ColorBrewer code")

    explicit_scheme = True
    if schemes.get(core_color_code) is None:
        explicit_scheme = False

    # Only if n is greater than the scheme length do we interpolate values.
    if not explicit_scheme:
        # Check to make sure that it is not a qualitative scheme.
        if scheme_info[base_code] == "Qualitative":
            matching_quals = []
            for key in schemes:
                if base_code + "_" in key:
                    matching_quals.append(int(key.split("_")[1]))

            raise ValueError(
                "Expanded color support is not available"
                " for Qualitative schemes; restrict the"
                " number of colors for the "
                + base_code
                + " code to between "
                + str(min(matching_quals))
                + " and "
                + str(max(matching_quals)),
            )
        else:
            longest_scheme_name = base_code
            longest_scheme_n = 0
            for sn_name in schemes.keys():
                if "_" not in sn_name:
                    continue
                if sn_name.split("_")[0] != base_code:
                    continue
                if int(sn_name.split("_")[1]) > longest_scheme_n:
                    longest_scheme_name = sn_name
                    longest_scheme_n = int(sn_name.split("_")[1])

            if not color_reverse:
                color_scheme = linear_gradient(schemes.get(longest_scheme_name), n)
            else:
                color_scheme = linear_gradient(
                    schemes.get(longest_scheme_name)[::-1],
                    n,
                )
    else:
        if not color_reverse:
            color_scheme = schemes.get(core_color_code, None)
        else:
            color_scheme = schemes.get(core_color_code, None)[::-1]
    return color_scheme


def split_six(series=None):
    """
    Given a Pandas Series, get a domain of values from zero to the 90% quantile
    rounded to the nearest order-of-magnitude integer. For example, 2100 is
    rounded to 2000, 2790 to 3000.

    Parameters
    ----------
    series: Pandas series, default None

    Returns
    -------
    list

    """
    if pd is None:
        raise ImportError("The Pandas package is required" " for this functionality")
    if np is None:
        raise ImportError("The NumPy package is required" " for this functionality")

    def base(x):
        if x > 0:
            base = pow(10, math.floor(math.log10(x)))
            return round(x / base) * base
        else:
            return 0

    quants = [0, 50, 75, 85, 90]
    # Some weirdness in series quantiles a la 0.13.
    arr = series.values
    return [base(np.percentile(arr, x)) for x in quants]


def image_to_url(image, colormap=None, origin="upper"):
    """Infers the type of an image argument and transforms it into a URL.

    Parameters
    ----------
    image: string, file or array-like object
        * If string, it will be written directly in the output file.
        * If file, it's content will be converted as embedded in the
          output file.
        * If array-like, it will be converted to PNG base64 string and
          embedded in the output.
    origin : ['upper' | 'lower'], optional, default 'upper'
        Place the [0, 0] index of the array in the upper left or
        lower left corner of the axes.
    colormap : callable, used only for `mono` image.
        Function of the form [x -> (r,g,b)] or [x -> (r,g,b,a)]
        for transforming a mono image into RGB.
        It must output iterables of length 3 or 4, with values between
        0. and 1.  Hint : you can use colormaps from `matplotlib.cm`.
    """
    if hasattr(image, "read"):
        # We got an image file.
        if hasattr(image, "name"):
            # We try to get the image format from the file name.
            fileformat = image.name.lower().split(".")[-1]
        else:
            fileformat = "png"
        url = "data:image/{};base64,{}".format(
            fileformat,
            base64.b64encode(image.read()).decode("utf-8"),
        )
    elif (not (isinstance(image, str) or isinstance(image, bytes))) and hasattr(
        image,
        "__iter__",
    ):
        # We got an array-like object.
        png = write_png(image, origin=origin, colormap=colormap)
        url = "data:image/png;base64," + base64.b64encode(png).decode("utf-8")
    else:
        # We got an URL.
        url = json.loads(json.dumps(image))

    return url.replace("\n", " ")


def write_png(
    data: Any,
    origin: str = "upper",
    colormap: Union["ColorMap", Callable, None] = None,
) -> bytes:
    """
    Transform an array of data into a PNG string.
    This can be written to disk using binary I/O, or encoded using base64
    for an inline PNG like this:

    >>> png_str = write_png(array)
    >>> "data:image/png;base64," + png_str.encode("base64")

    Inspired from
    http://stackoverflow.com/questions/902761/saving-a-numpy-array-as-an-image

    Parameters
    ----------
    data: numpy array or equivalent list-like object.
         Must be NxM (mono), NxMx3 (RGB) or NxMx4 (RGBA)
    origin : ['upper' | 'lower'], optional, default 'upper'
        Place the [0,0] index of the array in the upper left or lower left
        corner of the axes.
    colormap : ColorMap subclass or callable, optional
        Only needed to transform mono images into RGB. You have three options:
        - use a subclass of `ColorMap` like `LinearColorMap`
        - use a colormap from `matplotlib.cm`
        - use a custom function of the form [x -> (r,g,b)] or [x -> (r,g,b,a)].
          It must output iterables of length 3 or 4 with values between 0 and 1.

    Returns
    -------
    PNG formatted byte string
    """
    from branca.colormap import ColorMap

    if np is None:
        raise ImportError("The NumPy package is required" " for this functionality")

    if isinstance(colormap, ColorMap):
        colormap_callable = colormap.rgba_floats_tuple
    elif callable(colormap):
        colormap_callable = colormap
    else:
        colormap_callable = lambda x: (x, x, x, 1)  # noqa E731

    array = np.atleast_3d(data)
    height, width, nblayers = array.shape

    if nblayers not in [1, 3, 4]:
        raise ValueError("Data must be NxM (mono), " "NxMx3 (RGB), or NxMx4 (RGBA)")
    assert array.shape == (height, width, nblayers)

    if nblayers == 1:
        array = np.array(list(map(colormap_callable, array.ravel())))
        nblayers = array.shape[1]
        if nblayers not in [3, 4]:
            raise ValueError(
                "colormap must provide colors of" "length 3 (RGB) or 4 (RGBA)",
            )
        array = array.reshape((height, width, nblayers))
    assert array.shape == (height, width, nblayers)

    if nblayers == 3:
        array = np.concatenate((array, np.ones((height, width, 1))), axis=2)
        nblayers = 4
    assert array.shape == (height, width, nblayers)
    assert nblayers == 4

    # Normalize to uint8 if it isn't already.
    if array.dtype != "uint8":
        with np.errstate(divide="ignore", invalid="ignore"):
            array = array * 255.0 / array.max(axis=(0, 1)).reshape((1, 1, 4))
            array[~np.isfinite(array)] = 0
        array = array.astype("uint8")

    # Eventually flip the image.
    if origin == "lower":
        array = array[::-1, :, :]

    # Transform the array to bytes.
    raw_data = b"".join([b"\x00" + array[i, :, :].tobytes() for i in range(height)])

    def png_pack(png_tag, data):
        chunk_head = png_tag + data
        return (
            struct.pack("!I", len(data))
            + chunk_head
            + struct.pack("!I", 0xFFFFFFFF & zlib.crc32(chunk_head))
        )

    return b"".join(
        [
            b"\x89PNG\r\n\x1a\n",
            png_pack(b"IHDR", struct.pack("!2I5B", width, height, 8, 6, 0, 0, 0)),
            png_pack(b"IDAT", zlib.compress(raw_data, 9)),
            png_pack(b"IEND", b""),
        ],
    )


def _camelify(out):
    return (
        (
            "".join(
                [
                    "_" + x.lower()
                    if i < len(out) - 1 and x.isupper() and out[i + 1].islower()  # noqa
                    else x.lower() + "_"
                    if i < len(out) - 1 and x.islower() and out[i + 1].isupper()  # noqa
                    else x.lower()
                    for i, x in enumerate(list(out))
                ],
            )
        )
        .lstrip("_")
        .replace("__", "_")
    )  # noqa


def _parse_size(value):
    if isinstance(value, (int, float)):
        return float(value), "px"
    elif isinstance(value, str):
        # match digits or a point, possibly followed by a space,
        # followed by a unit: either 1 to 5 letters or a percent sign
        match = re.fullmatch(r"([\d.]+)\s?(\w{1,5}|%)", value.strip())
        if match:
            return float(match.group(1)), match.group(2)
        else:
            raise ValueError(
                f"Cannot parse {value!r}, it should be a number followed by a unit.",
            )
    elif (
        isinstance(value, tuple)
        and isinstance(value[0], (int, float))
        and isinstance(value[1], str)
    ):
        # value had been already parsed
        return (float(value[0]), value[1])
    else:
        raise TypeError(
            f"Cannot parse {value!r}, it should be a number or a string containing a number and a unit.",
        )


def _locations_mirror(x):
    """Mirrors the points in a list-of-list-of-...-of-list-of-points.
    For example:
    >>> _locations_mirror([[[1, 2], [3, 4]], [5, 6], [7, 8]])
    [[[2, 1], [4, 3]], [6, 5], [8, 7]]

    """
    if hasattr(x, "__iter__"):
        if hasattr(x[0], "__iter__"):
            return list(map(_locations_mirror, x))
        else:
            return list(x[::-1])
    else:
        return x


def _locations_tolist(x):
    """Transforms recursively a list of iterables into a list of list."""
    if hasattr(x, "__iter__"):
        return list(map(_locations_tolist, x))
    else:
        return x


def none_min(x, y):
    if x is None:
        return y
    elif y is None:
        return x
    else:
        return min(x, y)


def none_max(x, y):
    if x is None:
        return y
    elif y is None:
        return x
    else:
        return max(x, y)


def iter_points(x):
    """Iterates over a list representing a feature, and returns a list of points,
    whatever the shape of the array (Point, MultiPolyline, etc).
    """
    if isinstance(x, (list, tuple)):
        if len(x):
            if isinstance(x[0], (list, tuple)):
                out = []
                for y in x:
                    out += iter_points(y)
                return out
            else:
                return [x]
        else:
            return []
    else:
        raise ValueError(f"List/tuple type expected. Got {x!r}.")
