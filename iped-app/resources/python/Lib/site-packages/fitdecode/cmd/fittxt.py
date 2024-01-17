#!/usr/bin/env python3
# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import argparse
from collections import OrderedDict
import datetime
import decimal
import os.path
import re
import sys
import traceback

import fitdecode

echo = None


def txt_encode(obj):
    """
    Convert a given *obj* to either a *str* or *PrintableObject* depending on
    which is the most suitable
    """

    if isinstance(obj, PrintableObject):
        return obj

    if isinstance(obj, str):
        return '[' + obj + ']'

    if obj is None:
        return '<none>'

    if isinstance(obj, bool):
        return 'yes' if obj else 'no'

    if isinstance(obj, (int, float, decimal.Decimal)):
        return str(obj)

    if isinstance(obj, datetime.time):
        return obj.isoformat()

    if isinstance(obj, datetime.datetime):
        return obj.isoformat()

    if isinstance(obj, fitdecode.FitChunk):
        return PrintableObject(
            index=obj.index,
            offset=obj.offset,
            size=len(obj.bytes))

    if isinstance(obj, fitdecode.types.FieldDefinition):
        return PrintableObject(
            name=obj.name,
            def_num=obj.def_num,
            type_name=obj.type.name,
            base_type_name=obj.base_type.name,
            size=obj.size)

    if isinstance(obj, fitdecode.types.DevFieldDefinition):
        return PrintableObject(
            name=obj.name,
            dev_data_index=obj.dev_data_index,
            def_num=obj.def_num,
            type_name=obj.type.name,
            size=obj.size)

    if isinstance(obj, fitdecode.types.FieldData):
        return PrintableObject(
            name=obj.name,
            value=obj.value,
            units=obj.units if obj.units else '',
            def_num=obj.def_num,
            raw_value=obj.raw_value)

    if isinstance(obj, fitdecode.FitHeader):
        crc = obj.crc if obj.crc else 0
        return PrintableObject(
            _label=f'chunk#{obj.chunk.index} - fit_header',
            header_size=obj.header_size,
            proto_ver='(' + ', '.join([str(v) for v in obj.proto_ver]) + ')',
            profile_ver='(' + ', '.join([str(v) for v in obj.profile_ver]) + ')',
            body_size=obj.body_size,
            crc=f'{crc:#06x}',
            crc_matched=obj.crc_matched,
            chunk=obj.chunk)

    if isinstance(obj, fitdecode.FitCRC):
        return PrintableObject(
            _label=f'chunk#{obj.chunk.index} - fit_crc',
            crc=f'{obj.crc:#06x}',
            matched=obj.matched,
            chunk=obj.chunk)

    if isinstance(obj, fitdecode.FitDefinitionMessage):
        return PrintableObject(
            _label=(
                f'chunk#{obj.chunk.index} - fit_definition - {obj.name} '
                f'(loc#{obj.local_mesg_num} glob#{obj.global_mesg_num})'),
            chunk=obj.chunk,
            header=PrintableObject(
                local_mesg_num=obj.local_mesg_num,
                time_offset=obj.time_offset,
                is_developer_data=obj.is_developer_data),
            global_mesg_num=obj.global_mesg_num,
            endian=obj.endian,
            field_defs=obj.field_defs,
            dev_field_defs=obj.dev_field_defs)

    if isinstance(obj, fitdecode.FitDataMessage):
        return PrintableObject(
            _label=(
                f'chunk#{obj.chunk.index} - fit_data - {obj.name} '
                f'(loc#{obj.local_mesg_num} glob#{obj.global_mesg_num})'),
            chunk=obj.chunk,
            header=PrintableObject(
                local_mesg_num=obj.local_mesg_num,
                time_offset=obj.time_offset,
                is_developer_data=obj.is_developer_data),
            global_mesg_num=obj.global_mesg_num,
            fields=obj.fields)

    if __debug__:
        print(type(obj))
        assert 0

    return repr(obj)


