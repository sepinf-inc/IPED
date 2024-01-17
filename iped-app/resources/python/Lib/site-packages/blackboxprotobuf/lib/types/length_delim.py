"""Module for encoding and decoding length delimited fields"""
import copy
import sys

from google.protobuf.internal import wire_format, encoder, decoder

import blackboxprotobuf.lib.types
from blackboxprotobuf.lib.types import varint

def decode_guess(buf, pos):
    """Try to decode as an empty message first, then just do as bytes
       Returns the value + the type"""
    try:
        return decode_lendelim_message(buf, {}, pos), 'message'
    except Exception as exc:
        return decode_bytes(buf, pos), 'bytes'


def encode_bytes(value):
    """Encode varint length followed by the string"""
    if isinstance(value, str):
        value = bytearray(value, 'utf-8')
    else:
        value = bytearray(value)
    encoded_length = varint.encode_varint(len(value))
    return encoded_length + value

def decode_bytes(value, pos):
    """Decode varint for length and then the bytes"""
    length, pos = varint.decode_varint(value, pos)
    end = pos+length
    return value[pos:end], end

def decode_str(value, pos):
    """Decode varint for length and then the string"""
    length, pos = varint.decode_varint(value, pos)
    end = pos+length
    return value[pos:end].decode('utf-8', 'backslashreplace'), end


def encode_message(data, typedef, group=False):
    """Encode a Python dictionary representing a protobuf message
       data - Python dictionary mapping field numbers to values
       typedef - Type information including field number, field name and field type
       This will throw an exception if an unkown value is used as a key
    """
    output = bytearray()

    for field_number, value in data.items():
        # Get the field number convert it as necessary
        alt_field_number = None
        if isinstance(field_number, str):
            if '-' in field_number:
                field_number, alt_field_number = field_number.split('-')
            for number, info in typedef.items():
                if info['name'] == field_number and field_number != '':
                    field_number = number
                    break
        else:
            field_number = str(field_number)

        if field_number not in typedef:
            raise ValueError('Provided field name/number %s is not valid'
                             % (field_number))

        field_typedef = typedef[field_number]

        # Get encoder
        if 'type' not in field_typedef:
            raise ValueError('Field %s does not have a defined type' % field_number)

        field_type = field_typedef['type']

        field_encoder = None
        if field_type == 'message':
            innertypedef = None
            # Check for a defined message type
            if alt_field_number is not None:
                if alt_field_number not in field_typedef['alt_typedefs']:
                    raise ValueError(
                        'Provided alt field name/number %s is not valid for field_number %s'
                        % (alt_field_number, field_number))
                innertypedef = field_typedef['alt_typedefs'][alt_field_number]
            elif 'message_typedef' in field_typedef:
                # "Anonymous" inner message
                # Required to have a 'message_typedef'
                if 'message_typedef' not in field_typedef:
                    raise ValueError('Could not find type definition for message field: %s'
                                     % field_number)
                innertypedef = field_typedef['message_typedef']
            else:
                if field_typedef['message_type_name'] not in blackboxprotobuf.lib.known_messages:
                    raise ValueError('Message type (%s) has not been defined'
                                     % field_typedef['message_type_name'])
                innertypedef = field_typedef['message_type_name']

            field_encoder = lambda data: encode_lendelim_message(data, innertypedef)
        elif field_type == 'group':
            innertypedef = None
            # Check for a defined group type
            if 'group_typedef' not in field_typedef:
                raise ValueError('Could not find type definition for group field: %s'
                                 % field_number)
            innertypedef = field_typedef['group_typedef']

            field_encoder = lambda data: encode_group(data, innertypedef, field_number)
        else:
            if field_type not in blackboxprotobuf.lib.types.encoders:
                raise ValueError('Unknown type: %s' % field_type)
            field_encoder = blackboxprotobuf.lib.types.encoders[field_type]
            if field_encoder is None:
                raise ValueError('Encoder not implemented: %s' % field_type)


        # Encode the tag
        tag = encoder.TagBytes(int(field_number), blackboxprotobuf.lib.types.wiretypes[field_type])

        try:
            # Handle repeated values
            if isinstance(value, list) and not field_type.startswith('packed_'):
                for repeated in  value:
                    output += tag
                    output += field_encoder(repeated)
            else:
                output += tag
                output += field_encoder(value)
        except Exception as exc:
            raise ValueError(
                   'Error attempting to encode "%s" as %s: %s'
                   % (value, field_type, exc), sys.exc_info()[2])

    return output

