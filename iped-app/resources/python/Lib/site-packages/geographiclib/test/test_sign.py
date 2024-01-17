"""Geodesic tests"""

import unittest
import math
import sys

from geographiclib.geomath import Math
from geographiclib.geodesic import Geodesic

class SignTest(unittest.TestCase):
  """Sign test suite"""

  @staticmethod
  def equiv(x, y):
    """Test for equivalence"""

    return ( (math.isnan(x) and math.isnan(y)) or
             (x == y and math.copysign(1.0, x) == math.copysign(1.0, y)) )

  def test_AngRound(self):
    """Test special cases for AngRound"""
    eps = sys.float_info.epsilon
    self.assertTrue(SignTest.equiv(Math.AngRound(-eps/32), -eps/32))
    self.assertTrue(SignTest.equiv(Math.AngRound(-eps/64), -0.0   ))
    self.assertTrue(SignTest.equiv(Math.AngRound(-  0.0 ), -0.0   ))
    self.assertTrue(SignTest.equiv(Math.AngRound(   0.0 ), +0.0   ))
    self.assertTrue(SignTest.equiv(Math.AngRound( eps/64), +0.0   ))
    self.assertTrue(SignTest.equiv(Math.AngRound( eps/32), +eps/32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-2*eps)/64), (1-2*eps)/64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps  )/64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/2)/64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/4)/64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1.0     /64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps/2)/64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps  )/64),  1.0     /64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+2*eps)/64), (1+2*eps)/64))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps  )/32), (1-eps  )/32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/2)/32),  1.0     /32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/4)/32),  1.0     /32))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1.0     /32),  1.0     /32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps/2)/32),  1.0     /32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps  )/32), (1+eps  )/32))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps  )/16), (1-eps  )/16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/2)/16), (1-eps/2)/16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/4)/16),  1.0     /16))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1.0     /16),  1.0     /16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps/4)/16),  1.0     /16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps/2)/16),  1.0     /16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps  )/16), (1+eps  )/16))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps  )/ 8), (1-eps  )/ 8))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/2)/ 8), (1-eps/2)/ 8))
    self.assertTrue(SignTest.equiv(Math.AngRound((1-eps/4)/ 8),  1.0     / 8))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps/2)/ 8),  1.0     / 8))
    self.assertTrue(SignTest.equiv(Math.AngRound((1+eps  )/ 8), (1+eps  )/ 8))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1-eps      ),  1-eps      ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1-eps/2    ),  1-eps/2    ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1-eps/4    ),  1          ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1.0        ),  1          ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1+eps/4    ),  1          ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1+eps/2    ),  1          ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 1+eps      ),  1+  eps    ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 90.0-64*eps),  90-64*eps  ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 90.0-32*eps),  90         ))
    self.assertTrue(SignTest.equiv(Math.AngRound( 90.0       ),  90         ))

  def test_sincosd(self):
    """Test special cases for sincosd"""
    inf = math.inf
    nan = math.nan
    s, c = Math.sincosd(-  inf)
    self.assertTrue(SignTest.equiv(s,  nan) and SignTest.equiv(c,  nan))
    s, c = Math.sincosd(-810.0)
    self.assertTrue(SignTest.equiv(s, -1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(-720.0)
    self.assertTrue(SignTest.equiv(s, -0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(-630.0)
    self.assertTrue(SignTest.equiv(s, +1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(-540.0)
    self.assertTrue(SignTest.equiv(s, -0.0) and SignTest.equiv(c, -1.0))
    s, c = Math.sincosd(-450.0)
    self.assertTrue(SignTest.equiv(s, -1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(-360.0)
    self.assertTrue(SignTest.equiv(s, -0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(-270.0)
    self.assertTrue(SignTest.equiv(s, +1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(-180.0)
    self.assertTrue(SignTest.equiv(s, -0.0) and SignTest.equiv(c, -1.0))
    s, c = Math.sincosd(- 90.0)
    self.assertTrue(SignTest.equiv(s, -1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(-  0.0)
    self.assertTrue(SignTest.equiv(s, -0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(+  0.0)
    self.assertTrue(SignTest.equiv(s, +0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(+ 90.0)
    self.assertTrue(SignTest.equiv(s, +1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(+180.0)
    self.assertTrue(SignTest.equiv(s, +0.0) and SignTest.equiv(c, -1.0))
    s, c = Math.sincosd(+270.0)
    self.assertTrue(SignTest.equiv(s, -1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(+360.0)
    self.assertTrue(SignTest.equiv(s, +0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(+450.0)
    self.assertTrue(SignTest.equiv(s, +1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(+540.0)
    self.assertTrue(SignTest.equiv(s, +0.0) and SignTest.equiv(c, -1.0))
    s, c = Math.sincosd(+630.0)
    self.assertTrue(SignTest.equiv(s, -1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(+720.0)
    self.assertTrue(SignTest.equiv(s, +0.0) and SignTest.equiv(c, +1.0))
    s, c = Math.sincosd(+810.0)
    self.assertTrue(SignTest.equiv(s, +1.0) and SignTest.equiv(c, +0.0))
    s, c = Math.sincosd(+  inf)
    self.assertTrue(SignTest.equiv(s,  nan) and SignTest.equiv(c,  nan))
    s, c = Math.sincosd(  nan)
    self.assertTrue(SignTest.equiv(s,  nan) and SignTest.equiv(c,  nan))

  def test_sincosd2(self):
    """Test accuracy of sincosd"""
    s1, c1 = Math.sincosd(         9.0)
    s2, c2 = Math.sincosd(        81.0)
    s3, c3 = Math.sincosd(-123456789.0)
    self.assertTrue(SignTest.equiv(s1, c2))
    self.assertTrue(SignTest.equiv(s1, s3))
    self.assertTrue(SignTest.equiv(c1, s2))
    self.assertTrue(SignTest.equiv(c1,-c3))

  def test_atan2d(self):
    """Test special cases for atan2d"""
    inf = math.inf
    nan = math.nan
    self.assertTrue(SignTest.equiv(Math.atan2d(+0.0 , -0.0 ), +180))
    self.assertTrue(SignTest.equiv(Math.atan2d(-0.0 , -0.0 ), -180))
    self.assertTrue(SignTest.equiv(Math.atan2d(+0.0 , +0.0 ), +0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d(-0.0 , +0.0 ), -0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d(+0.0 , -1.0 ), +180))
    self.assertTrue(SignTest.equiv(Math.atan2d(-0.0 , -1.0 ), -180))
    self.assertTrue(SignTest.equiv(Math.atan2d(+0.0 , +1.0 ), +0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d(-0.0 , +1.0 ), -0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d(-1.0 , +0.0 ),  -90))
    self.assertTrue(SignTest.equiv(Math.atan2d(-1.0 , -0.0 ),  -90))
    self.assertTrue(SignTest.equiv(Math.atan2d(+1.0 , +0.0 ),  +90))
    self.assertTrue(SignTest.equiv(Math.atan2d(+1.0 , -0.0 ),  +90))
    self.assertTrue(SignTest.equiv(Math.atan2d(+1.0 ,  -inf), +180))
    self.assertTrue(SignTest.equiv(Math.atan2d(-1.0 ,  -inf), -180))
    self.assertTrue(SignTest.equiv(Math.atan2d(+1.0 ,  +inf), +0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d(-1.0 ,  +inf), -0.0))
    self.assertTrue(SignTest.equiv(Math.atan2d( +inf, +1.0 ),  +90))
    self.assertTrue(SignTest.equiv(Math.atan2d( +inf, -1.0 ),  +90))
    self.assertTrue(SignTest.equiv(Math.atan2d( -inf, +1.0 ),  -90))
    self.assertTrue(SignTest.equiv(Math.atan2d( -inf, -1.0 ),  -90))
    self.assertTrue(SignTest.equiv(Math.atan2d( +inf,  -inf), +135))
    self.assertTrue(SignTest.equiv(Math.atan2d( -inf,  -inf), -135))
    self.assertTrue(SignTest.equiv(Math.atan2d( +inf,  +inf),  +45))
    self.assertTrue(SignTest.equiv(Math.atan2d( -inf,  +inf),  -45))
    self.assertTrue(SignTest.equiv(Math.atan2d(  nan, +1.0 ),  nan))
    self.assertTrue(SignTest.equiv(Math.atan2d(+1.0 ,   nan),  nan))

  def test_atan2d2(self):
    """Test accuracy of atan2d"""
    s = 7e-16
    self.assertEqual(Math.atan2d(s, -1.0), 180 - Math.atan2d(s, 1.0))

  def test_sum(self):
    """Test special cases of sum"""
    s,_ = Math.sum(+9.0, -9.0); self.assertTrue(SignTest.equiv(s, +0.0))
    s,_ = Math.sum(-9.0, +9.0); self.assertTrue(SignTest.equiv(s, +0.0))
    s,_ = Math.sum(-0.0, +0.0); self.assertTrue(SignTest.equiv(s, +0.0))
    s,_ = Math.sum(+0.0, -0.0); self.assertTrue(SignTest.equiv(s, +0.0))
    s,_ = Math.sum(-0.0, -0.0); self.assertTrue(SignTest.equiv(s, -0.0))
    s,_ = Math.sum(+0.0, +0.0); self.assertTrue(SignTest.equiv(s, +0.0))

  def test_AngNormalize(self):
    """Test special cases of AngNormalize"""
    self.assertTrue(SignTest.equiv(Math.AngNormalize(-900.0), -180))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(-720.0), -0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(-540.0), -180))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(-360.0), -0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(-180.0), -180))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(  -0.0), -0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize(  +0.0), +0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize( 180.0), +180))
    self.assertTrue(SignTest.equiv(Math.AngNormalize( 360.0), +0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize( 540.0), +180))
    self.assertTrue(SignTest.equiv(Math.AngNormalize( 720.0), +0.0))
    self.assertTrue(SignTest.equiv(Math.AngNormalize( 900.0), +180))

  def test_AngDiff(self):
    """Test special cases of AngDiff"""
    eps = sys.float_info.epsilon
    s,_ = Math.AngDiff(+  0.0,+  0.0); self.assertTrue(SignTest.equiv(s,+0.0 ))
    s,_ = Math.AngDiff(+  0.0,-  0.0); self.assertTrue(SignTest.equiv(s,-0.0 ))
    s,_ = Math.AngDiff(-  0.0,+  0.0); self.assertTrue(SignTest.equiv(s,+0.0 ))
    s,_ = Math.AngDiff(-  0.0,-  0.0); self.assertTrue(SignTest.equiv(s,+0.0 ))
    s,_ = Math.AngDiff(+  5.0,+365.0); self.assertTrue(SignTest.equiv(s,+0.0 ))
    s,_ = Math.AngDiff(+365.0,+  5.0); self.assertTrue(SignTest.equiv(s,-0.0 ))
    s,_ = Math.AngDiff(+  5.0,+185.0); self.assertTrue(SignTest.equiv(s,+180.0))
    s,_ = Math.AngDiff(+185.0,+  5.0); self.assertTrue(SignTest.equiv(s,-180.0))
    s,_ = Math.AngDiff( +eps ,+180.0); self.assertTrue(SignTest.equiv(s,+180.0))
    s,_ = Math.AngDiff( -eps ,+180.0); self.assertTrue(SignTest.equiv(s,-180.0))
    s,_ = Math.AngDiff( +eps ,-180.0); self.assertTrue(SignTest.equiv(s,+180.0))
    s,_ = Math.AngDiff( -eps ,-180.0); self.assertTrue(SignTest.equiv(s,-180.0))

  def test_AngDiff2(self):
    """Test accuracy of AngDiff"""
    eps = sys.float_info.epsilon
    x = 138 + 128 * eps; y = -164; s,_ = Math.AngDiff(x, y)
    self.assertEqual(s, 58 - 128 * eps)

  def test_equatorial_coincident(self):
    """
    azimuth with coincident point on equator
    """
    # lat1 lat2 azi1/2
    C = [
      [ +0.0, -0.0, 180 ],
      [ -0.0, +0.0,   0 ]
    ]
    for l in C:
      (lat1, lat2, azi) = l
      inv = Geodesic.WGS84.Inverse(lat1, 0.0, lat2, 0.0)
      self.assertTrue(SignTest.equiv(inv["azi1"], azi))
      self.assertTrue(SignTest.equiv(inv["azi2"], azi))

  def test_equatorial_NS(self):
    """Does the nearly antipodal equatorial solution go north or south?"""
    # lat1 lat2 azi1 azi2
    C = [
      [ +0.0, +0.0,  56, 124],
      [ -0.0, -0.0, 124,  56]
    ]
    for l in C:
      (lat1, lat2, azi1, azi2) = l
      inv = Geodesic.WGS84.Inverse(lat1, 0.0, lat2, 179.5)
      self.assertAlmostEqual(inv["azi1"], azi1, delta = 1)
      self.assertAlmostEqual(inv["azi2"], azi2, delta = 1)

  def test_antipodal(self):
    """How does the exact antipodal equatorial path go N/S + E/W"""
    # lat1 lat2 lon2 azi1 azi2
    C = [
      [ +0.0, +0.0, +180,   +0.0, +180],
      [ -0.0, -0.0, +180, +180,   +0.0],
      [ +0.0, +0.0, -180,   -0.0, -180],
      [ -0.0, -0.0, -180, -180,   -0.0]
    ]
    for l in C:
      (lat1, lat2, lon2, azi1, azi2) = l
      inv = Geodesic.WGS84.Inverse(lat1, 0.0, lat2, lon2)
      self.assertTrue(SignTest.equiv(inv["azi1"], azi1))
      self.assertTrue(SignTest.equiv(inv["azi2"], azi2))

  def test_antipodal_prolate(self):
    """Antipodal points on the equator with prolate ellipsoid"""
    # lon2 azi1/2
    C = [
      [ +180, +90 ],
      [ -180, -90 ]
    ]
    geod = Geodesic(6.4e6, -1/300.0)
    for l in C:
      (lon2, azi) = l
      inv = geod.Inverse(0.0, 0.0, 0.0, lon2)
      self.assertTrue(SignTest.equiv(inv["azi1"], azi))
      self.assertTrue(SignTest.equiv(inv["azi2"], azi))

  def test_azimuth_0_180(self):
    """azimuths = +/-0 and +/-180 for the direct problem"""
    # azi1, lon2, azi2
    C = [
      [ +0.0, +180, +180 ],
      [ -0.0, -180, -180 ],
      [ +180, +180, +0.0 ],
      [ -180, -180, -0.0 ]
    ]
    for l in C:
      (azi1, lon2, azi2) = l
      direct = Geodesic.WGS84.Direct(0.0, 0.0, azi1, 15e6,
                                     Geodesic.STANDARD | Geodesic.LONG_UNROLL)
      self.assertTrue(SignTest.equiv(direct["lon2"], lon2))
      self.assertTrue(SignTest.equiv(direct["azi2"], azi2))
