# NetVault — Secure Network File Transfer System

[![Java Version](https://img.shields.io/badge/Java-17%20%7C%2018%2B-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-Academic-green.svg)]()
[![Build Tool](https://img.shields.io/badge/Build-PowerShell%20%2F%20Maven-orange.svg)]()

**NetVault** is a desktop file transfer system engineered in Java using **TCP/IP socket programming**, multithreaded concurrent client handling, and **SHA-256 cryptographic integrity verification**.

Designed for Computer Networks laboratory demonstrations, NetVault features a modern desktop graphical user interface built with Java Swing (Dark Navy, Slate Charcoal, Champagne-Gold aesthetic) and provides real-time throughput metrics, transfer history, and live activity logging.

---

## 1. Key Features

- **Concurrent TCP Server**:
  - Multi-client concurrency powered by a worker thread pool (`ExecutorService`).
  - Independent client handling: one client's upload or network drop never blocks or crashes the server.
  - Live connected-clients table showing remote IP, ephemeral port, and connection duration.
  - Real-time activity console logging all protocol handshakes, transfers, and error states.
  - Built-in storage vault inspector and direct folder launcher.
- **Client Workstation**:
  - User-friendly connection bar supporting local loopback (`127.0.0.1`) and LAN IP addresses.
  - Native file selection via `JFileChooser` with live file size and SHA-256 preview.
  - Upload files directly into the server's storage vault.
  - Download files from the server's catalog with customizable destination paths.
  - Real-time progress bar displaying transfer percentage, bytes transferred/total, and instantaneous throughput (KB/s).
  - Session transfer history table with timestamps and SHA-256 match results.
- **Application-Layer Protocol & Framing**:
  - Magic header validation (`0x4E564C54` "NVLT") and version handshake.
  - Unambiguous message framing preventing TCP stream coalescence and boundary ambiguity.
  - 64 KB chunked streaming: transfers multi-gigabyte files with low, constant memory footprint.
- **Cryptographic File Integrity (SHA-256)**:
  - On-the-fly incremental digest calculation during chunk streaming.
  - End-to-end verification comparing sender's SHA-256 hash against receiver's calculated digest.
  - Constant-time hash verification protecting against timing attacks.
- **Security Defenses**:
  - Full path-traversal prevention: blocks `../`, illegal directory separators, and null-byte injection attacks.
  - Enforced 5 GB maximum file size quota.

---

## 2. Technology Stack

- **Language**: Java 17 / Java 18+ (tested on OpenJDK / Oracle JDK 18).
- **GUI Framework**: Java Swing (`JFrame`, `JTable`, `JProgressBar`, `JFileChooser`, `JTabbedPane`).
- **Networking**: Java TCP Sockets (`java.net.ServerSocket`, `java.net.Socket`).
- **Concurrency**: `java.util.concurrent.ExecutorService`, `CopyOnWriteArrayList`, `AtomicInteger`.
- **I/O & Streams**: `DataInputStream`, `DataOutputStream`, `BufferedInputStream`, `BufferedOutputStream`.
- **Security & Hashing**: `java.security.MessageDigest` (SHA-256 algorithm).
- **Build Systems**: Native PowerShell/Batch scripts + Maven (`pom.xml`).

> [!WARNING]
> **Educational Network Context**: NetVault is designed for education and local network demonstrations. Data is transmitted unencrypted over raw TCP sockets without TLS/SSL or user authentication. It should only be run on trusted local networks.

---

## 3. Project Directory Structure

```
NetVault/
├── pom.xml                               # Maven project definition
├── build.bat                             # Windows Batch build script
├── build.ps1                             # PowerShell build script
├── run-launcher.bat                      # Interactive Launcher script
├── run-server.bat                        # Server standalone launcher
├── run-client.bat                        # Client workstation launcher
├── run-tests.bat                         # Automated test suite runner
├── netvault.jar                          # Pre-packaged executable JAR
├── README.md                             # User guide and execution manual
├── .gitignore                            # Git exclusion rules
├── docs/
│   └── NETWORK_ARCHITECTURE.md           # In-depth network architecture & protocol spec
├── received-files/                       # Server-side storage vault for uploaded files
└── src/
    ├── main/
    │   └── java/
    │       └── com/
    │           └── netvault/
    │               ├── Main.java                 # Unified system entry point & launcher
    │               ├── client/
    │               │   ├── ClientApp.java        # Client standalone main
    │               │   └── FileTransferClient.java # TCP client network engine
    │               ├── server/
    │               │   ├── ServerApp.java        # Server standalone main
    │               │   ├── FileTransferServer.java # Multi-client TCP server daemon
    │               │   ├── ClientHandler.java    # Per-client worker thread
    │               │   └── ServerEventListener.java # Server callback interface
    │               ├── protocol/
    │               │   ├── TransferProtocol.java # Protocol constants, framing, chunk streamer
    │               │   └── ProgressListener.java # Transfer progress callback
    │               ├── model/
    │               │   ├── FileInfo.java         # Server file metadata model
    │               │   └── TransferRecord.java   # Transfer history model
    │               ├── service/
    │               │   ├── ChecksumService.java  # SHA-256 cryptographic utility
    │               │   └── FileTransferService.java # Storage management & path security
    │               └── ui/
    │                   ├── Theme.java            # NetVault Design System (Dark Navy & Gold)
    │                   ├── ServerFrame.java      # Server Swing GUI
    │                   └── ClientFrame.java      # Client Swing GUI
    └── test/
        └── java/
            └── com/
                └── netvault/
                    ├── NetVaultTestRunner.java   # Standalone automated test runner
                    ├── ChecksumServiceTest.java  # SHA-256 unit tests
                    ├── TransferProtocolTest.java # Path traversal and protocol tests
                    └── NetworkTransferIntegrationTest.java # End-to-end TCP integration tests
```

---

## 4. Prerequisites

1. **Java Development Kit (JDK)**: Version 17 or higher.
   - Verify in PowerShell:
     ```powershell
     java -version
     javac -version
     ```
2. **Operating System**: Windows 10 / 11, macOS, or Linux.
3. *(Optional)* **Apache Maven**: Version 3.8+ if you prefer building with Maven.

---

## 5. How to Compile and Build

### Method A: Using PowerShell (No external tools needed)
Open Windows PowerShell in the `NetVault` root directory:
```powershell
# 1. Compile source files into bin directory
$sources = (Get-ChildItem -Recurse -Filter *.java src/main/java).FullName
javac -d bin $sources

# 2. Package into executable JAR
& "C:\Program Files\Java\jdk-18.0.2.1\bin\jar.exe" cfe netvault.jar com.netvault.Main -C bin com
```
Or simply run the provided build script:
```powershell
.\build.ps1
```

### Method B: Using Windows Command Prompt / Batch
```cmd
build.bat
```

### Method C: Using Maven (If Maven is installed)
```powershell
mvn clean package
```
This produces `target/netvault-1.0.0.jar`.

---

## 6. How to Launch and Test

### 6.1 Testing on a Single Computer (Localhost)

When running both the server and client on the same computer, the client connects to `127.0.0.1` (loopback).

#### Option 1: Interactive Launcher (Recommended)
Double-click `run-launcher.bat` or run in PowerShell:
```powershell
java -jar netvault.jar
```
Click **"Launch Both (Side-by-Side Test)"** to instantly open the Server Console on the left and the Client Workstation on the right.

#### Option 2: Independent Terminals
**Terminal 1 — Launch Server:**
```powershell
java -jar netvault.jar server
# Or: .\run-server.bat
```
1. Click **Start Server** (defaults to Port `5000`).
2. Status badge changes to `● ONLINE`.

**Terminal 2 — Launch Client:**
```powershell
java -jar netvault.jar client
# Or: .\run-client.bat
```
1. Server IP: `127.0.0.1`, Port: `5000`.
2. Click **Connect**. Status badge changes to `● CONNECTED`.

#### Testing File Transfers:
1. **Upload**:
   - In the Client window, click **Browse File...** and pick any text, image, or zip file.
   - Click **Upload File**.
   - Observe real-time progress bar and speed.
   - A verification dialog confirms: `SHA-256 Verification: PASSED`.
   - On the Server window, switch to the **"Received Files Vault"** tab to see your uploaded file and matching checksum.
2. **Download**:
   - In the Client window, click **Refresh Catalog**.
   - Select the file from the dropdown.
   - Click **Download Selected**, select a destination, and save.
   - The file is streamed back and verified byte-for-byte.

---

### 6.2 Testing Between Two Computers on the Same Wi-Fi Network

> [!IMPORTANT]
> The server must be started **before** clients attempt to connect. `127.0.0.1` works **only** for clients running on the same machine as the server. When testing across computers, the client must use the server's LAN IP address.

#### Step 1: Find the Server's LAN IP Address
On Computer A (Server machine), open PowerShell and run:
```powershell
ipconfig
```
Look for **IPv4 Address** under your active Wi-Fi or Ethernet adapter:
```
Wireless LAN adapter Wi-Fi:
   IPv4 Address. . . . . . . . . . . : 192.168.1.15
   Subnet Mask . . . . . . . . . . . : 255.255.255.0
```
*(The NetVault Server Console also automatically detects and displays this IP in the top control bar!)*

#### Step 2: Configure Windows Firewall on Server (Computer A)
Windows Firewall may block incoming TCP connections on port 5000 by default. To allow NetVault through port 5000, run PowerShell as Administrator on Computer A:
```powershell
New-NetFirewallRule -DisplayName "NetVault Server Port 5000" -Direction Inbound -LocalPort 5000 -Protocol TCP -Action Allow
```
*(To remove this rule later: `Remove-NetFirewallRule -DisplayName "NetVault Server Port 5000"`)*

#### Step 3: Start Server on Computer A
1. Launch `java -jar netvault.jar server`.
2. Port: `5000`. Click **Start Server**.

#### Step 4: Connect Client on Computer B
1. Copy `netvault.jar` to Computer B (or run client from source).
2. Launch `java -jar netvault.jar client`.
3. In the **Server IP** field, enter Computer A's LAN IP (e.g. `192.168.1.15`).
4. Port: `5000`.
5. Click **Connect**.
6. Upload and download files across your Wi-Fi network!

---

## 7. Automated Testing Suite

NetVault includes an automated test suite verifying 10 critical networking, cryptographic, and security requirements without requiring external dependencies:

```powershell
# Compile and run automated test suite
.\run-tests.bat
# Or:
java -cp bin com.netvault.NetVaultTestRunner
```

### Verified Test Cases:
1. `ChecksumServiceTest`:
   - Empty string SHA-256 digest validation.
   - Known string SHA-256 calculation.
   - File SHA-256 calculation.
   - Constant-time case-insensitive checksum verification.
   - File size unit formatting.
2. `TransferProtocolTest`:
   - Standard filename acceptance.
   - Path traversal blocking (`../secret.txt`, `folder/../../passwords.db`, `/etc/passwd`).
   - Absolute filesystem path sanitization (`C:\Windows\System32\calc.exe`).
   - Null-byte injection attack rejection (`innocent.txt\0.exe`).
3. `NetworkTransferIntegrationTest`:
   - Dynamic port binding on ephemeral port.
   - Client TCP connect and disconnect.
   - 4 concurrent clients connected and disconnected simultaneously.
   - Full 128 KB chunked file upload with SHA-256 integrity match.
   - Server catalog listing and file discovery.
   - Full chunked file download with byte-by-byte file comparison.
   - Graceful error handling for missing files.
   - Graceful rejection when attempting to connect to closed ports.
   - Clean server termination without thread pool leaks.

---

## 8. Troubleshooting Guide

| Issue | Root Cause | Solution |
|:---|:---|:---|
| `Port Conflict Error: Port 5000 is already in use` | Another server or process is using port 5000. | Change port in the port text field (e.g., `5001`, `5555`, or `8080`) and click Start Server. |
| `Connection Failed: Connection refused` | Server is not running or client specified incorrect IP/port. | Ensure Server is started on Computer A and status badge shows `● ONLINE`. Check that IP and port match. |
| `Connection timed out` across Wi-Fi | Windows Firewall is dropping incoming packets on the server machine. | Run the firewall rule command in Section 6.2 on Computer A to allow TCP port 5000. Ensure both devices are on the same Wi-Fi subnet. |
| `Security Error: Directory traversal attack detected` | Client sent a filename with `..` or illegal characters. | NetVault automatically blocks traversal attempts to prevent unauthorized access. Use standard filenames. |
| `SHA-256 checksum mismatch` | Bytes were corrupted or truncated during network transmission. | NetVault detected data corruption. Check network stability; the server marks the record as `Failed`. |

---

## 9. Academic References & Networking Concepts

For a deep-dive into the architectural theory, message framing diagrams, TCP 3-way handshakes, and socket buffer mechanics, refer to [`docs/NETWORK_ARCHITECTURE.md`](docs/NETWORK_ARCHITECTURE.md).
