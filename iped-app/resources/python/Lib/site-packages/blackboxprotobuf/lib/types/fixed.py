""" Functions for encoding and decoding fixed size structs """
import struct

# Generic functions for encoding/decoding structs based on the "struct" format
def encode_struct(fmt, value):
    """Generic method for encoding arbitrary python "struct" values"""
    return struct.pack(fmt, value)

def decode_struct(fmt, buf, pos):
    """Generic method for decoding arbitrary python "struct" values"""
    new_pos = pos + struct.calcsize(fmt)
    return struct.unpack(fmt, buf[pos:new_pos])[0], new_pos

_fixed32_fmt = '<I'
def encode_fixed32(value):
    """Encode a single 32 bit fixed-size value"""
    return encode_struct(_fixed32_fmt, value)

def decode_fixed32(buf, pos):
    """Decode a single 32 bit fixed-size value"""
    return decode_struct(_fixed32_fmt, buf, pos)

_sfixed32_fmt = '<i'
def encode_sfixed32(value):
    """Encode a single signed 32 bit fixed-size value"""
    return encode_struct(_sfixed32_fmt, value)

def decode_sfixed32(buf, pos):
    """Decode a single signed 32 bit fixed-size value"""
    return decode_struct(_sfixed32_fmt, buf, pos)

_float_fmt = '<f'
def encode_float(value):
    """Encode a single 32 bit floating point value"""
    return encode_struct(_float_fmt, value)

def decode_float(buf, pos):
    """Decode a single 32 bit floating point value"""
    return decode_struct(_float_fmt, buf, pos)

_fixed64_fmt = '<Q'
def encode_fixed64(value):
    """Encode a single 64 bit fixed-size value"""
    return encode_struct(_fixed64_fmt, value)

def decode_fixed64(buf, pos):
    """Decode a single 64 bit fixed-size value"""
    return decode_struct(_fixed64_fmt, buf, pos)

_sfixed64_fmt = '<q'
def encode_sfixed64(value):
    """Encode a single signed 64 bit fixed-size value"""
    return encode_struct(_sfixed64_fmt, value)

def decode_sfixed64(buf, pos):
    """Decode a single signed 64 bit fixed-size value"""
    return decode_struct(_sfixed64_fmt, buf, pos)

_double_fmt = '<d'
def encode_double(value):
    """Encode a single 64 bit floating point value"""
    return encode_struct(_double_fmt, value)

def decode_double(buf, pos):
    """Decode a single 64 bit floating point value"""
    return decode_struct(_double_fmt, buf, pos)
