@echo off
REM ==============================================================================
REM  start_local.bat - Launch all 4 JinBooks services into separate cmd windows.
REM  - Each window runs a start_<service>.bat in the FOREGROUND (so you can
REM    see live logs and kill the service by closing its window).
REM  - Skips services whose port is already in use.
REM  - Returns immediately after launching.
REM ==============================================================================
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "ENVS_DIR=%ROOT_DIR%\jinbooks_envs"
set "BIN_DIR=%ENVS_DIR%\bin"
set "LOG_DIR=%ENVS_DIR%\logs"
set "PID_DIR=%ENVS_DIR%\pids"
set "ENV_FILE=%ENVS_DIR%\.env"

echo.
echo ============================================================
echo   JinBooks local start
echo   ROOT_DIR = %ROOT_DIR%
echo ============================================================
echo.

if not exist "%ENV_FILE%" (
    echo [ERROR] .env missing, run init_local.bat first
    pause
    exit /b 1
)
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"
if not exist "%PID_DIR%" mkdir "%PID_DIR%"

REM Load .env (for port checks)
for /f "usebackq tokens=1,* delims==" %%a in ("%ENV_FILE%") do (
    if not "%%a"=="" set "%%a=%%b"
)

REM Port check via single PowerShell call
set "USED_PORTS="
for /f "usebackq delims=" %%p in (`powershell -NoProfile -Command "$c=Get-NetTCPConnection -LocalPort 33306,36379,32315,32080 -State Listen -ErrorAction SilentlyContinue; ($c.LocalPort -join ',')"`) do set "USED_PORTS=%%p"
echo [CHECK] ports in use: [%USED_PORTS%]   (empty = all free)
echo.

REM =================================================================
REM  1. MySQL  (port 3306)
REM =================================================================
echo [1/4] MySQL ...
set "SKIP=0"
call :PORT_USED 3306
if "%SKIP%"=="1" (
    echo   [SKIP] port 3306 already in use
) else (
    echo   starting in new window: JinBooks-MySQL
    start "JinBooks-MySQL" /D "%BIN_DIR%" "%BIN_DIR%\start_mysql.bat"
    timeout /t 3 /nobreak >nul
)
echo.

REM =================================================================
REM  2. Redis  (port 6379)
REM =================================================================
echo [2/4] Redis ...
set "SKIP=0"
call :PORT_USED 6379
if "%SKIP%"=="1" (
    echo   [SKIP] port 6379 already in use
) else (
    echo   starting in new window: JinBooks-Redis
    start "JinBooks-Redis" /D "%BIN_DIR%" "%BIN_DIR%\start_redis.bat"
)
echo.

REM =================================================================
REM  3. Backend  (port 2154)
REM =================================================================
echo [3/4] Backend ...
set "SKIP=0"
call :PORT_USED 2154
if "%SKIP%"=="1" (
    echo   [SKIP] port 2154 already in use
) else (
    echo   starting in new window: JinBooks-Backend
    start "JinBooks-Backend" /D "%BIN_DIR%" "%BIN_DIR%\start_backend.bat"
)
echo.

REM =================================================================
REM  4. Nginx  (port 18080)
REM =================================================================
echo [4/4] Nginx ...
set "SKIP=0"
call :PORT_USED 18080
if "%SKIP%"=="1" (
    echo   [SKIP] port 18080 already in use
) else (
    echo   starting in new window: JinBooks-Nginx
    start "JinBooks-Nginx" /D "%BIN_DIR%" "%BIN_DIR%\start_nginx.bat"
)
echo.

echo ============================================================
echo   DONE
echo.
echo   Frontend   :  http://127.0.0.1:%FRONTEND_PORT%
echo   Backend    :  http://127.0.0.1:%BACKEND_PORT%%BACKEND_CONTEXT_PATH%
echo.
echo   Each service runs in its OWN cmd window.
echo   Close that window to stop the service,
echo   or run stop_local.bat to stop all.
echo.
echo   Logs       :  %LOG_DIR%
echo ============================================================
goto :END

REM =================================================================
REM  Helpers
REM =================================================================
:PORT_USED
set "SKIP=0"
echo ",%USED_PORTS%," | findstr /C:",%~1," >nul 2>&1
if %errorlevel%==0 set "SKIP=1"
goto :eof

:END
endlocal
exit /b 0
