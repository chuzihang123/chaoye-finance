@echo off
REM ==============================================================================
REM  start_one_window.bat - launch all 4 JinBooks services and stream logs in
REM  THIS window (single-window mode).
REM
REM    - chcp 65001 so the Spring Boot UTF-8 log lines render Chinese correctly.
REM    - PowerShell launches each service as a detached background process.
REM    - After services are up, this window tails mysql.log / redis.log /
REM      backend.log / nginx.log line-by-line, colour-coded by file.
REM
REM  Stop:
REM    - Ctrl+C in this window stops tailing; services keep running.
REM    - Run stop_local.bat to kill all services.
REM ==============================================================================
chcp 65001 >nul
setlocal
cd /d "%~dp0"
set "ENVS_DIR=%~dp0jinbooks_envs"
set "BIN_DIR=%ENVS_DIR%\bin"
set "LOG_DIR=%ENVS_DIR%\logs"

echo.
echo ============================================================
echo   JinBooks - one-window starter
echo   ROOT_DIR = %~dp0
echo ============================================================
echo.

REM --- 1. Stop any leftover services ---
echo [cleanup] stopping any leftover services ...
call "%~dp0stop_local.bat" 2>nul
echo.

REM --- 2. Launch all 4 services (detached background) ---
powershell -NoProfile -ExecutionPolicy Bypass -File "%BIN_DIR%\start_all.ps1"
if errorlevel 1 (
    echo.
    echo [FAILED] one or more services did not start cleanly. Check the log
    echo          files under %LOG_DIR% for details.
    echo.
    pause
    goto :END
)

echo.
echo ============================================================
echo   Live log tail (Ctrl+C to stop tailing, services keep running)
echo   Run stop_local.bat to kill services
echo ============================================================

REM --- 3. Tail all 4 logs in this window ---
powershell -NoProfile -ExecutionPolicy Bypass -File "%BIN_DIR%\tail_logs.ps1" -LogDir "%LOG_DIR%"

echo.
echo [done] tail stopped. Services still running in background.
echo        Run stop_local.bat to kill them.

:END
endlocal
exit /b 0
