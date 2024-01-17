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

"""

class Coordinates(object):
    """Represents a list of Coordinate classes."""
    def __init__(self, coords=None):
        self._coords = []
        if coords is not None:
            self.addcoordinates(coords)

    def addcoordinates(self, coords):
        newcoords = []
        for coord in coords:
            if len(coord) == 2:
                coord = (coord[0], coord[1], 0.0)
            newcoords.append(coord)
        self._coords += newcoords

    def __str__(self):
        buf = []
        if not len(self._coords):
            return "0.0, 0.0, 0.0"
        for cd in self._coords:
            buf.append("{0},{1},{2}".format(cd[0], cd[1], cd[2]))
        return " ".join(buf)
