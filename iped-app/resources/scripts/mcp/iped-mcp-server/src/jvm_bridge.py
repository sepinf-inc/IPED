import logging

import jnius_config

from .config import settings

logger = logging.getLogger(__name__)

_JVM_INITIALIZED = False


def ensure_jvm() -> None:
    global _JVM_INITIALIZED
    if _JVM_INITIALIZED:
        return

    jars = settings.classpath_jars
    if not jars:
        lib_dir = settings.iped_home / "lib"
        lib_exists = lib_dir.is_dir()
        module_jars = list(settings.iped_home.glob("iped-*.jar"))
        raise RuntimeError(
            f"No IPED JARs found at {settings.iped_home}. "
            f"lib/ directory exists: {lib_exists}, "
            f"module jars found: {len(module_jars)}. "
            "Ensure IPED_HOME points to a valid IPED installation "
            "with lib/ directory and module JARs (iped-api, iped-engine, iped-utils)."
        )

    logger.info("Adding %d JARs to classpath", len(jars))

    for arg in settings.jvm_args:
        jnius_config.add_options(arg)

    for jar in jars:
        jnius_config.add_classpath(str(jar))

    _JVM_INITIALIZED = True
    logger.info("JVM configuration ready")


def get_class(name: str):
    ensure_jvm()
    from jnius import autoclass
    return autoclass(name)


def cast_to(name: str, obj):
    ensure_jvm()
    from jnius import cast
    return cast(name, obj)

