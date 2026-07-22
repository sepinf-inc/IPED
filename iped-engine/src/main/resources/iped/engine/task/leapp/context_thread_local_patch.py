"""
IPED patch: makes LEAPP's Context state thread-local.

scripts.context.Context stores per-plugin-run state (files_found, seeker,
report_folder, ...) in CLASS attributes. IPED runs many workers over a single
shared CPython interpreter (Jep SharedInterpreter), so that state would be
shared and concurrent plugin runs would overwrite each other.

Each IPED worker interacts with Python from its own dedicated thread, so
thread identity == worker identity: moving the state to threading.local()
isolates each worker.

The methods are patched IN PLACE (class identity is preserved), so bindings
created earlier via "from scripts.context import Context" (e.g. inside
ilapfuncs or plugin modules) also see the patched behavior.

One-time global state (_output_params, _data_folder) is left shared on
purpose. get_source_file_path and get_relative_path need no patch: they only
touch state through the patched methods or through the shared globals.
"""

import threading
from os.path import basename

from scripts.context import Context

_tls = threading.local()

_PER_RUN_ATTRS = ('report_folder', 'seeker', 'artifact_info', 'module_name',
                  'module_file_path', 'artifact_name', 'files_found')


def _state():
    st = getattr(_tls, 'state', None)
    if st is None:
        st = dict.fromkeys(_PER_RUN_ATTRS)
        st['files_found'] = []
        st['filename_lookup_map'] = {}
        _tls.state = st
    return st


def _make_setter(name):
    def _set(value):
        st = _state()
        st[name] = value
        if name == 'files_found':
            # the lookup map is derived from files_found: invalidate it
            st['filename_lookup_map'] = {}
    return _set


def _make_getter(name):
    def _get():
        value = _state()[name]
        if not value:
            # same behavior and message as the original Context getters
            raise ValueError('Context not set. This function should be' +
                             ' called from within an artifact.')
        return value
    return _get


def _get_filename_lookup_map():
    st = _state()
    if not st['filename_lookup_map']:
        if not st['files_found']:
            raise ValueError(
                'Cannot build lookup map: _files_found is not set.')
        lookup = {}
        for path in st['files_found']:
            lookup.setdefault(basename(path), []).append(path)
        st['filename_lookup_map'] = lookup
    return st['filename_lookup_map']


def _clear():
    # re-initialized lazily on next access
    _tls.state = None


for _name in _PER_RUN_ATTRS:
    setattr(Context, 'set_' + _name, staticmethod(_make_setter(_name)))
    setattr(Context, 'get_' + _name, staticmethod(_make_getter(_name)))

Context.get_filename_lookup_map = staticmethod(_get_filename_lookup_map)
Context._build_lookup_map = staticmethod(_get_filename_lookup_map)
Context.clear = staticmethod(_clear)
