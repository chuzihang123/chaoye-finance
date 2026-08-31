@echo off
REM ==============================================================================
REM  init_local.bat - JinBooks local env init (Windows)
REM  - prepare dirs, download MySQL/Redis/JRE/Nginx if missing
REM  - build backend jar (gradle), frontend dist (npm)
REM  - init MySQL data dir, create db, import SQL
REM  - write nginx.conf
REM
REM  NOTE: run as Administrator; first run downloads ~700MB and takes a while
REM ==============================================================================
setlocal EnableExtensions

cd /d "%~dp0"
set "ROOT_DIR=%~dp0"
if "%ROOT_DIR:~-1%"=="\" set "ROOT_DIR=%ROOT_DIR:~0,-1%"
set "ENVS_DIR=%ROOT_DIR%\jinbooks_envs"
set "LIB_DIR=%ENVS_DIR%\lib"
set "PS_EXE=powershell.exe"
set "PSEXE_RUN=%PS_EXE% -NoProfile -ExecutionPolicy Bypass"
set "ENV_FILE=%ENVS_DIR%\.env"

echo.
echo ============================================================
echo   JinBooks local env init
echo   ROOT_DIR = %ROOT_DIR%
echo ============================================================
echo.

REM ---------- 1. 准备目录 ----------
echo [1/8] preparing directories ...
%PSEXE_RUN% -File "%LIB_DIR%\common.ps1" -Action Init -RootDir "%ROOT_DIR%" -EnvsDir "%ENVS_DIR%"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 2. .env ----------
echo [2/8] loading .env ...
%PSEXE_RUN% -File "%LIB_DIR%\common.ps1" -Action PrintEnv -RootDir "%ROOT_DIR%" -EnvsDir "%ENVS_DIR%"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 3. 检查/下载/解压 环境组件 ----------
echo [3/8] preparing MySQL 8.0 ...
if not exist "%ENVS_DIR%\bin\mysql\bin\mysqld.exe" (
    call :DOWNLOAD_EXTRACT "MySQL" "%ENV_FILE%" "MYSQL_DOWNLOAD_URL" "%ENVS_DIR%\downloads\mysql.zip" "%ENVS_DIR%\bin" "mysql"
) else (
    echo   [SKIP] MySQL already installed
)
echo.

echo [3/8] preparing Redis ...
if not exist "%ENVS_DIR%\bin\redis\redis-server.exe" (
    call :DOWNLOAD_EXTRACT "Redis" "%ENV_FILE%" "REDIS_DOWNLOAD_URL" "%ENVS_DIR%\downloads\redis.zip" "%ENVS_DIR%\bin" "redis"
) else (
    echo   [SKIP] Redis already installed
)
echo.

echo [3/8] preparing JRE 21 ...
if not exist "%ENVS_DIR%\bin\jre\bin\java.exe" (
    call :DOWNLOAD_EXTRACT "JRE" "%ENV_FILE%" "JRE_DOWNLOAD_URL" "%ENVS_DIR%\downloads\jre.zip" "%ENVS_DIR%\bin" "jre"
) else (
    echo   [SKIP] JRE already installed
)
echo.

echo [3/8] preparing Nginx ...
if not exist "%ENVS_DIR%\bin\nginx\nginx.exe" (
    call :DOWNLOAD_EXTRACT "Nginx" "%ENV_FILE%" "NGINX_DOWNLOAD_URL" "%ENVS_DIR%\downloads\nginx.zip" "%ENVS_DIR%\bin" "nginx"
) else (
    echo   [SKIP] Nginx already installed
)
echo.

REM ---------- 4. 复制 SQL ----------
echo [4/8] copying SQL scripts ...
%PSEXE_RUN% -File "%LIB_DIR%\backend.ps1" -Action CopySql -RootDir "%ROOT_DIR%" -EnvsDir "%ENVS_DIR%" -SqlDestDir "%ENVS_DIR%\sql"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 5. 构建后端 jar ----------
echo [5/8] building backend jar (this may take a few minutes) ...
%PSEXE_RUN% -File "%LIB_DIR%\backend.ps1" -Action Build -RootDir "%ROOT_DIR%" -EnvsDir "%ENVS_DIR%" -BackendDir "%ENVS_DIR%\backend" -JarPath "%ENVS_DIR%\backend\app.jar"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 6. 构建前端 dist ----------
echo [6/8] building frontend dist ...
%PSEXE_RUN% -File "%LIB_DIR%\frontend.ps1" -Action Build -RootDir "%ROOT_DIR%" -EnvsDir "%ENVS_DIR%" -FrontendDir "%ENVS_DIR%\frontend\dist"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 7. 写 nginx.conf ----------
echo [7/8] generating nginx.conf ...
%PSEXE_RUN% -File "%LIB_DIR%\frontend.ps1" -Action WriteNginxConf -EnvsDir "%ENVS_DIR%" -NginxDir "%ENVS_DIR%\bin\nginx" -ConfDir "%ENVS_DIR%\conf" -NginxConf "%ENVS_DIR%\conf\nginx.conf" -FrontendDir "%ENVS_DIR%\frontend\dist"
if errorlevel 1 goto :FAIL
echo.

