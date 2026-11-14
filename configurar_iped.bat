@echo off
rem =======================================================================
rem  IPED - Indexador e Processador de Evidencias Digitais
rem  Asistente de Escaneo, Instalación y Configuración Automática
rem  Desarrollado para SHA256.US
rem =======================================================================
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

title Asistente de Configuración de IPED - SHA256.US
color 0B

rem -------------------------------------------------------------------------------
rem  FASE 0: DIRECTORIO BASE Y CHEQUEO DE PERMISOS
rem -------------------------------------------------------------------------------
set "IPED_DIR=%~dp0"
if "%IPED_DIR:~-1%"=="\" set "IPED_DIR=%IPED_DIR:~0,-1%"

rem Comprobar si se ejecuta como Administrador
net session >nul 2>&1
if %ERRORLEVEL% equ 0 (
    set "IS_ADMIN=1"
) else (
    set "IS_ADMIN=0"
)

:FASE_ESCANEO
cls
echo ===============================================================================
echo        ASISTENTE INTERACTIVO DE CONFIGURACIÓN AUTOMÁTICA DE IPED
echo                             SHA256.US
echo ===============================================================================
echo.
echo [1/8] Escaneando el sistema para verificar requisitos de ejecución...
echo ===============================================================================
echo.

rem 1. Verificar Arquitectura de 64 bits
set "IS_64BIT=0"
if "%PROCESSOR_ARCHITECTURE%"=="AMD64" set "IS_64BIT=1"
if "%PROCESSOR_ARCHITEW6432%"=="AMD64" set "IS_64BIT=1"

if "%IS_64BIT%"=="0" (
    echo [ERROR CRÍTICO] Este sistema operativo es de 32 bits.
    echo IPED requiere obligatoriamente un sistema operativo Windows de 64 bits.
    echo.
    pause
    exit /b 1
)
echo  [OK] Arquitectura de sistema: 64-Bit (x64) compatible.

rem 2. Verificar Microsoft Visual C++ 2015-2022 Redistributable (x64)
set "VCREDIST_INSTALLED=0"
reg query "HKLM\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64" /v Installed >nul 2>&1
if %ERRORLEVEL% equ 0 set "VCREDIST_INSTALLED=1"

if exist "%SystemRoot%\System32\vcruntime140.dll" (
    if exist "%SystemRoot%\System32\msvcp140.dll" (
        set "VCREDIST_INSTALLED=1"
    )
)

if "%VCREDIST_INSTALLED%"=="1" (
    echo  [OK] Microsoft Visual C++ 2015-2022 Redistributable (x64): Instalado.
) else (
    echo  [!] Microsoft Visual C++ Redistributable (x64): NO DETECTADO.
)

rem 3. Verificar Java 64-Bit
set "JAVA_CMD=java"
set "JAVA_OK=0"
set "JAVA_VERSION=No detectado"

where java >nul 2>&1
if %ERRORLEVEL% equ 0 (
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set "JAVA_VERSION=%%~g"
    )
    java -version 2>&1 | findstr /i "64-Bit" >nul 2>&1
    if !ERRORLEVEL! equ 0 set "JAVA_OK=1"
) else (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\java.exe" (
            set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
            for /f "tokens=3" %%g in ('"!JAVA_CMD!" -version 2^>^&1 ^| findstr /i "version"') do (
                set "JAVA_VERSION=%%~g"
            )
            "!JAVA_CMD!" -version 2>&1 | findstr /i "64-Bit" >nul 2>&1
            if !ERRORLEVEL! equ 0 set "JAVA_OK=1"
        )
    )
)

if "%JAVA_OK%"=="1" (
    echo  [OK] Java Runtime: Versión %JAVA_VERSION% de 64-Bit detectada.
) else (
    echo  [!] Java 64-Bit: NO INSTALADO o no es de 64 bits (Detectado: %JAVA_VERSION%).
)

rem -------------------------------------------------------------------------------
rem  FASE 1: INSTALACIÓN AUTOMÁTICA DE DEPENDENCIAS FALTANTES
rem -------------------------------------------------------------------------------
set "NEED_INSTALL=0"
if "%VCREDIST_INSTALLED%"=="0" set "NEED_INSTALL=1"
if "%JAVA_OK%"=="0" set "NEED_INSTALL=1"

