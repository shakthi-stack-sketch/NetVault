package com.netvault.protocol;

/**
 * Functional callback interface for reporting real-time progress during
 * streaming chunk file transfers over TCP sockets.
 */
@FunctionalInterface
public interface ProgressListener {
    /**
     * Called when a chunk of data has been transferred.
     *
     * @param bytesTransferred cumulative bytes sent or received so far
     * @param totalBytes       total file size in bytes
     */
    void onProgress(long bytesTransferred, long totalBytes);
}
