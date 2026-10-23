package com.netvault.service;

import com.netvault.model.FileInfo;
import com.netvault.protocol.TransferProtocol;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that manages server-side storage, directory integrity checks,
 * file validation, and file cataloging.
 */
public class FileTransferService {

    private final Path storageDirectory;

    public FileTransferService(Path storageDirectory) {
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
        ensureDirectoryExists();
    }

    /**
     * Ensures that the server storage directory exists and is writable.
     */
    public synchronized void ensureDirectoryExists() {
        try {
            if (!Files.exists(storageDirectory)) {
                Files.createDirectories(storageDirectory);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create storage directory: " + storageDirectory, e);
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }

    /**
     * Resolves and verifies that a destination path stays strictly within the storage directory.
     * Prevents path traversal and directory escape.
     *
     * @param filename the raw filename
     * @return safe validated File object within storage directory
     */
    public File resolveSafeDestination(String filename) {
        String cleanName = TransferProtocol.sanitizeFilename(filename);
        Path targetPath = storageDirectory.resolve(cleanName).normalize();

        // Defense-in-depth: confirm path is strictly a descendant of storageDirectory
        if (!targetPath.startsWith(storageDirectory)) {
            throw new SecurityException("Path traversal attempt detected: resolved path is outside storage directory!");
        }

        return targetPath.toFile();
    }

    /**
     * Lists all available files currently stored in the server storage directory.
     */
    public synchronized List<FileInfo> listStoredFiles() {
        List<FileInfo> list = new ArrayList<>();
        File dir = storageDirectory.toFile();
        File[] files = dir.listFiles(File::isFile);
        if (files == null) {
            return list;
        }

        for (File file : files) {
            // Skip hidden or system placeholder files
            if (file.getName().startsWith(".")) {
                continue;
            }
            String hash = "";
            try {
                hash = ChecksumService.calculateChecksum(file);
            } catch (Exception ignored) {
                hash = "Error computing SHA-256";
            }
            list.add(new FileInfo(file.getName(), file.length(), file.lastModified(), hash));
        }

        // Sort alphabetically by filename
        list.sort((a, b) -> a.getFilename().compareToIgnoreCase(b.getFilename()));
        return list;
    }

    /**
     * Returns the total storage used in bytes.
     */
    public synchronized long getTotalStorageUsed() {
        File dir = storageDirectory.toFile();
        File[] files = dir.listFiles(File::isFile);
        if (files == null) return 0;
        long total = 0;
        for (File f : files) {
            if (!f.getName().startsWith(".")) {
                total += f.length();
            }
        }
        return total;
    }

    /**
     * Returns the total count of stored files.
     */
    public synchronized int getStoredFilesCount() {
        File dir = storageDirectory.toFile();
        File[] files = dir.listFiles(f -> f.isFile() && !f.getName().startsWith("."));
        return files == null ? 0 : files.length;
    }
}
