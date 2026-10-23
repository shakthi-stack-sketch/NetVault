# NetVault Network Architecture & Protocol Specification

## 1. System Overview

**NetVault** is a high-performance desktop file transfer system engineered in Java using the Transport Control Protocol (TCP/IP). The system facilitates reliable, concurrent, and verified transmission of binary and text files across local area networks (LANs).

At its foundation, NetVault pairs standard Java TCP Sockets (`java.net.ServerSocket` and `java.net.Socket`) with an application-layer binary framing protocol, chunked I/O streaming, and cryptographic hash verification via SHA-256 (`java.security.MessageDigest`).

---

## 2. Client-Server Architecture Diagram

```
+-----------------------------------------------------------------------------------------+
|                               NETVAULT SYSTEM ARCHITECTURE                              |
+-----------------------------------------------------------------------------------------+

  CLIENT WORKSTATION (ClientFrame)                 SERVER HOST (ServerFrame)
  +--------------------------------+               +-------------------------------------+
  | [Swing GUI Thread]             |               | [Swing GUI Thread]                  |
  |  - File Chooser & SHA Preview  |               |  - Client Table & Storage Catalog   |
  |  - Progress Bar & History      |               |  - Real-Time Live Activity Log      |
  +---------------+----------------+               +------------------+------------------+
                  | (Worker Thread)                                   | (Event Listener)
  +---------------v----------------+               +------------------v------------------+
  | FileTransferClient             |               | FileTransferServer                  |
  |  - Handshake negotiation       |               |  - ServerSocket (Port 5000)         |
  |  - Frame encoder/decoder       |               |  - Thread Pool (ExecutorService)    |
  +---------------+----------------+               +------------------+------------------+
                  |                                                   | Dispatches
                  |                                +------------------v------------------+
                  |                                | ClientHandler (Worker Thread)       |
                  |                                |  - Dedicated Socket Connection      |
                  |                                |  - Path Sanitization & Validation   |
                  |                                |  - FileTransferService              |
                  |                                +------------------+------------------+
                  |                                                   |
                  |                                +------------------v------------------+
                  |                                | ChecksumService (SHA-256)           |
                  |                                |  - Disk File Verification           |
                  |                                +------------------+------------------+
                  |                                                   |
                  |                                +------------------v------------------+
                  |                                | Server Storage (received-files/)    |
                  +================================>==================+
                            TCP/IP Socket Stream
                      (Framed Chunks: 64 KB + Digests)
```

---

## 3. Computer Networks Fundamentals

### 3.1 Role of IP Addresses and Port Numbers
In the Internet Protocol Suite (TCP/IP):
- **IP Address (Network Layer - Layer 3)**: Uniquely identifies a host interface connected to the network.
  - *Loopback Address (`127.0.0.1`)*: Resolves internally within the local host OS network stack without generating physical network card traffic. Used for single-machine development and testing.
  - *LAN Address (`192.168.x.x`, `10.x.x.x`)*: Routable within a local Wi-Fi router or Ethernet switch subnet, allowing physically separated computers to reach each other.
- **Port Number (Transport Layer - Layer 4)**: A 16-bit unsigned integer (range: 1 – 65,535) that multiplexes network traffic to specific process sockets on a host.
  - NetVault defaults to listening port `5000` (User/Registered Port range).
  - When a client connects, the client OS assigns an ephemeral port (e.g., `54128`) to create a unique 4-tuple socket identifier:
    $$\text{Socket Session} = (\text{Client IP}, \text{Client Port}, \text{Server IP}, \text{Server Port})$$

### 3.2 TCP Connection Lifecycle: Handshake and Teardown
TCP is a connection-oriented, reliable byte-stream protocol. Before any application data passes between NetVault client and server:

#### A. Three-Way Handshake (Establishment)
1. **SYN**: Client sends a TCP segment with the `SYN` (Synchronize) flag set and an initial sequence number $ISN_c$.
2. **SYN-ACK**: Server receives the SYN, binds an active socket, and replies with `SYN-ACK`, acknowledging $ISN_c + 1$ with its own $ISN_s$.
3. **ACK**: Client acknowledges $ISN_s + 1$. The connection enters the `ESTABLISHED` state.

