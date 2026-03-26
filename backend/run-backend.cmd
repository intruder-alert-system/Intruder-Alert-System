@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "JAR_PATH=%PROJECT_ROOT%target\intruder-alert-0.0.1-SNAPSHOT.jar"

where java >nul 2>nul
if errorlevel 1 (
  echo Java is not installed or not available on PATH. Install Java 17+ and try again.
  exit /b 1
)

if exist "%PROJECT_ROOT%mvnw.cmd" (
  echo Starting backend with Maven Wrapper...
  call "%PROJECT_ROOT%mvnw.cmd" spring-boot:run
  exit /b %errorlevel%
)

where mvn >nul 2>nul
if not errorlevel 1 (
  echo Starting backend with Maven...
  call mvn spring-boot:run
  exit /b %errorlevel%
)

if exist "%JAR_PATH%" (
  echo Maven is not installed. Starting backend from the existing JAR build...
  java -jar "%JAR_PATH%"
  exit /b %errorlevel%
)

echo Maven is not installed and no built JAR was found at "%JAR_PATH%". Install Maven or add a Maven Wrapper to rebuild the backend.
exit /b 1