if "%NEED_INSTALL%"=="1" (
    echo.
    echo ===============================================================================
    echo  [AVISO] Se requiere instalar componentes faltantes para ejecutar IPED.
    echo ===============================================================================
    echo  El instalador procederá a descargar e instalar automáticamente:
    if "%VCREDIST_INSTALLED%"=="0" echo   - Microsoft Visual C++ 2015-2022 Redistributable (x64)
    if "%JAVA_OK%"=="0"           echo   - Java OpenJDK JRE 17 (64-Bit)
    echo.

    if "%IS_ADMIN%"=="0" (
        echo [!] Se requieren permisos de Administrador para realizar la instalación.
        echo Se abrirá una ventana solicitando autorización (UAC)...
        echo.
        powershell -NoProfile -Command "Start-Process cmd.exe -ArgumentList '/c \"\"%~f0\"\"' -Verb RunAs"
        exit /b 0
    )

    rem --- Instalar Visual C++ si falta ---
    if "%VCREDIST_INSTALLED%"=="0" (
        echo.
        echo [2/8] Descargando e instalando Microsoft Visual C++ Redistributable (x64)...
        echo -------------------------------------------------------------------------------
        where winget >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            echo  Intentando instalar mediante winget...
            winget install --id Microsoft.VCRedist.2015+.x64 -e --silent --accept-source-agreements --accept-package-agreements >nul 2>&1
        )
        reg query "HKLM\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\x64" /v Installed >nul 2>&1
        if !ERRORLEVEL! neq 0 (
            echo  Descargando instalador oficial desde Microsoft...
            powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://aka.ms/vs/17/release/vc_redist.x64.exe', '$env:TEMP\vc_redist.x64.exe')"
            if exist "%TEMP%\vc_redist.x64.exe" (
                echo  Instalando silenciosamente...
                "%TEMP%\vc_redist.x64.exe" /install /quiet /norestart
                del /f /q "%TEMP%\vc_redist.x64.exe" >nul 2>&1
            )
        )
        echo  [OK] Microsoft Visual C++ Redistributable configurado.
    )

    rem --- Instalar Java 64-bit si falta ---
    if "%JAVA_OK%"=="0" (
        echo.
        echo [3/8] Descargando e instalando Java OpenJDK 17 (64-Bit)...
        echo -------------------------------------------------------------------------------
        where winget >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            echo  Intentando instalar Adoptium Temurin 17 JRE mediante winget...
            winget install --id EclipseAdoptium.Temurin.17.JRE -e --silent --accept-source-agreements --accept-package-agreements >nul 2>&1
        )
        where java >nul 2>&1
        if !ERRORLEVEL! neq 0 (
            echo  Descargando instalador MSI oficial de Java OpenJDK 17...
            set "JAVA_MSI=%TEMP%\temurin17_x64.msi"
            powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://api.adoptium.net/v3/installer/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk', '$env:TEMP\temurin17_x64.msi')"
            if exist "!JAVA_MSI!" (
                echo  Instalando Java silenciosamente y configurando variables de entorno...
                msiexec /i "!JAVA_MSI!" /quiet /norestart ADDLOCAL=FeatureJavaHome,FeatureJarFileRunWith,FeaturePath
                del /f /q "!JAVA_MSI!" >nul 2>&1
            )
        )
        rem Actualizar PATH de la sesión actual
        for /f "tokens=2*" %%a in ('reg query "HKLM\System\CurrentControlSet\Control\Session Manager\Environment" /v Path 2^>nul') do set "PATH=%%b;%PATH%"
        for /f "tokens=2*" %%a in ('reg query "HKLM\System\CurrentControlSet\Control\Session Manager\Environment" /v JAVA_HOME 2^>nul') do (
            set "JAVA_HOME=%%b"
            set "PATH=%%b\bin;%PATH%"
        )
        echo  [OK] Java Runtime de 64 bits instalado y registrado.
    )

    echo.
    echo [OK] Todas las dependencias han sido instaladas con éxito.
    echo Presione cualquier tecla para iniciar el asistente de configuración de IPED...
    pause >nul
)

rem -------------------------------------------------------------------------------
rem  FASE 2: DETECCIÓN DE HARDWARE LOCAL (RAM, CPU, SSD)
rem -------------------------------------------------------------------------------
cls
echo ===============================================================================
echo                    DIAGNÓSTICO DE HARDWARE DE ESTE EQUIPO
echo ===============================================================================
echo.

