"""
IPED: checks if the python packages a LEAPP tool needs are installed.

Defines _iped_leapp_check_requirements(tool_folder), which reads the
requirements files shipped with the tool and returns a dict with:

  missing:    requirements that are not installed
  mismatched: requirements installed in a version other than the required one
  command:    the command line that installs them in THIS python environment

The command is not a generic "pip install": it uses the python executable of
the environment the packages are being loaded from (IPED embedded python, a
virtualenv or the system python) and the actual location of the tool.
"""


def _iped_leapp_check_requirements(tool_folder):
    import os
    import re
    import sys
    from importlib import metadata

    try:
        from packaging.requirements import Requirement
    except Exception:  # packaging is itself one of the requirements
        Requirement = None

    # (file name, installed with --no-deps)
    files = (('requirements.txt', False), ('requirements-msl-lock.txt', True))

    missing = []
    mismatched = []

    def check(line):
        line = re.split(r'\s+#', line, maxsplit=1)[0].strip()
        if not line or line.startswith(('#', '-')):
            return
        # a wheel shipped with the tool: the same package is also required by name
        if re.match(r'^[^;@=<>!~\s]*[/\\]', line) or line.split(';')[0].strip().endswith('.whl'):
            return

        name, specifier = None, None
        if Requirement is not None:
            try:
                requirement = Requirement(line)
            except Exception:
                return
            if requirement.marker is not None and not requirement.marker.evaluate():
                # not for this platform / python version
                return
            name, specifier = requirement.name, requirement.specifier
        else:
            if ';' in line:
                # the marker cannot be evaluated: do not report what may not apply here
                return
            match = re.match(r'^([A-Za-z0-9][A-Za-z0-9._-]*)', line)
            if not match:
                return
            name = match.group(1)

        try:
            version = metadata.version(name)
        except metadata.PackageNotFoundError:
            missing.append(name)
            return
        except Exception:
            return

        if specifier and not specifier.contains(version, prereleases=True):
            mismatched.append('%s%s (installed: %s)' % (name, specifier, version))

    found_files = []
    for file_name, no_deps in files:
        path = os.path.join(tool_folder, file_name)
        if not os.path.isfile(path):
            continue
        found_files.append((file_name, no_deps))
        with open(path, encoding='utf-8', errors='replace') as f:
            for line in f:
                check(line)

    def find_python():
        executable = sys.executable or ''
        if os.path.basename(executable).lower().startswith('python'):
            return executable

        # embedded python (Jep): sys.executable is the java binary
        if os.name == 'nt':
            names = ['python.exe']
        else:
            names = ['python%d.%d' % sys.version_info[:2], 'python3', 'python']

        folders = []
        try:
            # packages may come from a virtualenv added to the path of the embedded python:
            # it is found from the location of the jep module in use
            import jep
            folder = os.path.dirname(os.path.abspath(jep.__file__))
            while True:
                if os.path.isfile(os.path.join(folder, 'pyvenv.cfg')):
                    folders += [os.path.join(folder, 'Scripts'), os.path.join(folder, 'bin')]
                    break
                parent = os.path.dirname(folder)
                if parent == folder:
                    break
                folder = parent
        except Exception:
            pass

        for prefix in (sys.prefix, sys.exec_prefix, sys.base_prefix):
            folders += [prefix, os.path.join(prefix, 'bin'), os.path.join(prefix, 'Scripts')]

        for folder in folders:
            for name in names:
                candidate = os.path.join(folder, name)
                if os.path.isfile(candidate):
                    return candidate

        return names[0]

    python = find_python()

    # run from the tool folder: the requirements file refers to wheels by relative path
    steps = ['cd /d "%s"' % tool_folder if os.name == 'nt' else 'cd "%s"' % tool_folder]
    for file_name, no_deps in found_files:
        steps.append('"%s" -m pip install %s-r %s' % (python, '--no-deps ' if no_deps else '', file_name))

    return {
        'missing': sorted(set(missing), key=str.lower),
        'mismatched': sorted(set(mismatched), key=str.lower),
        'command': ' && '.join(steps) if found_files else '',
    }
