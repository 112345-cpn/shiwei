# ===============================================
# Local starter for Hm-DianPing Spring Boot
# Connects to Docker-based MySQL/Redis/RabbitMQ
# PowerShell 5.1 safe (ASCII only)
# ===============================================

$env:DB_HOST = "localhost"
$env:DB_PORT = "3306"
$env:DB_NAME = "hmdp"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "hmdp123456"

$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = ""

$env:RABBITMQ_HOST = "localhost"
$env:RABBITMQ_PORT = "5672"
$env:RABBITMQ_USERNAME = "guest"
$env:RABBITMQ_PASSWORD = "guest"

Write-Output "=========================================="
Write-Output "  HM-DianPing Local Start"
Write-Output "=========================================="
Write-Output ("DB:        {0}:{1}/{2}" -f $env:DB_HOST, $env:DB_PORT, $env:DB_NAME)
Write-Output ("Redis:     {0}:{1}" -f $env:REDIS_HOST, $env:REDIS_PORT)
Write-Output ("RabbitMQ:  {0}:{1}" -f $env:RABBITMQ_HOST, $env:RABBITMQ_PORT)
Write-Output "=========================================="
Write-Output ""

# Make sure we are in the project root (script-relative)
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Output "[1/3] Checking dependent services..."
$services = @("hmdp-mysql", "hmdp-redis", "hmdp-rabbitmq")
$ok = $true
foreach ($svc in $services) {
    $status = docker inspect -f "{{.State.Health.Status}}" $svc 2>$null
    if ($status -eq "healthy") {
        Write-Output ("  [OK] {0} - healthy" -f $svc)
    } else {
        Write-Output ("  [FAIL] {0} - {1}" -f $svc, $status)
        $ok = $false
    }
}

if (-not $ok) {
    Write-Output ""
    Write-Output "[ERROR] Some services are not healthy. Start them first:"
    Write-Output "  docker compose up -d"
    exit 1
}

Write-Output ""
Write-Output "[2/3] Cleaning leftover java processes..."
Get-Process java -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $proc = $_
        $started = $null
        try { $started = (Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $proc.Id) -ErrorAction Stop).CommandLine } catch { }
        $windowTitle = $null
        try { $windowTitle = $proc.MainWindowTitle } catch { }
        if ($started -like "*hmdp*" -or $windowTitle -like "*hmdp*") {
            Write-Output ("  Killing PID {0}" -f $proc.Id)
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        }
    } catch { }
}

Write-Output ""
Write-Output "[3/3] Starting Spring Boot..."
Write-Output "  (Press Ctrl+C to stop)"
Write-Output ""
mvn spring-boot:run -Dspring-boot.run.profiles=dev
