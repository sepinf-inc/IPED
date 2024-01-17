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

from simplekml.base import Kmlable, HotSpot, check
from simplekml.constants import ColorMode, DisplayMode, ListItemType
from simplekml.icon import Icon, ItemIcon


class ColorStyle(Kmlable):
    """Abstract base class for geometry styles.

    The arguments are the same as the properties.

    .. note::
      Not to be used directly.
    """

    def __init__(self, color=None, colormode=ColorMode.normal):
        super(ColorStyle, self).__init__()
        self._kml["color"] = color
        self._kml["colorMode"] = colormode
        
    @property
    def id(self):
        """The unique id of the substyle."""
        return self._id

    @property
    def color(self):
        """Hex string representing a color, accepts string."""
        return self._kml['color']

    @color.setter
    def color(self, color):
        self._kml['color'] = color

    @property
    def colormode(self):
        """How the color is to be used, string from :class:`simplekml.ColorMode` constants."""
        return self._kml["colorMode"]

    @colormode.setter
    def colormode(self, colormode):
        self._kml["colorMode"] = colormode
        
    def __str__(self):
        buf = []
        buf.append('<{0} id="{1}">'.format(self.__class__.__name__, self._id))
        buf.append(super(ColorStyle, self).__str__())
        buf.append("</{0}>".format(self.__class__.__name__))
        return "".join(buf)


class LineStyle(ColorStyle):
    """Specifies the drawing style for all line geometry.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        lin = kml.newlinestring(name="Pathway", description="A pathway in Kirstenbosch",
                        coords=[(18.43312,-33.98924), (18.43224,-33.98914),
                                (18.43144,-33.98911), (18.43095,-33.98904)])
        lin.style.linestyle.color = simplekml.Color.red  # Red
        lin.style.linestyle.width = 10  # 10 pixels
        kml.save("LineStyle.kml")
    """

    def __init__(self,
                 width=None,
                 gxoutercolor=None,
                 gxouterwidth=None,
                 gxphysicalwidth=None, 
                 gxlabelvisibility=None,
                 **kwargs):
        super(LineStyle, self).__init__(**kwargs)
        self._kml["width"] = width
        self._kml["gx:outerColor"] = gxoutercolor
        self._kml["gx:outerWidth"] = gxouterwidth
        self._kml["gx:physicalWidth"] = gxphysicalwidth
        self._kml["gx:labelVisibility"] = gxlabelvisibility

    @property
    def width(self):
        """Width of the line, accepts float."""
        return self._kml['width']

    @width.setter
    def width(self, width):
        self._kml['width'] = width

    @property
    def gxoutercolor(self):
        """Outer color of the line, accepts string."""
        return self._kml["gx:outerColor"]

    @gxoutercolor.setter
    def gxoutercolor(self, gxoutercolor):
        self._kml["gx:outerColor"] = gxoutercolor

    @property
    def gxouterwidth(self):
        """Outer width of the line, accepts float."""
        return self._kml["gx:outerWidth"]

    @gxouterwidth.setter
    def gxouterwidth(self, gxouterwidth):
        self._kml["gx:outerWidth"] = gxouterwidth

    @property
    def gxphysicalwidth(self):
        """Physical width of the line, accepts float."""
        return self._kml["gx:physicalWidth"]

    @gxphysicalwidth.setter
    def gxphysicalwidth(self, gxphysicalwidth):
        self._kml["gx:physicalWidth"] = gxphysicalwidth
        
    @property
    def gxlabelvisibility(self):
        """Whether or not to display a text label."""
        return self._kml['scale']

    @gxlabelvisibility.setter
    def gxlabelvisibility(self, gxlabelvisibility):
        self._kml['gx:labelVisibility'] = gxlabelvisibility


class PolyStyle(ColorStyle):
    """Specifies the drawing style for all polygons.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pol = kml.newpolygon(name="Atrium Garden",
                     outerboundaryis=[(18.43348,-33.98985),(18.43387,-33.99004),(18.43410,-33.98972),
                                      (18.43371,-33.98952),(18.43348,-33.98985)],
                     innerboundaryis=[(18.43360,-33.98982),(18.43386,-33.98995),(18.43401,-33.98974),
                                      (18.43376,-33.98962),(18.43360,-33.98982)])
        pol.style.polystyle.color = simplekml.Color.red
        pol.style.polystyle.outline = 0
        kml.save("PolyStyle.kml")
    """

    def __init__(self, fill=1, outline=1, **kwargs):
        super(PolyStyle, self).__init__(**kwargs)
        self._kml["fill"] = fill
        self._kml["outline"] = outline

    @property
    def fill(self):
        """Must the polygon be filled, accepts int of 0 or 1."""
        return self._kml['fill']

    @fill.setter
    def fill(self, fill):
        self._kml['fill'] = fill

    @property
    def outline(self):
        """Must the polygon be outlined, accepts int of 0 or 1."""
        return self._kml['outline']

    @outline.setter
    def outline(self, outline):
        self._kml['outline'] = outline


