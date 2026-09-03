@echo off
echo Building NodeSpark plugin...

:: Build JDK comes from org.gradle.java.home in gradle.properties

:: Increment patch version in gradle.properties
for /f "tokens=1,2 delims==" %%a in ('findstr "pluginVersion" gradle.properties') do set CURRENT_VERSION=%%b
for /f "tokens=1,2,3 delims=." %%a in ("%CURRENT_VERSION%") do (
    set MAJOR=%%a
    set MINOR=%%b
    set /a PATCH=%%c+1
)
set NEW_VERSION=%MAJOR%.%MINOR%.%PATCH%
powershell -Command "(Get-Content gradle.properties) -replace 'pluginVersion=%CURRENT_VERSION%', 'pluginVersion=%NEW_VERSION%' | Set-Content gradle.properties"
echo Version: %CURRENT_VERSION% ^> %NEW_VERSION%

call gradlew.bat buildPlugin
if errorlevel 1 (
    echo BUILD FAILED
    exit /b 1
)

echo.
echo Build successful!
echo Plugin zip: build\distributions\
dir /b build\distributions\*.zip
echo.
echo To install: Settings ^> Plugins ^> gear icon ^> Install Plugin from Disk
echo             Select: %CD%\build\distributions\node-spark-%NEW_VERSION%.zip
echo.
pause
