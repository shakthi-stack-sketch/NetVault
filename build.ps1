# NetVault PowerShell Build Script
Write-Host "==================================================" -ForegroundColor Cyan
Write-Host "   NetVault - Building Project with PowerShell" -ForegroundColor Cyan
Write-Host "==================================================" -ForegroundColor Cyan

if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

Write-Host "Compiling Java source files..." -ForegroundColor Yellow
$sources = (Get-ChildItem -Recurse -Filter *.java src/main/java).FullName
javac -d bin $sources

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Packaging netvault.jar..." -ForegroundColor Yellow
$jarCmd = "jar"
if (Test-Path "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe") {
    $jarCmd = "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe"
}
& $jarCmd cfe netvault.jar com.netvault.Main -C bin com

Write-Host "[SUCCESS] NetVault built successfully!" -ForegroundColor Green
Write-Host "Run '.\run-launcher.bat', '.\run-server.bat', or '.\run-client.bat' to launch." -ForegroundColor Green
