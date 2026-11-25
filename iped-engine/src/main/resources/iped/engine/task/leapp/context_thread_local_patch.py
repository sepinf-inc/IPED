"""
IPED patch: makes LEAPP's Context state thread-local.

scripts.context.Context stores per-plugin-run state (files_found, seeker,
report_folder, ...) in CLASS attributes. IPED runs many workers over a single
shared CPython interpreter (Jep SharedInterpreter), so that state would be
shared and concurrent plugin runs would overwrite each other.

Each IPED worker interacts with Python from its own thread, so thread
identity == worker identity: moving the state to threading.local() isolates
each worker.

How: Context is replaced by a subclass whose METACLASS exposes every per-run
class attribute as a property backed by threading.local(). A data descriptor
on the metaclass takes precedence over the class __dict__, so every read and
write of "Context._seeker" (and the like) becomes thread-local, while the
original methods are kept untouched. All Context methods are staticmethods
that resolve the global name "Context" of scripts.context at call time, so
rebinding that name is enough: bindings created earlier via
"from scripts.context import Context" (the original class) still run the same
functions, which now read and write the thread-local state.

The attributes are not listed by hand: they are every data attribute declared
by the class, so one added by a future LEAPP version is isolated
automatically.

The output parameters (_output_params, _data_folder, _output_folder_base) are
thread-local too: each IPED worker stages the evidence files in a folder of
its own, and Context.get_relative_path() must strip the folder of the worker
running the plugin. Each worker sets them once, at setup.

Everything is defined inside a single function, so after the installer global
is deleted below no name is left behind in the interpreter namespace (which
is shared with PythonParser/PythonTask scripts).
"""


def _iped_leapp_patch_context():
    import copy
    import sys
    import threading
    import types

    import scripts.context

    original = scripts.context.Context

    if getattr(original, '_iped_thread_local', False):
        # already patched
        return

    defaults = {
        name: value for name, value in vars(original).items()
        if not name.startswith('__')
        and not isinstance(value, (staticmethod, classmethod, property))
        and not callable(value)
    }

    tls = threading.local()

    def make_property(name, default):
        def fget(cls):
            try:
                return getattr(tls, name)
            except AttributeError:
                # mutable defaults (lists, dicts) must not be shared among threads
                value = copy.copy(default)
                setattr(tls, name, value)
                return value

        def fset(cls, value):
            setattr(tls, name, value)

        return property(fget, fset)

    meta_namespace = {name: make_property(name, default) for name, default in defaults.items()}
    meta = type('_IpedThreadLocalContextMeta', (type(original),), meta_namespace)

    patched = meta(original.__name__, (original,), {
        '__module__': original.__module__,
        '__doc__': original.__doc__,
        '_iped_thread_local': True,
    })

    scripts.context.Context = patched

    # modules that already ran "from scripts.context import Context" keep working with the original
    # class (see the module docstring), but are rebound anyway, so everyone sees the same object
    # (ModuleType.__getattribute__ reads __dict__ without triggering the load of lazy modules)
    for module in list(sys.modules.values()):
        if isinstance(module, types.ModuleType) \
                and types.ModuleType.__getattribute__(module, '__dict__').get('Context') is original:
            module.Context = patched


_iped_leapp_patch_context()
del _iped_leapp_patch_context
