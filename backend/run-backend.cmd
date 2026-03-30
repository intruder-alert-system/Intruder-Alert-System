@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "VENV_PYTHON=%PROJECT_ROOT%.venv\Scripts\python.exe"

if not exist "%VENV_PYTHON%" (
  echo Backend environment is not ready yet. Running setup first...
  call "%PROJECT_ROOT%setup-backend.cmd"
  if errorlevel 1 exit /b %errorlevel%
)

if not exist "%PROJECT_ROOT%.env" if exist "%PROJECT_ROOT%.env.example" (
  copy /Y "%PROJECT_ROOT%.env.example" "%PROJECT_ROOT%.env" >nul
  echo Created backend\.env from .env.example
)

echo Starting FastAPI backend...
call "%VENV_PYTHON%" -m uvicorn main:app --host 0.0.0.0 --port 8080
exit /b %errorlevel%
