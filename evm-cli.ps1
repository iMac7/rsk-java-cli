$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $scriptDir "gradlew.bat"
$jarPath = Join-Path $scriptDir "cli-app\build\libs\evm-cli-all.jar"
$javaExe = "C:\Program Files\OpenJDK\jdk-25\bin\java.exe"

if (-not (Test-Path $gradleWrapper)) {
  throw "Could not find gradlew.bat at $gradleWrapper"
}

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$escapedGradle = $gradleWrapper.Replace('"', '""')
cmd /c """$escapedGradle"" :cli-app:shadowJar >nul 2>nul" | Out-Null
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}

if (-not (Test-Path $javaExe)) {
  $javaExe = "java"
}

& $javaExe --enable-native-access=ALL-UNNAMED -jar $jarPath @args
exit $LASTEXITCODE