def decode_message(buf, typedef=None, pos=0, end=None, group=False):
    """Decode a protobuf message with no length delimiter"""
    if end is None:
        end = len(buf)

    if typedef is None:
        typedef = {}
    else:
        # Don't want to accidentally modify the original
        typedef = copy.deepcopy(typedef)

    output = {}

    while pos < end:
        # Read in a field
        tag, pos = decoder._DecodeVarint(buf, pos)
        try:
            field_number, wire_type = wire_format.UnpackTag(tag)
        except Exception as exc:
            raise (ValueError,
                   'Could not read valid tag at pos %d. Ensure it is a valid protobuf message: %s'
                   % (pos-len(tag), exc), sys.exc_info()[2])

        # Convert to str
        field_number = str(field_number)
        orig_field_number = field_number

        field_typedef = None
        if field_number in typedef:
            field_typedef = typedef[field_number]
        else:
            field_typedef = {}
            field_typedef['type'] = blackboxprotobuf.lib.types.wire_type_defaults[wire_type]

        field_type = field_typedef['type']

        # If field_type is None, its either an unsupported wire type, length delim or group
        # length delim we have to try and decode first
        field_out = None
        if field_type is None:
            if wire_type == wire_format.WIRETYPE_LENGTH_DELIMITED:
                out, field_type = decode_guess(buf, pos)
                if field_type == 'message':
                    field_out, message_typedef, pos = out
                    field_typedef['message_typedef'] = message_typedef
                else:
                    field_out, pos = out
            elif  wire_type == wire_format.WIRETYPE_END_GROUP:
                # TODO Should probably match the field_number to START_GROUP
                if not group:
                    raise ValueError("Found END_GROUP before START_GROUP")
                # exit out
                return output, typedef, pos
            else:
                raise ValueError("Could not find default type for wiretype: %d" % wire_type)
        else:
            if field_type == 'message':
                #TODO probably big enough to factor out
                message_typedef = None
                # Check for a anonymous type
                if 'message_typedef' in field_typedef:
                    message_typedef = field_typedef['message_typedef']
                # Check for type defined by message type name
                elif 'message_type_name' in field_typedef:
                    message_typedef = blackboxprotobuf.lib.types.messages[
                        field_typedef['message_type_name']]

                try:
                    field_out, message_typedef, pos = decode_lendelim_message(
                        buf, message_typedef, pos)
                    # Save type definition
                    field_typedef['message_typedef'] = message_typedef
                except Exception as exc:
                    # If this is the root message just fail
                    if pos == 0:
                        raise exc

                if field_out is None and 'alt_typedefs' in field_typedef:
                    # check for an alternative type definition
                    for alt_field_number, alt_typedef in field_typedef['alt_typedefs'].items():
                        try:
                            field_out, message_typedef, pos = decode_lendelim_message(
                                buf, alt_typedef, pos)
                        except Exception as exc:
                            pass

                        if field_out is not None:
                            # Found working typedef
                            field_typedef['alt_typedefs'][alt_field_number] = message_typedef
                            field_number = field_number + "-" + alt_field_number
                            break

                if field_out is None:
                    # Still no typedef, try anonymous, and let the error propogate if it fails
                    field_out, message_typedef, pos = decode_lendelim_message(buf, {}, pos)
                    if 'alt_typedefs' in field_typedef:
                        # get the next higher alt field number
                        alt_field_number = str(
                            max(map(int, field_tyepdef['alt_typedefs'].keys()))
                            + 1)
                    else:
                        field_typedef['alt_typedefs'] = {}
                        alt_field_number = '1'

                    field_typedef['alt_typedefs'][alt_field_number] = message_typedef
                    field_number = field_number + "-" + alt_field_number
            elif field_type == 'group':
                group_typedef = None
                # Check for a anonymous type
                if 'group_typedef' in field_typedef:
                    group_typedef = field_typedef['group_typedef']
                field_out, group_typedef, pos = decode_group(buf, group_typedef, pos)
                # Save type definition
                field_typedef['group_typedef'] = group_typedef
            else:
                # Verify wiretype matches
                if blackboxprotobuf.lib.types.wiretypes[field_type] != wire_type:
                    raise ValueError("Invalid wiretype for field number %s. %s is not wiretype %s"
                                     % (field_number, field_type, wire_type))

                # Simple type, just look up the decoder
                field_out, pos = blackboxprotobuf.lib.types.decoders[field_type](buf, pos)
        field_typedef['type'] = field_type
        if 'name' not in field_typedef:
            field_typedef['name'] = ''

        field_key = field_number
        if '-' not in field_number  and 'name' in field_typedef and field_typedef['name'] != '':
            field_key = field_typedef['name']
        # Deal with repeats
        if field_key in output:
            if isinstance(field_out, list):
                if isinstance(output[field_number], list):
                    output[field_key] += field_out
                else:
                    output[field_key] = field_out.append(output[field_key])
            else:
                if isinstance(output[field_number], list):
                    output[field_key].append(field_out)
                else:
                    output[field_key] = [output[field_key], field_out]
        else:
            output[field_key] = field_out
            typedef[orig_field_number] = field_typedef
    if pos > end:
        raise decoder._DecodeError("Invalid Message Length")
    # Should never hit here as a group
    if group:
        raise ValueError("Got START_GROUP with no END_GROUP.")
    return output, typedef, pos

