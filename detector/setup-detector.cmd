@echo off
setlocal

set "PY311=C:\Users\Predator\AppData\Local\Programs\Python\Python311\python.exe"
set "PROJECT_ROOT=%~dp0"
set "VENV_PYTHON=%PROJECT_ROOT%.venv\Scripts\python.exe"
set "PIP_DISABLE_PIP_VERSION_CHECK=1"

if not exist "%PY311%" (
  echo Python 3.11 was not found at "%PY311%".
  echo Install Python 3.11 and try again.
  exit /b 1
)

if not exist "%VENV_PYTHON%" (
  echo Creating detector virtual environment...
  call "%PY311%" -m venv "%PROJECT_ROOT%.venv"
  if errorlevel 1 exit /b %errorlevel%
)

echo Installing detector dependencies...
call "%VENV_PYTHON%" -m pip install --upgrade pip "setuptools<81" wheel
if errorlevel 1 exit /b %errorlevel%

call "%VENV_PYTHON%" -m pip install opencv-python requests python-dotenv pillow click numpy face-recognition-models dlib-bin
if errorlevel 1 exit /b %errorlevel%

call "%VENV_PYTHON%" -m pip install face-recognition --no-deps
if errorlevel 1 exit /b %errorlevel%

echo Detector environment is ready.
exit /b 0
