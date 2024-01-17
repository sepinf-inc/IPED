# Copyright (c) Jean-Charles Lefebvre
# SPDX-License-Identifier: MIT

from sys import version_info
if version_info < (3, 6):
    raise ImportError('fitdecode requires Python 3.6+')
del version_info

from .__meta__ import (
    __version__, version_info,
    __title__, __fancy_title__, __description__, __url__,
    __license__, __author__, __copyright__)

from .exceptions import *
from .records import *
from .reader import *
from .processors import *

from . import types
from . import profile
from . import utils
from . import processors
from . import reader
