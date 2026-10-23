@echo off
setlocal
echo ==================================================
echo   NetVault - Building Project
echo ==================================================

if not exist bin mkdir bin

echo Compiling Java source files...
dir /s /b src\main\java\*.java > sources.txt
javac -d bin @sources.txt
del sources.txt

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo Packaging netvault.jar...
if exist "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe" (
    "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe" cfe netvault.jar com.netvault.Main -C bin com
) else (
    jar cfe netvault.jar com.netvault.Main -C bin com 2>nul
)

echo [SUCCESS] NetVault built successfully!
echo Run 'run-launcher.bat', 'run-server.bat', or 'run-client.bat' to start.
