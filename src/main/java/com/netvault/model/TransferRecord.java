package com.netvault.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Model representing a single file transfer record in the session history.
 */
public class TransferRecord {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String id;
    private final String filename;
    private final String operation; // "Upload" or "Download"
    private final long fileSize;
    private final String startTime;
    private volatile String completionStatus; // "In Progress", "Completed", "Failed"
    private volatile String integrityResult;  // "Passed", "Failed", "N/A"
    private volatile String sha256Hash;
    private volatile long durationMs;
    private volatile String errorMessage;

    public TransferRecord(String filename, String operation, long fileSize) {
        this.id = System.currentTimeMillis() + "_" + Math.abs(filename.hashCode());
        this.filename = filename;
        this.operation = operation;
        this.fileSize = fileSize;
        this.startTime = LocalDateTime.now().format(FORMATTER);
        this.completionStatus = "In Progress";
        this.integrityResult = "Pending";
        this.sha256Hash = "";
        this.durationMs = 0;
        this.errorMessage = "";
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getOperation() {
        return operation;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getCompletionStatus() {
        return completionStatus;
    }

    public void setCompletionStatus(String completionStatus) {
        this.completionStatus = completionStatus;
    }

    public String getIntegrityResult() {
        return integrityResult;
    }

    public void setIntegrityResult(String integrityResult) {
        this.integrityResult = integrityResult;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
