from collections import OrderedDict
from .tokens import *


class DecoderError(Exception):
    pass


class Decoder:
    """Use to decode date(bytes)."""

    def __init__(self, data):
        self.data = data
        self._index = 0
        self._max_index = len(self.data) - 1

    def decode(self):
        """Return OrderedDict of self data"""
        c = self._peek()
        if c is None:
            raise DecoderError('unexpected eof')
        elif c == TOKEN_INTEGER:
            self._consume()
            return self._decode_int()
        elif c == TOKEN_LIST:
            self._consume()
            return self._decode_list()
        elif c == TOKEN_DICT:
            self._consume()
            return self._decode_dict()
        elif c == TOKEN_END:
            return None
        elif c in b'0123456789':
            return self._decode_string()
        else:
            raise DecoderError('invalid token:{}'.format(c))

    def _peek(self):
        if self._index >= self._max_index:
            return None
        return self.data[self._index:self._index + 1]

    def _consume(self):
        self._index += 1

    def _read(self, length):
        if self._index + length > self._max_index:
            raise DecoderError('out of range')
        result = self.data[self._index:self._index + length]
        self._index += length
        return result

    def _read_until(self, token):
        try:
            lowest_index = self.data.index(token, self._index)
            result = self.data[self._index:lowest_index]
            self._index = lowest_index + 1
            return result
        except ValueError:
            raise DecoderError('unable to find token:{}'.format(token.decode()))

    def _decode_int(self):
        return int(self._read_until(TOKEN_END))

    def _decode_list(self):
        result = []
        while self.data[self._index:self._index + 1] != TOKEN_END:
            result.append(self.decode())
        self._consume()
        return result

    def _decode_dict(self):
        result = OrderedDict()
        while self.data[self._index:self._index + 1] != TOKEN_END:
            key = self.decode()
            value = self.decode()
            result[key] = value
        self._consume()
        return result

    def _decode_string(self):
        length = int(self._read_until(TOKEN_STRING_SEPARATOR))
        data = self._read(length)
        return data
