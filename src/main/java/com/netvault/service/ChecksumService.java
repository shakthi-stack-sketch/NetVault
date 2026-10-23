package com.netvault.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Service responsible for computing and verifying cryptographic checksums
 * using the SHA-256 algorithm. Also provides human-readable file size utilities.
 */
public final class ChecksumService {

    public static final String ALGORITHM = "SHA-256";
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB buffer

    private ChecksumService() {
        // Utility class, cannot instantiate
    }

    /**
     * Creates a new SHA-256 MessageDigest instance.
     */
    public static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JRE", e);
        }
    }

    /**
     * Computes the SHA-256 checksum of a file on disk.
     *
     * @param file the file to hash
     * @return hex-encoded SHA-256 string
     * @throws IOException if file cannot be read
     */
    public static String calculateChecksum(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("File must exist and be a regular file: " + file);
        }

        MessageDigest digest = createDigest();
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return bytesToHex(digest.digest());
    }

    /**
     * Computes the SHA-256 checksum of a byte array.
     */
    public static String calculateChecksum(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }
        MessageDigest digest = createDigest();
        digest.update(data);
        return bytesToHex(digest.digest());
    }

    /**
     * Computes the SHA-256 checksum of a UTF-8 text string.
     */
    public static String calculateChecksum(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        return calculateChecksum(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compares an expected SHA-256 checksum with an actual SHA-256 checksum.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param expected checksum
     * @param actual   checksum
     * @return true if both match
     */
    public static boolean verifyChecksum(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.trim().toLowerCase().getBytes(StandardCharsets.UTF_8),
                actual.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * Converts a raw byte array to a lowercase hexadecimal string.
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Formats bytes into human-readable units (B, KB, MB, GB).
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.2f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.2f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.2f GB", gb);
    }
}
