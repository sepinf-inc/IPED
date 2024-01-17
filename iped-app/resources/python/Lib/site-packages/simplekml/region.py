"""
Copyright 2011-2018 Kyle Lancaster | 2019 Patrick Eisoldt

Simplekml is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

"""

from simplekml.base import Kmlable, check
from simplekml.constants import AltitudeMode
from simplekml.coordinates import Coordinates


class Box(Kmlable):
    """Abstract class for box elements.

    The arguments are the same as the properties.

    .. note::
      Not to be used directly.
    """

    def __init__(self,
                 north=None,
                 south=None,
                 east=None,
                 west=None):
        super(Box, self).__init__()
        self._kml["north"] = north
        self._kml["south"] = south
        self._kml["east"] = east
        self._kml["west"] = west
        
    @property
    def north(self):
        """Latitude of the north edge of the bounding box, in decimal degrees from 0 to 90, accepts float."""
        return self._kml['north']
    
    @north.setter
    def north(self, north):
        self._kml['north'] = north
        
    @property
    def south(self):
        """Latitude of the south edge of the bounding box, in decimal degrees from 0 to 90, accepts float."""
        return self._kml['south']
    
    @south.setter
    def south(self, south):
        self._kml['south'] = south
        
    @property
    def east(self):
        """Longitude of the east edge of the bounding box, in decimal degrees from 0 to 90, accepts float."""
        return self._kml['east']
    
    @east.setter
    def east(self, east):
        self._kml['east'] = east
        
    @property
    def west(self):
        """Longitude of the west edge of the bounding box, in decimal degrees from 0 to 90, accepts float."""
        return self._kml['west']
    
    @west.setter
    def west(self, west):
        self._kml['west'] = west


class LatLonBox(Box):
    """Specifies where the top, bottom, right, and left sides of a bounding box for the ground overlay are aligned.

    Args:
      * *same as properties*
      * *all other args same as* :class:`simplekml.Box`

    """
    def __init__(self, rotation=None, **kwargs):
        super(LatLonBox, self).__init__(**kwargs)
        self._kml['rotation'] = rotation
        
    @property
    def rotation(self):
        """Rotation of the overlay about its center, in degrees.

        Values can be 180, accepts float.
        """
        return self._kml['rotation']
    
    @rotation.setter
    def rotation(self, rotation):
        self._kml['rotation'] = rotation


class LatLonAltBox(Box):
    """A bounding box that describes an area of interest defined by geographic coordinates and altitudes.

    Args:
      * *same as properties*
      * *all other args same as* :class:`simplekml.Box`
    """
    def __init__(self,
                 minaltitude=0,
                 maxaltitude=0,
                 altitudemode=AltitudeMode.clamptoground,
                 **kwargs):
        super(LatLonAltBox, self).__init__(**kwargs)
        self._kml["minAltitude"] = minaltitude
        self._kml["maxAltitude"] = maxaltitude
        self._kml["altitudeMode"] = altitudemode

    @property
    def minaltitude(self):
        """Minimum altitude in meters, accepts float."""
        return self._kml["minAltitude"]

    @minaltitude.setter
    def minaltitude(self, minAltitude):
        self._kml["minAltitude"] = minAltitude

    @property
    def maxaltitude(self):
        """Maximum altitude in meters, accepts float."""
        return self._kml["maxAltitude"]

    @maxaltitude.setter
    def maxaltitude(self, maxaltitude):
        self._kml["maxAltitude"] = maxaltitude

    @property
    def altitudemode(self):
        """Specifies how the altitude for the Camera is interpreted.

        Accepts :class:`simplkml.AltitudeMode` constants.
        """
        return self._kml["altitudeMode"]

    @altitudemode.setter
    def altitudemode(self, altitudemode):
        self._kml["altitudeMode"] = altitudemode


class Lod(Kmlable):
    """Level of Detail describes the size of the projected region.

    The arguments are the same as the properties.
    """
    def __init__(self,
                 minlodpixels=0,
                 maxlodpixels=-1,
                 minfadeextent=0,
                 maxfadeextent=0):
        super(Lod, self).__init__()
        self._kml["minLodPixels"] = minlodpixels
        self._kml["maxLodPixels"] = maxlodpixels
        self._kml["minFadeExtent"] = minfadeextent
        self._kml["maxFadeExtent"] = maxfadeextent

    @property
    def minlodpixels(self):
        """Minimum limit of the visibility range, accepts int."""
        return self._kml["minLodPixels"]

    @minlodpixels.setter
    def minlodpixels(self, minlodpixels):
        self._kml["minLodPixels"] = minlodpixels

    @property
    def maxlodpixels(self):
        """Maximum limit of the visibility range, accepts int."""
        return self._kml["maxLodPixels"]

    @maxlodpixels.setter
    def maxlodpixels(self, maxlodpixels):
        self._kml["maxLodPixels"] = maxlodpixels

    @property
    def minfadeextent(self):
        """Minumum distance over which the geometry fades, accepts int."""
        return self._kml["minFadeExtent"]

    @minfadeextent.setter
    def minfadeextent(self, minfadeextent):
        self._kml["minFadeExtent"] = minfadeextent

    @property
    def maxfadeextent(self):
        """Maximum distance over which the geometry fades, accepts int."""
        return self._kml["maxFadeExtent"]

    @maxfadeextent.setter
    def maxfadeextent(self, maxfadeextent):
        self._kml["maxFadeExtent"] = maxfadeextent


class GxLatLonQuad(Kmlable):
    """Used for nonrectangular quadrilateral ground overlays.

    The arguments are the same as the properties.
    """
    def __init__(self, coords=None):
        super(GxLatLonQuad, self).__init__()
        self._kml['coordinates'] = Coordinates()
        self.coords = coords

    @property
    def coords(self):
        """Four corners of quad coordinates, accepts list of four tuples in the order lon, lat.

        The coordinates must be specified in counter-clockwise order with the first coordinate corresponding to the
        lower-left corner of the overlayed image. eg. [(0, 1), (1,1), (1,0), (0,0)]
        """
        return self._kml['coordinates']

    @coords.setter
    def coords(self, coords):
        if coords is None:
            coords = []
        elif len(coords) < 4:
            raise ValueError("Invalid list length. List should contain 4 tuples.")
        self._kml['coordinates'] = Coordinates()
        self._kml['coordinates'].addcoordinates(coords)


class Region(Kmlable):
    """Used for nonrectangular quadrilateral ground overlays.

    The arguments are the same as the properties.
    """
    def __init__(self, latlonaltbox=None, lod=None):
        super(Region, self).__init__()
        if latlonaltbox is None:
            latlonaltbox = LatLonAltBox()
        if lod is None:
            lod = Lod()
        self._kml["LatLonAltBox"] = latlonaltbox
        self._kml["Lod"] = lod

    @property
    def latlonaltbox(self):
        """Bounding box that describes an area, accepts `simplkml.LatLonAltBox`"""
        return self._kml["LatLonAltBox"]

    @latlonaltbox.setter
    @check(LatLonAltBox)
    def latlonaltbox(self, latlonaltbox):
        self._kml["LatLonAltBox"] = latlonaltbox

    @property
    def lod(self):
        """Level of Detail, accepts `simplkml.Lod`"""
        return self._kml["Lod"]

    @lod.setter
    @check(Lod)
    def lod(self, lod):
        self._kml["Lod"] = lod
