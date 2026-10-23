@echo off
echo Starting NetVault System Launcher...
if exist netvault.jar (
    java -jar netvault.jar
) else (
    java -cp bin com.netvault.Main
)
