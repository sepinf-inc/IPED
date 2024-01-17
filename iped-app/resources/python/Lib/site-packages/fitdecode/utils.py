# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import re
import time

from . import profile

__all__ = []


METHOD_NAME_SCRUBBER = re.compile(r'\W|^(?=\d)')
UNIT_NAME_TO_FUNC_REPLACEMENTS = (
    ('/', ' per '),
    ('%', 'percent'),
    ('*', ' times '))

CRC_START = 0
CRC_TABLE = (
    0x0000, 0xcc01, 0xd801, 0x1400, 0xf001, 0x3c00, 0x2800, 0xe401,
    0xa001, 0x6c00, 0x7800, 0xb401, 0x5000, 0x9c01, 0x8801, 0x4400)


def scrub_method_name(method_name, convert_units=False):
    """Create a valid Python name out of *method_name*"""
    if convert_units:
        for replace_from, replace_to in UNIT_NAME_TO_FUNC_REPLACEMENTS:
            method_name = method_name.replace(replace_from, str(replace_to))

    return METHOD_NAME_SCRUBBER.sub('_', method_name)


def get_mesg_type(mesg_name_or_num):
    """
    Get a :class:`fitdecode.MessageType` from ``profile``, by its name (`str`)
    or its global number (`int`).

    Raise `ValueError` if type was not found.
    """
    # assume mesg_num first
    try:
        return profile.MESSAGE_TYPES[mesg_name_or_num]
    except KeyError:
        pass

    for mesg_type in profile.MESSAGE_TYPES.values():
        if mesg_name_or_num == mesg_type.name:
            return mesg_type

    raise ValueError(f'message type "{mesg_name_or_num}" not found')


def get_mesg_num(mesg_name):
    """
    Get the global number of a message as defined in ``profile``, by its name

    Raise `ValueError` if type was not found.
    """
    for mesg_type in profile.MESSAGE_TYPES.values():
        if mesg_name == mesg_type.name:
            return mesg_type.mesg_num

    raise ValueError(f'message type "{mesg_name}" not found')


def get_mesg_field(mesg_name_or_num, field_name_or_num):
    """
    Get the :class:`fitdecode.types.Field` object of a particular field from a
    particular message.

    Raise `ValueError` if message or field was not found.
    """
    mesg_type = get_mesg_type(mesg_name_or_num)
    for field in mesg_type.fields:
        if field_name_or_num in (field.def_num, field.name):
            return field

    raise ValueError(
        f'field "{field_name_or_num}" not found in '
        f'message "{mesg_name_or_num}"')


def get_mesg_field_num(mesg_name_or_num, field_name):
    """
    Get the definition number of a particular field from a particular message.

    Raise `ValueError` if message or field was not found.
    """
    mesg_type = get_mesg_type(mesg_name_or_num)
    for field in mesg_type.fields:
        if field.name == field_name:
            return field.def_num

    raise ValueError(
        f'field "{field_name}" not found in message "{mesg_name_or_num}"')


def get_field_type(field_name):
    """
    Get :class:`fitdecode.FieldType` by name from ``profile``.

    Raise `ValueError` if type was not found.
    """
    try:
        return profile.FIELD_TYPE[field_name]
    except KeyError:
        raise ValueError(f'field type "{field_name}" not found')


def compute_crc(byteslike, *, crc=CRC_START, start=0, end=None):
    """
    Compute the CRC as per FIT definition, of *byteslike* object, from offset
    *start* (included) to *end* (excluded)
    """
    if not end:
        end = len(byteslike)

    if start >= end:
        assert 0
        return crc

    # According to some performance tests, A is always (at least slightly)
    # faster than B, either with a high number of calls to this fonction, and/or
    # with a high number of "for" iterations (CPython 3.6.5 x64 on Windows).
    #
    # A. for byte in memoryview(byteslike)[start:end]:
    #        # ...
    #
    # B. for idx in range(start, end):
    #        byte = byteslike[idx]
    #        # ...

    for byte in memoryview(byteslike)[start:end]:
        tmp = CRC_TABLE[crc & 0xf]
        crc = (crc >> 4) & 0x0fff
        crc = crc ^ tmp ^ CRC_TABLE[byte & 0xf]

        tmp = CRC_TABLE[crc & 0xf]
        crc = (crc >> 4) & 0x0fff
        crc = crc ^ tmp ^ CRC_TABLE[(byte >> 4) & 0xf]

    return crc


def blocking_read(istream, size=-1, nonblocking_reads_delay=0.06):
    """
    Read from *istream* and do not return until *size* `bytes` have been read
    unless EOF has been reached.

    Return all the data read so far. The length of the returned data may still
    be less than *size* in case EOF has been reached.

    *nonblocking_reads_delay* specifies the number of seconds (float) to wait
    before trying to read from *istream* again in case `BlockingIOError` has
    been raised during previous call.
    """
    assert size is None or (isinstance(size, int) and not isinstance(size, bool))

    if not size:
        return None

    output = []
    len_read = 0

    def _join():
        glue = '' if isinstance(output[0], str) else b''
        return glue.join(output)

    while True:
        try:
            chunk = istream.read(-1 if size < 0 else size - len_read)

            if size < 0:
                return chunk
            elif not chunk:
                if not output:
                    return chunk
                else:
                    return _join()
            else:
                assert size > 0
                output.append(chunk)
                len_read += len(chunk)
                if len_read >= size:
                    return _join()
        except BlockingIOError:
            time.sleep(nonblocking_reads_delay)