def encode_lendelim_message(data, typedef):
    """Encode the length before the message"""
    message_out = encode_message(data, typedef)
    length = varint.encode_varint(len(message_out))
    return length + message_out

def decode_lendelim_message(buf, typedef=None, pos=0):
    """Read in the length and use it as the end"""
    length, pos = varint.decode_varint(buf, pos)
    ret = decode_message(buf, typedef, pos, pos+length)
    return ret

# Not actually length delim, but we're hijacking the methods anyway
def encode_group(value, typedef, field_number):
    """Encode a protobuf group type"""
    # Message will take care of the start tag
    # Need to add the end_tag
    output = encode_message(value, typedef, group=True)
    end_tag = encoder.TagBytes(int(field_number), wire_format.WIRETYPE_END_GROUP)
    output.append(end_tag)
    return output

def decode_group(buf, typedef=None, pos=0, end=None):
    """Decode a protobuf group type"""
    return decode_message(buf, typedef, pos, end, group=True)

def generate_packed_encoder(wrapped_encoder):
    """Generate an encoder for a packed type from the base type encoder"""
    def length_wrapper(values):
        """Encode repeat values and prefix with the length"""
        output = bytearray()
        for value in values:
            output += wrapped_encoder(value)
        length = varint.encode_varint(len(output))
        return length + output
    return length_wrapper

def generate_packed_decoder(wrapped_decoder):
    """Generate an decoder for a packer type from a base type decoder"""
    def length_wrapper(buf, pos):
        """Decode repeat values prefixed with the length"""
        length, pos = varint.decode_varint(buf, pos)
        end = pos+length
        output = []
        while pos < end:
            value, pos = wrapped_decoder(buf, pos)
            output.append(value)
        if pos > end:
            raise decoder._DecodeError("Invalid Packed Field Length")
        return output, pos
    return length_wrapper
