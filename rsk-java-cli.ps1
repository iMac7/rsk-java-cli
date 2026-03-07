$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleWrapper = Join-Path $scriptDir "gradlew.bat"
$jarPath = Join-Path $scriptDir "application\build\libs\rsk-java-cli-all.jar"
$javaExe = "C:\Program Files\OpenJDK\jdk-25\bin\java.exe"

if (-not (Test-Path $gradleWrapper)) {
  throw "Could not find gradlew.bat at $gradleWrapper"
}

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)

$escapedGradle = $gradleWrapper.Replace('"', '""')
cmd /c """$escapedGradle"" :application:shadowJar >nul 2>nul" | Out-Null
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}

if (-not (Test-Path $javaExe)) {
  $javaExe = "java"
}

$javaArgs = @('--enable-native-access=ALL-UNNAMED', '-Dfile.encoding=UTF-8', '-Dorg.jline.terminal.encoding=UTF-8', '-jar', $jarPath) + $args; & $javaExe @javaArgs
exit $LASTEXITCODE
