@echo off
rem Startet den Cosmetics-Server lokal im Testmodus (erlaubt Offline-Test-Accounts) auf http://localhost:8787.
cd /d "%~dp0backend"
if not exist node_modules call npm install
call npm run dev
pause
