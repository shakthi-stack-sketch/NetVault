package com.netvault.server;

import com.netvault.model.FileInfo;
import com.netvault.model.TransferRecord;
import com.netvault.protocol.TransferProtocol;
import com.netvault.service.ChecksumService;
import com.netvault.service.FileTransferService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles communication with a single connected client over a dedicated TCP socket.
 * Executed concurrently in a thread pool.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final FileTransferServer server;
    private final FileTransferService fileService;
    private final String clientId;
    private final String clientIp;
    private final int clientPort;
    private final String connectedAt;

    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running = true;

    public ClientHandler(Socket socket, FileTransferServer server, FileTransferService fileService, int clientNumber) {
        this.socket = socket;
        this.server = server;
        this.fileService = fileService;
        this.clientIp = socket.getInetAddress().getHostAddress();
        this.clientPort = socket.getPort();
        this.clientId = String.format("Client-%02d (%s:%d)", clientNumber, clientIp, clientPort);
        this.connectedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getClientPort() {
        return clientPort;
    }

    public String getConnectedAt() {
        return connectedAt;
    }

    @Override
    public void run() {
        server.notifyClientConnected(clientId, clientIp, clientPort);
        server.logActivity(String.format("Accepted new connection: %s", clientId));

        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), TransferProtocol.CHUNK_SIZE));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), TransferProtocol.CHUNK_SIZE));

            // Perform protocol handshake
            TransferProtocol.readHandshake(in);
            TransferProtocol.sendHandshake(out);

            // Command loop
            while (running && !socket.isClosed()) {
                byte cmd;
                try {
                    cmd = in.readByte();
                } catch (EOFException | SocketException e) {
                    // Client disconnected normally or socket closed
                    break;
                }

                switch (cmd) {
                    case TransferProtocol.CMD_LIST:
                        handleList();
                        break;
                    case TransferProtocol.CMD_UPLOAD:
                        handleUpload();
                        break;
                    case TransferProtocol.CMD_DOWNLOAD:
                        handleDownload();
                        break;
                    case TransferProtocol.CMD_DISCONNECT:
                        server.logActivity(String.format("%s sent clean disconnect command.", clientId));
                        running = false;
                        break;
                    default:
                        server.logError(String.format("%s sent unknown command code: 0x%02X", clientId, cmd), null);
                        out.writeByte(TransferProtocol.STATUS_ERROR);
                        out.writeUTF("Unknown command code: " + cmd);
                        out.flush();
                        break;
                }
            }
        } catch (Exception e) {
            if (running && !socket.isClosed()) {
                server.logError(String.format("Error communicating with %s: %s", clientId, e.getMessage()), e);
            }
        } finally {
            close();
            server.removeClient(this);
            server.notifyClientDisconnected(clientId);
            server.logActivity(String.format("Connection closed for %s", clientId));
        }
    }

    private void handleList() throws IOException {
        server.logActivity(String.format("%s requested available file catalog.", clientId));
        List<FileInfo> files = fileService.listStoredFiles();

        out.writeByte(TransferProtocol.STATUS_OK);
        out.writeInt(files.size());
        for (FileInfo f : files) {
            out.writeUTF(f.getFilename());
            out.writeLong(f.getSize());
            out.writeLong(f.getLastModified());
            out.writeUTF(f.getSha256());
        }
        out.flush();
    }

    private void handleUpload() throws IOException {
        String filename = in.readUTF();
        long fileSize = in.readLong();
        String expectedChecksum = in.readUTF();

        server.logActivity(String.format("%s initiated UPLOAD for '%s' (%s, Expected SHA-256: %s)",
                clientId, filename, ChecksumService.formatFileSize(fileSize), expectedChecksum));

        TransferRecord record = new TransferRecord(filename, "Upload", fileSize);
        long startTime = System.currentTimeMillis();

        File destinationFile;
        try {
            if (fileSize > TransferProtocol.MAX_FILE_SIZE) {
                out.writeByte(TransferProtocol.STATUS_ERROR);
                out.writeUTF("File size exceeds maximum permitted limit (" +
                        ChecksumService.formatFileSize(TransferProtocol.MAX_FILE_SIZE) + ")");
                out.flush();
                record.setCompletionStatus("Failed");
                record.setErrorMessage("File exceeds max permitted size");
                server.notifyTransferCompleted(record);
                return;
            }

            destinationFile = fileService.resolveSafeDestination(filename);
            out.writeByte(TransferProtocol.STATUS_OK);
            out.flush();
        } catch (SecurityException se) {
            server.logError(String.format("Path traversal attempt blocked from %s for filename '%s'", clientId, filename), se);
            out.writeByte(TransferProtocol.STATUS_ERROR);
            out.writeUTF("Security Error: " + se.getMessage());
            out.flush();
            record.setCompletionStatus("Failed (Blocked)");
            record.setErrorMessage("Security violation: " + se.getMessage());
            server.notifyTransferCompleted(record);
            return;
        }

        try {
            String actualChecksum = TransferProtocol.receiveFileChunks(
                    in,
                    destinationFile,
                    fileSize,
                    (bytesReceived, total) -> server.notifyTransferProgress(clientId, filename, "Upload", bytesReceived, total)
            );

            boolean integrityPassed = ChecksumService.verifyChecksum(expectedChecksum, actualChecksum);
            long duration = System.currentTimeMillis() - startTime;

            record.setDurationMs(duration);
            record.setSha256Hash(actualChecksum);

            if (integrityPassed) {
                out.writeByte(TransferProtocol.STATUS_OK);
                out.writeUTF(actualChecksum);
                out.writeBoolean(true);
                out.writeUTF("Upload and SHA-256 integrity verification succeeded.");
                out.flush();

                record.setCompletionStatus("Completed");
                record.setIntegrityResult("Passed (SHA-256 match)");
                server.logActivity(String.format("SUCCESS: Upload of '%s' from %s completed in %d ms. Checksum: %s",
                        filename, clientId, duration, actualChecksum));
            } else {
                out.writeByte(TransferProtocol.STATUS_ERROR);
                out.writeUTF(actualChecksum);
                out.writeBoolean(false);
                out.writeUTF("SHA-256 checksum mismatch! Expected: " + expectedChecksum + " but calculated: " + actualChecksum);
                out.flush();

                record.setCompletionStatus("Failed (Checksum Mismatch)");
                record.setIntegrityResult("Failed");
                server.logError(String.format("CORRUPTION: Checksum mismatch for '%s' from %s! Expected: %s, Got: %s",
                        filename, clientId, expectedChecksum, actualChecksum), null);
            }

            server.notifyTransferCompleted(record);

        } catch (Exception e) {
            record.setCompletionStatus("Failed");
            record.setIntegrityResult("N/A");
            record.setErrorMessage(e.getMessage());
            server.notifyTransferCompleted(record);
            server.logError(String.format("Upload transfer aborted for '%s' from %s: %s",
                    filename, clientId, e.getMessage()), e);
            throw e;
        }
    }

    private void handleDownload() throws IOException {
        String filename = in.readUTF();
        server.logActivity(String.format("%s requested DOWNLOAD for '%s'", clientId, filename));

        TransferRecord record = new TransferRecord(filename, "Download", 0);
        long startTime = System.currentTimeMillis();

        File sourceFile;
        try {
            sourceFile = fileService.resolveSafeDestination(filename);
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                out.writeByte(TransferProtocol.STATUS_ERROR);
                out.writeUTF("Requested file does not exist on server: " + filename);
                out.flush();
                record.setCompletionStatus("Failed");
                record.setErrorMessage("File not found on server");
                server.notifyTransferCompleted(record);
                return;
            }
        } catch (SecurityException se) {
            server.logError(String.format("Path traversal attempt in download from %s: '%s'", clientId, filename), se);
            out.writeByte(TransferProtocol.STATUS_ERROR);
            out.writeUTF("Security Error: " + se.getMessage());
            out.flush();
            record.setCompletionStatus("Failed (Blocked)");
            record.setErrorMessage("Security violation: " + se.getMessage());
            server.notifyTransferCompleted(record);
            return;
        }

        long fileSize = sourceFile.length();
        String checksum = ChecksumService.calculateChecksum(sourceFile);

        out.writeByte(TransferProtocol.STATUS_OK);
        out.writeLong(fileSize);
        out.writeUTF(checksum);
        out.flush();

        try {
            TransferProtocol.streamFileChunks(
                    sourceFile,
                    out,
                    (bytesSent, total) -> server.notifyTransferProgress(clientId, filename, "Download", bytesSent, total)
            );

            // Read client acknowledgment
            byte clientAck = in.readByte();
            long duration = System.currentTimeMillis() - startTime;
            record.setDurationMs(duration);
            record.setSha256Hash(checksum);

            if (clientAck == TransferProtocol.STATUS_OK) {
                record.setCompletionStatus("Completed");
                record.setIntegrityResult("Passed (Verified by Client)");
                server.logActivity(String.format("SUCCESS: Download of '%s' to %s finished in %d ms.",
                        filename, clientId, duration));
            } else {
                String clientError = in.readUTF();
                record.setCompletionStatus("Failed (Client Error)");
                record.setIntegrityResult("Failed");
                record.setErrorMessage(clientError);
                server.logError(String.format("Client %s reported download error: %s", clientId, clientError), null);
            }

            server.notifyTransferCompleted(record);

        } catch (Exception e) {
            record.setCompletionStatus("Failed");
            record.setIntegrityResult("N/A");
            record.setErrorMessage(e.getMessage());
            server.notifyTransferCompleted(record);
            server.logError(String.format("Download transfer aborted for '%s' to %s: %s",
                    filename, clientId, e.getMessage()), e);
            throw e;
        }
    }

    public synchronized void close() {
        running = false;
        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