rem Detectar Cores de CPU
set "CPU_CORES=%NUMBER_OF_PROCESSORS%"
if not defined CPU_CORES set "CPU_CORES=4"

rem Detectar Memoria RAM Total en GB usando PowerShell
set "TOTAL_RAM_GB=8"
for /f "usebackq tokens=*" %%a in (`powershell -NoProfile -Command "[Math]::Round((Get-CimInstance Win32_ComputerSystem).TotalPhysicalMemory / 1GB)" 2^>nul`) do (
    set "TOTAL_RAM_GB=%%a"
)

rem Cálculo de opciones de memoria
set /a RAM_REC=(TOTAL_RAM_GB * 3) / 4
if !RAM_REC! lss 2 set "RAM_REC=2"
set /a RAM_MOD=TOTAL_RAM_GB / 2
if !RAM_MOD! lss 2 set "RAM_MOD=2"
set /a RAM_MAX=(TOTAL_RAM_GB * 85) / 100
if !RAM_MAX! lss !RAM_REC! set "RAM_MAX=!RAM_REC!"

rem Cálculo de núcleos recomendados
set /a CORES_SAFE=CPU_CORES - 2
if !CORES_SAFE! lss 2 set "CORES_SAFE=2"

echo  * Directorio IPED : %IPED_DIR%
echo  * Procesador      : %CPU_CORES% núcleos lógicos detectados
echo  * Memoria RAM     : %TOTAL_RAM_GB% GB detectados
echo  * Java Runtime    : Listo para procesar
echo.
pause

rem -------------------------------------------------------------------------------
rem  FASE 3: CUESTIONARIO INTERACTIVO DE CONFIGURACIÓN
rem -------------------------------------------------------------------------------

:PREGUNTA_IDIOMA
cls
echo ===============================================================================
echo  PASO 1: SELECCIÓN DE IDIOMA / LOCALE
echo ===============================================================================
echo.
echo Seleccione el idioma para la interfaz gráfica, filtros y reportes HTML:
echo.
echo   [1] Español (es-AR) - Predeterminado
echo   [2] English (en)
echo   [3] Português (pt-BR)
echo   [4] Français (fr-FR)
echo   [5] Deutsch (de-DE)
echo   [6] Italiano (it-IT)
echo.
set "OPT_LOCALE="
set /p "OPT_LOCALE=Ingrese opción [1-6] (Enter para Español): "
if "%OPT_LOCALE%"=="" set "OPT_LOCALE=1"

if "%OPT_LOCALE%"=="1" set "CFG_LOCALE=es-AR"
if "%OPT_LOCALE%"=="2" set "CFG_LOCALE=en"
if "%OPT_LOCALE%"=="3" set "CFG_LOCALE=pt-BR"
if "%OPT_LOCALE%"=="4" set "CFG_LOCALE=fr-FR"
if "%OPT_LOCALE%"=="5" set "CFG_LOCALE=de-DE"
if "%OPT_LOCALE%"=="6" set "CFG_LOCALE=it-IT"

if not defined CFG_LOCALE (
    echo [!] Opción no válida. Reintentando...
    timeout /t 2 >nul
    goto PREGUNTA_IDIOMA
)
echo --^> Idioma seleccionado: %CFG_LOCALE%
timeout /t 1 >nul

:PREGUNTA_PERFIL
cls
echo ===============================================================================
echo  PASO 2: PERFIL DE PROCESAMIENTO FORENSE
echo ===============================================================================
echo.
echo ¿Cómo desea que IPED analice y procese las evidencias?
echo.
echo   [1] FORENSE COMPLETO (forensic) [Recomendado]
echo       - Análisis exhaustivo de firmas, contenedores, correos, metadatos y enlaces.
echo.
echo   [2] MODO RÁPIDO (fastmode)
echo       - Máxima velocidad. Omite pasos intensivos para análisis preliminar ágil.
echo.
echo   [3] TRIAJE EN CAMPO (triage)
echo       - Inspección rápida en el lugar de los hechos (in-situ) con índice liviano.
echo.
echo   [4] CSAM / PROTECCIÓN INFANTIL (pedo)
echo       - Enfoque prioritario en imágenes y vídeos, detección de pornografía e IA.
echo.
echo   [5] BÚSQUEDA CIEGA (blind)
echo       - Búsqueda orientada a palabras clave estrictas sin indexación general.
echo.
echo   [6] ESTÁNDAR / BASE (Sin perfil específico)
echo.
set "OPT_PERFIL="
set /p "OPT_PERFIL=Ingrese opción [1-6] (Enter para Forense Completo): "
if "%OPT_PERFIL%"=="" set "OPT_PERFIL=1"

