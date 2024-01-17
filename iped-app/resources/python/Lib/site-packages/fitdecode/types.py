# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import math
import struct

__all__ = []


class BaseType:
    __slots__ = ('name', 'identifier', 'fmt', 'size', 'parse')

    enum = None  # in case we're treated as a FieldType

    def __init__(self, name, identifier, fmt, parse):
        self.name = name
        self.identifier = identifier
        self.fmt = fmt
        self.size = struct.calcsize(fmt)
        self.parse = parse

    @property
    def type_num(self):
        """"Base Type Number" as per SDK definition"""
        return self.identifier & 0x1F


class FieldType:
    __slots__ = ('name', 'base_type', 'enum')

    def __init__(self, name, base_type, enum=None):
        self.name = name
        self.base_type = base_type
        self.enum = enum


class _FieldAndSubFieldBase:
    __slots__ = ()

    @property
    def base_type(self):
        return self.type if self.is_base_type else self.type.base_type

    @property
    def is_base_type(self):
        return isinstance(self.type, BaseType)

    def render(self, raw_value):
        if self.type.enum and (raw_value in self.type.enum):
            return self.type.enum[raw_value]
        return raw_value


class Field(_FieldAndSubFieldBase):
    __slots__ = (
        'name', 'type', 'def_num', 'scale', 'offset', 'units', 'components',
        'subfields')

    field_type = 'field'

    def __init__(self, name, type, def_num, scale=None, offset=None, units=None,
                 components=None, subfields=None):
        super().__init__()
        self.name = name
        self.type = type  #: `FieldType`
        self.def_num = def_num
        self.scale = scale
        self.offset = offset
        self.units = units
        self.components = components
        self.subfields = subfields


class SubField(_FieldAndSubFieldBase):
    __slots__ = ('name', 'def_num', 'type', 'scale', 'offset', 'units',
                 'components', 'ref_fields')

    field_type = 'subfield'

    def __init__(self, name, def_num, type, scale=None, offset=None, units=None,
                 components=None, ref_fields=None):
        super().__init__()
        self.name = name
        self.def_num = def_num
        self.type = type
        self.scale = scale
        self.offset = offset
        self.units = units
        self.components = components
        self.ref_fields = ref_fields


class DevField(_FieldAndSubFieldBase):
    __slots__ = (
        'dev_data_index', 'name', 'def_num', 'type', 'units',
        'native_field_num',

        # "Inherited" from FitField only to maintain interface compatibility.
        # They are always None.
        'scale', 'offset', 'components', 'subfields')

    field_type = 'devfield'

    def __init__(self, dev_data_index, name, def_num, type, units,
                 native_field_num):
        super().__init__()

        self.dev_data_index = dev_data_index
        self.name = name
        self.def_num = def_num
        self.type = type
        self.units = units
        self.native_field_num = native_field_num

        self.scale = None
        self.offset = None
        self.components = None
        self.subfields = None


class ReferenceField:
    __slots__ = ('name', 'def_num', 'value', 'raw_value')

    def __init__(self, name, def_num, value, raw_value):
        self.name = name
        self.def_num = def_num
        self.value = value
        self.raw_value = raw_value


class ComponentField:
    __slots__ = (
        'name', 'def_num', 'scale', 'offset', 'units', 'accumulate', 'bits',
        'bit_offset')

    field_type = 'component'

    def __init__(self, name, def_num, scale=None, offset=None, units=None,
                 accumulate=None, bits=None, bit_offset=None):
        self.name = name
        self.def_num = def_num
        self.scale = scale
        self.offset = offset
        self.units = units
        self.accumulate = accumulate
        self.bits = bits
        self.bit_offset = bit_offset

    def render(self, raw_value):
        if raw_value is None:
            return None

        # if it's a tuple, then it's a byte array and unpack it as such
        # (only type that uses this is compressed speed/distance)
        if isinstance(raw_value, tuple):
            # Profile.xlsx sometimes contains more components than the read raw
            # value is able to hold (typically the *event_timestamp_12* field in
            # *hr* messages).
            # This test allows to ensure *unpacked_num* is not right-shifted
            # more than possible.
            if self.bit_offset and self.bit_offset >= len(raw_value) << 3:
                raise ValueError()

            unpacked_num = 0

            # unpack byte array as little endian
            for value in reversed(raw_value):
                unpacked_num = (unpacked_num << 8) + value

            raw_value = unpacked_num

        # mask and shift like a normal number
        if isinstance(raw_value, int):
            raw_value = (raw_value >> self.bit_offset) & ((1 << self.bits) - 1)

        return raw_value


class MessageType:
    __slots__ = ('name', 'mesg_num', 'fields')

    def __init__(self, name, mesg_num, fields):
        self.name = name
        self.mesg_num = mesg_num
        self.fields = fields


class FieldDefinition:
    __slots__ = ('field', 'def_num', 'base_type', 'size')

    def __init__(self, field, def_num, base_type, size):
        self.field = field  #: `Field`
        self.def_num = def_num
        self.base_type = base_type
        self.size = size

    @property
    def is_dev(self):
        return False

    @property
    def name(self):
        return self.field.name if self.field else 'unknown_' + str(self.def_num)

    @property
    def type(self):
        return self.field.type if self.field else self.base_type


class DevFieldDefinition:
    __slots__ = ('field', 'dev_data_index', 'base_type', 'def_num', 'size')

    def __init__(self, field, dev_data_index, def_num, size):
        self.field = field
        self.dev_data_index = dev_data_index
        self.def_num = def_num
        self.size = size

        # for dev fields, the base_type and type are always the same
        self.base_type = self.type

    @property
    def is_dev(self):
        return True

    @property
    def name(self):
        if self.field:
            return self.field.name
        else:
            return f'unknown_dev_{self.dev_data_index}_{self.def_num}'

    @property
    def type(self):
        return self.field.type