class PrintableObject:
    __slots__ = (
        '_label', '_dict', '_max_key_length',
        '_pad', '_key_prefix', '_key_suffix')

    def __init__(self, *, _label=None, _pad=' ', _key_prefix='',
                 _key_suffix='  ', **props):
        self._label = _label
        self._dict = OrderedDict()
        self._max_key_length = 0
        self._pad = _pad
        self._key_prefix = _key_prefix
        self._key_suffix = _key_suffix

        for key, value in props.items():
            # to avoid potential collision with PrintableObject members
            # (see __setattr__)
            assert key[0] != '_'

            self._dict[key] = value
            if len(key) > self._max_key_length:
                self._max_key_length = len(key)

    def __iter__(self):
        for key, value in self._dict.items():
            name = self._key_prefix
            name += key.ljust(self._max_key_length, self._pad)
            name += self._key_suffix

            yield name, value

    def __getattr__(self, name):
        try:
            return self._dict[name]
        except KeyError:
            raise AttributeError

    def __setattr__(self, name, value):
        if name[0] == '_':
            super().__setattr__(name, value)
        elif name not in self._dict:
            raise AttributeError
        else:
            self._dict[name] = value


def global_stats(frames, options):
    if options.filter:
        filter_str = []
        for msg_num, include in options.filter.items():
            include = '-' if not include else '+'
            msg_name = fitdecode.utils.get_mesg_type(msg_num).name
            filter_str.append(f'{include}{msg_name}#{msg_num}')
        filter_str = '[' + ', '.join(filter_str) + ']'
    else:
        filter_str = '[]'

    stats = PrintableObject(
        _label='TXT',
        name=os.path.basename(options.infile.name),
        filter=filter_str,
        frames=len(frames),
        size=0,
        missing_headers=0,
        fit_files=[])

    stats_got_header = False
    for frame in frames:
        stats.size += len(frame.chunk.bytes)

        if isinstance(frame, fitdecode.FitHeader):
            stats_got_header = True
            stats.fit_files.append(PrintableObject(
                definition_messages=0,
                data_messages=0,
                has_footer=False,
                header_crc_matched=frame.crc_matched,
                footer_crc_matched=False))
            continue

        if not stats_got_header:
            stats.missing_headers += 1
            continue

        curr_file = stats.fit_files[-1]

        if isinstance(frame, fitdecode.FitCRC):
            stats_got_header = False
            curr_file.has_footer = True
            curr_file.footer_crc_matched = frame.matched

        elif isinstance(frame, fitdecode.FitDefinitionMessage):
            curr_file.definition_messages += 1

        elif isinstance(frame, fitdecode.FitDataMessage):
            curr_file.data_messages += 1

    if options.strip:
        stats.fit_files = len(stats.fit_files)

    return stats


def txt_print(obj, *, indent='\t', level=0):

    def _recurse(subobj, sublevel=level):
        txt_print(subobj, indent=indent, level=sublevel)

    def _p(*values, end='\n'):
        echo(indent * level, *values, sep='', end=end)

    if isinstance(obj, str):
        _p(obj)

    elif isinstance(obj, (tuple, list)):
        # first pass to check if we can keep this list on a single line
        one_line = True
        for value in obj:
            if value is not None and not isinstance(
                    value, (bool, int, float, decimal.Decimal)):
                one_line = False
                break

        if one_line:
            _p('[' + ', '.join([txt_encode(v) for v in obj]) + ']')
        else:
            first = True
            for value in obj:
                if first:
                    first = False
                else:
                    _p('-')
                _recurse(value)

    elif isinstance(obj, PrintableObject):
        if obj._label:
            _p(obj._label)
            obj._label = None
            _recurse(obj, sublevel=level + 1)
        else:
            for key, value in obj:
                if isinstance(value, str):
                    _p(key, value)
                elif isinstance(value, (tuple, list)):
                    _p(f'{key.rstrip()} ({len(value)})')
                    _recurse(value, sublevel=level + 1)
                else:
                    value = txt_encode(value)
                    if isinstance(value, str):
                        _p(key, value)
                    else:
                        _p(key.rstrip())
                        _recurse(value, sublevel=level + 1)

    else:
        _recurse(txt_encode(obj))


