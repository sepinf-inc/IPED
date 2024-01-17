from .encoder import Encoder
from .decoder import Decoder

__version__ = '0.2.6'


def bencode(data):
    """Return bytes of the bencoded data."""
    encoder = Encoder(data)
    return encoder.encode()


def bdecode(data):
    """Return OrderedDict of the bdecoded data."""
    decoder = Decoder(data)
    return decoder.decode()
