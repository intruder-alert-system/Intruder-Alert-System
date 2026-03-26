@echo off
setlocal

set "PROJECT_ROOT=%~dp0"
set "VENV_PYTHON=%PROJECT_ROOT%.venv\Scripts\python.exe"
set "VENV_CFG=%PROJECT_ROOT%.venv\pyvenv.cfg"
set "PIP_DISABLE_PIP_VERSION_CHECK=1"
set "PYTHON_EXE="
set "PYTHON_ARGS="
set "RESET_VENV="

if exist "%LocalAppData%\Programs\Python\Python311\python.exe" (
  set "PYTHON_EXE=%LocalAppData%\Programs\Python\Python311\python.exe"
)

if not defined PYTHON_EXE (
  where python >nul 2>nul
  if not errorlevel 1 (
    python -c "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 11) else 1)" >nul 2>nul
    if not errorlevel 1 (
      set "PYTHON_EXE=python"
    )
  )
)

if not defined PYTHON_EXE (
  where py >nul 2>nul
  if not errorlevel 1 (
    py -3.11 -c "import sys" >nul 2>nul
    if not errorlevel 1 (
      set "PYTHON_EXE=py"
      set "PYTHON_ARGS=-3.11"
    )
  )
)

if not defined PYTHON_EXE (
  echo Python 3.11 was not found.
  echo Install Python 3.11 and make sure either py -3.11 or python is available.
  exit /b 1
)

if exist "%VENV_CFG%" (
  findstr /C:"WindowsApps" "%VENV_CFG%" >nul 2>nul
  if not errorlevel 1 (
    set "RESET_VENV=1"
  )
)

if exist "%VENV_PYTHON%" (
  call "%VENV_PYTHON%" -c "import sys" >nul 2>nul
  if errorlevel 1 (
    set "RESET_VENV=1"
  )
)

if defined RESET_VENV (
  echo Existing detector virtual environment is broken. Recreating it...
  rmdir /s /q "%PROJECT_ROOT%.venv"
)

if not exist "%VENV_PYTHON%" (
  echo Creating detector virtual environment...
  call "%PYTHON_EXE%" %PYTHON_ARGS% -m venv "%PROJECT_ROOT%.venv"
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
