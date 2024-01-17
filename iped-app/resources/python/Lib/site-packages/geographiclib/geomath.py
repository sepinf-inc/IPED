"""geomath.py: transcription of GeographicLib::Math class."""
# geomath.py
#
# This is a rather literal translation of the GeographicLib::Math class to
# python.  See the documentation for the C++ class for more information at
#
#    https://geographiclib.sourceforge.io/html/annotated.html
#
# Copyright (c) Charles Karney (2011-2021) <charles@karney.com> and
# licensed under the MIT/X11 License.  For more information, see
# https://geographiclib.sourceforge.io/
######################################################################

import sys
import math

class Math:
  """
  Additional math routines for GeographicLib.
  """

  @staticmethod
  def sq(x):
    """Square a number"""

    return x * x

  @staticmethod
  def cbrt(x):
    """Real cube root of a number"""

    return math.copysign(math.pow(abs(x), 1/3.0), x)

  @staticmethod
  def norm(x, y):
    """Private: Normalize a two-vector."""

    r = (math.sqrt(Math.sq(x) + Math.sq(y))
         # hypot is inaccurate for 3.[89].  Problem reported by agdhruv
         # https://github.com/geopy/geopy/issues/466 ; see
         # https://bugs.python.org/issue43088
         # Visual Studio 2015 32-bit has a similar problem.
         if (3, 8) <= sys.version_info < (3, 10)
         else math.hypot(x, y))
    return x/r, y/r

  @staticmethod
  def sum(u, v):
    """Error free transformation of a sum."""

    # Error free transformation of a sum.  Note that t can be the same as one
    # of the first two arguments.
    s = u + v
    up = s - v
    vpp = s - up
    up -= u
    vpp -= v
    t = s if s == 0 else 0.0 - (up + vpp)
    # u + v =       s      + t
    #       = round(u + v) + t
    return s, t

  @staticmethod
  def polyval(N, p, s, x):
    """Evaluate a polynomial."""

    y = float(0 if N < 0 else p[s]) # make sure the returned value is a float
    while N > 0:
      N -= 1; s += 1
      y = y * x + p[s]
    return y

  @staticmethod
  def AngRound(x):
    """Private: Round an angle so that small values underflow to zero."""

    # The makes the smallest gap in x = 1/16 - nextafter(1/16, 0) = 1/2^57
    # for reals = 0.7 pm on the earth if x is an angle in degrees.  (This
    # is about 1000 times more resolution than we get with angles around 90
    # degrees.)  We use this to avoid having to deal with near singular
    # cases when x is non-zero but tiny (e.g., 1.0e-200).
    z = 1/16.0
    y = abs(x)
    # The compiler mustn't "simplify" z - (z - y) to y
    if y < z: y = z - (z - y)
    return math.copysign(y, x)

  @staticmethod
  def remainder(x, y):
    """remainder of x/y in the range [-y/2, y/2]."""

    return math.remainder(x, y) if math.isfinite(x) else math.nan

  @staticmethod
  def AngNormalize(x):
    """reduce angle to [-180,180]"""

    y = Math.remainder(x, 360)
    return math.copysign(180.0, x) if abs(y) == 180 else y

  @staticmethod
  def LatFix(x):
    """replace angles outside [-90,90] by NaN"""

    return math.nan if abs(x) > 90 else x

  @staticmethod
  def AngDiff(x, y):
    """compute y - x and reduce to [-180,180] accurately"""

    d, t = Math.sum(Math.remainder(-x, 360), Math.remainder(y, 360))
    d, t = Math.sum(Math.remainder(d, 360), t)
    if d == 0 or abs(d) == 180:
      d = math.copysign(d, y - x if t == 0 else -t)
    return d, t

  @staticmethod
  def sincosd(x):
    """Compute sine and cosine of x in degrees."""

    r = math.fmod(x, 360) if math.isfinite(x) else math.nan
    q = 0 if math.isnan(r) else int(round(r / 90))
    r -= 90 * q; r = math.radians(r)
    s = math.sin(r); c = math.cos(r)
    q = q % 4
    if   q == 1: s, c =  c, -s
    elif q == 2: s, c = -s, -c
    elif q == 3: s, c = -c,  s
    c = c + 0.0
    if s == 0: s = math.copysign(s, x)
    return s, c

  @staticmethod
  def sincosde(x, t):
    """Compute sine and cosine of (x + t) in degrees with x in [-180, 180]"""

    q = int(round(x / 90)) if math.isfinite(x) else 0
    r = x - 90 * q; r = math.radians(Math.AngRound(r + t))
    s = math.sin(r); c = math.cos(r)
    q = q % 4
    if   q == 1: s, c =  c, -s
    elif q == 2: s, c = -s, -c
    elif q == 3: s, c = -c,  s
    c = c + 0.0
    if s == 0: s = math.copysign(s, x)
    return s, c

  @staticmethod
  def atan2d(y, x):
    """compute atan2(y, x) with the result in degrees"""

    if abs(y) > abs(x):
      q = 2; x, y = y, x
    else:
      q = 0
    if x < 0:
      q += 1; x = -x
    ang = math.degrees(math.atan2(y, x))
    if   q == 1: ang = math.copysign(180, y) - ang
    elif q == 2: ang =                90      - ang
    elif q == 3: ang =               -90     + ang
    return ang
