@echo off
REM ===============================================
REM  Shiwei HM-DianPing  -  one click start
REM  Double click this file, or run:  start.bat
REM ===============================================
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

echo ==========================================
echo   Shiwei  HM-DianPing  -  starting
echo ==========================================
echo.

REM ---------- 1. Docker must be installed ----------
where docker >nul 2>&1
if errorlevel 1 (
    echo [ERROR] docker not found in PATH.
    echo         Install Docker Desktop first.
    pause
    exit /b 1
)

REM ---------- 2. Docker engine must be running ----------
docker info >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Docker engine is not running.
    echo         Start Docker Desktop, wait for "Engine running", then retry.
    pause
    exit /b 1
)

REM ---------- 3. .env from template on first run ----------
if not exist ".env" (
    echo [INFO ] .env not found, creating from .env.example
    copy /y ".env.example" ".env" >nul
)

REM ---------- 4. pick a working compose command ----------
REM  On some machines the docker CLI does not discover the compose plugin,
REM  so "docker compose" fails with "unknown command". The standalone
REM  docker-compose.exe still works, so try both and prefer the plugin form.
set COMPOSE=docker compose
where docker-compose >nul 2>&1
if not errorlevel 1 set COMPOSE=docker-compose

REM ---------- 5. Build + start ----------
echo [1/2] %COMPOSE% up -d --build
echo       first run compiles the jar inside a Maven image, 3-8 min is normal
%COMPOSE% up -d --build
if errorlevel 1 (
    echo.
    echo [ERROR] compose failed. See output above.
    pause
    exit /b 1
)

REM ---------- 6. Wait until the web port answers ----------
echo.
echo [2/2] waiting for http://localhost:8080 ...
set READY=0
for /l %%i in (1,1,90) do (
    if "!READY!"=="0" (
        curl -sf -o nul http://localhost:8080/ >nul 2>&1
        if not errorlevel 1 (
            set READY=1
        ) else (
            ping -n 3 127.0.0.1 >nul
        )
    )
)

echo.
%COMPOSE% ps
echo.
echo ==========================================
echo   Front end : http://localhost:8080
echo   Back end  : http://localhost:8081
echo   RabbitMQ  : http://localhost:15672   (guest / guest)
echo   Stop      : stop.bat
echo ==========================================
echo.

start "" http://localhost:8080
endlocal
