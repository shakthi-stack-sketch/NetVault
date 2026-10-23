@echo off
echo Starting NetVault Client Workstation...
if exist netvault.jar (
    java -jar netvault.jar client
) else (
    java -cp bin com.netvault.client.ClientApp
)
