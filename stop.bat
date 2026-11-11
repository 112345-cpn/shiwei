@echo off
REM ===============================================
REM  Shiwei HM-DianPing  -  stop all containers
REM ===============================================
setlocal
chcp 65001 >nul
cd /d "%~dp0"

set COMPOSE=docker compose
where docker-compose >nul 2>&1
if not errorlevel 1 set COMPOSE=docker-compose

echo Stopping containers ...
%COMPOSE% down

echo.
echo Data volumes are kept:
echo   hmdp-mysql-data  hmdp-redis-data  hmdp-rabbitmq-data
echo   hmdp-app-logs    hmdp-upload-data
echo To wipe them too, run:  %COMPOSE% down -v
echo.
pause
endlocal
