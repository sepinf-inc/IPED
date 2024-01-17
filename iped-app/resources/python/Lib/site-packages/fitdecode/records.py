# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import itertools

__all__ = [
    'FitChunk', 'FitHeader', 'FitCRC', 'FitDefinitionMessage', 'FitDataMessage',
    'FIT_FRAME_HEADER', 'FIT_FRAME_CRC',
    'FIT_FRAME_DEFINITION', 'FIT_FRAME_DATA',
    'FIT_FRAME_DEFMESG', 'FIT_FRAME_DATAMESG']


_UNSET = object()

FIT_FRAME_HEADER = 1
FIT_FRAME_CRC = 2
FIT_FRAME_DEFINITION = 3
FIT_FRAME_DATA = 4

# keep aliases for backward compatibility
FIT_FRAME_DEFMESG = FIT_FRAME_DEFINITION
FIT_FRAME_DATAMESG = FIT_FRAME_DATA


class FitChunk:
    __slots__ = ('index', 'offset', 'bytes')

    def __init__(self, index, offset, bytes):
        self.index = index    #: zero-based index of this frame in the file
        self.offset = offset  #: the offset at which this frame starts in the file
        self.bytes = bytes    #: the frame itself as a `bytes` object


class FitHeader:
    frame_type = FIT_FRAME_HEADER

    __slots__ = (
        'header_size', 'proto_ver', 'profile_ver', 'body_size',
        'crc', 'crc_matched', 'chunk')

    def __init__(
            self, header_size, proto_ver, profile_ver, body_size, crc,
            crc_matched, chunk):
        self.header_size = header_size
        self.proto_ver = proto_ver
        self.profile_ver = profile_ver
        self.body_size = body_size
        self.crc = crc  #: may be null
        self.crc_matched = crc_matched
        self.chunk = chunk  #: `FitChunk` or `None` (depends on ``keep_raw_chunks`` option)  # noqa


class FitCRC:
    frame_type = FIT_FRAME_CRC

    __slots__ = ('crc', 'matched', 'chunk')

    def __init__(self, crc, matched, chunk):
        self.crc = crc
        self.matched = matched
        self.chunk = chunk  #: `FitChunk` or `None` (depends on ``keep_raw_chunks`` option)  # noqa


class FitDefinitionMessage:
    frame_type = FIT_FRAME_DEFMESG

    __slots__ = (
        # record header
        'is_developer_data',
        'local_mesg_num',
        'time_offset',

        # payload
        'mesg_type',
        'global_mesg_num',
        'endian',
        'field_defs',
        'dev_field_defs',

        'chunk')

    def __init__(
            self, is_developer_data, local_mesg_num, time_offset, mesg_type,
            global_mesg_num, endian, field_defs, dev_field_defs, chunk):
        self.is_developer_data = is_developer_data
        self.local_mesg_num = local_mesg_num
        self.time_offset = time_offset
        self.mesg_type = mesg_type
        self.global_mesg_num = global_mesg_num
        self.endian = endian
        self.field_defs = field_defs  #: list of `FieldDefinition`
        self.dev_field_defs = dev_field_defs  #: list of `DevFieldDefinition`
        #: `FitChunk` or `None` (depends on ``keep_raw_chunks`` option)
        self.chunk = chunk

    @property
    def name(self):
        if self.mesg_type is not None:
            return self.mesg_type.name
        else:
            return f'unknown_{self.global_mesg_num}'

    @property
    def all_field_defs(self):
        if not self.dev_field_defs:
            return self.field_defs
        return itertools.chain(self.field_defs, self.dev_field_defs)


