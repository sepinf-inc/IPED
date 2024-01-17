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

class SimpleField(Kmlable):
    """Custom field, forms part of a schema.

    The arguments are the same as the properties.
    """

    def __init__(self, name=None, type='string', displayname=None):
        super(SimpleField, self).__init__()
        self._kml['name'] = name
        self._kml['type'] = type
        self._kml['displayName'] = displayname

    @property
    def name(self):
        """Name of field, accepts string."""
        return self._kml['name']

    @name.setter
    def name(self, name):
        self._kml['name'] = name

    @property
    def type(self):
        """Type of field, accepts string from :class:`simplekml.Types` constants."""
        return self._kml['type']

    @type.setter
    def type(self, type):
        self._kml['type'] = type

    @property
    def displayname(self):
        """Pretty name of field that is shown in google earth, accepts string."""
        return self._kml['displayName']

    @displayname.setter
    def displayname(self, displayname):
        self._kml['displayName'] = displayname

    def __str__(self):
        buf = [u'<SimpleField type="{0}" name="{1}">'.format(self.type, Kmlable._chrconvert(self.name))]
        if self.displayname is not None:
            buf.append(u'<displayName>{0}</displayName>'.format(Kmlable._chrconvert(self.displayname)))
        buf.append('</SimpleField>')
        return "".join(buf)


class GxSimpleArrayField(SimpleField):
    """Custom array field for gx:track, forms part of a schema.

    Args:
      * *same as properties*
      * *all other args same as* :class:`simplekml.SimpleField`
    """

    def __init__(self, name=None, type='string', displayname=None):
        super(GxSimpleArrayField, self).__init__(name, type, displayname)

    def __str__(self):
        buf = ['<gx:SimpleArrayField type="{0}" name="{1}">'.format(self.type,
                                                                    self.name)]
        if self.displayname is not None:
            buf.append('<displayName>{0}</displayName>'.format(self.displayname))
        buf.append('</gx:SimpleArrayField>')
        return "".join(buf)


class SimpleData(Kmlable):
    """Data of a schema.

    The arguments are the same as the properties.
    """

    def __init__(self, name, value):
        super(SimpleData, self).__init__()
        self._kml['name'] = name
        self._kml['value'] = value

    @property
    def name(self):
        """Name of field, accepts string."""
        return self._kml['name']

    @name.setter
    def name(self, name):
        self._kml['name'] = name

    @property
    def value(self):
        """Value of field, accepts int, float or string."""
        return self._kml['value']

    @value.setter
    def value(self, value):
        self._kml['value'] = value

    def __str__(self):
        return u'<SimpleData name="{0}">{1}</SimpleData>'.format(Kmlable._chrconvert(self.name), self.value)


class GxSimpleArrayData(Kmlable):
    """Data of a :class:`simplekml.GxSimpleArrayField`.

    The arguments are the same as the properties.
    """

    def __init__(self, name, values=None):
        super(GxSimpleArrayData, self).__init__()
        self._kml['name'] = name
        self.values = []
        if values is not None:
            self.values += values

    @property
    def name(self):
        """Name of field, accepts string."""
        return self._kml['name']

    @name.setter
    def name(self, name):
        self._kml['name'] = name

    def newvalue(self, value):
        """Adds a value to the gxsimpledarraydata."""
        self.values.append(value)

    def __str__(self):
        buf = [u'<gx:SimpleArrayData name="{0}">'.format(Kmlable._chrconvert(self.name))]
        for value in self.values:
            buf.append("<gx:value>{0}</gx:value>".format(value))
        buf.append("</gx:SimpleArrayData>")
        return "".join(buf)



class Schema(Kmlable):
    """Custom KML schema that is used to add custom data to KML Features.

    The arguments are the same as the properties.
    """

    def __init__(self, name=None):
        super(Schema, self).__init__()
        self._kml['name'] = name
        self.simplefields = []
        self.gxsimplearrayfields = []

    @property
    def id(self):
        """Unique id of the schema."""
        return self._id

    @property
    def name(self):
        """Name of schema, accepts string."""
        return self._kml['name']

    @name.setter
    def name(self, name):
        self._kml['name'] = name

    def newsimplefield(self, name, type, displayname=None):
        """Creates a new :class:`simplekml.SimpleField` and attaches it to this schema.

        Returns an instance of :class:`simplekml.SimpleField` class.

        Args:
          * name: string name of simplefield (required)
          * type: string type of field (required)
          * displayname: string for pretty name that will be displayed (default None)
        """
        self.simplefields.append(SimpleField(name, type, displayname))
        return self.simplefields[-1]

    def newgxsimplearrayfield(self, name, type, displayname=None):
        """Creates a new :class:`simplekml.GxSimpleArrayField` and attaches it to this schema.

        Returns an instance of :class:`simplekml.GxSimpleArrayField` class.

        Args:
          * name: string name of simplefield (required)
          * type: string type of field (required)
          * displayname: string for pretty name that will be displayed (default None)
        """
        self.gxsimplearrayfields.append(GxSimpleArrayField(name, type, displayname))
        return self.gxsimplearrayfields[-1]

    def __str__(self):
        buf = []
        if self.name is not None:
            buf.append(u'<Schema name="{0}" id="{1}">'.format(Kmlable._chrconvert(self.name), self._id))
        else:
            buf.append('<Schema id="{0}">'.format(self._id))
        for field in self.simplefields:
            buf.append(field.__str__())
        for field in self.gxsimplearrayfields:
            buf.append(field.__str__())
        buf.append('</Schema>')
        return "".join(buf)



