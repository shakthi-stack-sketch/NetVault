package com.netvault.server;

import com.netvault.model.TransferRecord;
import com.netvault.service.FileTransferService;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-performance concurrent TCP file transfer server.
 * Manages ServerSocket lifecycle, thread pool dispatching, and client event propagation.
 */
public class FileTransferServer {

    public static final int DEFAULT_PORT = 5000;
    public static final Path DEFAULT_STORAGE_PATH = Paths.get("received-files");

    private final FileTransferService fileService;
    private final List<ServerEventListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ClientHandler> activeClients = new CopyOnWriteArrayList<>();
    private final AtomicInteger clientCounter = new AtomicInteger(0);

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread acceptorThread;
    private volatile boolean running = false;
    private int boundPort;

    public FileTransferServer() {
        this(DEFAULT_STORAGE_PATH);
    }

    public FileTransferServer(Path storagePath) {
        this.fileService = new FileTransferService(storagePath);
    }

    public FileTransferService getFileService() {
        return fileService;
    }

    public void addListener(ServerEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ServerEventListener listener) {
        listeners.remove(listener);
    }

    public synchronized boolean isRunning() {
        return running;
    }

    public synchronized int getBoundPort() {
        return boundPort;
    }

    public List<ClientHandler> getActiveClients() {
        return Collections.unmodifiableList(activeClients);
    }

    /**
     * Starts listening for client TCP connections on the specified port.
     *
     * @param port port number (0 for dynamic port allocation)
     * @throws IOException if port cannot be opened (e.g. port already in use)
     */
    public synchronized void start(int port) throws IOException {
        if (running) {
            throw new IllegalStateException("Server is already running on port " + boundPort);
        }

        fileService.ensureDirectoryExists();
        threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("NetVault-Worker-" + t.getId());
            t.setDaemon(true);
            return t;
        });

        serverSocket = new ServerSocket(port);
        boundPort = serverSocket.getLocalPort();
        running = true;

        String serverIp = getLocalHostAddress();
        logActivity(String.format("Server bound to %s:%d. Storage directory: %s",
                serverIp, boundPort, fileService.getStorageDirectory().toAbsolutePath()));

        for (ServerEventListener listener : listeners) {
            listener.onServerStarted(serverIp, boundPort);
        }

        acceptorThread = new Thread(this::acceptLoop, "NetVault-Acceptor");
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                // Configure TCP socket options for optimal throughput and responsiveness
                clientSocket.setTcpNoDelay(true); // Disable Nagle's algorithm for immediate packet dispatch
                clientSocket.setSoTimeout(0);     // Infinite timeout for active transfers (handled by protocol)

                int clientNum = clientCounter.incrementAndGet();
                ClientHandler handler = new ClientHandler(clientSocket, this, fileService, clientNum);
                activeClients.add(handler);
                threadPool.submit(handler);

            } catch (IOException e) {
                if (!running || serverSocket.isClosed()) {
                    // Normal server shutdown
                    break;
                }
                logError("Failed to accept client connection: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Gracefully stops the server, disconnects all active clients,
     * and releases network sockets and thread pool resources.
     */
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        logActivity("Shutting down server...");

        // Close server socket to interrupt acceptor thread
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logError("Error closing server socket: " + e.getMessage(), e);
        }

        // Close all active client connections
        for (ClientHandler client : activeClients) {
            client.close();
        }
        activeClients.clear();

        // Shutdown thread pool
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdownNow();
        }

        logActivity("Server stopped successfully.");
        for (ServerEventListener listener : listeners) {
            listener.onServerStopped();
        }
    }

    void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
    }

    void notifyClientConnected(String clientId, String ip, int port) {
        for (ServerEventListener listener : listeners) {
            listener.onClientConnected(clientId, ip, port);
        }
    }

    void notifyClientDisconnected(String clientId) {
        for (ServerEventListener listener : listeners) {
            listener.onClientDisconnected(clientId);
        }
    }

    void notifyTransferProgress(String clientId, String filename, String op, long transferred, long total) {
        for (ServerEventListener listener : listeners) {
            listener.onTransferProgress(clientId, filename, op, transferred, total);
        }
    }

    void notifyTransferCompleted(TransferRecord record) {
        for (ServerEventListener listener : listeners) {
            listener.onTransferCompleted(record);
        }
    }

    public void logActivity(String message) {
        for (ServerEventListener listener : listeners) {
            listener.onLogActivity(message);
        }
    }

    public void logError(String message, Throwable t) {
        for (ServerEventListener listener : listeners) {
            listener.onLogError(message, t);
        }
    }

    /**
     * Discovers the best non-loopback IPv4 address for local network display.
     */
    public static String getLocalHostAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            List<String> candidateIps = new ArrayList<>();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                Enumeration<InetAddress> addresses = ni.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // Prefer site-local / private IPv4 address (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
                    if (!addr.isLoopbackAddress() && addr.getAddress().length == 4) {
                        if (addr.isSiteLocalAddress()) {
                            return addr.getHostAddress();
                        }
                        candidateIps.add(addr.getHostAddress());
                    }
                }
            }
            if (!candidateIps.isEmpty()) {
                return candidateIps.get(0);
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
