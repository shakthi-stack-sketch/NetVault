package com.netvault.server;

import com.netvault.model.TransferRecord;

/**
 * Event listener interface for monitoring NetVault server lifecycle,
 * client connections, and file transfer operations.
 */
public interface ServerEventListener {

    void onServerStarted(String hostAddress, int port);

    void onServerStopped();

    void onClientConnected(String clientId, String remoteAddress, int remotePort);

    void onClientDisconnected(String clientId);

    void onTransferProgress(String clientId, String filename, String operation, long transferred, long total);

    void onTransferCompleted(TransferRecord record);

    void onLogActivity(String message);

    void onLogError(String message, Throwable throwable);
}
