# SPDX-FileCopyrightText: 2026 深圳哇蓝文化科技有限公司 (ShenZhen wowblue culture and technology CO.,LTD.)
# SPDX-License-Identifier: MIT
<#
.SYNOPSIS
    把全部外置插件 AAR 打包为 Android 宿主可加载的 dex JAR。

.DESCRIPTION
    外置插件经 plugins.json 以 status=remote 分发。宿主 PluginRuntimeLoader 只接受
    含 classes.dex 的 JAR (DexClassLoader), 标准 AAR 内是 classes.jar 字节码,
    无法直接加载。本脚本:
      1. assembleRelease 构建全部模块 (8 普通 + 5 连接器 + common)
      2. 用 Android SDK d8 把各插件 classes.jar 转 classes.dex
      3. 连接器额外合并 common + jsch/okhttp/okio (宿主未内置, 需 fat)
      4. 打包为 <module>-release.jar / <module>-plugin.jar (含 META-INF/plugin-class)
    普通插件依赖 ktor/serialization/coroutines, 宿主 APK 已内置, 无需 fat。
    产物输出 releases/plugins/, 供 plugins-v* tag 发布 (upload + plugins.json 指向 .jar)。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/package-plugins.ps1
#>

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $RootDir "releases\plugins"

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

# ── 2. 模块分组 (连接器需 fat, 普通插件不需) ─────────────────────
$connectors = @(
    "plugin-connector-openclaw", "plugin-connector-qwenpaw",
    "plugin-connector-claude-code", "plugin-connector-reasonix", "plugin-connector-trae"
)
$plain = @(
    "plugin-update", "plugin-translate", "plugin-error-report",
    "plugin-render", "plugin-comfy", "plugin-browser-push",
    "plugin-browser-search", "plugin-browser-mcp"
)
$modules = @($plain + $connectors + @("plugin-connector-yinxiang"))

# ── 3. 依赖 jar (连接器 fat 用, Gradle 缓存) ────────────────────
$cacheRoot = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1"
function Find-Jar([string]$Group, [string]$Artifact, [string]$Version) {
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
    Write-Warning "Gradle 缓存中找不到 jsch/okhttp/okio — 连接器打包将跳过 fat 依赖, 请先执行 .\gradlew.bat assembleRelease"
}
Write-Host "依赖 jars ($($depJars.Count)): $($depJars -join ', ')" -ForegroundColor DarkGray

# ── 4. 构建全部 AAR ─────────────────────────────────────────────
Push-Location $RootDir
try {
& .\gradlew.bat assembleRelease --console=plain 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw "assembleRelease 失败 (exit $LASTEXITCODE)" }
} finally { Pop-Location }

# ── 4.5 印象笔记 fat 依赖 (evernote-api/jsoup 宿主未内置; 需先构建让 Gradle 拉入缓存) ──
$yinxiangDeps = @(
    (Find-Jar "com.evernote" "evernote-api" "1.25.1"),
    (Find-Jar "org.jsoup" "jsoup" "1.17.2")
) | Where-Object { $_ -ne $null }
if ($yinxiangDeps.Count -lt 2) {
    Write-Warning "Gradle 缓存中找不到 evernote-api/jsoup — plugin-connector-yinxiang 打包将缺少依赖"
}

# ── 5. 主类清单 ─────────────────────────────────────────────────
$manifest = @{}
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($line in [System.IO.File]::ReadAllLines((Join-Path $PSScriptRoot "plugin-class.txt"), $utf8)) {
    $t = $line.Trim()
    if (-not $t -or $t.StartsWith("#")) { continue }
    $parts = $t -split ":", 2
    if ($parts.Count -eq 2) { $manifest[$parts[0].Trim()] = $parts[1].Trim() }
}
if ($manifest.Count -eq 0) { Write-Error "主类清单为空 — 检查 scripts/plugin-class.txt" }

# ── 6. 逐模块打包 ───────────────────────────────────────────────
if (Test-Path $OutDir) { Remove-Item $OutDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$commonAar = Join-Path $RootDir "plugin-connector-common\build\outputs\aar\plugin-connector-common-release.aar"
$commonExists = Test-Path $commonAar

foreach ($module in $modules) {
    $mainClass = $manifest[$module]
    if (-not $mainClass) { Write-Warning "$module 无主类清单, 跳过"; continue }
    $aar = Get-ChildItem (Join-Path $RootDir "$module\build\outputs\aar") -Filter "*-release.aar" -ErrorAction Stop | Select-Object -First 1
    $work = Join-Path $env:TEMP ("plugin-pack-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Force -Path (Join-Path $work "in") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $work "dex") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $work "stage\META-INF") | Out-Null
    try {
        # 解 AAR → classes.jar
        Push-Location (Join-Path $work "in")
        & jar xf $aar.FullName classes.jar | Out-Null
        Rename-Item (Join-Path $work "in\classes.jar") "$module.jar"
        Pop-Location

        $inputs = @(Join-Path $work "in\$module.jar")
        $fatRequired = ($module -in $connectors) -or ($module -eq "plugin-connector-yinxiang")
        $moduleDeps = if ($module -eq "plugin-connector-yinxiang") { $yinxiangDeps } else { $depJars }
        if ($fatRequired) {
            if (-not $commonExists) { throw "缺少 plugin-connector-common AAR" }
            Push-Location (Join-Path $work "in")
            & jar xf $commonAar classes.jar | Out-Null
            Rename-Item (Join-Path $work "in\classes.jar") "common.jar"
            Pop-Location
            $inputs = $inputs + @(Join-Path $work "in\common.jar") + $moduleDeps
        }

        & $d8 --release --min-api 26 --lib $androidJar --output (Join-Path $work "dex") $inputs 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw "d8 失败 (exit $LASTEXITCODE)" }

        Copy-Item (Join-Path $work "dex\classes.dex") (Join-Path $work "stage\classes.dex")
        [System.IO.File]::WriteAllText((Join-Path $work "stage\META-INF\plugin-class"), $mainClass, $utf8)
        $suffix = if ($fatRequired) { "plugin" } else { "release" }
        $outJar = Join-Path $OutDir "$module-$suffix.jar"
        Push-Location (Join-Path $work "stage")
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
