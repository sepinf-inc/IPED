#!/usr/bin/env python3
# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

import argparse
from collections import OrderedDict
import datetime
import json
import types
import re
import sys
import traceback

import fitdecode


class RecordJSONEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, types.GeneratorType):
            return list(obj)

        if isinstance(obj, datetime.time):
            return obj.isoformat()

        if isinstance(obj, datetime.datetime):
            return obj.isoformat()

        if isinstance(obj, fitdecode.FitChunk):
            return OrderedDict((
                ('index', obj.index),
                ('offset', obj.offset),
                ('size', len(obj.bytes))))

        if isinstance(obj, fitdecode.types.FieldDefinition):
            return OrderedDict((
                ('name', obj.name),
                ('def_num', obj.def_num),
                ('type_name', obj.type.name),
                ('base_type_name', obj.base_type.name),
                ('size', obj.size)))

        if isinstance(obj, fitdecode.types.DevFieldDefinition):
            return OrderedDict((
                ('name', obj.name),
                ('dev_data_index', obj.dev_data_index),
                ('def_num', obj.def_num),
                ('type_name', obj.type.name),
                ('size', obj.size)))

        if isinstance(obj, fitdecode.types.FieldData):
            return OrderedDict((
                ('name', obj.name),
                ('value', obj.value),
                ('units', obj.units if obj.units else ''),
                ('def_num', obj.def_num),
                ('raw_value', obj.raw_value)))

        if isinstance(obj, fitdecode.FitHeader):
            crc = obj.crc if obj.crc else 0
            return OrderedDict((
                ('frame_type', 'header'),
                ('header_size', obj.header_size),
                ('proto_ver', obj.proto_ver),
                ('profile_ver', obj.profile_ver),
                ('body_size', obj.body_size),
                ('crc', f'{crc:#06x}'),
                ('crc_matched', obj.crc_matched),
                ('chunk', obj.chunk)))

        if isinstance(obj, fitdecode.FitCRC):
            return OrderedDict((
                ('frame_type', 'crc'),
                ('crc', f'{obj.crc:#06x}'),
                ('matched', obj.matched),
                ('chunk', obj.chunk)))

        if isinstance(obj, fitdecode.FitDefinitionMessage):
            return OrderedDict((
                ('frame_type', 'definition_message'),
                ('name', obj.name),
                ('header', OrderedDict((
                    ('local_mesg_num', obj.local_mesg_num),
                    ('time_offset', obj.time_offset),
                    ('is_developer_data', obj.is_developer_data)))),
                ('global_mesg_num', obj.global_mesg_num),
                ('endian', obj.endian),
                ('field_defs', obj.field_defs),
                ('dev_field_defs', obj.dev_field_defs),
                ('chunk', obj.chunk)))

        if isinstance(obj, fitdecode.FitDataMessage):
            return OrderedDict((
                ('frame_type', 'data_message'),
                ('name', obj.name),
                ('header', OrderedDict((
                    ('local_mesg_num', obj.local_mesg_num),
                    ('time_offset', obj.time_offset),
                    ('is_developer_data', obj.is_developer_data)))),
                ('fields', obj.fields),
                ('chunk', obj.chunk)))

        # fall back to original to raise a TypeError
        return super().default(obj)


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
        description='Dump a FIT file to JSON format',
        epilog=f'fitdecode version {fitdecode.__version__}',
        allow_abbrev=False)

    parser.add_argument(
        '--output', '-o', type=argparse.FileType(mode='wt', encoding='utf-8'),
        default='-',
        help='File to output data into (defaults to stdout)')

    parser.add_argument(
        '--pretty', action='store_true',
        help='Prettify JSON output.')

    parser.add_argument(
        '--nocrc', action='store_const',
        const=fitdecode.CrcCheck.DISABLED,
        default=fitdecode.CrcCheck.WARN,
        help='Some devices seem to write invalid CRC\'s, ignore these.')

    parser.add_argument(
        '--nodef', action='store_true',
        help='Do not output FIT local message definitions.')

    parser.add_argument(
        '--nounk', action='store_true',
        help='Do not output unknown FIT messages (e.g. "unknown_140")')

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

    frames = []

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
            'WARNING: the following error occurred while parsing FIT file. '
            'Output file might be incomplete or corrupted.',
            file=sys.stderr)
        print('', file=sys.stderr)
        traceback.print_exc()

    indent = '\t' if options.pretty else None
    json.dump(frames, fp=options.output, cls=RecordJSONEncoder, indent=indent)

    return 0


if __name__ == '__main__':
    sys.exit(main())