#### B. Four-Way Handshake (Teardown)
1. When either side disconnects (`client.disconnect()` or `CMD_DISCONNECT`):
2. Sender sends a segment with the `FIN` flag set.
3. Receiver replies with `ACK`.
4. Receiver sends its own `FIN` segment.
5. Sender replies with `ACK`, transitioning through `TIME_WAIT` to safely clear residual in-flight segments.

---

## 4. Socket Programming and Network Options

Java provides `ServerSocket` on the server and `Socket` on the client. NetVault tunes underlying socket parameters:
- **`setTcpNoDelay(true)`**: Disables **Nagle's algorithm**. Nagle's algorithm buffers small outgoing packets to minimize header overhead. Disabling it ensures that control command frames and chunk headers are transmitted immediately across the wire without artificial 40ms delayed-ACK latencies.
- **`BufferedInputStream` / `BufferedOutputStream`**: Wraps the raw socket input/output streams with 64 KB memory buffers to reduce the frequency of native system calls (`read`/`write` kernel context switches).

---

## 5. Application-Layer Protocol & Message Framing

Because TCP provides an **unstructured stream of bytes**, it does not preserve message boundaries (the "packet fragmentation" and "packet coalescence" problem). Without explicit application-layer framing, metadata (filename, file size) would blend indistinguishably into the payload bytes.

### 5.1 Protocol Header (Handshake)
Immediately after TCP connection establishment, both parties exchange a fixed 5-byte protocol header:
```
+-----------------------------------+--------------------+
| Magic Header: 0x4E564C54 ("NVLT") | Version: 0x01      |
| 4 Bytes (Big-Endian int)          | 1 Byte             |
+-----------------------------------+--------------------+
```
Any client that does not produce the magic header or presents an incompatible version is dropped immediately.

### 5.2 Command Framing Structure
NetVault frames every request with an operation command code:

| Command Byte | Identifier | Payload Fields |
|:---|:---|:---|
| `0x01` | `CMD_LIST` | None. Server replies with file count and metadata records. |
| `0x02` | `CMD_UPLOAD` | `filename` (UTF), `fileSize` (long), `expectedSha256` (UTF). |
| `0x03` | `CMD_DOWNLOAD` | `filename` (UTF). |
| `0x04` | `CMD_DISCONNECT` | None. Signals graceful socket closure. |

### 5.3 Chunked Streaming Protocol
To transfer files of arbitrary size without saturating memory, files are divided into **64 KB chunks**:

```
+-----------------------------------+-----------------------------------+
| Chunk Size (int, 4 bytes)         | Chunk Payload (N raw bytes)       |
+-----------------------------------+-----------------------------------+
| ... Next Chunk ...                | ... Next Chunk Payload ...        |
+-----------------------------------+-----------------------------------+
| EOF Marker (int = 0, 4 bytes)     | [End of Stream]                   |
+-----------------------------------+-----------------------------------+
```

- When sending: The sender loops, reading up to 65,536 bytes from disk, writes the integer `chunkLength`, writes the raw byte slice, and updates the running SHA-256 digest.
- When finished: The sender writes an integer of value `0` as the terminal sentinel.
- When receiving: The receiver reads `chunkLength = in.readInt()`. If `chunkLength == 0`, transmission is complete. Otherwise, the receiver executes `in.readFully(buffer, 0, chunkLength)` to guarantee that the full chunk is read even if TCP fragmented the packet.

---

## 6. End-to-End Data Flow

The complete data pipeline for an upload operation:

```
[Client UI] Select file & pre-compute SHA-256
      |
[TCP Socket] Client writes: CMD_UPLOAD + Filename + Size + Expected SHA-256
      |
[Server Socket] Accepts connection & passes to ClientHandler in thread pool
      |
[File Validation] Path traversal checks: strip '../', illegal chars, ensure subpath
      |
[Server Response] Writes STATUS_OK readiness byte
      |
[Streaming Engine] Client streams 64 KB chunks -> Server reads chunks into received-files/
      |            (Both sender and receiver incrementally feed SHA-256 MessageDigest)
      |
[EOF Marker] Client sends chunk length = 0
      |
[Integrity Verification] Server completes digest -> compares calculated vs expected SHA-256
      |
[Server Result] Writes STATUS_OK + Computed Checksum + Match Boolean + Confirmation
      |
[Client UI] Validates server response -> Updates progress to 100% -> Displays Success in History
```

