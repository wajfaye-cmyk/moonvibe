@echo off
rem Baut die moonvibe-Mod fuer ALLE Minecraft-Versionen (mod\versions\*.properties und mod-legacy)
rem und kopiert sie in den Launcher (launcher\client-mods\<Version>).
rem Laedt beim ersten Mal ein Java 25 (JDK) in den Ordner .tools herunter.
setlocal
cd /d "%~dp0"

set "JDK_DIR="
for /d %%d in (".tools\jdk-25*") do set "JDK_DIR=%%~fd"

if not defined JDK_DIR (
  echo Downloading Java 25 ^(Temurin JDK^) ...
  if not exist ".tools" mkdir ".tools"
  curl -L -o ".tools\jdk25.zip" "https://api.adoptium.net/v3/binary/latest/25/ga/windows/x64/jdk/hotspot/normal/eclipse"
  if errorlevel 1 goto failed
  tar -xf ".tools\jdk25.zip" -C ".tools"
  if errorlevel 1 goto failed
  del ".tools\jdk25.zip"
  for /d %%d in (".tools\jdk-25*") do set "JDK_DIR=%%~fd"
)

set "JAVA_HOME=%JDK_DIR%"
echo Using Java: %JAVA_HOME%

cd mod
for %%f in (versions\*.properties) do (
  echo.
  echo === Minecraft %%~nf ===
  call "%~dp0mod\gradlew.bat" build -Pmc=%%~nf
  if errorlevel 1 goto failed
)
cd ..

cd mod-legacy
for %%f in (versions\*.properties) do (
  echo.
  echo === Minecraft %%~nf ===
  call "%~dp0mod-legacy\gradlew.bat" build -Pmc=%%~nf
  if errorlevel 1 goto failed
)
cd ..

echo.
echo Done! The mod is now in launcher\client-mods\
if not "%1"=="nopause" pause
exit /b 0

:failed
echo.
echo Building the mod failed.
if not "%1"=="nopause" pause
exit /b 1
