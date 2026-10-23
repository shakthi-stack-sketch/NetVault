package com.netvault.client;

import com.netvault.model.FileInfo;
import com.netvault.model.TransferRecord;
import com.netvault.protocol.ProgressListener;
import com.netvault.protocol.TransferProtocol;
import com.netvault.service.ChecksumService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Client networking engine for interacting with the NetVault server.
 * Handles TCP socket connection, protocol framing, file uploads,
 * file downloads, and end-to-end SHA-256 integrity validation.
 */
public class FileTransferClient {

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String connectedHost;
    private int connectedPort;
    private volatile boolean connected = false;

    public FileTransferClient() {
    }

    public synchronized boolean isConnected() {
        return connected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getConnectedHost() {
        return connectedHost;
    }

    public int getConnectedPort() {
        return connectedPort;
    }

    /**
     * Establishes a TCP connection to the NetVault server and performs protocol handshake.
     *
     * @param host      server IP or hostname
     * @param port      server port
     * @param timeoutMs connection timeout in milliseconds
     * @throws IOException if connection fails or protocol handshake fails
     */
    public synchronized void connect(String host, int port, int timeoutMs) throws IOException {
        if (isConnected()) {
            disconnect();
        }

        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), timeoutMs);

        in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), TransferProtocol.CHUNK_SIZE));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream(), TransferProtocol.CHUNK_SIZE));

        // Perform protocol handshake
        TransferProtocol.sendHandshake(out);
        TransferProtocol.readHandshake(in);

        this.connectedHost = host;
        this.connectedPort = port;
        this.connected = true;
    }

    /**
     * Gracefully disconnects from the NetVault server.
     */
    public synchronized void disconnect() {
        if (!connected) {
            return;
        }
        connected = false;

        try {
            if (out != null) {
                out.writeByte(TransferProtocol.CMD_DISCONNECT);
                out.flush();
            }
        } catch (Exception ignored) {
        }

        try {
            if (in != null) in.close();
        } catch (Exception ignored) {}
        try {
            if (out != null) out.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}

        socket = null;
        in = null;
        out = null;
    }

    /**
     * Requests the catalog of files currently hosted on the server.
     *
     * @return list of FileInfo descriptors
     * @throws IOException on network or protocol error
     */
    public synchronized List<FileInfo> listFiles() throws IOException {
        ensureConnected();

        out.writeByte(TransferProtocol.CMD_LIST);
        out.flush();

        byte status = in.readByte();
        if (status != TransferProtocol.STATUS_OK) {
            String errorMsg = in.readUTF();
            throw new IOException("Server error listing files: " + errorMsg);
        }

        int count = in.readInt();
        List<FileInfo> fileList = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = in.readUTF();
            long size = in.readLong();
            long modified = in.readLong();
            String hash = in.readUTF();
            fileList.add(new FileInfo(name, size, modified, hash));
        }

        return fileList;
    }

    /**
     * Uploads a local file to the server and verifies integrity via SHA-256.
     *
     * @param localFile the file to upload
     * @param listener  optional progress callback
     * @return TransferRecord with details of the operation
     * @throws IOException on transfer or verification failure
     */
    public synchronized TransferRecord uploadFile(File localFile, ProgressListener listener) throws IOException {
        ensureConnected();

        if (localFile == null || !localFile.exists() || !localFile.isFile()) {
            throw new IllegalArgumentException("Invalid local file: " + localFile);
        }

        String rawName = localFile.getName();
        String safeName = TransferProtocol.sanitizeFilename(rawName);
        long fileSize = localFile.length();

        TransferRecord record = new TransferRecord(safeName, "Upload", fileSize);
        long startTime = System.currentTimeMillis();

        // 1. Calculate local SHA-256 checksum prior to sending
        String expectedChecksum = ChecksumService.calculateChecksum(localFile);
        record.setSha256Hash(expectedChecksum);

        // 2. Send upload request header
        out.writeByte(TransferProtocol.CMD_UPLOAD);
        out.writeUTF(safeName);
        out.writeLong(fileSize);
        out.writeUTF(expectedChecksum);
        out.flush();

        // 3. Await server readiness acknowledgment
        byte ack = in.readByte();
        if (ack != TransferProtocol.STATUS_OK) {
            String reason = in.readUTF();
            record.setCompletionStatus("Failed");
            record.setErrorMessage("Server rejected upload: " + reason);
            throw new IOException("Server rejected upload: " + reason);
        }

        // 4. Stream file chunks to server
        String streamedChecksum = TransferProtocol.streamFileChunks(localFile, out, listener);

        // 5. Read server verification result
        byte serverResult = in.readByte();
        String serverChecksum = in.readUTF();
        boolean matched = in.readBoolean();
        String message = in.readUTF();

        long duration = System.currentTimeMillis() - startTime;
        record.setDurationMs(duration);

        if (serverResult == TransferProtocol.STATUS_OK && matched) {
            record.setCompletionStatus("Completed");
            record.setIntegrityResult("Passed (SHA-256 match)");
        } else {
            record.setCompletionStatus("Failed (Integrity Mismatch)");
            record.setIntegrityResult("Failed");
            record.setErrorMessage(message);
            throw new IOException("Upload integrity verification failed: " + message);
        }

        return record;
    }

    /**
     * Downloads a file from the server to the local destination and verifies SHA-256 integrity.
     *
     * @param remoteFilename filename on the server
     * @param destination    local file destination
     * @param listener       optional progress callback
     * @return TransferRecord detailing the result
     * @throws IOException on network or verification error
     */
    public synchronized TransferRecord downloadFile(String remoteFilename, File destination, ProgressListener listener)
            throws IOException {
        ensureConnected();

        String safeName = TransferProtocol.sanitizeFilename(remoteFilename);
        TransferRecord record = new TransferRecord(safeName, "Download", 0);
        long startTime = System.currentTimeMillis();

        // 1. Send download request
        out.writeByte(TransferProtocol.CMD_DOWNLOAD);
        out.writeUTF(safeName);
        out.flush();

        // 2. Read server response
        byte status = in.readByte();
        if (status != TransferProtocol.STATUS_OK) {
            String error = in.readUTF();
            record.setCompletionStatus("Failed");
            record.setErrorMessage(error);
            throw new IOException("Server cannot provide file: " + error);
        }

        long expectedSize = in.readLong();
        String expectedChecksum = in.readUTF();

        // 3. Stream chunks into destination file and compute SHA-256
        String receivedChecksum;
        try {
            receivedChecksum = TransferProtocol.receiveFileChunks(in, destination, expectedSize, listener);
        } catch (IOException e) {
            record.setCompletionStatus("Failed");
            record.setErrorMessage(e.getMessage());
            // Acknowledge failure to server
            try {
                out.writeByte(TransferProtocol.STATUS_ERROR);
                out.writeUTF(e.getMessage());
                out.flush();
            } catch (Exception ignored) {}
            throw e;
        }

        // 4. Verify received checksum against expected checksum
        boolean verified = ChecksumService.verifyChecksum(expectedChecksum, receivedChecksum);
        long duration = System.currentTimeMillis() - startTime;
        record.setDurationMs(duration);
        record.setSha256Hash(receivedChecksum);

        if (verified) {
            // Acknowledge success to server
            out.writeByte(TransferProtocol.STATUS_OK);
            out.flush();

            record.setCompletionStatus("Completed");
            record.setIntegrityResult("Passed (SHA-256 match)");
        } else {
            // Acknowledge failure to server
            out.writeByte(TransferProtocol.STATUS_ERROR);
            out.writeUTF("Client checksum mismatch! Expected: " + expectedChecksum + ", Got: " + receivedChecksum);
            out.flush();

            record.setCompletionStatus("Failed (Integrity Mismatch)");
            record.setIntegrityResult("Failed");
            record.setErrorMessage("Integrity verification failed!");
            throw new IOException(String.format("Checksum mismatch! Expected: %s, Received: %s",
                    expectedChecksum, receivedChecksum));
        }

        return record;
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Client is not connected to any NetVault server.");
        }
    }
}
