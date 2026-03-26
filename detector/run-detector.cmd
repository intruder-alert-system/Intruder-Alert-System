@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "VENV_PYTHON=%PROJECT_ROOT%.venv\Scripts\python.exe"

if not exist "%VENV_PYTHON%" (
  echo Detector environment is not ready yet. Running setup first...
  call "%PROJECT_ROOT%setup-detector.cmd"
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%PROJECT_ROOT%.env" if exist "%PROJECT_ROOT%.env.example" (
  copy /Y "%PROJECT_ROOT%.env.example" "%PROJECT_ROOT%.env" >nul
  echo Created detector\.env from .env.example
)

echo Starting detector...
call "%VENV_PYTHON%" "%PROJECT_ROOT%detector.py"
exit /b %errorlevel%
