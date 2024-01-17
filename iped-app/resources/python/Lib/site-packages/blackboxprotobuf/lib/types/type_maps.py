"""Contains various maps for protobuf types, including encoding/decoding
   functions, wiretypes and default types
"""
from google.protobuf.internal import wire_format
from blackboxprotobuf.lib.types import varint, fixed, length_delim

encoders = {
    'uint': varint.encode_uvarint,
    'int': varint.encode_varint,
    'sint': varint.encode_svarint,
    'fixed32': fixed.encode_fixed32,
    'sfixed32': fixed.encode_sfixed32,
    'float': fixed.encode_float,
    'fixed64': fixed.encode_fixed64,
    'sfixed64': fixed.encode_sfixed64,
    'double': fixed.encode_double,
    'bytes': length_delim.encode_bytes,
    'packed_uint': length_delim.generate_packed_encoder(varint.encode_uvarint),
    'packed_int': length_delim.generate_packed_encoder(varint.encode_varint),
    'packed_sint': length_delim.generate_packed_encoder(varint.encode_svarint),
    'packed_fixed32': length_delim.generate_packed_encoder(fixed.encode_fixed32),
    'packed_sfixed32': length_delim.generate_packed_encoder(fixed.encode_sfixed32),
    'packed_float': length_delim.generate_packed_encoder(fixed.encode_float),
    'packed_fixed64': length_delim.generate_packed_encoder(fixed.encode_fixed64),
    'packed_sfixed64': length_delim.generate_packed_encoder(fixed.encode_sfixed64),
    'packed_double': length_delim.generate_packed_encoder(fixed.encode_double)
}

decoders = {
    'uint': varint.decode_uvarint,
    'int': varint.decode_varint,
    'sint': varint.decode_svarint,
    'fixed32': fixed.decode_fixed32,
    'sfixed32': fixed.decode_sfixed32,
    'float': fixed.decode_float,
    'fixed64': fixed.decode_fixed64,
    'sfixed64': fixed.decode_sfixed64,
    'double': fixed.decode_double,
    'bytes': length_delim.decode_bytes,
    'str': length_delim.decode_str,
    'packed_uint': length_delim.generate_packed_decoder(varint.decode_uvarint),
    'packed_int': length_delim.generate_packed_decoder(varint.decode_varint),
    'packed_sint': length_delim.generate_packed_decoder(varint.decode_svarint),
    'packed_fixed32': length_delim.generate_packed_decoder(fixed.decode_fixed32),
    'packed_sfixed32': length_delim.generate_packed_decoder(fixed.decode_sfixed32),
    'packed_float': length_delim.generate_packed_decoder(fixed.decode_float),
    'packed_fixed64': length_delim.generate_packed_decoder(fixed.decode_fixed64),
    'packed_sfixed64': length_delim.generate_packed_decoder(fixed.decode_sfixed64),
    'packed_double': length_delim.generate_packed_decoder(fixed.decode_double)
}

wiretypes = {
    'uint': wire_format.WIRETYPE_VARINT,
    'int': wire_format.WIRETYPE_VARINT,
    'sint': wire_format.WIRETYPE_VARINT,
    'fixed32': wire_format.WIRETYPE_FIXED32,
    'sfixed32': wire_format.WIRETYPE_FIXED32,
    'float': wire_format.WIRETYPE_FIXED32,
    'fixed64': wire_format.WIRETYPE_FIXED64,
    'sfixed64': wire_format.WIRETYPE_FIXED64,
    'double': wire_format.WIRETYPE_FIXED64,
    'bytes':  wire_format.WIRETYPE_LENGTH_DELIMITED,
    'str':  wire_format.WIRETYPE_LENGTH_DELIMITED,
    'message':  wire_format.WIRETYPE_LENGTH_DELIMITED,
    'group': wire_format.WIRETYPE_START_GROUP,
    'packed_uint': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_int': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_sint': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_fixed32': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_sfixed32': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_float': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_fixed64': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_sfixed64': wire_format.WIRETYPE_LENGTH_DELIMITED,
    'packed_double': wire_format.WIRETYPE_LENGTH_DELIMITED
}

wire_type_defaults = {
    wire_format.WIRETYPE_VARINT: 'int',
    wire_format.WIRETYPE_FIXED32: 'fixed32',
    wire_format.WIRETYPE_FIXED64: 'fixed64',
    wire_format.WIRETYPE_LENGTH_DELIMITED: None,
    wire_format.WIRETYPE_START_GROUP: 'group',
    wire_format.WIRETYPE_END_GROUP: None
    }