class IconStyle(ColorStyle):
    """Specifies how icons for point Placemarks are drawn.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pnt = kml.newpoint(name='A Point')
        pnt.coords = [(1.0, 2.0)]
        pnt.style.iconstyle.scale = 3  # Icon thrice as big
        pnt.style.iconstyle.icon.href = 'http://maps.google.com/mapfiles/kml/shapes/info-i.png'
        kml.save("IconStyle.kml")
    """

    def __init__(self, scale=1, heading=0, icon=None, hotspot=None, **kwargs):
        super(IconStyle, self).__init__(**kwargs)
        if icon is None:
            icon = Icon(href="http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png")
        self._kml["scale"] = scale
        self._kml["heading"] = heading
        self._kml["Icon_"] = icon
        self._kml["hotspot_"] = hotspot

    @property
    def scale(self):
        """Size of the icon, accepts float."""
        return self._kml['scale']

    @scale.setter
    def scale(self, scale):
        self._kml['scale'] = scale

    @property
    def heading(self):
        """Rotation of the icon, accepts float."""
        return self._kml['heading']

    @heading.setter
    def heading(self, heading):
        self._kml['heading'] = heading

    @property
    def icon(self):
        """The actual :class:`simplekml.Icon` to be displayed, accepts :class:`simplekml.Icon`."""
        return self._kml["Icon_"]

    @icon.setter
    @check(Icon)
    def icon(self, icon):
        self._kml["Icon_"] = icon

    @property
    def hotspot(self):
        """Anchor position inside of the icon, accepts :class:`simplekml.HotSpot`."""
        if self._kml["hotspot_"] is None:
            self._kml["hotspot_"] = HotSpot()
        return self._kml["hotspot_"]

    @hotspot.setter
    @check(HotSpot)
    def hotspot(self, hotspot):
        self._kml["hotspot_"] = hotspot


class LabelStyle(ColorStyle):
    """Specifies how the name of a Feature is drawn.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pnt = kml.newpoint(name='A Point')
        pnt.coords = [(1.0, 2.0)]
        pnt.style.labelstyle.color = simplekml.Color.red
        pnt.style.labelstyle.scale = 2  # Text twice as big
        pnt.style.labelstyle.color = simplekml.Color.blue
        kml.save("LabelStyle.kml")
    """

    def __init__(self, 
                 scale=1,
                 **kwargs):
        super(LabelStyle, self).__init__(**kwargs)
        self._kml["scale"] = scale

    @property
    def scale(self):
        """Size of the icon, accepts float."""
        return self._kml['scale']

    @scale.setter
    def scale(self, scale):
        self._kml['scale'] = scale


class BalloonStyle(Kmlable):
    """Specifies the content and layout of the description balloon.

    The arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        pnt = kml.newpoint(name="BallonStyle", coords=[(18.429191, -33.987286)])
        pnt.style.balloonstyle.text = 'These are trees and this text is blue with a green background.'
        pnt.style.balloonstyle.bgcolor = simplekml.Color.lightgreen
        pnt.style.balloonstyle.textcolor = simplekml.Color.rgb(0, 0, 255)
        kml.save("BalloomStyle.kml")
    """
    
    def __init__(self,
                 bgcolor=None,
                 textcolor=None,
                 text=None,
                 displaymode=DisplayMode.default):
        super(BalloonStyle, self).__init__()
        self._kml["bgColor"] = bgcolor
        self._kml["textColor"] = textcolor
        self._kml["text"] = text
        self._kml["displayMode"] = displaymode
        
    @property
    def id(self):
        """The unique id of the substyle."""
        return self._id

    @property
    def bgcolor(self):
        """Background color of the balloon, accepts hex string."""
        return self._kml["bgColor"]

    @bgcolor.setter
    def bgcolor(self, bgcolor):
        self._kml["bgColor"] = bgcolor

    @property
    def textcolor(self):
        """Text color in the balloon, accepts hex string."""
        return self._kml["textColor"]

    @textcolor.setter
    def textcolor(self, textcolor):
        self._kml["textColor"] = textcolor

    @property
    def text(self):
        """The actual text that will appear in the balloon, accepts string."""
        return self._kml['text']

    @text.setter
    def text(self, text):
        self._kml['text'] = text

    @property
    def displaymode(self):
        """How the balloon is tyo be displayed, accepts string from :class:`simplekml.DisplayMode` constants."""
        return self._kml["displayMode"]
    
    
    @displaymode.setter
    def displaymode(self, displaymode):
        self._kml["displayMode"] = displaymode


class ListStyle(Kmlable):
    """Specifies the display of the elements style in the navigation bar.

    The arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        fol = kml.newfolder(name='Folder')
        fol.style.liststyle.listitemtype = ListItemType.radiofolder
        fol.style.liststyle.itemicon.href = 'http://maps.google.com/mapfiles/kml/shapes/info.png'
        kml.save("ListStyle.kml")
    """

    def __init__(self,
                 listitemtype=ListItemType.check,
                 bgcolor=None,
                 itemicon=None):
        super(ListStyle, self).__init__()
        self._kml["listItemType"] = listitemtype
        self._kml["bgColor"] = bgcolor
        self._kml["ItemIcon"] = itemicon

    @property
    def id(self):
        """The unique id of the substyle."""
        return self._id

    @property
    def itemicon(self):
        """An instance of an :class:`simplekml.ItemIcon` class, accepts :class:`simplekml.ItemIcon`."""
        if self._kml["ItemIcon"] is None:
            self._kml["ItemIcon"] = ItemIcon()
        return self._kml["ItemIcon"]

    @itemicon.setter
    @check(ItemIcon)
    def itemicon(self, itemicon):
        self._kml["ItemIcon"] = itemicon

    @property
    def listitemtype(self):
        """How an item is diaplyed, accepts string from :class:`simplekml.ListItemType` constants."""
        return self._kml["listItemType"]

    @listitemtype.setter
    def listitemtype(self, listitemtype):
        self._kml["listItemType"] = listitemtype

    @property
    def bgcolor(self):
        """The background color of the item, accepts a hex string."""
        return self._kml["bgColor"]

    @bgcolor.setter
    def bgcolor(self, bgcolor):
        self._kml["bgColor"] = bgcolor
