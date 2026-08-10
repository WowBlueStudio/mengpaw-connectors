# SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
# SPDX-License-Identifier: MIT
<#
.SYNOPSIS
    把连接器 AAR 打包为 Android 宿主可加载的 fat dex JAR。

.DESCRIPTION
    连接器经 plugins.json 以 remote 插件分发。宿主 PluginRuntimeLoader 只接受
    含 classes.dex 的 JAR (DexClassLoader), 标准 AAR 内是 classes.jar 字节码,
    无法直接加载。本脚本:
      1. assembleRelease 构建全部 AAR
      2. 用 Android SDK d8 把各连接器 classes.jar + common + jsch/okhttp/okio
         合并为 classes.dex
      3. 打包为 <module>-plugin.jar (含 META-INF/plugin-class 主类清单)
    产物输出 releases/connectors/, 供 plugins-v* tag 发布 (upload + plugins.json 指向 .jar)。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/package-connectors.ps1
#>

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $RootDir "releases\connectors"

# ── 1. Android SDK 工具定位 ─────────────────────────────────────
$sdkDir = (Get-Content (Join-Path $RootDir "local.properties") | Select-String '^sdk\.dir=').ToString().Split('=')[1].Trim()
$d8 = Get-ChildItem (Join-Path $sdkDir "build-tools") -Directory |
    Sort-Object Name -Descending | Select-Object -First 1 |
    ForEach-Object { Join-Path $_.FullName "d8.bat" }
$androidJar = Get-ChildItem (Join-Path $sdkDir "platforms") -Directory |
    Sort-Object Name -Descending | Select-Object -First 1 |
    ForEach-Object { Join-Path $_.FullName "android.jar" }
if (-not (Test-Path $d8)) { Write-Error "找不到 d8.bat (Android SDK build-tools)" }
if (-not (Test-Path $androidJar)) { Write-Error "找不到 android.jar (Android SDK platforms)" }
Write-Host "d8: $d8" -ForegroundColor Cyan
Write-Host "android.jar: $androidJar" -ForegroundColor Cyan

# ── 2. 依赖 jar (Gradle 缓存) ───────────────────────────────────
$cacheRoot = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"
function Find-Jar([string]$Group, [string]$Artifact, [string]$Version) {
    # Gradle 缓存目录: files-2.1/<group>/<artifact>/<version>/<hash>/<jar>
    $dir = Join-Path $cacheRoot (Join-Path $Group (Join-Path $Artifact $Version))
    if (Test-Path $dir) {
        $jar = Get-ChildItem $dir -Recurse -Filter "*.jar" |
            Where-Object { $_.FullName -notmatch 'sources|javadoc' } | Select-Object -First 1
        if ($jar) { return $jar.FullName }
    }
    return $null
}

$depJars = @(
    (Find-Jar "com.github.mwiede" "jsch" "0.2.26"),
    (Find-Jar "com.squareup.okhttp3" "okhttp" "4.12.0"),
    (Find-Jar "com.squareup.okio" "okio-jvm" "3.9.1")
) | Where-Object { $_ -ne $null }
if ($depJars.Count -eq 0) {
    Write-Error "Gradle 缓存中找不到 jsch/okhttp/okio — 先执行 .\gradlew.bat assembleRelease"
}
Write-Host "依赖 jars ($($depJars.Count)): $($depJars -join ', ')" -ForegroundColor DarkGray

# ── 3. 构建 AAR ─────────────────────────────────────────────────
Push-Location $RootDir
try {
    & .\gradlew.bat assembleRelease --console=plain 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease 失败 (exit $LASTEXITCODE)" }
} finally { Pop-Location }

# ── 4. 主类清单 ─────────────────────────────────────────────────
$manifest = @{}
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($line in [System.IO.File]::ReadAllLines((Join-Path $PSScriptRoot "plugin-class.txt"), $utf8)) {
    $t = $line.Trim()
    if (-not $t -or $t.StartsWith("#")) { continue }
    $parts = $t -split ":", 2
    if ($parts.Count -eq 2) { $manifest[$parts[0].Trim()] = $parts[1].Trim() }
}

# ── 5. 逐模块打包 ───────────────────────────────────────────────
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$modules = @("plugin-connector-openclaw", "plugin-connector-qwenpaw", "plugin-connector-claude-code", "plugin-connector-reasonix", "plugin-connector-trae")
$commonClassesJar = Join-Path $RootDir "plugin-connector-common\build\outputs\aar\plugin-connector-common-release.aar"

foreach ($module in $modules) {
    $mainClass = $manifest[$module]
    if (-not $mainClass) { Write-Warning "$module 无主类清单, 跳过"; continue }
    $aar = Get-ChildItem (Join-Path $RootDir "$module\build\outputs\aar") -Filter "*-release.aar" -ErrorAction Stop | Select-Object -First 1
    $work = Join-Path $env:TEMP ("connector-pack-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path (Join-Path $work "in") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $work "dex") | Out-Null
    try {
        # 解 AAR → classes.jar; common AAR → classes.jar
        Push-Location (Join-Path $work "in")
        & jar xf $aar.FullName classes.jar | Out-Null
        Rename-Item (Join-Path $work "in\classes.jar") "$module.jar"
        & jar xf $commonClassesJar classes.jar | Out-Null
        Rename-Item (Join-Path $work "in\classes.jar") "common.jar"
        Pop-Location

        # d8: 模块 + common + 依赖 → classes.dex
        $inputs = @((Join-Path $work "in\$module.jar"), (Join-Path $work "in\common.jar")) + $depJars
        & $d8 --release --min-api 26 --lib $androidJar --output (Join-Path $work "dex") $inputs 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "d8 失败 (exit $LASTEXITCODE)" }

        # 组装 jar: classes.dex + META-INF/plugin-class
        $stage = Join-Path $work "stage"
        New-Item -ItemType Directory -Force -Path (Join-Path $stage "META-INF") | Out-Null
        Copy-Item (Join-Path $work "dex\classes.dex") (Join-Path $stage "classes.dex")
        $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
        [System.IO.File]::WriteAllText((Join-Path $stage "META-INF\plugin-class"), $mainClass, $utf8NoBom)
        $outJar = Join-Path $OutDir "$module-plugin.jar"
        Push-Location $stage
        & jar cf $outJar classes.dex META-INF/plugin-class | Out-Null
        Pop-Location

        $kb = [math]::Round((Get-Item $outJar).Length / 1KB, 1)
        Write-Host "  -> $outJar ($kb KB, 主类 $mainClass)" -ForegroundColor Green
    } catch {
        Write-Host "  -> FAILED: $_" -ForegroundColor Red
    } finally {
        Pop-Location
        Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "完成 — 产物目录: $OutDir" -ForegroundColor Cyan
