param([switch]$WithWorldEdit)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkDirectory = Get-ChildItem -LiteralPath '.tools' -Directory -Filter 'jdk*' | Select-Object -First 1
    if ($null -eq $jdkDirectory) { throw 'Project JDK missing. Install JDK 25, set JAVA_HOME, and run ./gradlew.bat build.' }
    $env:JAVA_HOME = $jdkDirectory.FullName
    $env:GRADLE_USER_HOME = Join-Path $PSScriptRoot '.gradle-home'
    # Force Java's documented internal TCP fallback on this Windows host.
    $env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=Z:/rampdoor-no-such-dir'
    if ($WithWorldEdit) { & ./gradlew.bat build '-PtestWorldEdit=.tools/worldedit.jar' --console=plain }
    else { & ./gradlew.bat build --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "Gradle exited with $LASTEXITCODE" }
} finally { Pop-Location }
