@echo off
REM ==============================================================================
REM  restart_local.bat - Stop and restart all JinBooks services.
REM ==============================================================================
setlocal EnableExtensions

cd /d "%~dp0"
echo.
echo ============================================================
echo   JinBooks - restart
echo ============================================================
echo.

call "%~dp0stop_local.bat"

echo.
echo Waiting 3s for ports to free ...
timeout /t 3 /nobreak >nul
echo.

call "%~dp0start_local.bat"
if errorlevel 1 exit /b 1

endlocal
exit /b 0
