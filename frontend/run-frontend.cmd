@echo off
setlocal

set "NODE_DIR=C:\Program Files\nodejs"
set "PROJECT_ROOT=%~dp0"

if exist "%NODE_DIR%\node.exe" (
  set "PATH=%NODE_DIR%;%PATH%"
)

if not exist "%NODE_DIR%\npm.cmd" (
  where npm >nul 2>nul
  if errorlevel 1 (
    echo Node.js is not installed or not available on PATH. Install Node.js LTS and try again.
    exit /b 1
  )
  call npm run dev
  exit /b %errorlevel%
)

if not exist "%PROJECT_ROOT%node_modules" (
  echo Frontend dependencies are missing. Installing packages first...
  call "%NODE_DIR%\npm.cmd" install
  if errorlevel 1 exit /b %errorlevel%
)

echo Starting frontend with npm...
call "%NODE_DIR%\npm.cmd" run dev
exit /b %errorlevel%
