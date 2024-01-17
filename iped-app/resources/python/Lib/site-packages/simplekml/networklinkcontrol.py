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

from simplekml.base import Kmlable, Snippet, check
from simplekml.abstractview import Camera, LookAt
from simplekml.tour import Update


class LinkSnippet(Snippet):
    """
    A short description of the feature.

    Arguments are the same as the properties.

    *New in version 1.1.1*
    """

    def __init__(self, **kwargs):
        super(LinkSnippet, self).__init__(**kwargs)

    def __str__(self):
        if self._kml['maxlines'] is not None:
            return u'<linkSnippet maxLines="{0}">{1}</linkSnippet>'.format(self._kml['maxlines'],Kmlable._chrconvert(self._kml['content']))
        else:
            return u'<linkSnippet>{0}</linkSnippet>'.format(Kmlable._chrconvert(self._kml['content']))



class NetworkLinkControl(Kmlable):
    """Controls the behavior of files fetched by a :class:`simplekml.NetworkLink`.

    Arguments are the same as the properties.

    Usage::

        import simplekml
        kml = simplekml.Kml()
        kml.document = None  # Removes the default document
        kml.networklinkcontrol.minrefreshperiod = 5  # By accessing the networklinkcontrol property one it created
        kml.save('NetworkLinkControl.kml')

    *New in version 1.1.1*
    """

    def __init__(self, minrefreshperiod=None,
                 maxsessionlength=None,
                 cookie=None,
                 message=None,
                 linkname=None,
                 linkdescription=None,
                 linksnippet=None,
                 expires=None,
                 update=None,
                 camera=None,
                 lookat=None,
                 **kwargs):
        super(NetworkLinkControl, self).__init__()
        self._kml['minRefreshPeriod'] = minrefreshperiod
        self._kml['maxSessionLength'] = maxsessionlength
        self._kml['cookie'] = cookie
        self._kml['message'] = message
        self._kml['linkName'] = linkname
        self._kml['linkDescription'] = linkdescription
        self._kml['linkSnippet_'] = linksnippet
        self._kml['expires'] = expires
        self._kml['Update'] = update
        self._kml['Camera'] = camera
        self._kml['LookAt'] = lookat

    @property
    def minrefreshperiod(self):
        """Minimum allowed time between fetches of the file in seconds, accepts int.

        *New in version 1.1.1*
        """
        return self._kml['minRefreshPeriod']

    @minrefreshperiod.setter
    def minrefreshperiod(self, minrefreshperiod):
        self._kml['minRefreshPeriod'] = minrefreshperiod

    @property
    def maxsessionlength(self):
        """Maximum amount of time for which the client :class:`simplekml.NetworkLink` can remain connected in seconds, accepts int.

        *New in version 1.1.1*
        """
        return self._kml['maxSessionLength']

    @maxsessionlength.setter
    def maxsessionlength(self, maxsessionlength):
        self._kml['maxSessionLength'] = maxsessionlength

    @property
    def cookie(self):
        """Use this to append a string to the URL query on the next refresh of the network link, accepts string.

        *New in version 1.1.1*
        """
        return self._kml['cookie']

    @cookie.setter
    def cookie(self, cookie):
        self._kml['cookie'] = cookie

    @property
    def message(self):
        """A message that appears when the network link is first loaded into Google Earth, accepts string.

        *New in version 1.1.1*
        """
        return self._kml['message']

    @message.setter
    def message(self, message):
        self._kml['message'] = message

    @property
    def linkname(self):
        """Name of the network link, accepts string.

        *New in version 1.1.1*
        """
        return self._kml['linkName']

    @linkname.setter
    def linkname(self, linkname):
        self._kml['linkName'] = linkname

    @property
    def linkdescription(self):
        """Description of the network link, accepts string.

        *New in version 1.1.1*
        """
        return self._kml['linkDescription']

    @linkdescription.setter
    def linkdescription(self, linkdescription):
        self._kml['linkDescription'] = linkdescription

    @property
    def linksnippet(self):
        """Short description of the feature, accepts :class:`simplekml.LinkSnippet`

        *New in version 1.1.1*
        """
        if self._kml['linkSnippet_'] is None:
            self._kml['linkSnippet_'] = LinkSnippet()
        return self._kml['linkSnippet_']

    @linksnippet.setter
    @check(LinkSnippet)
    def linksnippet(self, linksnippet):
        self._kml['linkSnippet_'] = linksnippet

    @property
    def camera(self):
        """Camera that views the scene, accepts :class:`simplekml.Camera`

        *New in version 1.1.1*
        """
        if self._kml['Camera'] is None:
            self._kml['Camera'] = Camera()
            self._kml['LookAt'] = None
        return self._kml['Camera']

    @camera.setter
    @check(Camera)
    def camera(self, camera):
        self._kml['Camera'] = camera
        self._kml['LookAt'] = None

    @property
    def lookat(self):
        """Camera relative to the feature, accepts :class:`simplekml.LookAt`

        *New in version 1.1.1*
        """
        if self._kml['LookAt'] is None:
            self._kml['LookAt'] = LookAt()
            self._kml['Camera'] = None
        return self._kml['LookAt']

    @lookat.setter
    @check(LookAt)
    def lookat(self, lookat):
        self._kml['Camera'] = None
        self._kml['LookAt'] = lookat

    @property
    def expires(self):
        """Date/time at which the link should be refreshed, accepts string.

        *New in version 1.1.1*
        """
        return self._kml['expires']

    @expires.setter
    def expires(self, expires):
        self._kml['expires'] = expires

    @property
    def update(self):
        """Instance of :class:`simplekml.Update`

        *New in version 1.1.1*
        """
        if self._kml['Update'] is None:
            self._kml['Update'] = Update()
        return self._kml['Update']

    @update.setter
    @check(Update)
    def update(self, update):
        self._kml['Update'] = update

    def __str__(self):
        buf = []
        str = '<{0}>'.format(self.__class__.__name__)
        buf.append(str)
        buf.append(super(NetworkLinkControl, self).__str__())
        buf.append("</{0}>".format(self.__class__.__name__))
        return "".join(buf)