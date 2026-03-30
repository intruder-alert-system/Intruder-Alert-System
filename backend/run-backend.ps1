$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvPython = Join-Path $projectRoot ".venv\Scripts\python.exe"
$setupScript = Join-Path $projectRoot "setup-backend.cmd"
$envFile = Join-Path $projectRoot ".env"
$envExample = Join-Path $projectRoot ".env.example"

if (-not (Test-Path $venvPython)) {
    Write-Host "Backend environment is not ready yet. Running setup first..."
    & $setupScript
    exit $LASTEXITCODE
}

if (-not (Test-Path $envFile) -and (Test-Path $envExample)) {
    Copy-Item $envExample $envFile
    Write-Host "Created backend\.env from .env.example"
}

Write-Host "Starting FastAPI backend..."
& $venvPython "-m" "uvicorn" "main:app" "--host" "0.0.0.0" "--port" "8080"
exit $LASTEXITCODE
