@echo off
setlocal

set SCRIPT_DIR=%~dp0
set JAR_PATH=%SCRIPT_DIR%cli-app\build\libs\evm-cli-all.jar
set JAVA_EXE=C:\Program Files\OpenJDK\jdk-25\bin\java.exe

chcp 65001 >nul


call "%SCRIPT_DIR%gradlew.bat" :cli-app:shadowJar >nul 2>nul
if errorlevel 1 (
  exit /b %errorlevel%
)

if not exist "%JAVA_EXE%" (
  set JAVA_EXE=java
)

"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" %*
exit /b %errorlevel%
