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

from simplekml.abstractview import AbstractView,Camera,GxOption,GxTimeSpan,GxTimeStamp,GxViewerOptions,LookAt
from simplekml.base import HotSpot,OverlayXY,RotationXY,ScreenXY,Size,Snippet
from simplekml.constants import AltitudeMode,Color,ColorMode,DisplayMode,GridOrigin,GxAltitudeMode,ListItemType,RefreshMode,Shape,State,Types,Units,ViewRefreshMode,GxFlyToMode,GxPlayMode
from simplekml.coordinates import Coordinates
from simplekml.featgeom import Container,Document, Folder,GroundOverlay,GxMultiTrack,GxTrack,LinearRing,LineString,Model,MultiGeometry,NetworkLink,Point,Polygon,PhotoOverlay,ScreenOverlay
from simplekml.icon import Icon,ItemIcon,Link
from simplekml.kml import Kml
from simplekml.model import Alias,Location,Orientation,ResourceMap,Scale
from simplekml.overlay import GridOrigin,ImagePyramid,ViewVolume
from simplekml.region import Box,GxLatLonQuad,LatLonAltBox,LatLonBox,Lod,Region
from simplekml.schema import Data,ExtendedData,GxSimpleArrayData,GxSimpleArrayField,SchemaData,SimpleData,Schema,SimpleField
from simplekml.styleselector import Style,StyleMap
from simplekml.substyle import BalloonStyle,IconStyle,LabelStyle,LineStyle,ListStyle,PolyStyle
from simplekml.timeprimitive import GxTimeSpan,GxTimeStamp,TimeSpan,TimeStamp
from simplekml.tour import GxAnimatedUpdate,GxFlyTo,GxPlaylist,GxSoundCue,GxTour,GxTourControl,GxWait,Update
from simplekml.networklinkcontrol import LinkSnippet, NetworkLinkControl

__version__ = "1.3.2"