---

## 7. Multithreading and Concurrency Architecture

### 7.1 Server Concurrency
The server uses a decoupling pattern:
- **Acceptor Thread (`NetVault-Acceptor`)**: Loops continuously on `serverSocket.accept()`. It never performs I/O itself.
- **Worker Thread Pool (`java.util.concurrent.ExecutorService`)**: A cached thread pool dynamically instantiates or reuses worker threads for each connected client (`ClientHandler`).
- **Thread-Safe State**:
  - `activeClients`: Backed by `CopyOnWriteArrayList<ClientHandler>` to allow safe iteration during broadcast and concurrent additions/removals.
  - Disconnection of one client or an aborted upload in one thread is caught in a `try-catch-finally` block within that thread, closing only that client's socket without affecting other active transfers or terminating the server.

### 7.2 Client Responsiveness
Swing is single-threaded; all visual rendering happens on the **Event Dispatch Thread (EDT)**. If network I/O were executed on the EDT, the window would freeze ("Not Responding").
- All network operations (`connect`, `uploadFile`, `downloadFile`, `listFiles`) execute inside background worker threads (`new Thread(...)`).
- Live updates (progress bar, table rows, labels) are marshaled back to the EDT via `SwingUtilities.invokeLater(() -> { ... })`.

---

## 8. Cryptographic Integrity: SHA-256 Verification

TCP guarantees error-free delivery of packet bytes via its internal 16-bit checksum. However, TCP checksums:
1. Are only 16-bit polynomials (susceptible to undetected burst collisions).
2. Protect only packet-to-packet transmission, not disk I/O errors, memory corruption, or intermediate proxy manipulation.

NetVault uses **SHA-256 (Secure Hash Algorithm 256-bit)**:
- Output length: 256 bits (32 bytes), rendered as a 64-character hexadecimal string.
- Collision resistance: Finding two different files with the same SHA-256 digest requires $\approx 2^{128}$ operations, making it mathematically impossible for accidental corruption to pass undetected.
- **Incremental Digesting**: Rather than making a separate disk pass over a large multi-gigabyte file, the SHA-256 digest is updated in-memory *as chunks pass through the network stream*:
  $$\text{digest.update}(buffer, 0, bytesRead)$$
- **Constant-Time Verification**: `MessageDigest.isEqual(...)` is employed to protect against timing analysis during checksum comparison.

---

## 9. Security Defenses and Current Limitations

### 9.1 Path Traversal Defenses
Malicious clients could attempt to overwrite critical system files by requesting filenames such as `../../Windows/System32/evil.dll` or `/etc/shadow`.
NetVault enforces defense-in-depth:
1. `TransferProtocol.sanitizeFilename(raw)`:
   - Rejects null bytes (`\0`).
   - Detects and rejects directory traversal sequences (`..`).
   - Strips directory paths using `Paths.get(...).getFileName()`.
   - Replaces illegal filesystem characters (`<>:"/\|?*`) with underscores.
2. `FileTransferService.resolveSafeDestination(cleanName)`:
   - Resolves target path against the canonical storage directory:
     $$\text{targetPath} = \text{storageDirectory.resolve}(cleanName).\text{normalize}()$$
   - Confirms:
     $$\text{targetPath.startsWith}(\text{storageDirectory}) == \text{true}$$
   - Throws `SecurityException` if escape is detected.

### 9.2 Maximum File Size Quota
A denial-of-service attack attempting to exhaust server disk space with petabyte files is mitigated by checking file length against `TransferProtocol.MAX_FILE_SIZE` (5 GB) before allocating disk resources.

### 9.3 Transparent Limitations
- **No TLS/SSL Encryption**: Data traverses the TCP connection in plaintext. Eavesdroppers on an unencrypted Wi-Fi network can capture file contents with tools like Wireshark. NetVault is designed as an educational networking project for trusted LAN environments.
- **No User Authentication**: Any client reachable on the IP/port can interact with the server. Production enterprise deployments should introduce TLS mutual authentication (`SSLSocket`) and user session tokens.
