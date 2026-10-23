@echo off
echo Starting NetVault Server Console...
if exist netvault.jar (
    java -jar netvault.jar server
) else (
    java -cp bin com.netvault.server.ServerApp
)
