import os
import re
import shutil
from pathlib import Path
from dataclasses import dataclass, field
from dotenv import load_dotenv

load_dotenv()


def _find_java_home() -> str:
    env_java = os.getenv("JAVA_HOME", os.environ.get("JAVA_HOME", ""))
    if env_java:
        return env_java
    
    iped_home = Path(__file__).resolve().parents[4]
    
    # 1. JRE Embutido no IPED
    builtin_jre = iped_home / "jre"
    if builtin_jre.is_dir():
        return str(builtin_jre)
    
    # 2. Variável PATH do Sistema
    java_exe = shutil.which("java")
    if java_exe:
        return str(Path(java_exe).resolve().parents[1])
    
    return ""


@dataclass
class Settings:
    iped_home: Path = field(default_factory=lambda: Path(__file__).resolve().parents[4])
    case_path: Path = field(default_factory=lambda: Path(__file__).resolve().parents[5])
    java_home: str = field(default_factory=_find_java_home)
    jvm_max_heap: str = field(default_factory=lambda: os.getenv("JVM_MAX_HEAP", "4g"))

    def validate(self) -> list[str]:
        errors = []

        if not self.iped_home.is_dir():
            errors.append(
                f"IPED_HOME directory does not exist: {self.iped_home}. "
                f"Check the relative path."
            )

        if not self.case_path.exists():
            errors.append(
                f"CASE_PATH does not exist: {self.case_path}. Check the relative path."
            )

        java = self.java_home
        if not java:
            errors.append(
                "JAVA_HOME could not be found automatically. Please ensure Java is in your PATH or that the IPED folder contains a 'jre' folder."
            )
        else:
            java_exe = Path(java) / "bin" / "java.exe"
            if not java_exe.is_file():
                java_exe = Path(java) / "bin" / "java"
                if not java_exe.is_file():
                    errors.append(
                        f"java executable not found at \"{Path(java) / 'bin' / 'java.exe'}\". "
                        f"JAVA_HOME should point to a JDK or JRE installation root (not the bin/ subdirectory)."
                    )

        heap = self.jvm_max_heap
        if not re.fullmatch(r"\d+[kKmMgGtT]", heap):
            errors.append(
                f"Invalid JVM_MAX_HEAP value: '{heap}'. Expected a valid heap size like '4g', '1024m', '2g'."
            )

        return errors

    @property
    def jvm_args(self) -> list[str]:
        return [f"-Xmx{self.jvm_max_heap}", "-Djava.awt.headless=true"]

    @property
    def iped_lib_jars(self) -> list[Path]:
        lib_dir = self.iped_home / "lib"
        if not lib_dir.is_dir():
            return []
        return sorted(lib_dir.glob("*.jar"))

    @property
    def iped_module_jars(self) -> list[Path]:
        mods = ["iped-api", "iped-engine", "iped-utils"]
        jars = []
        for m in mods:
            jar = self.iped_home / f"{m}.jar"
            if jar.is_file():
                jars.append(jar)
            else:
                for p in self.iped_home.glob(f"{m}-*.jar"):
                    jars.append(p)
        return jars

    @property
    def classpath_jars(self) -> list[Path]:
        return self.iped_module_jars + self.iped_lib_jars


settings = Settings()
