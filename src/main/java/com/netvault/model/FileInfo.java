package com.netvault.model;

import java.io.Serializable;

/**
 * Model representing metadata for a file stored on the server.
 */
public class FileInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String filename;
    private final long size;
    private final long lastModified;
    private final String sha256;

    public FileInfo(String filename, long size, long lastModified, String sha256) {
        this.filename = filename;
        this.size = size;
        this.lastModified = lastModified;
        this.sha256 = sha256 != null ? sha256 : "";
    }

    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public String getSha256() {
        return sha256;
    }

    @Override
    public String toString() {
        return filename + " (" + size + " bytes)";
    }
}
