@echo off
rem =======================================================================
rem  IPED Forensics - Lanzador de Procesamiento
rem  Desarrollado para SHA256.US
rem =======================================================================
chcp 65001 >nul 2>&1
setlocal EnableDelayedExpansion

title IPED Forensics - SHA256.US
color 0A

set "IPED_HOME=%~dp0"
if "%IPED_HOME:~-1%"=="\" set "IPED_HOME=%IPED_HOME:~0,-1%"

rem Si no existe configuración previa, invitar a configurar
if not exist "%IPED_HOME%\LocalConfig.txt" (
    if exist "%IPED_HOME%\configurar_iped.bat" (
        echo [!] No se ha detectado una configuración previa de IPED en este equipo.
        echo Se recomienda ejecutar el asistente interactivo para optimizar la memoria y rendimiento.
        echo.
        set "RUN_CFG=S"
        set /p "RUN_CFG=¿Desea ejecutar el asistente de configuración ahora? [S/N] (Enter para Sí): "
        if /i "!RUN_CFG!"=="S" (
            call "%IPED_HOME%\configurar_iped.bat"
            exit /b 0
        )
    )
)

rem Detectar ejecutable binario o JAR
set "IPED_EXEC="
if exist "%IPED_HOME%\iped.exe" set "IPED_EXEC=%IPED_HOME%\iped.exe"
if not defined IPED_EXEC (
    if exist "%IPED_HOME%\iped.jar" set "IPED_EXEC=java -jar %IPED_HOME%\iped.jar"
    if exist "%IPED_HOME%\target\release\*\iped.jar" (
        for /d %%i in ("%IPED_HOME%\target\release\*") do (
            if exist "%%i\iped.jar" set "IPED_EXEC=java -jar %%i\iped.jar"
        )
    )
)

if not defined IPED_EXEC (
    echo [ERROR] No se encontró iped.exe ni iped.jar en este directorio.
    echo Asegúrese de que los binarios o la versión compilada de IPED estén en la misma carpeta.
    echo.
    pause
    exit /b 1
)

rem Si se pasaron argumentos directos por línea de comandos, ejecutarlos directamente
if not "%~1"=="" (
    echo Ejecutando: !IPED_EXEC! %*
    !IPED_EXEC! %*
    goto FIN
)

:MENU_INTERACTIVO
cls
echo ===============================================================================
echo                   PROCESADOR DE EVIDENCIAS DIGITALES (IPED)
echo                                   SHA256.US
echo ===============================================================================
echo.
echo Ingrese la ruta de la evidencia a procesar (o arrastre y suelte el archivo/carpeta aquí):
echo (Formatos soportados: DD, RAW, 001, E01, Ex01, VHD, VHDX, VMDK, UFDR, AD1, ISO, carpetas)
echo.
set "DATA_SRC="
set /p "DATA_SRC=Ruta de la Evidencia (-d): "
if "!DATA_SRC!"=="" (
    echo [!] No se indicó evidencia. Abortando.
    pause
    exit /b 1
)
rem Limpiar comillas iniciales/finales
set "DATA_SRC=!DATA_SRC:"=!"

echo.
echo Ingrese la ruta de la carpeta donde se guardará el caso procesado:
set "OUTPUT_DIR="
set /p "OUTPUT_DIR=Carpeta de Salida (-o): "
if "!OUTPUT_DIR!"=="" (
    echo [!] No se indicó carpeta de salida. Abortando.
    pause
    exit /b 1
)
set "OUTPUT_DIR=!OUTPUT_DIR:"=!"

echo.
echo ¿Desea utilizar un perfil específico de análisis?
echo   [1] Perfil por defecto / Configurado en LocalConfig.txt [Recomendado]
echo   [2] forensic (Análisis forense completo)
echo   [3] fastmode (Modo rápido de alta velocidad)
echo   [4] triage (Triaje en el terreno in-situ)
echo   [5] pedo (Protección infantil / CSAM / PhotoDNA)
echo.
set "PROF_OPT=1"
set /p "PROF_OPT=Seleccione perfil [1-5] (Enter para defecto): "
set "PROF_ARG="
if "%PROF_OPT%"=="2" set "PROF_ARG=-profile forensic"
if "%PROF_OPT%"=="3" set "PROF_ARG=-profile fastmode"
if "%PROF_OPT%"=="4" set "PROF_ARG=-profile triage"
if "%PROF_OPT%"=="5" set "PROF_ARG=-profile pedo"

echo.
echo Modo de ejecución:
echo   [1] Con interfaz gráfica (Ventana de progreso) [Recomendado]
echo   [2] Modo consola sin ventana gráfica (--nogui)
echo.
set "GUI_OPT=1"
set /p "GUI_OPT=Seleccione modo [1-2] (Enter para Interfaz Gráfica): "
set "EXTRA_ARGS="
if "%GUI_OPT%"=="2" set "EXTRA_ARGS=--nogui"

echo.
echo ===============================================================================
echo  Iniciando procesamiento de evidencias con IPED...
echo ===============================================================================
echo Comando: !IPED_EXEC! !PROF_ARG! -d "!DATA_SRC!" -o "!OUTPUT_DIR!" !EXTRA_ARGS!
echo ===============================================================================
echo.

!IPED_EXEC! !PROF_ARG! -d "!DATA_SRC!" -o "!OUTPUT_DIR!" !EXTRA_ARGS!

:FIN
echo.
echo ===============================================================================
echo Proceso finalizado.
echo ===============================================================================
pause
exit /b 0
