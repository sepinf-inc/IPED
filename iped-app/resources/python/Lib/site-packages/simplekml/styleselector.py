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
from simplekml.substyle import IconStyle, LabelStyle, LineStyle, PolyStyle, BalloonStyle, ListStyle


class StyleSelector(Kmlable):
    """Abstract style class, extended by :class:`simplekml.Style` and :class:`simplekml.StyleMap`

    There are no arguments.
    """

    def __init__(self):
        super(StyleSelector, self).__init__()

    @property
    def id(self):
        """The id of the style, read-only."""
        return self._id


class Style(StyleSelector):
    """Styles affect how Geometry is presented.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pnt = kml.newpoint(name='A Point')
        pnt.coords = [(1.0, 2.0)]
        pnt.style.labelstyle.color = simplekml.Color.red  # Make the text red
        pnt.style.labelstyle.scale = 2  # Make the text twice as big
        pnt.style.iconstyle.icon.href = 'http://maps.google.com/mapfiles/kml/shapes/placemark_circle.png'
        kml.save("Style.kml")
    """
    def __init__(self,
                 iconstyle=None,
                 labelstyle=None,
                 linestyle=None,
                 polystyle=None,
                 balloonstyle=None,
                 liststyle=None):
        super(Style, self).__init__()
        self._kml["IconStyle_"] = iconstyle
        self._kml["LabelStyle_"] = labelstyle
        self._kml["LineStyle_"] = linestyle
        self._kml["PolyStyle_"] = polystyle
        self._kml["BalloonStyle"] = balloonstyle
        self._kml["ListStyle"] = liststyle

    def __str__(self):
        return '<Style id="{0}">{1}</Style>'.format(self._id, super(Style, self).__str__())
      
    @property
    def iconstyle(self):
        """The iconstyle, accepts :class:`simplekml.IconStyle`."""
        if self._kml["IconStyle_"] is None:
            self._kml["IconStyle_"] = IconStyle()
        return self._kml["IconStyle_"]
        
    @iconstyle.setter
    @check(IconStyle)
    def iconstyle(self, iconstyle):
        self._kml["IconStyle_"] = iconstyle
        
    @property
    def labelstyle(self):
        """The labelstyle, accepts :class:`simplekml.LabelStyle`."""
        if self._kml["LabelStyle_"] is None:
            self._kml["LabelStyle_"] = LabelStyle()
        return self._kml["LabelStyle_"]

    @labelstyle.setter
    @check(LabelStyle)
    def labelstyle(self, labelstyle):
        self._kml["LabelStyle_"] = labelstyle
        
    @property
    def linestyle(self):
        """The linestyle, accepts :class:`simplekml.LineStyle`."""
        if self._kml["LineStyle_"] is None:
            self._kml["LineStyle_"] = LineStyle()
        return self._kml["LineStyle_"]
        
    @linestyle.setter
    @check(LineStyle)
    def linestyle(self, linestyle):
        self._kml["LineStyle_"] = linestyle

    @property
    def polystyle(self):
        """The polystyle, accepts :class:`simplekml.PolyStyle`."""
        if self._kml["PolyStyle_"] is None:
            self._kml["PolyStyle_"] = PolyStyle()
        return self._kml["PolyStyle_"]
        
    @polystyle.setter
    @check(PolyStyle)
    def polystyle(self, polystyle):
        self._kml["PolyStyle_"] = polystyle
        
    @property
    def balloonstyle(self):
        """The balloonstyle, accepts :class:`simplekml.BalloonStyle`."""
        if self._kml["BalloonStyle"] is None:
            self._kml["BalloonStyle"] = BalloonStyle()
        return self._kml["BalloonStyle"]

    @balloonstyle.setter
    @check(BalloonStyle)
    def balloonstyle(self, balloonstyle):
        self._kml["BalloonStyle"] = balloonstyle

    @property
    def liststyle(self):
        """The liststyle, accepts :class:`simplekml.ListStyle`."""
        if self._kml["ListStyle"] is None:
            self._kml["ListStyle"] = ListStyle()
        return self._kml["ListStyle"]

    @liststyle.setter
    @check(ListStyle)
    def liststyle(self, liststyle):
        self._kml["ListStyle"] = liststyle


class StyleMap(StyleSelector):
    """Styles affect how Geometry is presented.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pnt = kml.newpoint(coords=[(18.432314,-33.988862)])
        pnt.stylemap.normalstyle.labelstyle.color = simplekml.Color.blue
        pnt.stylemap.highlightstyle.labelstyle.color = simplekml.Color.red
        kml.save("StyleMap.kml")
    """
    def __init__(self,
                 normalstyle=None,
                 highlightstyle=None):
        super(StyleMap, self).__init__()
        self._pairnormal = None
        self._pairhighlight = None
        self.normalstyle = normalstyle
        self.highlightstyle = highlightstyle

    def __str__(self):
        buf = ['<StyleMap id="{0}">'.format(self._id),
               super(StyleMap, self).__str__()]
        if self._pairnormal is not None:
            buf.append("<Pair>")
            buf.append("<key>normal</key>")
            buf.append("<styleUrl>#{0}</styleUrl>".format(self._pairnormal._id))
            buf.append("</Pair>")
        if self._pairhighlight is not None:
            buf.append("<Pair>")
            buf.append("<key>highlight</key>")
            buf.append("<styleUrl>#{0}</styleUrl>".format(self._pairhighlight._id))
            buf.append("</Pair>")
        buf.append("</StyleMap>")
        return "".join(buf)

    @property
    def normalstyle(self):
        """The normal :class:`simplekml.Style`, accepts :class:`simplekml.Style`."""
        if self._pairnormal is None:
            self._pairnormal = Style()
        return self._pairnormal

    @normalstyle.setter
    @check(Style)
    def normalstyle(self, normal):
        self._pairnormal = normal

    @property
    def highlightstyle(self):
        """The highlighted :class:`simplekml.Style`, accepts :class:`simplekml.Style`."""
        if self._pairhighlight is None:
            self._pairhighlight = Style()
        return self._pairhighlight

    @highlightstyle.setter
    @check(Style)
    def highlightstyle(self, highlighturl):
        self._pairhighlight = highlighturl