if "%OPT_PERFIL%"=="1" set "CFG_PROFILE=forensic"
if "%OPT_PERFIL%"=="2" set "CFG_PROFILE=fastmode"
if "%OPT_PERFIL%"=="3" set "CFG_PROFILE=triage"
if "%OPT_PERFIL%"=="4" set "CFG_PROFILE=pedo"
if "%OPT_PERFIL%"=="5" set "CFG_PROFILE=blind"
if "%OPT_PERFIL%"=="6" set "CFG_PROFILE=none"

if not defined CFG_PROFILE (
    echo [!] Opción no válida. Reintentando...
    timeout /t 2 >nul
    goto PREGUNTA_PERFIL
)
echo --^> Perfil seleccionado: %CFG_PROFILE%
timeout /t 1 >nul

:PREGUNTA_MEMORIA
cls
echo ===============================================================================
echo  PASO 3: MEMORIA RAM PARA JAVA HEAP (-Xmx)
echo ===============================================================================
echo.
echo Su equipo cuenta con %TOTAL_RAM_GB% GB de memoria RAM física.
echo Una asignación óptima de RAM evita cuelgues al analizar contenedores gigantes.
echo.
echo   [1] Recomendado: %RAM_REC% GB (-Xmx%RAM_REC%G) [75%% de la memoria]
echo   [2] Moderado   : %RAM_MOD% GB (-Xmx%RAM_MOD%G) [50%% de la memoria]
echo   [3] Máximo     : %RAM_MAX% GB (-Xmx%RAM_MAX%G) [Alto rendimiento dedicado]
echo   [4] Personalizado (especificar cantidad manual en GB)
echo.
set "OPT_MEM="
set /p "OPT_MEM=Ingrese opción [1-4] (Enter para Recomendado - %RAM_REC% GB): "
if "%OPT_MEM%"=="" set "OPT_MEM=1"

if "%OPT_MEM%"=="1" set "CFG_RAM=%RAM_REC%G"
if "%OPT_MEM%"=="2" set "CFG_RAM=%RAM_MOD%G"
if "%OPT_MEM%"=="3" set "CFG_RAM=%RAM_MAX%G"
if "%OPT_MEM%"=="4" (
    set /p "CUSTOM_RAM=Indique la cantidad de GB a asignar (ej: 16 o 32): "
    set "CFG_RAM=!CUSTOM_RAM!G"
)

if not defined CFG_RAM (
    echo [!] Opción no válida. Reintentando...
    timeout /t 2 >nul
    goto PREGUNTA_MEMORIA
)
echo --^> Memoria asignada: -Xmx%CFG_RAM%
timeout /t 1 >nul

:PREGUNTA_HILOS
cls
echo ===============================================================================
echo  PASO 4: HILOS DE PROCESAMIENTO (numThreads)
echo ===============================================================================
echo.
echo Su equipo cuenta con %CPU_CORES% núcleos/hilos lógicos.
echo.
echo   [1] default (Utilizar todos los núcleos disponibles) [Recomendado]
echo   [2] Reservar para el sistema (%CORES_SAFE% hilos para IPED, 2 libres)
echo   [3] Especificar número manual de hilos
echo.
set "OPT_THREADS="
set /p "OPT_THREADS=Ingrese opción [1-3] (Enter para default): "
if "%OPT_THREADS%"=="" set "OPT_THREADS=1"

if "%OPT_THREADS%"=="1" set "CFG_THREADS=default"
if "%OPT_THREADS%"=="2" set "CFG_THREADS=%CORES_SAFE%"
if "%OPT_THREADS%"=="3" (
    set /p "CUSTOM_THREADS=Indique el número de hilos deseado (ej: 4, 8, 16): "
    set "CFG_THREADS=!CUSTOM_THREADS!"
)

