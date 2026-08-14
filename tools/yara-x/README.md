# `tools/yara-x/` — native YARA-X engine for IPED

This directory ships the **YARA-X 1.x** runtime (`libyara-x-capi`) that the
`YaraScanTask` consumes via JNA. YARA-X is the official Rust rewrite of YARA by
Victor M. Alvarez and is the successor of the classic libyara.

## Layout

```
tools/yara-x/
├── README.md              (this file)
├── LICENSE                (BSD 3-clause from upstream YARA-X)
├── win64/
│   ├── yara_x_capi.dll        (21,542,400 bytes — YARA-X 1.16.0, MSVC x86_64)
│   └── yara_x.h               (39,444 bytes — C header, kept for reference)
└── linux64/
    └── libyara_x_capi.so      (31,940,896 bytes — YARA-X 1.16.0, built from source)
```

The Java side loads the library via `Native.load("yara_x_capi", LibYaraX.class)`
(JNA) after `Bootstrap` adds the platform-specific subdirectory to
`jna.library.path`. When the binary is missing, the `YaraScanTask` logs a single
warning and disables itself for the case — the rest of IPED continues to work
normally.

## Versão pinned

- **YARA-X 1.16.0** (release oficial; congelada por release do IPED — atualize
  esta versão, a constante `YaraEngine.ENGINE_VERSION`, o `YARAX_VERSION` no
  workflow de CI (`.github/workflows/maven.yml`) e os SHA-256 abaixo
  sempre que trocar).
- Módulos `pe`, `elf`, `math`, `hash`, `magic`, `dotnet`, `time` vêm habilitados
  no release oficial. O módulo `cuckoo` é **banido em runtime** pelo
  `YaraEngine` via `yrx_compiler_ban_module(...)`, então rules com
  `import "cuckoo"` falham na compilação com mensagem clara.

### Binários bundled (SHA-256)

| Arquivo | Tamanho | SHA-256 |
|---|---|---|
| `win64/yara_x_capi.dll` | 21,542,400 | `0F56AC336EFF5242F4BAB23F9A4419FC466A5DD2696B7A3CF6B11F6758B29121` |
| `linux64/libyara_x_capi.so` | 31,940,896 | `4ccc394ffbad106674e628672ce67ddd960b3619451f992161f20100b7f1ba00` |

SHA-256 do zip upstream do qual a DLL foi extraída:
`D9FFF45807F752333138B8959F16C0E68D6603F08E161F254CF1E95CF725ECC4`
(`yara-x-capi-v1.16.0-x86_64-pc-windows-msvc.zip`, 19,800,959 bytes).

A `.so` Linux foi compilada do fonte (tag `v1.16.0` em `github.com/VirusTotal/yara-x`)
em WSL Ubuntu 26.04 LTS com Rust 1.96.0 stable, perfil `--release`, conforme
procedimento da seção "Linux (x86_64)" abaixo. Apenas dependências de sistema
(`libc`, `libm`, `libgcc_s`, `ld-linux`) — OpenSSL e demais dependências Rust
ficam estaticamente linkadas. 53 símbolos `yrx_*` exportados, cobrindo a
superfície consumida pelo `YaraEngine.java`.

## Como atualizar a versão do `libyara-x-capi`

Diferente do YARA clássico, o upstream do YARA-X publica um binário
self-contained pré-compilado para **Windows** (MSVC). Para **Linux x86_64** o
release 1.16.0 não traz prebuilt da C API, então a `.so` é compilada do fonte
(ver a seção "Linux (x86_64)" abaixo).

1. **Identifique a versão alvo** em https://github.com/VirusTotal/yara-x/releases.
   Procure os assets que começam com `libyara-x-capi-vX.Y.Z-...`.

