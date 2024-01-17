"""Classes for encoding and decoding varint types"""
from google.protobuf.internal import wire_format, encoder, decoder

def encode_uvarint(value):
    """Encode a long or int into a bytearray."""
    if not isinstance(value, int):
        raise TypeError("UVarInt encode requires a int type")

    output = bytearray()
    encoder._EncodeVarint(output.extend, value)
    return output

def decode_uvarint(buf, pos):
    """Decode bytearray into a long."""
    # Convert buffer to string
    #buf = buf.decode('latin')
    value, pos = decoder._DecodeVarint(buf, pos)
    return (value, pos)


def encode_varint(value):
    """Encode a long or int into a bytearray."""
    if not isinstance(value, int):
        raise TypeError("VarInt encode requires an int type")

    output = bytearray()
    encoder._EncodeSignedVarint(output.extend, value)
    return output

def decode_varint(buf, pos):
    """Decode bytearray into a long."""
    # Convert buffer to string
    #buf = buf.decode('latin')
    value, pos = decoder._DecodeSignedVarint(buf, pos)
    return (value, pos)


def encode_svarint(value):
    """Zigzag encode the potentially signed value prior to encoding"""
    # zigzag encode value
    return encode_uvarint(wire_format.ZigZagEncode(value))

def decode_svarint(buf, pos):
    """Decode bytearray into a long."""
    output, pos = decode_uvarint(buf, pos)
    # zigzag encode value
    return wire_format.ZigZagDecode(output), pos