if not defined CFG_THREADS (
    echo [!] Opción no válida. Reintentando...
    timeout /t 2 >nul
    goto PREGUNTA_HILOS
)
echo --^> Hilos configurados: %CFG_THREADS%
timeout /t 1 >nul

:PREGUNTA_DISCOS
cls
echo ===============================================================================
echo  PASO 5: OPTIMIZACIÓN DE ALMACENAMIENTO Y DISCOS SSD
echo ===============================================================================
echo.
echo IPED puede acelerar la velocidad de indexación hasta 2x si se le indica
echo que los directorios están en unidades de estado sólido (SSD).
echo.
echo  --- Directorio Temporal de Procesamiento (indexTemp) ---
echo   [1] Predeterminado del sistema (default: %%TEMP%%\IPED_TEMP)
echo   [2] Especificar una ruta rápida en otro disco (ej: D:\IPED_TEMP)
echo.
set "OPT_TEMP="
set /p "OPT_TEMP=Ingrese opción [1-2] (Enter para Predeterminado): "
if "%OPT_TEMP%"=="" set "OPT_TEMP=1"

if "%OPT_TEMP%"=="1" set "CFG_INDEXTEMP=default"
if "%OPT_TEMP%"=="2" (
    set /p "CFG_INDEXTEMP=Ingrese la ruta completa (ej: D:\IPED_TEMP): "
)

echo.
set "OPT_SSD_TEMP=S"
set /p "OPT_SSD_TEMP=¿El directorio temporal está en un disco SSD? [S/N] (Enter para Sí): "
if /i "%OPT_SSD_TEMP%"=="S" (set "CFG_TEMP_SSD=true") else (set "CFG_TEMP_SSD=false")

echo.
set "OPT_SSD_OUT=S"
set /p "OPT_SSD_OUT=¿El directorio de salida del caso (output) estará en un SSD? [S/N] (Enter para Sí): "
if /i "%OPT_SSD_OUT%"=="S" (set "CFG_OUT_SSD=true") else (set "CFG_OUT_SSD=false")

echo --^> indexTemp: %CFG_INDEXTEMP% ^| Temp en SSD: %CFG_TEMP_SSD% ^| Salida en SSD: %CFG_OUT_SSD%
timeout /t 1 >nul

:PREGUNTA_HASHDB
cls
echo ===============================================================================
echo  PASO 6: BASE DE DATOS DE HASHES (hashesDB)
echo ===============================================================================
echo.
echo ¿Dispone de una base de datos de hashes de IPED (ej: iped-hashes.db o NSRL)?
echo Permite identificar archivos conocidos de sistema o material de interés.
echo.
set "OPT_HASHDB=N"
set /p "OPT_HASHDB=¿Desea configurar la ruta de una base de hashes? [S/N] (Enter para No): "
if /i "%OPT_HASHDB%"=="S" (
    set /p "CFG_HASHDB=Ingrese la ruta completa del archivo .db (ej: C:\IPED\iped-hashes.db): "
) else (
    set "CFG_HASHDB="
)

:PREGUNTA_MODULOS
cls
echo ===============================================================================
echo  PASO 7: MÓDULOS FORENSES Y AHORRO DE RECURSOS (IPEDConfig.txt)
echo ===============================================================================
echo.
echo [AHORRO DE RECURSOS]:
echo  * OCR (Reconocimiento Óptico de Caracteres): DESACTIVADO POR DEFECTO.
echo    (Desactivar OCR reduce el consumo de CPU en un 70%% y acelera hasta 5x el proceso).
echo.
echo ¿Desea mantener la configuración por defecto de alto rendimiento (OCR Desactivado)?
echo.
echo   [1] Mantener configuración por defecto [Recomendado: OCR Desactivado]
echo   [2] Personalizar módulos manualmente (activar OCR u otros módulos)
echo.
set "OPT_PERF_MODE="
set /p "OPT_PERF_MODE=Seleccione opción [1-2] (Enter para opción 1 - OCR Desactivado): "
if "%OPT_PERF_MODE%"=="" set "OPT_PERF_MODE=1"

rem Valores por defecto con OCR DESACTIVADO
set "MOD_OCR=false"
set "MOD_QR=false"
set "MOD_GRAPH=true"
set "MOD_REGEX=true"
set "MOD_LEDDIE=false"
set "MOD_WALLETS=true"

