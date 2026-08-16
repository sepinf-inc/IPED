"""
IPED patch: makes LEAPP's Context state thread-local.

scripts.context.Context stores per-plugin-run state (files_found, seeker,
report_folder, ...) in CLASS attributes. IPED runs many workers over a single
shared CPython interpreter (Jep SharedInterpreter), so that state would be
shared and concurrent plugin runs would overwrite each other.

Each IPED worker interacts with Python from its own thread, so thread
identity == worker identity: moving the state to threading.local() isolates
each worker.

The methods are patched IN PLACE (class identity is preserved), so bindings
created earlier via "from scripts.context import Context" (e.g. inside
ilapfuncs or plugin modules) also see the patched behavior.

One-time global state (_output_params, _data_folder) is left shared on
purpose. get_source_file_path and get_relative_path need no patch: they only
touch state through the patched methods or through the shared globals.

Everything is defined inside a single function so the patched methods keep
their dependencies as CLOSURES: after the installer global is deleted below,
no name is left behind in the interpreter namespace (which is shared with
PythonParser/PythonTask scripts).
"""


def _iped_leapp_patch_context():
    import threading
    from os.path import basename

    from scripts.context import Context

    tls = threading.local()

    per_run_attrs = ('report_folder', 'seeker', 'artifact_info', 'module_name',
                     'module_file_path', 'artifact_name', 'files_found')

    def state():
        st = getattr(tls, 'state', None)
        if st is None:
            st = dict.fromkeys(per_run_attrs)
            st['files_found'] = []
            st['filename_lookup_map'] = {}
            tls.state = st
        return st

    def make_setter(name):
        def _set(value):
            st = state()
            st[name] = value
            if name == 'files_found':
                # the lookup map is derived from files_found: invalidate it
                st['filename_lookup_map'] = {}
        return _set

    def make_getter(name):
        def _get():
            value = state()[name]
            if not value:
                # same behavior and message as the original Context getters
                raise ValueError('Context not set. This function should be' +
                                 ' called from within an artifact.')
            return value
        return _get

    def get_filename_lookup_map():
        st = state()
        if not st['filename_lookup_map']:
            if not st['files_found']:
                raise ValueError(
                    'Cannot build lookup map: _files_found is not set.')
            lookup = {}
            for path in st['files_found']:
                lookup.setdefault(basename(path), []).append(path)
            st['filename_lookup_map'] = lookup
        return st['filename_lookup_map']

    def clear():
        # re-initialized lazily on next access
        tls.state = None

    for name in per_run_attrs:
        setattr(Context, 'set_' + name, staticmethod(make_setter(name)))
        setattr(Context, 'get_' + name, staticmethod(make_getter(name)))

    Context.get_filename_lookup_map = staticmethod(get_filename_lookup_map)
    Context._build_lookup_map = staticmethod(get_filename_lookup_map)
    Context.clear = staticmethod(clear)


_iped_leapp_patch_context()
del _iped_leapp_patch_context
