$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jarPath = Join-Path $projectRoot "target\intruder-alert-0.0.1-SNAPSHOT.jar"
$mavenWrapper = Join-Path $projectRoot "mvnw.cmd"

function Test-CommandExists {
    param([string]$CommandName)

    return $null -ne (Get-Command $CommandName -ErrorAction SilentlyContinue)
}

if (-not (Test-CommandExists "java")) {
    Write-Error "Java is not installed or not available on PATH. Install Java 17+ and try again."
}

if (Test-Path $mavenWrapper) {
    Write-Host "Starting backend with Maven Wrapper..."
    & $mavenWrapper "spring-boot:run"
    exit $LASTEXITCODE
}

if (Test-CommandExists "mvn") {
    Write-Host "Starting backend with Maven..."
    & mvn "spring-boot:run"
    exit $LASTEXITCODE
}

if (Test-Path $jarPath) {
    Write-Host "Maven is not installed. Starting backend from the existing JAR build..."
    & java "-jar" $jarPath
    exit $LASTEXITCODE
}

Write-Error "Maven is not installed and no built JAR was found at '$jarPath'. Install Maven or add a Maven Wrapper to rebuild the backend."
