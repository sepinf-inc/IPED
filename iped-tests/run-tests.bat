@echo off
setlocal enabledelayedexpansion

REM Script para executar os testes do módulo iped-tests no Windows
REM Uso: run-tests.bat [profile]

REM Verifica se estamos no diretório correto
if not exist "pom.xml" (
    echo [ERROR] Este script deve ser executado no diretório iped-tests
    exit /b 1
)

REM Verifica se o Maven está disponível
mvn -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven não encontrado. Instale o Maven primeiro.
    exit /b 1
)

REM Determina o perfil a ser usado
set PROFILE=%1
if "%PROFILE%"=="" set PROFILE=full

echo [INFO] Iniciando execução dos testes do IPED...
echo [INFO] Perfil selecionado: %PROFILE%

REM Limpa builds anteriores
echo [INFO] Limpando builds anteriores...
call mvn clean

REM Executa os testes com o perfil especificado
echo [INFO] Executando testes com perfil: %PROFILE%

if "%PROFILE%"=="quick" (
    echo [INFO] Executando testes rápidos (apenas análise básica)...
    call mvn test -Dtest.profile=quick
) else if "%PROFILE%"=="full" (
    echo [INFO] Executando todos os testes (análise completa + verificações)...
    call mvn test -Dtest.profile=full
) else if "%PROFILE%"=="analysis-only" (
    echo [INFO] Executando apenas a análise forense...
    call mvn test -Dtest=ImageProcessingTest#test01_ForensicAnalysis
) else if "%PROFILE%"=="verification-only" (
    echo [INFO] Executando apenas as verificações (requer análise prévia)...
    call mvn test -Dtest=ForensicResultsTest
) else (
    echo [ERROR] Perfil inválido: %PROFILE%
    echo Perfis disponíveis:
    echo   quick          - Testes rápidos (apenas análise básica)
    echo   full           - Todos os testes (padrão)
    echo   analysis-only  - Apenas análise forense
    echo   verification-only - Apenas verificações (requer análise prévia)
    exit /b 1
)

if errorlevel 0 (
    echo [SUCCESS] Todos os testes foram executados com sucesso!
    
    REM Mostra informações sobre os resultados
    if exist "target\iped-result" (
        echo [INFO] Resultados da análise encontrados em: target\iped-result
        
        if exist "target\iped-result\csv" (
            for /f %%i in ('dir /b "target\iped-result\csv\*.csv" 2^>nul ^| find /c /v ""') do set CSV_COUNT=%%i
            echo [INFO] Arquivos CSV gerados: !CSV_COUNT!
        )
        
        if exist "target\iped-result\html" (
            for /f %%i in ('dir /b "target\iped-result\html\*.html" 2^>nul ^| find /c /v ""') do set HTML_COUNT=%%i
            echo [INFO] Relatórios HTML gerados: !HTML_COUNT!
        )
    )
) else (
    echo [ERROR] Alguns testes falharam. Verifique os logs acima.
    exit /b 1
)

pause 