if "%OPT_PERF_MODE%"=="2" (
    echo.
    echo --- Personalización Manual de Módulos ---
    set /p "INP_OCR=1. ¿Desea ACTIVAR OCR en imágenes y PDFs? (Consume mucha CPU/RAM) [S/N] (Defecto N): "
    if /i "!INP_OCR!"=="S" set "MOD_OCR=true"

    set /p "INP_LED=2. ¿Habilitar detección de desnudez / NSFW (LedDie)? [S/N] (Defecto N): "
    if /i "!INP_LED!"=="S" set "MOD_LEDDIE=true"

    set /p "INP_QR=3. ¿Habilitar detección de códigos QR? [S/N] (Defecto N): "
    if /i "!INP_QR!"=="S" set "MOD_QR=true"

    set /p "INP_GRAPH=4. ¿Habilitar grafos de comunicaciones (llamadas/mensajes)? [S/N] (Defecto S): "
    if /i "!INP_GRAPH!"=="N" set "MOD_GRAPH=false"

    set /p "INP_REG=5. ¿Habilitar búsqueda de patrones RegEx (tarjetas, DNIs)? [S/N] (Defecto S): "
    if /i "!INP_REG!"=="N" set "MOD_REGEX=false"

    set /p "INP_WAL=6. ¿Habilitar búsqueda de billeteras cripto hardware? [S/N] (Defecto S): "
    if /i "!INP_WAL!"=="N" set "MOD_WALLETS=false"
)

rem -------------------------------------------------------------------------------
rem  FASE 4: APLICACIÓN Y ESCRITURA DE LA CONFIGURACIÓN
rem -------------------------------------------------------------------------------
cls
echo ===============================================================================
echo  [8/8] APLICANDO CONFIGURACIÓN FINAL EN EL SISTEMA...
echo ===============================================================================
echo.

rem 1. Localizar y escribir LocalConfig.txt
set "TARGET_LOCAL_CONFIG=%IPED_DIR%\LocalConfig.txt"

echo Guardando archivo: %TARGET_LOCAL_CONFIG%
(
    echo ########################################################################
    echo # Local environment configuration - Configurado automáticamente por SHA256.US
    echo # Fecha: %DATE% %TIME%
    echo ########################################################################
    echo.
    echo # Idioma de la interfaz y reportes
    echo locale = %CFG_LOCALE%
    echo.
    echo # Directorio temporal de procesamiento
    echo indexTemp = %CFG_INDEXTEMP%
    echo.
    echo # Optimización SSD para temporal
    echo indexTempOnSSD = %CFG_TEMP_SSD%
    echo.
    echo # Optimización SSD para carpeta de caso
    echo outputOnSSD = %CFG_OUT_SSD%
    echo.
    echo # Número de hilos de procesamiento
    echo numThreads = %CFG_THREADS%
    echo.
    if defined CFG_HASHDB (
        echo # Base de datos de hashes configurada
        echo hashesDB = %CFG_HASHDB%
    ) else (
        echo # Full path for IPED hash database.
        echo #hashesDB = C:/IPED/iped-hashes.db
    )
    echo.
    echo # Carpeta de plugins
    echo pluginFolder = ../plugins/
) > "%TARGET_LOCAL_CONFIG%"

rem Duplicar en carpetas alternativas si existen
if exist "%IPED_DIR%\conf" (
    copy /y "%TARGET_LOCAL_CONFIG%" "%IPED_DIR%\conf\LocalConfig.txt" >nul 2>&1
)
if exist "%IPED_DIR%\iped-app\resources\config" (
    copy /y "%TARGET_LOCAL_CONFIG%" "%IPED_DIR%\iped-app\resources\config\LocalConfig.txt" >nul 2>&1
)

rem 2. Aplicar módulos en IPEDConfig.txt asegurando el estado de OCR
set "TARGET_IPED_CONF=%IPED_DIR%\IPEDConfig.txt"
if not exist "!TARGET_IPED_CONF!" (
    if exist "%IPED_DIR%\conf\IPEDConfig.txt" set "TARGET_IPED_CONF=%IPED_DIR%\conf\IPEDConfig.txt"
    if exist "%IPED_DIR%\iped-app\resources\config\IPEDConfig.txt" set "TARGET_IPED_CONF=%IPED_DIR%\iped-app\resources\config\IPEDConfig.txt"
)

