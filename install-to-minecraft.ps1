# RampDoor: 빌드 후 .minecraft\mods 에 설치합니다.
# 사용법 (PowerShell):  ./install-to-minecraft.ps1
param([string]$MinecraftDir = (Join-Path $env:APPDATA '.minecraft'))
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    Write-Host '[1/3] 빌드 (GameTest 포함, 1~2분 소요)...' -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot 'build-local.ps1')
    if ($LASTEXITCODE -ne 0) { throw "빌드 실패 (exit $LASTEXITCODE)" }

    Write-Host '[2/3] JAR 확인...' -ForegroundColor Cyan
    $jar = Get-ChildItem (Join-Path $PSScriptRoot 'build\libs\rampdoor-*.jar') |
        Where-Object { $_.Name -notlike '*-sources.jar' } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $jar) { throw '빌드 결과 JAR을 찾지 못했습니다.' }

    Write-Host '[3/3] mods 폴더에 설치...' -ForegroundColor Cyan
    $mods = Join-Path $MinecraftDir 'mods'
    if (-not (Test-Path $mods)) { New-Item -ItemType Directory -Path $mods | Out-Null }
    # Fabric must see one RampDoor version. Preserve previous versions outside the mods directory.
    $oldJars = @(Get-ChildItem -LiteralPath $mods -Filter 'rampdoor-*.jar' -File |
        Where-Object { $_.Name -ne $jar.Name })
    if ($oldJars.Count -gt 0) {
        $backup = Join-Path $MinecraftDir ('rampdoor-backups\' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
        New-Item -ItemType Directory -Path $backup -Force | Out-Null
        foreach ($oldJar in $oldJars) {
            Move-Item -LiteralPath $oldJar.FullName -Destination (Join-Path $backup $oldJar.Name)
        }
        Write-Host "  이전 RampDoor JAR 백업: $backup"
    }
    Copy-Item $jar.FullName (Join-Path $mods $jar.Name) -Force

    # Fabric API가 없으면 Gradle 캐시의 동일 버전을 함께 설치합니다.
    if (-not (Get-ChildItem $mods -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue)) {
        $api = Get-ChildItem (Join-Path $PSScriptRoot '.gradle-home\caches') -Recurse -Filter 'fabric-api-*.jar' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($null -ne $api) { Copy-Item $api.FullName (Join-Path $mods $api.Name) -Force; Write-Host "  + $($api.Name)" }
        else { Write-Warning 'Fabric API를 찾지 못했습니다. 26.2용 Fabric API를 직접 mods에 넣어야 합니다.' }
    }

    Write-Host ''
    Write-Host "설치 완료: $($jar.Name) -> $mods" -ForegroundColor Green
    Write-Host '런처에서 fabric-loader-0.19.3-26.2 프로필로 실행하세요.'
    Get-ChildItem $mods -Filter '*.jar' | Select-Object Name, Length, LastWriteTime | Format-Table
} finally { Pop-Location }