class FitDataMessage:
    frame_type = FIT_FRAME_DATAMESG

    __slots__ = (
        # record header
        'is_developer_data',
        'local_mesg_num',
        'time_offset',

        'def_mesg',
        'fields',
        'chunk')

    def __init__(
            self, is_developer_data, local_mesg_num, time_offset, def_mesg,
            fields, chunk):
        #: Is this a "developer" message?
        self.is_developer_data = is_developer_data

        #: The **local** definition number of this message
        self.local_mesg_num = local_mesg_num

        #: Time offset in case header was compressed. `None` otherwise.
        self.time_offset = time_offset

        #: `FitDefinitionMessage`
        self.def_mesg = def_mesg

        #: list of `FieldData`
        self.fields = fields

        #: `FitChunk` or `None` (depends on ``keep_raw_chunks`` option)
        self.chunk = chunk

    def __iter__(self):
        """Iterate over the `FieldData` object in this mesage"""
        return iter(self.fields)

    @property
    def name(self):
        """Message name"""
        return self.def_mesg.name

    @property
    def global_mesg_num(self):
        """The **global** definition number of this message"""
        return self.def_mesg.global_mesg_num

    @property
    def mesg_type(self):
        """The `MessageType` object this message is associated to"""
        return self.def_mesg.mesg_type

    def has_field(self, field_name_or_num):
        """
        Is the desired field present in this message?

        *field_name_or_num* is the name of the field (`str`), or its definition
        number (`int`).

        .. seealso:: `get_field`, `get_fields`, `get_value`, `get_values`
        """
        for field in self.fields:
            if field.is_named(field_name_or_num):
                return True

        return False

    def get_field(self, field_name_or_num, idx=0):
        """
        Get the desired `FieldData` object.

        *field_name_or_num* is the name of the field (`str`), or its definition
        number (`int`).

        *idx* is the zero-based index of the specified field **among other
        fields with the same name/number**. I.e. not the index of the field in
        the list of fields of this message. That is, ``idx=0`` is the first
        *field_name_or_num* field found in this message.

        *idx* is useful in case a message contains multiple fields with the same
        *field_name_or_num*.

        .. seealso:: `get_fields`, `get_value`, `get_values`, `has_field`
        """
        current_idx = -1
        for field in self.fields:
            if field.is_named(field_name_or_num):
                current_idx += 1
                if current_idx == idx:
                    return field

        raise KeyError(
            f'field "{field_name_or_num}" (idx #{idx}) not found in '
            f'message "{self.name}"')

    def get_fields(self, field_name_or_num):
        """
        Like `get_field` but **yield** every `FieldData` object matching
        *field_name_or_num* fields in this message - i.e. generator.

        .. seealso:: `get_field`, `get_value`, `get_values`, `has_field`
        """
        for field in self.fields:
            if field.is_named(field_name_or_num):
                yield field

    def get_raw_value(
            self, field_name_or_num, *, idx=0, fallback=_UNSET, raw_value=True,
            fit_type=None, py_type=_UNSET):
        return self.get_value(
            field_name_or_num, idx=idx, fallback=fallback, raw_value=raw_value,
            fit_type=fit_type, py_type=py_type)

    def get_value(
            self, field_name_or_num, *, idx=0, fallback=_UNSET, raw_value=False,
            fit_type=None, py_type=_UNSET):
        """
        Get the value (or raw_value) of a field specified by its name or its
        definition number (*field_name_or_num*), with optional type checking.

        *idx* has the same meaning than for `get_field`.

        *fallback* can be specified to avoid `KeyError` being raised in case no
        field matched *field_name_or_num*.

        *fit_type* can be a `str` to indicate a given FIT type is expected (as
        defined in FIT profile; e.g. ``date_time``, ``manufacturer``, ...), in
        which case `TypeError` may be raised in case of a type mismatch.

        *py_type* can be a Python type or a `tuple` of types to expect (as
        passed to `isinstance`), in which case `TypeError` may be raised in case
        of a type mismatch.

        *raw_value* can be set to a true value so that the returned value is
        field's ``raw_value`` property instead of ``value``. This does not
        impact the way *fit_type* and *py_type* are interpreted.

        Special case: *field_name_or_num* can be `None`, in which case the field
        will be selected using *idx* only. In this case, *idx* is interpreted to
        be the zero-based index in the list of fields.

        .. seealso:: `get_values`, `get_field`, `get_fields`, `has_field`
        """
        assert (
            fit_type is _UNSET or
            fit_type is None or
            isinstance(fit_type, str))

        field_data = None

        if field_name_or_num is None:
            try:
                field_data = self.fields[idx]
                field_name_or_num = field_data.name_or_num

                # change the representation of idx so that its meaning can be
                # differentiated in case an exception is raised and it has to be
                # printed later on
                idx = f'[{idx}]'
            except KeyError:
                # KeyError exception is handled below so that the *fallback*
                # argument can be honored
                pass
        else:
            current_idx = -1
            for field in self.fields:
                if field.is_named(field_name_or_num):
                    current_idx += 1
                    if current_idx == idx:
                        field_data = field
                        break

        if field_data is None:
            if fallback is _UNSET:
                raise KeyError(
                    f'field "{field_name_or_num}" (idx #{idx}) not found in '
                    f'message "{self.name}"')
            return fallback

        # check FIT type if needed
        if fit_type and field_data.type.name != fit_type:
            raise TypeError(
                'unexpected type for FIT field '
                f'"{self.name}.{field_name_or_num}" (idx #{idx}; '
                f'got {field_data.type.name} instead of {fit_type})')

        # pick the right property
        value = field_data.value if not raw_value else field_data.raw_value

        # check value's type if needed
        if py_type is not _UNSET and not isinstance(value, py_type):
            if isinstance(py_type, (tuple, list)):
                py_type_str = ' or '.join([str(type(t)) for t in py_type])
            else:
                py_type_str = str(type(py_type))

            raise TypeError(
                'unexpected type for FIT value '
                f'"{self.name}.{field_name_or_num}" (idx #{idx}; '
                f'got {type(value)} instead of {py_type_str})')

        return value

    def get_values(
            self, field_name_or_num, *, raw_value=False, fit_type=None,
            py_type=_UNSET):
        """
        Like `get_value` but **yield** every value of all the fields that match
        *field_name_or_num* - i.e. generator.

        It is not possible to specify a *fallback* value so `KeyError` will
        always be raised in case the specified field was not found.

        The other arguments have the same meaning than for `get_value`.

        .. seealso:: `get_value`, `get_field`, `get_fields`, `has_field`
        """
        for idx, field_data in enumerate(self.fields):
            if field_data.is_named(field_name_or_num):
                value = self.get_value(
                    None, idx=idx, raw_value=raw_value,
                    fit_type=fit_type, py_type=py_type)
                yield value
