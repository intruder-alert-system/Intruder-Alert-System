@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "VENV_PYTHON=%PROJECT_ROOT%.venv\Scripts\python.exe"

if not exist "%VENV_PYTHON%" (
  py -3.11 -m venv "%PROJECT_ROOT%.venv" 2>nul
  if errorlevel 1 python -m venv "%PROJECT_ROOT%.venv"
  if errorlevel 1 exit /b %errorlevel%
)

call "%VENV_PYTHON%" -m pip install --upgrade pip
if errorlevel 1 exit /b %errorlevel%

call "%VENV_PYTHON%" -m pip install -r "%PROJECT_ROOT%requirements.txt"
if errorlevel 1 exit /b %errorlevel%

if not exist "%PROJECT_ROOT%.env" if exist "%PROJECT_ROOT%.env.example" (
  copy /Y "%PROJECT_ROOT%.env.example" "%PROJECT_ROOT%.env" >nul
)

echo FastAPI backend environment is ready.
exit /b 0