class FieldData:
    __slots__ = (
        'field_def', 'field', 'parent_field', 'value', 'raw_value', 'units')

    def __init__(self, field_def, field, parent_field, value, raw_value,
                 units=None):
        self.field_def = field_def  #: `FieldDefinition` object
        self.field = field
        self.parent_field = parent_field
        self.value = value
        self.raw_value = raw_value
        self.units = units

        if not self.units and self.field:
            # Default to units on field, otherwise None.
            # NOTE: Not a property since you may want to override this in a data
            # processor
            self.units = self.field.units

    @property
    def name(self):
        """
        Field's name as defined in FIT global profile.

        If name was not found in global profile, a string is created with the
        form: ``unknown_{def_num}`` where ``def_num`` is the field's definition
        number.

        This value is **NOT** compatible with `is_named`.

        .. seealso:: `name_or_num`
        """
        return self.field.name if self.field else 'unknown_%d' % self.def_num

    @property
    def name_or_num(self):
        """
        Field's name as defined in FIT global profile.

        If name was not found in global profile, ``self.def_num`` is returned
        (`int`).

        This value is compatible with `is_named`.

        .. seealso:: `name`
        """
        return self.field.name if self.field else self.def_num

    @property
    def def_num(self):
        """Field's definition number (`int`)"""
        # prefer to return the def_num on the field since field_def may be None
        # if this field is dynamic
        return self.field.def_num if self.field else self.field_def.def_num

    @property
    def base_type(self):
        """Field's `BaseType`"""
        # try field_def's base type, if it doesn't exist, this is a dynamically
        # added field, so field doesn't be None
        if self.field_def:
            return self.field_def.base_type
        else:
            return self.field.base_type

    @property
    def is_base_type(self):
        """Field's `BaseType`"""
        return self.field.is_base_type if self.field else True

    @property
    def type(self):
        return self.field.type if self.field else self.base_type

    @property
    def field_type(self):
        return self.field.field_type if self.field else 'field'

    @property
    def is_expanded(self):
        """
        Flag to indicate whether this field has been generated through expansion
        """
        return not self.field_def

    def is_named(self, name_or_num):
        """
        Check if this field has the specified name (`str`) or definition number
        (`int`)
        """
        if self.field:
            if name_or_num in (self.field.def_num, self.field.name):
                return True

        if self.parent_field:
            if name_or_num in (self.parent_field.def_num, self.parent_field.name):
                return True

        if self.field_def:
            if name_or_num == self.field_def.def_num:
                return True

        return False


def parse_string(byteslike):
    try:
        s = byteslike[:byteslike.index(0x00)]
    except ValueError:
        # FIT specification defines the 'string' type as follows: "Null
        # terminated string encoded in UTF-8 format".
        #
        # However 'string' values are not always null-terminated when encoded,
        # according to FIT files created by Garmin devices (e.g. DEVICE.FIT file
        # from a fenix3).
        #
        # So in order to be more flexible, in case index() could not find any
        # null byte, we just decode the whole bytes-like object.
        s = byteslike

    return s.decode(encoding='utf-8', errors='replace') or None


BASE_TYPE_BYTE = BaseType(
    name='byte', identifier=0x0d, fmt='B',
    parse=lambda x: None if all(b == 0xff for b in x) else x)


BASE_TYPES = {
    0x00: BaseType(name='enum', identifier=0x00, fmt='B', parse=lambda x: None if x == 0xff else x),  # noqa
    0x01: BaseType(name='sint8', identifier=0x01, fmt='b', parse=lambda x: None if x == 0x7f else x),  # noqa
    0x02: BaseType(name='uint8', identifier=0x02, fmt='B', parse=lambda x: None if x == 0xff else x),  # noqa
    0x83: BaseType(name='sint16', identifier=0x83, fmt='h', parse=lambda x: None if x == 0x7fff else x),  # noqa
    0x84: BaseType(name='uint16', identifier=0x84, fmt='H', parse=lambda x: None if x == 0xffff else x),  # noqa
    0x85: BaseType(name='sint32', identifier=0x85, fmt='i', parse=lambda x: None if x == 0x7fffffff else x),  # noqa
    0x86: BaseType(name='uint32', identifier=0x86, fmt='I', parse=lambda x: None if x == 0xffffffff else x),  # noqa
    0x07: BaseType(name='string', identifier=0x07, fmt='s', parse=parse_string),
    0x88: BaseType(name='float32', identifier=0x88, fmt='f', parse=lambda x: None if math.isnan(x) else x),  # noqa
    0x89: BaseType(name='float64', identifier=0x89, fmt='d', parse=lambda x: None if math.isnan(x) else x),  # noqa
    0x0a: BaseType(name='uint8z', identifier=0x0a, fmt='B', parse=lambda x: None if x == 0 else x),  # noqa
    0x8b: BaseType(name='uint16z', identifier=0x8b, fmt='H', parse=lambda x: None if x == 0 else x),  # noqa
    0x8c: BaseType(name='uint32z', identifier=0x8c, fmt='I', parse=lambda x: None if x == 0 else x),  # noqa
    0x0d: BASE_TYPE_BYTE,
    0x8e: BaseType(name='sint64', identifier=0x8e, fmt='q', parse=lambda x: None if x == 0x7fffffffffffffff else x),  # noqa
    0x8f: BaseType(name='uint64', identifier=0x8f, fmt='Q', parse=lambda x: None if x == 0xffffffffffffffff else x),  # noqa
    0x90: BaseType(name='uint64z', identifier=0x90, fmt='Q', parse=lambda x: None if x == 0 else x)}  # noqa
