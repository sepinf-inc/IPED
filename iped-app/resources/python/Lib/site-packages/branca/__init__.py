import branca.colormap as colormap
import branca.element as element

try:
    from ._version import __version__
except ImportError:
    __version__ = "unknown"


__all__ = [
    "colormap",
    "element",
]
