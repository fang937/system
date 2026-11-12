<#
  dev.ps1 - 本地启动（不依赖 Docker），复用根目录 .env

  用法：
    powershell -ExecutionPolicy Bypass -File scripts\dev.ps1
    powershell -ExecutionPolicy Bypass -File scripts\dev.ps1 -DryRun        只打印解析后的配置
    powershell -ExecutionPolicy Bypass -File scripts\dev.ps1 -BackendOnly
    powershell -ExecutionPolicy Bypass -File scripts\dev.ps1 -FrontendOnly

  说明：
    .env 是为 docker compose 写的（MYSQL_* 给 mysql 镜像用），
    而后端 Spring 读的是 DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD。
    本脚本负责把前者映射成后者，再注入当前进程环境变量，
    子进程（mvn / npm）会继承，所以不需要 alter .env 本身。
#>
[CmdletBinding()]
param(
    [switch]$BackendOnly,
    [switch]$FrontendOnly,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

function Import-DotEnv {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "[warn] 未找到 $Path，将使用 application.yml 的默认值" -ForegroundColor Yellow
        return
    }
    foreach ($raw in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $raw.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { continue }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if ($val.Length -ge 2) {
            $q = $val[0]
            if (($q -eq '"' -or $q -eq "'") -and $val[$val.Length - 1] -eq $q) {
                $val = $val.Substring(1, $val.Length - 2)
            }
        }
        Set-Item -Path ("Env:" + $key) -Value $val
    }
}

Import-DotEnv -Path (Join-Path $root '.env')

# ---- 映射：compose 变量 -> Spring 变量 ----
if (-not $env:DB_HOST) { $env:DB_HOST = 'localhost' }
if (-not $env:DB_PORT) { if ($env:MYSQL_PORT) { $env:DB_PORT = $env:MYSQL_PORT } else { $env:DB_PORT = '3306' } }
if (-not $env:DB_NAME) { if ($env:MYSQL_DATABASE) { $env:DB_NAME = $env:MYSQL_DATABASE } else { $env:DB_NAME = 'lab_select_v2' } }
if (-not $env:DB_USER) { $env:DB_USER = 'root' }
if (-not $env:DB_PASSWORD) {
    if ($env:MYSQL_PASSWORD) { $env:DB_PASSWORD = $env:MYSQL_PASSWORD }
    elseif ($env:MYSQL_ROOT_PASSWORD) { $env:DB_PASSWORD = $env:MYSQL_ROOT_PASSWORD }
}
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }
if (-not $env:WEB_PORT) { $env:WEB_PORT = '8089' }

$beDir = Join-Path $root 'backend'
$feDir = Join-Path $root 'frontend'

if ($DryRun) {
    Write-Host ""
    Write-Host "解析后的运行配置（.env -> 进程环境变量）：" -ForegroundColor Cyan
    $rows = @(
        @{ K = 'DB_HOST';          V = $env:DB_HOST },
        @{ K = 'DB_PORT';          V = $env:DB_PORT },
        @{ K = 'DB_NAME';          V = $env:DB_NAME },
        @{ K = 'DB_USER';          V = $env:DB_USER },
        @{ K = 'DB_PASSWORD';      V = $(if ($env:DB_PASSWORD) { '****** (已注入)' } else { '(空)' }) },
        @{ K = 'SERVER_PORT';      V = $env:SERVER_PORT },
        @{ K = 'WEB_PORT';         V = $env:WEB_PORT },
        @{ K = 'JWT_SECRET';       V = $(if ($env:JWT_SECRET) { $env:JWT_SECRET.Substring(0, [Math]::Min(8, $env:JWT_SECRET.Length)) + '...(已注入)' } else { '(空)' }) },
        @{ K = 'JWT_EXPIRE_HOURS'; V = $env:JWT_EXPIRE_HOURS },
        @{ K = 'DEMO_DATA';        V = $env:DEMO_DATA },
        @{ K = 'JAVA_OPTS';        V = $env:JAVA_OPTS }
    )
    $rows | ForEach-Object { Write-Host ("  {0,-18} {1}" -f $_.K, $_.V) }
    Write-Host ""
    Write-Host "文件检查：" -ForegroundColor Cyan
    "  backend\pom.xml        : {0}" -f $(if (Test-Path (Join-Path $beDir 'pom.xml')) { 'OK' } else { '缺失' })
    "  frontend\package.json  : {0}" -f $(if (Test-Path (Join-Path $feDir 'package.json')) { 'OK' } else { '缺失' })
    "  frontend\node_modules  : {0}" -f $(if (Test-Path (Join-Path $feDir 'node_modules')) { 'OK（无需 npm install）' } else { '缺失（需 npm install）' })
    Write-Host ""
    return
}