def parse_filter_args(arg_parser, filter_opt):
    FILTER_DESC = re.compile(r'^\s*([\+\-]?)\s*([^\s]+)\s*$', re.A)

    if not filter_opt:
        return filter_opt, None

    filtr = {}  # {msg_num: bool_include}
    default_include_policy = False

    for desc in filter_opt:
        msg = None
        rem = FILTER_DESC.fullmatch(desc)
        if rem:
            include = False if rem[1] and rem[1] == '-' else True
            msg = rem[2].lower()

            if not include:
                default_include_policy = True

            try:
                msg = fitdecode.utils.get_mesg_num(msg)
            except ValueError:
                try:
                    msg = int(msg_name, base=0)
                except ValueError:
                    msg = None

        if msg is None:
            arg_parser.error(f'malformed filter: "{desc}"')
            sys.exit(1)

        filtr[msg] = include

    return filtr, default_include_policy


def parse_args(args=None):
    parser = argparse.ArgumentParser(
        description='Dump a FIT file to TXT format that ease debugging',
        epilog=f'fitdecode version {fitdecode.__version__}',
        allow_abbrev=False)

    parser.add_argument(
        '--output', '-o', type=argparse.FileType(mode='wt', encoding='utf-8'),
        default='-',
        help='File to output data into (defaults to stdout)')

    parser.add_argument(
        '--nocrc', action='store_const',
        const=fitdecode.CrcCheck.DISABLED,
        default=fitdecode.CrcCheck.WARN,
        help='Some devices seem to write invalid CRC\'s, ignore these')

    parser.add_argument(
        '--nodef', action='store_true',
        help='Do not output FIT local message definitions')

    parser.add_argument(
        '--nounk', action='store_true',
        help='Do not output unknown FIT messages (e.g. "unknown_140")')

    parser.add_argument(
        '--strip', action='store_true',
        help='Do not output the extended global stats in header')

    parser.add_argument(
        '--filter', '-f', action='append',
        help=(
            'Message name(s) (or global numbers) to filter-in or out, '
            'depending on sign prefix.  Examples: "-record" to exclude record '
            'messages; "+file_id" or "file_id" to include file_id messages.'))

    parser.add_argument(
        'infile', metavar='FITFILE', type=argparse.FileType(mode='rb'),
        help='Input .FIT file (use - for stdin)')

    options = parser.parse_args(args)
    options.filter, options.default_filter = \
        parse_filter_args(parser, options.filter)

    return options


def main(args=None):
    options = parse_args(args)

    def _echo(*objects, sep=' ', end='\n', file=options.output, flush=False):
        print(*objects, sep=sep, end=end, file=file, flush=flush)

    def _echo_separator():
        _echo('')
        _echo('*' * 80)
        _echo('')
        _echo('')

    global echo
    echo = _echo

    # fully parse input file and filter out the unwanted messages
    frames = []
    exception_msg = None
    try:
        with fitdecode.FitReader(
                options.infile,
                processor=fitdecode.StandardUnitsDataProcessor(),
                check_crc=options.nocrc,
                keep_raw_chunks=True) as fit:
            for frame in fit:
                if (options.nodef and
                        frame.frame_type == fitdecode.FIT_FRAME_DEFINITION):
                    continue

                if (options.nounk and
                        frame.frame_type in (
                            fitdecode.FIT_FRAME_DEFINITION,
                            fitdecode.FIT_FRAME_DATA) and
                        frame.mesg_type is None):
                    continue

                if (options.filter and
                        frame.frame_type in (
                            fitdecode.FIT_FRAME_DEFINITION,
                            fitdecode.FIT_FRAME_DATA)):
                    try:
                        include = options.filter[frame.global_mesg_num]
                    except KeyError:
                        include = options.default_filter

                    if not include:
                        continue

                frames.append(frame)
    except Exception:
        print(
            'WARNING: error(s) occurred while parsing FIT file. '
            'See output file for more info.',
            file=sys.stderr)
        exception_msg = traceback.format_exc()

    # print some statistics as a header
    if not exception_msg:
        txt_print(global_stats(frames, options))
        echo('')
    else:
        echo('ERROR OCCURRED WHILE PARSING', options.infile.name)
        echo('')
        echo(exception_msg)
        echo('')

    # pretty-print the file
    had_frames = False
    for frame in frames:
        if had_frames and isinstance(frame, fitdecode.FitHeader):
            _echo_separator()
        had_frames = True
        txt_print(frame)
        echo('')

    return 0


if __name__ == '__main__':
    sys.exit(main())