class Data(Kmlable):
    """Data of extended data used to add custom data to KML Features.

    The arguments are the same as the properties.
    """

    def __init__(self, name=None, value=None, displayname=None):
        super(Data, self).__init__()
        self._kml['name'] = name
        self._kml['value'] = value
        self._kml['displayName'] = displayname

    @property
    def name(self):
        """Data name, accepts string."""
        return self._kml['name']

    @name.setter
    def name(self, name):
        self._kml['name'] = name

    @property
    def value(self):
        """Data value, accepts string."""
        return self._kml['value']

    @value.setter
    def value(self, value):
        self._kml['value'] = value

    @property
    def displayname(self):
        """The name that is displayed to the user, accepts string."""
        return self._kml['displayName']

    @displayname.setter
    def displayname(self, displayname):
        self._kml['displayName'] = displayname

    def __str__(self):
        buf = [u'<Data name="{0}">'.format(Kmlable._chrconvert(self.name))]
        if self._kml['value'] is not None:
            buf.append("<value>{0}</value>".format(self._kml['value']))
        if self._kml['displayName'] is not None:
            buf.append(u"<displayName>{0}</displayName>".format(Kmlable._chrconvert(self._kml['displayName'])))
        buf.append('</Data>')
        return "".join(buf)



class SchemaData(Kmlable):
    """Data of a schema that is used to add custom data to KML Features.

    The arguments are the same as the properties.
    """

    def __init__(self, schemaurl=None):
        super(SchemaData, self).__init__()
        self.simpledatas = []
        self.gxsimplearraydatas = []
        self._kml["schemaUrl"] = schemaurl

    @property
    def schemaurl(self):
        """Schema url, accepts string."""
        if self._kml['schemaUrl'] is not None:
            return '#{0}'.format(self._kml['schemaUrl'])
        else:
            return None

    @schemaurl.setter
    def schemaurl(self, schemaurl):
        self._kml['schemaUrl'] = schemaurl

    def newsimpledata(self, name, value):
        """
        Creates a new :class:`simplekml.SimpleData` and attaches it to this schemadata.

        Returns an instance of :class:`simplekml.SimpleData` class.

        Args:
          * name: string name of simplefield (required)
          * value: int, float or string for value of field (required)
        """
        self.simpledatas.append(SimpleData(name, value))
        return self.simpledatas[-1]

    def newgxsimplearraydata(self, name, value):
        """Creates a new :class:`simplekml.GxSimpleArrayData` and attaches it to this schemadata.

        Returns an instance of :class:`simplekml.GxSimpleArrayData` class.

        Args:
          * name: string name of simplefield (required)
          * value: int, float or string for value of field (required)
        """
        self.gxsimplearraydatas.append(GxSimpleArrayData(name, value))
        return self.gxsimplearraydatas[-1]

    def __str__(self):
        buf = ['<SchemaData schemaUrl="{0}">'.format(self.schemaurl)]
        for field in self.simpledatas:
            buf.append(field.__str__())
        for field in self.gxsimplearraydatas:
            buf.append(field.__str__())
        buf.append('</SchemaData>')
        return "".join(buf)



class ExtendedData(Kmlable):
    """Data of a schema that is used to add custom data to KML Features.

    The arguments are the same as the properties.
    """

    def __init__(self):
        super(ExtendedData, self).__init__()
        self._kml['schemaData_'] = None
        self.datas = []

    @property
    def schemadata(self):
        """Extra data for the feature, accepts :class:`simplekml.SchemaData`."""
        if self._kml['schemaData_'] is None:
            self._kml['schemaData_'] = SchemaData()
        return self._kml['schemaData_']

    @schemadata.setter
    @check(SchemaData)
    def schemadata(self, schemadata):
        self._kml['schemaData_'] = schemadata

    def newdata(self, name, value, displayname=None):
        """Creates a new :class:`simplekml.Data` and attaches it to this schemadata.

        Returns an instance of :class:`simplekml.Data` class.

        Args:
          * name: string name of simplefield (required)
          * value: int, float or string for value of field (required)
          * displayname: string for pretty name that will be displayed (default None)
        """
        self.datas.append(Data(name, value, displayname))
        return self.datas[-1]

    def __str__(self):
        buf = []
        for data in self.datas:
            buf.append(data.__str__())
        if self._kml['schemaData_'] is not None:
            buf.append(self._kml['schemaData_'].__str__())
        return "".join(buf)