REM ---------- 8. 初始化 MySQL + 导入 SQL ----------
echo [8/8] initializing MySQL data dir ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action InitDataDir -MySqlDir "%ENVS_DIR%\bin\mysql" -DataDir "%ENVS_DIR%\data\mysql" -TmpDir "%ENVS_DIR%\tmp" -ConfDir "%ENVS_DIR%\conf" -Port 3306
if errorlevel 1 goto :FAIL
echo.

echo [8/8] starting MySQL (for db init) ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action Start -MySqlDir "%ENVS_DIR%\bin\mysql" -ConfDir "%ENVS_DIR%\conf" -LogFile "%ENVS_DIR%\logs\mysql_init.log" -PidFile "%ENVS_DIR%\pids\mysql_init.pid" -Port 3306
if errorlevel 1 goto :FAIL
echo.

echo [8/8] waiting for MySQL ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action WaitReady -MySqlDir "%ENVS_DIR%\bin\mysql" -Port 3306 -TimeoutSec 60
if errorlevel 1 goto :FAIL
echo.

echo [8/8] setting root password ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action SetRootPassword -MySqlDir "%ENVS_DIR%\bin\mysql" -Port 3306 -RootPwd "JinbooksRoot321!"
if errorlevel 1 goto :FAIL
echo.

echo [8/8] creating database jinbooks ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action CreateDb -MySqlDir "%ENVS_DIR%\bin\mysql" -Port 3306 -RootPwd "JinbooksRoot321!" -Database "jinbooks"
if errorlevel 1 goto :FAIL
echo.

echo [8/8] importing init SQL ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action ImportSql -MySqlDir "%ENVS_DIR%\bin\mysql" -Port 3306 -RootPwd "JinbooksRoot321!" -Database "jinbooks" -SqlFile "%ENVS_DIR%\sql\jinbooks_v1.1.0.sql"
if errorlevel 1 goto :FAIL
echo.

echo [8/8] stopping init MySQL ...
%PSEXE_RUN% -File "%LIB_DIR%\mysql.ps1" -Action Stop -MySqlDir "%ENVS_DIR%\bin\mysql" -PidFile "%ENVS_DIR%\pids\mysql_init.pid" -Port 3306
echo.

echo.
echo ============================================================
echo   INIT DONE!
echo.
echo   Next:  double-click start_local.bat to start all services
echo   URL:   http://127.0.0.1:18080
echo ============================================================
echo.
endlocal
exit /b 0

REM ==============================================================================
REM  Tool: download and extract
REM  URL supports | separated alternatives or local file path
REM ==============================================================================
:DOWNLOAD_EXTRACT
setlocal
set "DL_NAME=%~1"
set "ENV_FILE=%~2"
set "URL_KEY=%~3"
set "DEST_ZIP=%~4"
set "EXTRACT_DIR=%~5"
set "SUBDIR=%~6"

REM read URL from .env
set "DL_URLS="
for /f "usebackq tokens=1,2 delims==" %%a in ("%ENV_FILE%") do (
    if /i "%%a"=="%URL_KEY%" set "DL_URLS=%%b"
)
if "%DL_URLS%"=="" (
    echo   [ERROR] %DL_NAME% URL not in .env: %URL_KEY%
    exit /b 1
)

REM call PowerShell to do download+extract+flatten
%PSEXE_RUN% -File "%LIB_DIR%\download.ps1" -Action DownloadAndExtract -Url "%DL_URLS%" -Dest "%DEST_ZIP%" -Name "%DL_NAME%" -MaxRetry 2 -ExtractTo "%EXTRACT_DIR%\%SUBDIR%_tmp"
if errorlevel 1 (
    echo   [ERROR] %DL_NAME% download/extract failed
    echo   [HINT] for offline env, change %URL_KEY% in .env to local zip path
    exit /b 1
)

REM flatten: move subdir to SUBDIR
if exist "%EXTRACT_DIR%\%SUBDIR%" rmdir /s /q "%EXTRACT_DIR%\%SUBDIR%"
if not exist "%EXTRACT_DIR%\%SUBDIR%" mkdir "%EXTRACT_DIR%\%SUBDIR%"

set "FOUND_DIR="
for /d %%d in ("%EXTRACT_DIR%\%SUBDIR%_tmp\*") do (
    if not defined FOUND_DIR set "FOUND_DIR=%%d"
)
if defined FOUND_DIR (
    xcopy /E /I /Y /Q "%FOUND_DIR%\*" "%EXTRACT_DIR%\%SUBDIR%\" 1>nul
) else (
    xcopy /E /I /Y /Q "%EXTRACT_DIR%\%SUBDIR%_tmp\*" "%EXTRACT_DIR%\%SUBDIR%\" 1>nul
)
rmdir /s /q "%EXTRACT_DIR%\%SUBDIR%_tmp" 2>nul

if not exist "%EXTRACT_DIR%\%SUBDIR%\bin\java.exe" (
    for /r "%EXTRACT_DIR%\%SUBDIR%" %%j in (java.exe) do (
        if exist "%%j" (
            echo   [INFO] found java.exe: %%j
        )
    )
)

echo   [OK] %DL_NAME% installed to %EXTRACT_DIR%\%SUBDIR%
exit /b 0

:FAIL
echo.
echo [FAILED] init failed, see errors above
echo   log: %ENVS_DIR%\logs\jinbooks_envs.log
echo.
endlocal
exit /b 1
