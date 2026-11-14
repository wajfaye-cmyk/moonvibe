@echo off
rem Startet den moonvibe-Launcher (Entwicklermodus).
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is missing. Please install it: https://nodejs.org  ^(LTS version^)
  pause
  exit /b 1
)

rem The client mod for all versions is already in launcher\client-mods (rebuild it with build-mod.bat).
if not exist "launcher\client-mods\1.8.9" (
  echo The moonvibe mod is missing - building it now ...
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
