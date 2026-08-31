@echo off
REM ==============================================================================
REM  stop_local.bat - Kill all JinBooks service processes.
REM  Order: Nginx -> Backend (java) -> Redis -> MySQL  (reverse of start)
REM ==============================================================================
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "ENVS_DIR=%ROOT_DIR%\jinbooks_envs"
set "PID_DIR=%ENVS_DIR%\pids"

echo.
echo ============================================================
echo   JinBooks - stopping services
echo ============================================================
echo.

echo [1/4] stopping Nginx ...
call :KILL_IMG "nginx.exe"
echo.

echo [2/4] stopping Backend (java) ...
call :KILL_IMG "java.exe"
echo.

echo [3/4] stopping Redis ...
call :KILL_IMG "redis-server.exe"
echo.

echo [4/4] stopping MySQL ...
call :KILL_IMG "mysqld.exe"
echo.

REM cleanup pid files
if exist "%PID_DIR%\nginx.pid"   del /f /q "%PID_DIR%\nginx.pid"   >nul 2>&1
if exist "%PID_DIR%\backend.pid" del /f /q "%PID_DIR%\backend.pid" >nul 2>&1
if exist "%PID_DIR%\redis.pid"   del /f /q "%PID_DIR%\redis.pid"   >nul 2>&1
if exist "%PID_DIR%\mysql.pid"   del /f /q "%PID_DIR%\mysql.pid"   >nul 2>&1

timeout /t 1 /nobreak >nul

echo ============================================================
echo   All JinBooks services stopped
echo ============================================================
echo.
goto :END

REM =================================================================
REM  Helpers
REM =================================================================
:KILL_IMG
set "IMG=%~1"
set "KILLED=0"
for /f "tokens=2" %%p in ('tasklist /FI "IMAGENAME eq %IMG%" /NH 2^>nul') do (
    taskkill /F /PID %%p >nul 2>&1
    set "KILLED=1"
)
if "%KILLED%"=="1" (
    echo   [OK] killed %IMG%
) else (
    echo   [SKIP] %IMG% not running
)
goto :eof

:END
endlocal
exit /b 0
