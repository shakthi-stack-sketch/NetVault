package com.netvault;

import com.netvault.client.FileTransferClient;
import com.netvault.model.FileInfo;
import com.netvault.model.TransferRecord;
import com.netvault.server.FileTransferServer;
import com.netvault.service.ChecksumService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * End-to-end integration tests for NetVault TCP socket networking,
 * concurrent client handling, chunked upload/download, SHA-256 integrity,
 * and error resilience.
 */
public class NetworkTransferIntegrationTest {

    public static void runTests() throws Exception {
        System.out.println("--- Running NetworkTransferIntegrationTest ---");

        Path tempDir = Files.createTempDirectory("netvault_integration_");
        Path serverStorage = tempDir.resolve("server_storage");
        Path clientStorage = tempDir.resolve("client_storage");
        Files.createDirectories(serverStorage);
        Files.createDirectories(clientStorage);

        FileTransferServer server = new FileTransferServer(serverStorage);
        // Start server on an ephemeral port (0 allows OS to allocate any free port)
        server.start(0);
        int port = server.getBoundPort();
        System.out.println("  [OK] Server started on dynamic port: " + port);

        try {
            // Test 1: Connect and Disconnect
            testConnectDisconnect(port);

            // Test 2: Multiple concurrent clients
            testMultipleConcurrentClients(port);

            // Test 3: Upload and SHA-256 verification
            File testUploadFile = createSampleFile(tempDir.resolve("upload_sample.bin"), 128 * 1024); // 128 KB
            testFileUpload(port, testUploadFile);

            // Test 4: Catalog Listing
            testCatalogListing(port, testUploadFile.getName());

            // Test 5: Download and SHA-256 verification
            File downloadedFile = clientStorage.resolve("downloaded_sample.bin").toFile();
            testFileDownload(port, testUploadFile.getName(), downloadedFile, testUploadFile);

            // Test 6: Non-existent file download handling
            testDownloadNonExistent(port, clientStorage);

            // Test 7: Connection to invalid port
            testInvalidPortConnection();

            System.out.println("  [PASS] All NetworkTransferIntegration tests completed successfully!\n");

        } finally {
            server.stop();
            System.out.println("  [OK] Server stopped cleanly.");
            // Cleanup temp directory
            deleteRecursively(tempDir.toFile());
        }
    }

    private static void testConnectDisconnect(int port) throws IOException {
        FileTransferClient client = new FileTransferClient();
        client.connect("127.0.0.1", port, 3000);
        if (!client.isConnected()) {
            throw new AssertionError("Client failed to connect to server");
        }
        client.disconnect();
        if (client.isConnected()) {
            throw new AssertionError("Client failed to disconnect cleanly");
        }
        System.out.println("  [OK] Client connect and disconnect verified.");
    }

    private static void testMultipleConcurrentClients(int port) throws Exception {
        int clientCount = 4;
        FileTransferClient[] clients = new FileTransferClient[clientCount];
        for (int i = 0; i < clientCount; i++) {
            clients[i] = new FileTransferClient();
            clients[i].connect("127.0.0.1", port, 3000);
            if (!clients[i].isConnected()) {
                throw new AssertionError("Client " + i + " failed to connect concurrently");
            }
        }
        System.out.println("  [OK] " + clientCount + " concurrent clients connected simultaneously.");

        for (int i = 0; i < clientCount; i++) {
            clients[i].disconnect();
        }
        System.out.println("  [OK] " + clientCount + " concurrent clients disconnected cleanly.");
    }

    private static void testFileUpload(int port, File sourceFile) throws IOException {
        FileTransferClient client = new FileTransferClient();
        client.connect("127.0.0.1", port, 3000);
        try {
            TransferRecord record = client.uploadFile(sourceFile, null);
            if (!"Completed".equals(record.getCompletionStatus())) {
                throw new AssertionError("Upload status was not Completed: " + record.getCompletionStatus());
            }
            if (!record.getIntegrityResult().startsWith("Passed")) {
                throw new AssertionError("Upload integrity check did not pass: " + record.getIntegrityResult());
            }
            String expectedSha = ChecksumService.calculateChecksum(sourceFile);
            if (!ChecksumService.verifyChecksum(expectedSha, record.getSha256Hash())) {
                throw new AssertionError("Upload SHA-256 mismatch!");
            }
            System.out.println("  [OK] File upload succeeded with valid SHA-256 (" + record.getSha256Hash() + ").");
        } finally {
            client.disconnect();
        }
    }

    private static void testCatalogListing(int port, String expectedFileName) throws IOException {
        FileTransferClient client = new FileTransferClient();
        client.connect("127.0.0.1", port, 3000);
        try {
            List<FileInfo> files = client.listFiles();
            boolean found = files.stream().anyMatch(f -> f.getFilename().equals(expectedFileName));
            if (!found) {
                throw new AssertionError("Uploaded file not found in server catalog!");
            }
            System.out.println("  [OK] Server catalog listing verified. Found: " + expectedFileName);
        } finally {
            client.disconnect();
        }
    }

    private static void testFileDownload(int port, String remoteFileName, File destFile, File originalFile)
            throws IOException {
        FileTransferClient client = new FileTransferClient();
        client.connect("127.0.0.1", port, 3000);
        try {
            TransferRecord record = client.downloadFile(remoteFileName, destFile, null);
            if (!"Completed".equals(record.getCompletionStatus())) {
                throw new AssertionError("Download status was not Completed: " + record.getCompletionStatus());
            }
            if (!record.getIntegrityResult().startsWith("Passed")) {
                throw new AssertionError("Download integrity check failed!");
            }

            // Byte-by-byte file comparison
            byte[] originalBytes = Files.readAllBytes(originalFile.toPath());
            byte[] downloadedBytes = Files.readAllBytes(destFile.toPath());

            if (!Arrays.equals(originalBytes, downloadedBytes)) {
                throw new AssertionError("Downloaded bytes do not match original bytes!");
            }
            System.out.println("  [OK] File download succeeded and byte-by-byte content verified identical.");
        } finally {
            client.disconnect();
        }
    }

    private static void testDownloadNonExistent(int port, Path clientDir) throws IOException {
        FileTransferClient client = new FileTransferClient();
        client.connect("127.0.0.1", port, 3000);
        try {
            File dest = clientDir.resolve("ghost.bin").toFile();
            client.downloadFile("does_not_exist_xyz.bin", dest, null);
            throw new AssertionError("Downloading non-existent file should have thrown IOException!");
        } catch (IOException expected) {
            System.out.println("  [OK] Non-existent file download handled gracefully: " + expected.getMessage());
        } finally {
            client.disconnect();
        }
    }

    private static void testInvalidPortConnection() {
        FileTransferClient client = new FileTransferClient();
        try {
            // Attempt connection to unused port
            client.connect("127.0.0.1", 64321, 500);
            throw new AssertionError("Connecting to closed port should fail!");
        } catch (IOException expected) {
            System.out.println("  [OK] Connection to closed port rejected as expected: " + expected.getMessage());
        }
    }

    private static File createSampleFile(Path path, int sizeBytes) throws IOException {
        File file = path.toFile();
        byte[] data = new byte[sizeBytes];
        new Random(42).nextBytes(data); // Deterministic random payload
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        return file;
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File c : children) {
                    deleteRecursively(c);
                }
            }
        }
        file.delete();
    }
}
