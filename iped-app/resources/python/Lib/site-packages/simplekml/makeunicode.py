"""
Obtained from http://python3porting.com/noconv.html enable unicode support
in python 2 and 3.
"""

import sys
if sys.version < '3':
    import codecs
    def u(x):
        return codecs.unicode_escape_decode(x)[0]
else:
    def u(x):
        return x
