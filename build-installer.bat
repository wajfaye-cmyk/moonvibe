@echo off
rem Builds the Windows installer (launcher\release\Moonvibe-Setup-<version>.exe).
rem Everything is included: the client mod for all Minecraft versions, the cosmetics and the built-in cosmetics server.
cd /d "%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is missing. Please install it: https://nodejs.org  ^(LTS version^)
  pause
  exit /b 1
)

cd launcher
echo Installing launcher dependencies ...
call npm install
if errorlevel 1 goto failed

echo Building the installer ...
call npm run dist
if errorlevel 1 goto failed

echo.
echo Done! The installer is in launcher\release\
pause
exit /b 0

:failed
echo.
echo Building the installer failed.
pause
exit /b 1
