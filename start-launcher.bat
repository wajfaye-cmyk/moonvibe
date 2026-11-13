@echo off
rem Startet den moonvibe-Launcher (Entwicklermodus).
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is missing. Please install it: https://nodejs.org  ^(LTS version^)
  pause
  exit /b 1
)

if not exist "launcher\client-mods" (
  echo The moonvibe mod has not been built yet - building it now ...
  call "%~dp0build-mod.bat" nopause
  if errorlevel 1 exit /b 1
)

cd launcher
if not exist "node_modules" (
  echo Installing launcher dependencies ...
  call npm install
  if errorlevel 1 (
    pause
    exit /b 1
  )
)

call npm start
