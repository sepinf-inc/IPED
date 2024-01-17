"""
Copyright 2011-2018 Kyle Lancaster

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

from simplekml.base import Kmlable


class Location(Kmlable):
    """Specifies the exact coordinates of the Model's origin.

    The arguments are the same as the properties.
    """

    def __init__(self,
                 longitude=None,
                 latitude=None,
                 altitude=0):
        super(Location, self).__init__()
        self._kml['longitude'] = longitude
        self._kml['latitude'] = latitude
        self._kml['altitude'] = altitude

    @property
    def longitude(self):
        """Decimal degree, accepts float."""
        return self._kml['longitude']

    @longitude.setter
    def longitude(self, longitude):
        self._kml['longitude'] = longitude

    @property
    def latitude(self):
        """Decimal degree, accepts float."""
        return self._kml['latitude']

    @latitude.setter
    def latitude(self, latitude):
        self._kml['latitude'] = latitude

    @property
    def altitude(self):
        """Height above the earth's surface in meters, accepts float."""
        return self._kml['altitude']

    @altitude.setter
    def altitude(self, altitude):
        self._kml['altitude'] = altitude


class Orientation(Kmlable):
    """Describes rotation of a 3D model's coordinate system.

    The arguments are the same as the properties.
    """
    def __init__(self,
                 heading=0,
                 tilt=0,
                 roll=0):
        super(Orientation, self).__init__()
        self._kml['heading'] = heading
        self._kml['tilt'] = tilt
        self._kml['roll'] = roll

    @property
    def heading(self):
        """Rotation about the z axis, accepts float."""
        return self._kml['heading']

    @heading.setter
    def heading(self, heading):
        self._kml['heading'] = heading

    @property
    def tilt(self):
        """Rotation about the x axis, accepts float."""
        return self._kml['tilt']

    @tilt.setter
    def tilt(self, tilt):
        self._kml['tilt'] = tilt

    @property
    def roll(self):
        """Rotation about the y axis, accepts float."""
        return self._kml['roll']

    @roll.setter
    def roll(self, roll):
        self._kml['roll'] = roll


class Scale(Kmlable):
    """Scales a model along the x, y, and z axes in the model's coordinate space.

    The arguments are the same as the properties.
    """

    def __init__(self,
                 x=1,
                 y=1,
                 z=1):
        super(Scale, self).__init__()
        self._kml['x'] = x
        self._kml['y'] = y
        self._kml['z'] = z

    @property
    def x(self):
        """Scale in the x direction, accepts float."""
        return self._kml['x']

    @x.setter
    def x(self, x):
        self._kml['x'] = x

    @property
    def y(self):
        """Scale in the y direction, accepts float."""
        return self._kml['y']

    @y.setter
    def y(self, y):
        self._kml['y'] = y

    @property
    def z(self):
        """Scale in the z direction, accepts float."""
        return self._kml['z']

    @z.setter
    def z(self, z):
        self._kml['z'] = z


class Alias(Kmlable):
    """Contains a mapping from a sourcehref to a targethref.

    The arguments are the same as the properties.
    """

    def __init__(self,
                 targethref=None,
                 sourcehref=None):
        super(Alias, self).__init__()
        self._kml['targetHref'] = targethref
        self._kml['sourceHref'] = sourcehref

    @property
    def targethref(self):
        """The target href, accepts string."""
        return self._kml['targetHref']

    @targethref.setter
    def targethref(self, targethref):
        self._kml['targetHref'] = targethref

    @property
    def sourcehref(self):
        """The source href, accepts string."""
        return self._kml['sourceHref']

    @sourcehref.setter
    def sourcehref(self, sourcehref):
        self._kml['sourceHref'] = sourcehref


class ResourceMap(Kmlable):
    """Contains and specifies 0 or more [Alias] elements.

    The arguments are the same as the properties.
    """

    def __init__(self,
                 aliases=None):
        super(ResourceMap, self).__init__()
        self._aliases = aliases
        if self._aliases is None:
            self._aliases = []

    @property
    def aliases(self):
        """A list of all the aliases, accepts a list of aliases"""
        return self._aliases

    @aliases.setter
    def aliases(self, aliases):
        self._aliases = aliases

    def newalias(self, **kwargs):
        """Creates a new :class:`simplekml.Alias` and attaches it to the :class:`simplekml.ResourceMap`.

        Args:
          * Same as :class:`simplekml.Alias`

        """
        alias = Alias(**kwargs)
        self._aliases.append(alias)
        return alias

    def __str__(self):
        buf = []
        for alias in self._aliases:
            buf.append(alias.__str__())
        return "".join(buf)