# ---- 前置检查 ----
$needBackend = -not $FrontendOnly
$needFrontend = -not $BackendOnly

if ($needBackend) {
    if (-not (Test-Path (Join-Path $beDir 'pom.xml'))) {
        Write-Host "[error] 找不到 backend\pom.xml，后端源码不在，无法启动。请先恢复 backend 源码。" -ForegroundColor Red
        exit 1
    }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        Write-Host "[error] PATH 里找不到 mvn，请安装 Maven。" -ForegroundColor Red
        exit 1
    }
}

if ($needFrontend) {
    if (-not (Test-Path (Join-Path $feDir 'package.json'))) {
        Write-Host "[error] 找不到 frontend\package.json，前端源码不在（node_modules 有也不能跑）。" -ForegroundColor Red
        exit 1
    }
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        Write-Host "[error] PATH 里找不到 npm，请安装 Node.js。" -ForegroundColor Red
        exit 1
    }
}

# ---- 数据库可达性 ----
try {
    $ok = Test-NetConnection -ComputerName $env:DB_HOST -Port ([int]$env:DB_PORT) -InformationLevel Quiet -WarningAction SilentlyContinue
    if ($ok) {
        Write-Host "[ok] MySQL $($env:DB_HOST):$($env:DB_PORT) 可达" -ForegroundColor Green
    } else {
        Write-Host "[warn] MySQL $($env:DB_HOST):$($env:DB_PORT) 连不上，后端启动会失败。请先启动本机 MySQL 服务。" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[warn] 无法探测 MySQL 端口，跳过检查" -ForegroundColor Yellow
}

# ---- 启动 ----
$javaOpts = $env:JAVA_OPTS
Write-Host ""
Write-Host "后端：http://localhost:$($env:SERVER_PORT)   前端：http://localhost:$($env:WEB_PORT)" -ForegroundColor Cyan
Write-Host ""

if ($needBackend) {
    # spring-boot:run 默认 fork 出独立 JVM，MAVEN_OPTS 只作用于 Maven 自身，
    # 所以 JVM 参数必须走 -Dspring-boot.run.jvmArguments 才会真正生效。
    $pomPath = Join-Path $beDir 'pom.xml'
    if ($javaOpts) {
        $cmd = "mvn -f '$pomPath' spring-boot:run '-Dspring-boot.run.jvmArguments=$javaOpts'"
    } else {
        $cmd = "mvn -f '$pomPath' spring-boot:run"
    }
    Write-Host "[start] 后端 -> 新窗口 (JVM: $(if ($javaOpts) { $javaOpts } else { '默认' }))" -ForegroundColor Green
    Start-Process -FilePath 'powershell.exe' -ArgumentList "-NoExit -NoProfile -Command `"$cmd`"" -WorkingDirectory $beDir | Out-Null
}

if ($needFrontend) {
    if (-not (Test-Path (Join-Path $feDir 'node_modules'))) {
        Write-Host "[install] 首次运行，安装前端依赖（registry=$($env:NPM_REGISTRY)）..." -ForegroundColor Yellow
        Push-Location $feDir
        try {
            if ($env:NPM_REGISTRY) { npm install --registry $env:NPM_REGISTRY } else { npm install }
        } finally { Pop-Location }
    }
    $cmd = "npm run dev -- --port $($env:WEB_PORT) --host"
    Write-Host "[start] 前端 -> 新窗口" -ForegroundColor Green
    Start-Process -FilePath 'powershell.exe' -ArgumentList "-NoExit -NoProfile -Command `"$cmd`"" -WorkingDirectory $feDir | Out-Null
}

Write-Host ""
Write-Host "已在新窗口启动。关闭对应窗口即停止服务。Ctrl+C 不会停止它们。" -ForegroundColor Cyan