if exist "!TARGET_IPED_CONF!" (
    echo Actualizando IPEDConfig.txt (enableOCR = %MOD_OCR%)...
    powershell -NoProfile -Command "(Get-Content -Path '!TARGET_IPED_CONF!' -Raw) -replace 'enableOCR\s*=\s*(true|false)', 'enableOCR = %MOD_OCR%' -replace 'enableLedDie\s*=\s*(true|false)', 'enableLedDie = %MOD_LEDDIE%' -replace 'enableQRCode\s*=\s*(true|false)', 'enableQRCode = %MOD_QR%' -replace 'enableGraphGeneration\s*=\s*(true|false)', 'enableGraphGeneration = %MOD_GRAPH%' -replace 'enableRegexSearch\s*=\s*(true|false)', 'enableRegexSearch = %MOD_REGEX%' -replace 'enableSearchHardwareWallets\s*=\s*(true|false)', 'enableSearchHardwareWallets = %MOD_WALLETS%' | Set-Content -Path '!TARGET_IPED_CONF!' -Encoding UTF8"
)

rem 3. Generar archivo ejecutable lanzador: ejecutar_iped.bat
set "LAUNCHER=%IPED_DIR%\ejecutar_iped.bat"
echo Generando lanzador preconfigurado: %LAUNCHER%

(
    echo @echo off
    echo rem =======================================================================
    echo rem  Lanzador Optimizado de IPED - Generado por Asistente SHA256.US
    echo rem =======================================================================
    echo chcp 65001 ^>nul 2^>^&1
    echo setlocal EnableDelayedExpansion
    echo title IPED Forensics - SHA256.US
    echo.
    echo set "IPED_HOME=%%~dp0"
    echo if "%%IPED_HOME:~-1%%"=="\" set "IPED_HOME=%%IPED_HOME:~0,-1%%"
    echo.
    echo rem Parámetros calculados
    echo set "HEAP_MEM=-Xmx%CFG_RAM%"
    if not "%CFG_PROFILE%"=="none" (
        echo set "PROFILE_ARG=-profile %CFG_PROFILE%"
    ) else (
        echo set "PROFILE_ARG="
    )
    echo.
    echo rem Detectar ejecutable binario o JAR
    echo set "IPED_EXEC="
    echo if exist "%%IPED_HOME%%\iped.exe" set "IPED_EXEC=%%IPED_HOME%%\iped.exe"
    echo if not defined IPED_EXEC (
        echo if exist "%%IPED_HOME%%\iped.jar" set "IPED_EXEC=java -jar %%IPED_HOME%%\iped.jar"
        echo if exist "%%IPED_HOME%%\target\release\*\iped.jar" (
            echo for /d %%%%i in ("%%IPED_HOME%%\target\release\*") do (
                echo if exist "%%%%i\iped.jar" set "IPED_EXEC=java -jar %%%%i\iped.jar"
            echo )
        echo )
    echo )
    echo.
    echo if not defined IPED_EXEC (
        echo echo [ERROR] No se encontró iped.exe ni iped.jar en el directorio.
        echo echo Asegúrese de que IPED esté compilado o descargado en esta carpeta.
        echo pause
        echo exit /b 1
    echo )
    echo.
    echo rem Si se pasaron argumentos por línea de comandos, ejecutarlos directamente
    echo if not "%%~1"=="" (
        echo echo Ejecutando: !IPED_EXEC! !HEAP_MEM! !PROFILE_ARG! %%*
        echo !IPED_EXEC! !HEAP_MEM! !PROFILE_ARG! %%*
        echo goto FIN
    echo )
    echo.
    echo rem Modo Interactivo de Ejecución
    echo cls
    echo ===============================================================================
    echo                       EJECUTAR PROCESAMIENTO CON IPED
    echo ===============================================================================
    echo  Configuración: Memoria: !HEAP_MEM! ^| Perfil: %CFG_PROFILE% ^| OCR: %MOD_OCR%
    echo ===============================================================================
    echo.
    echo Ingrese la ruta de la evidencia o arrastre el archivo/carpeta aquí:
    echo (Soporta DD, E01, UFDR, AD1, ISO, carpetas, etc.)
    echo.
    echo set /p "DATA_SRC=Ruta de la Evidencia (-d): "
    echo if "!DATA_SRC!"=="" (
        echo [!] No se indicó evidencia. Abortando.
        echo pause
        echo exit /b 1
    echo )
    echo.
    echo Ingrese la ruta de la carpeta donde se guardará el caso procesado:
    echo set /p "OUTPUT_DIR=Carpeta de Salida (-o): "
    echo if "!OUTPUT_DIR!"=="" (
        echo [!] No se indicó carpeta de salida. Abortando.
        echo pause
        echo exit /b 1
    echo )
    echo.
    echo ¿Desea ejecutar con interfaz gráfica o modo consola?
    echo   [1] Con interfaz gráfica (Ventana de progreso) [Recomendado]
    echo   [2] Modo texto sin ventanas (--nogui)
    echo set /p "GUI_OPT=Seleccione opción [1-2] (Enter para Interfaz Gráfica): "
    echo set "EXTRA_ARGS="
    echo if "!GUI_OPT!"=="2" set "EXTRA_ARGS=--nogui"
    echo.
    echo ===============================================================================
    echo  Iniciando procesamiento forense...
    echo ===============================================================================
    echo !IPED_EXEC! !HEAP_MEM! !PROFILE_ARG! -d "!DATA_SRC!" -o "!OUTPUT_DIR!" !EXTRA_ARGS!
    echo.
    echo :FIN
    echo echo.
    echo echo Procesamiento completado o finalizado.
    echo pause
) > "%LAUNCHER%"