2. **Linux (x86_64)** — **NÃO há prebuilt no release 1.16.0** (o upstream só
   publica o asset `yara-x-capi-*-msvc.zip` para Windows; o asset Linux
   `yara-x-v1.16.0-x86_64-unknown-linux-gnu.gz` é o CLI `yara-x`, não a C API).
   Para gerar `libyara_x_capi.so` é preciso compilar a partir do fonte com a
   toolchain Rust:
   ```bash
   git clone https://github.com/VirusTotal/yara-x.git
   cd yara-x
   git checkout v1.16.0
   cargo build -p yara-x-capi --release
   # A biblioteca sai em target/release/libyara_x_capi.so
   cp target/release/libyara_x_capi.so path/to/IPED/tools/yara-x/linux64/
   ```
   Requisitos: Rust 1.75+ (estável), `pkg-config`, `libssl-dev` (para o módulo
   `hash` linkar contra OpenSSL). Build leva ~3–5 minutos numa máquina típica.

3. **Windows (x64)** — baixe e extraia:
   ```powershell
   $YARAX_VERSION = "1.16.0"  # ajuste para a versão alvo
   Invoke-WebRequest `
     -Uri "https://github.com/VirusTotal/yara-x/releases/download/v$YARAX_VERSION/libyara-x-capi-v$YARAX_VERSION-x86_64-pc-windows-msvc.zip" `
     -OutFile yara-x-capi-windows.zip
   Expand-Archive yara-x-capi-windows.zip -DestinationPath yara-x-capi-windows
   # O zip contém lib/, include/, etc.
   Copy-Item yara-x-capi-windows\lib\yara_x_capi.dll path\to\IPED\tools\yara-x\win64\
   ```

4. **Gerar SHA-256 dos novos binários** e registrar em `ReleaseNotes.txt` na
   entrada da versão correspondente do IPED:
   ```bash
   sha256sum tools/yara-x/linux64/libyara_x_capi.so
   sha256sum tools/yara-x/win64/yara_x_capi.dll
   ```

5. **Atualizar `licenses/YARA-X.txt`** se o arquivo `LICENSE` do upstream tiver
   mudado entre versões (raro — é BSD 3-clause estável).

## Verificação rápida

A partir do release construído (`target/release/iped-<version>/`):

```bash
# Linux
ldd tools/yara-x/linux64/libyara_x_capi.so
# Confirmar que não há dependências quebradas (UNRESOLVED). Como o YARA-X
# linka estaticamente OpenSSL e dependências Rust, o output deve ser
# essencialmente libc/libpthread/libdl.

# Windows (PowerShell + Dependencies.exe ou similar)
# Listar dependências dinâmicas e confirmar que são apenas system DLLs
# (kernel32, ucrtbase, etc.).
```

Para validar a integração JNA+libyara-x-capi end-to-end:

```powershell
# Windows
$env:JAVA_HOME = "<path to Liberica JDK 11 Full>"
$env:YARA_X_LIB_PATH = "$PWD\tools\yara-x\win64\yara_x_capi.dll"
mvn -pl iped-engine -Dtest='YaraEngineTest' -DfailIfNoTests=false test
# Esperado: 5 tests run, 0 failures, 0 skipped.
```

```bash
# Linux (após o build from source acima)
export JAVA_HOME="/path/to/liberica-jdk-11-full"
export YARA_X_LIB_PATH="$PWD/tools/yara-x/linux64/libyara_x_capi.so"
mvn -pl iped-engine -Dtest='YaraEngineTest' -DfailIfNoTests=false test
```

## Por que YARA-X e não libyara clássica?

Resumo:

- O upstream do YARA clássico entrou em modo manutenção; novas features migraram
  para YARA-X.
- O YARA clássico **não publica `libyara.dll` pré-compilada** para Windows; só
  os executáveis estáticos `yara64.exe`/`yarac64.exe`. Forçaria o IPED a manter
  um build próprio da DLL. YARA-X resolve isso publicando os artefatos
  `libyara-x-capi-vX.Y.Z-*-msvc.zip`.
- Linguagem de regras ~99% retrocompatível; flag `YRX_RELAXED_RE_SYNTAX` cobre
  o gap residual de regex.
- C API (`yrx_*`) é mais limpa e expõe mais informação ao chamador (iteradores
  separados para patterns/matches/metadata), o que simplificará a extração de
  match detail nas próximas iterações.
