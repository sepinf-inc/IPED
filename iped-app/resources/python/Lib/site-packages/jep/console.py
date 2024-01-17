#
# Copyright (c) 2004-2021 JEP AUTHORS.
#
# This file is licensed under the the zlib/libpng License.
#
# This software is provided 'as-is', without any express or implied
# warranty. In no event will the authors be held liable for any
# damages arising from the use of this software.
# 
# Permission is granted to anyone to use this software for any
# purpose, including commercial applications, and to alter it and
# redistribute it freely, subject to the following restrictions:
# 
#     1. The origin of this software must not be misrepresented; you
#     must not claim that you wrote the original software. If you use
#     this software in a product, an acknowledgment in the product
#     documentation would be appreciated but is not required.
# 
#     2. Altered source versions must be plainly marked as such, and
#     must not be misrepresented as being the original software.
# 
#     3. This notice may not be removed or altered from any source
#     distribution.
#

from __future__ import print_function
import traceback
import os
import sys

history_file = None

has_readline = False

try:
    import readline
    has_readline = True
except ImportError:
    try:
        import pyreadline as readline
        has_readline = True
    except ImportError:
        msg = """
              No readline available. History will not be available.
              """
        if os.name == "posix":
            msg += """
                   You may want to set the LD_PRELOAD environment variable, see the
                   README file for details.

                   i.e.: export LD_PRELOAD=/usr/lib/libpython2.7.so.1.0
                   """
        elif os.name == "nt":
            msg += """
                   For Windows use pyreadline and get it from the official git
                   repo on github:
                   https://github.com/pyreadline/pyreadline

                   Do NOT use the version on pypi.python.org, and therefore
                   Do NOT use the version installed by pip.  It is out of date
                   and doesn't work with Jep!
                   """
        print(msg)
except OSError as e:
    if hasattr(e, 'winerror'):
        print("Windows error importing readline: " + str(e))
        print("Please try using the latest pyreadline from https://github.com/pyreadline/pyreadline")
    else:
        print("Error importing readline: " + str(e))

if has_readline:
    try:
        import rlcompleter
        readline.set_completer(rlcompleter.Completer(locals()).complete)
        readline.parse_and_bind("tab: complete")
    except:
        pass
    try:
        history_file = os.path.join(os.path.expanduser('~'), '.jep')
        if not os.path.exists(history_file):
            readline.write_history_file(history_file)
        else:
            readline.read_history_file(history_file)
    except IOError as err:
        pass


PS1 = ">>> "
PS2 = "... "

evalLines = []

def jepeval(line):
    global evalLines
    if not line:
        if evalLines:
            code = "\n".join(evalLines)
            evalLines = None
            exec(compile(code, '<stdin>', 'single'), globals(), globals())
        return True
    elif not evalLines:
        try:
            exec(compile(line, '<stdin>', 'single'), globals(), globals())
            return True
        except SyntaxError as err:
            evalLines = [line]
            return False
    else:
        evalLines.append(line)
        return False

def prompt(jep):
    try:
        line = None
        while True:
            ran = True
            try:
                ran = jepeval(line)
            except Exception as err:
                printedErr = False
                try:
                    if len(err.args):
                        if 'printStackTrace' in dir(err.args[0]):
                            err.args[0].printStackTrace()
                            printedErr = True
                except Exception as exc:
                    print("Error printing stacktrace:", str(exc))
                finally:
                    if not printedErr:
                        print(str(err))

            try:
                if ran:
                    line = input(PS1)
                else:
                    line = input(PS2)
            except:
                break

    finally:
        if has_readline:
            try:
                readline.write_history_file(history_file)
            except IOError as err:
                pass