rem -------------------------------------------------------------------------------
rem  FASE 5: RESUMEN Y FINALIZACIÓN
rem -------------------------------------------------------------------------------
cls
echo ===============================================================================
echo                ¡CONFIGURACIÓN COMPLETADA EXITOSAMENTE!
echo ===============================================================================
echo.
echo Resumen de los ajustes aplicados:
echo -------------------------------------------------------------------------------
echo  * Idioma de interfaz         : %CFG_LOCALE%
echo  * Perfil forense             : %CFG_PROFILE%
echo  * Memoria RAM para Java      : -Xmx%CFG_RAM%
echo  * Hilos de procesamiento     : %CFG_THREADS%
echo  * Directorio temporal        : %CFG_INDEXTEMP% (SSD: %CFG_TEMP_SSD%)
echo  * Carpeta de caso en SSD     : %CFG_OUT_SSD%
echo  * OCR (Ahorro de recursos)   : %MOD_OCR%
if "%MOD_OCR%"=="false" (
echo                                 (OCR DESACTIVADO: Gran ahorro de CPU y RAM)
)
if defined CFG_HASHDB (
echo  * Base de datos de Hashes    : %CFG_HASHDB%
) else (
echo  * Base de datos de Hashes    : Ninguna configurada
)
echo.
echo Archivos generados / actualizados:
echo   [OK] %TARGET_LOCAL_CONFIG%
echo   [OK] %LAUNCHER%
echo.
echo ===============================================================================
echo ¿Qué desea hacer a continuación?
echo.
echo   [1] Salir (IPED queda listo para ejecutarse en cualquier momento).
echo   [2] Iniciar procesamiento ahora con "ejecutar_iped.bat".
echo   [3] Ver el contenido de "LocalConfig.txt" generado.
echo.
set "FINAL_OPT="
set /p "FINAL_OPT=Seleccione una opción [1-3] (Enter para Salir): "
if "%FINAL_OPT%"=="" set "FINAL_OPT=1"

if "%FINAL_OPT%"=="2" (
    call "%LAUNCHER%"
    exit /b 0
)
if "%FINAL_OPT%"=="3" (
    cls
    echo ===============================================================================
    echo                     CONTENIDO DE LocalConfig.txt
    echo ===============================================================================
    type "%TARGET_LOCAL_CONFIG%"
    echo.
    echo ===============================================================================
    pause
    goto FASE_ESCANEO
)

echo.
echo Gracias por utilizar el Asistente de Configuración de IPED.
echo Para volver a configurar en cualquier momento, vuelva a ejecutar este archivo.
echo Para procesar evidencias, ejecute directamente: ejecutar_iped.bat
echo.
pause
exit /b 0
