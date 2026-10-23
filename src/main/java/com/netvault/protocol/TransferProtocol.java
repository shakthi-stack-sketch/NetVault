package com.netvault.protocol;

import com.netvault.service.ChecksumService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

/**
 * NetVault Application-Layer Protocol Specification and Streaming Engine.
 *
 * Provides unambiguous message framing, chunked buffered streaming over TCP,
 * incremental SHA-256 checksum calculation, and path traversal protection.
 */
public final class TransferProtocol {

    /** Magic 4-byte header: 'NVLT' (0x4E564C54) */
    public static final int MAGIC_HEADER = 0x4E564C54;

    /** Current Protocol Version */
    public static final byte PROTOCOL_VERSION = 0x01;

    /** Command identifiers */
    public static final byte CMD_LIST = 0x01;
    public static final byte CMD_UPLOAD = 0x02;
    public static final byte CMD_DOWNLOAD = 0x03;
    public static final byte CMD_DISCONNECT = 0x04;

    /** Response status codes */
    public static final byte STATUS_OK = 0x10;
    public static final byte STATUS_ERROR = 0x11;

    /** Streaming chunk size (64 KB) */
    public static final int CHUNK_SIZE = 64 * 1024;

    /** Maximum allowed file size: 5 Gigabytes */
    public static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;

    private TransferProtocol() {
        // Utility class
    }

    /**
     * Sanitizes a user-supplied or client-supplied filename to prevent
     * path traversal, directory escape, and illegal file naming attacks.
     *
     * @param rawFilename the raw input filename
     * @return clean basename strictly without directory delimiters
     * @throws SecurityException if an attempt to traverse or inject invalid paths is detected
     */
    public static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            throw new SecurityException("Filename cannot be null or empty");
        }

        // Strip leading/trailing whitespaces
        String cleaned = rawFilename.trim();

        // Reject null-byte injection attacks
        if (cleaned.indexOf('\0') != -1) {
            throw new SecurityException("Null byte detected in filename");
        }

        // Normalize separators to forward slash
        cleaned = cleaned.replace('\\', '/');

        // Check for directory traversal sequences
        if (cleaned.contains("..")) {
            throw new SecurityException("Directory traversal attack detected (contains '..'): " + rawFilename);
        }

        // Extract base filename only
        Path path = Paths.get(cleaned);
        Path fileNamePath = path.getFileName();
        if (fileNamePath == null) {
            throw new SecurityException("Invalid filename path: " + rawFilename);
        }
        cleaned = fileNamePath.toString();

        // Strip any remaining dangerous characters or drive letters
        cleaned = cleaned.replaceAll("[<>:\"/\\\\|?*]", "_");

        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            throw new SecurityException("Filename resolves to an invalid or empty name: " + rawFilename);
        }

        return cleaned;
    }

    /**
     * Sends the protocol handshake header (Magic number + version).
     */
    public static void sendHandshake(DataOutputStream out) throws IOException {
        out.writeInt(MAGIC_HEADER);
        out.writeByte(PROTOCOL_VERSION);
        out.flush();
    }

    /**
     * Reads and verifies the protocol handshake header.
     *
     * @throws IOException if header is invalid or protocol version is incompatible
     */
    public static void readHandshake(DataInputStream in) throws IOException {
        int magic = in.readInt();
        if (magic != MAGIC_HEADER) {
            throw new IOException(String.format("Invalid protocol magic header: 0x%08X (expected: 0x%08X)",
                    magic, MAGIC_HEADER));
        }
        byte version = in.readByte();
        if (version != PROTOCOL_VERSION) {
            throw new IOException("Unsupported protocol version: " + version + " (expected: " + PROTOCOL_VERSION + ")");
        }
    }

    /**
     * Streams a file from disk across the network socket in buffered chunks.
     * Computes incremental SHA-256 digest on the fly.
     *
     * @param sourceFile the file to send
     * @param out        network output stream
     * @param listener   optional progress listener
     * @return hex SHA-256 checksum of the streamed bytes
     * @throws IOException if network or file read error occurs
     */
    public static String streamFileChunks(File sourceFile, DataOutputStream out, ProgressListener listener)
            throws IOException {
        if (!sourceFile.exists() || !sourceFile.isFile()) {
            throw new IOException("Source file does not exist: " + sourceFile.getAbsolutePath());
        }

        long totalSize = sourceFile.length();
        long bytesSent = 0;
        MessageDigest digest = ChecksumService.createDigest();
        byte[] buffer = new byte[CHUNK_SIZE];

        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(sourceFile), CHUNK_SIZE)) {
            int read;
            while ((read = fileIn.read(buffer)) != -1) {
                // Write chunk size followed by payload
                out.writeInt(read);
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                bytesSent += read;

                if (listener != null) {
                    listener.onProgress(bytesSent, totalSize);
                }
            }
            // Write EOF chunk marker (0 size)
            out.writeInt(0);
            out.flush();
        }

        return ChecksumService.bytesToHex(digest.digest());
    }

    /**
     * Receives chunked file stream from network socket and writes to destination file.
     * Computes incremental SHA-256 digest on the fly.
     *
     * @param in              network input stream
     * @param destinationFile local file destination to save
     * @param expectedTotal   expected total size (for progress reporting)
     * @param listener        optional progress listener
     * @return hex SHA-256 checksum of received bytes
     * @throws IOException if network or disk write error occurs
     */
    public static String receiveFileChunks(DataInputStream in, File destinationFile, long expectedTotal,
                                           ProgressListener listener) throws IOException {
        // Ensure parent directory exists
        File parent = destinationFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        long bytesReceived = 0;
        MessageDigest digest = ChecksumService.createDigest();
        byte[] buffer = new byte[CHUNK_SIZE];

        try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(destinationFile), CHUNK_SIZE)) {
            while (true) {
                int chunkSize = in.readInt();
                if (chunkSize == 0) {
                    // EOF chunk marker received
                    break;
                }
                if (chunkSize < 0 || chunkSize > CHUNK_SIZE * 2) {
                    throw new IOException("Malformed chunk size received: " + chunkSize);
                }

                // Read exactly chunkSize bytes into buffer
                in.readFully(buffer, 0, chunkSize);
                fileOut.write(buffer, 0, chunkSize);
                digest.update(buffer, 0, chunkSize);
                bytesReceived += chunkSize;

                if (listener != null) {
                    listener.onProgress(bytesReceived, expectedTotal);
                }
            }
            fileOut.flush();
        }

        if (expectedTotal > 0 && bytesReceived != expectedTotal) {
            throw new IOException(String.format("Transfer size mismatch: received %d bytes, expected %d bytes",
                    bytesReceived, expectedTotal));
        }

        return ChecksumService.bytesToHex(digest.digest());
    }
}
