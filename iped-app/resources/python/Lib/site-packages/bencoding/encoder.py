from collections import OrderedDict
from .tokens import *


class EncoderError(Exception):
    pass


class Encoder:
    """Use to encode data(str, bytes, int, list, dict)."""

    def __init__(self, data):
        self.data = data

    def encode(self):
        """Return bytes of self data."""
        return self._encode(self.data)

    def _encode(self, data):
        if isinstance(data, str):
            return self._encode_string(data)
        elif isinstance(data, bytes):
            return self._encode_bytes(data)
        elif isinstance(data, int):
            return self._encode_int(data)
        elif isinstance(data, list):
            return self._encode_list(data)
        elif isinstance(data, (dict, OrderedDict)):
            return self._encode_dict(data)
        else:
            raise EncoderError('unsupported this type:{}'.format(type(data)))

    def _encode_string(self, data):
        return (str(len(data))).encode() + TOKEN_STRING_SEPARATOR + data.encode()

    def _encode_bytes(self, data):
        return (str(len(data))).encode() + TOKEN_STRING_SEPARATOR + data

    def _encode_int(self, data):
        return TOKEN_INTEGER + str(data).encode() + TOKEN_END

    def _encode_list(self, data):
        return TOKEN_LIST + b''.join([self._encode(item) for item in data]) + TOKEN_END

    def _encode_dict(self, data):
        return TOKEN_DICT + b''.join([self._encode(k) + self._encode(v) for k, v in data.items()]) + TOKEN_END
