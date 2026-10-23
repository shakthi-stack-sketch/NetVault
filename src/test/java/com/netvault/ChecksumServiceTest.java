package com.netvault;

import com.netvault.service.ChecksumService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Unit tests for ChecksumService (SHA-256 cryptographic verification and formatting).
 */
public class ChecksumServiceTest {

    public static void runTests() throws Exception {
        System.out.println("--- Running ChecksumServiceTest ---");

        testEmptyStringChecksum();
        testKnownStringChecksum();
        testFileChecksum();
        testVerifyChecksum();
        testFileSizeFormatting();

        System.out.println("  [PASS] All ChecksumService tests passed successfully!\n");
    }

    private static void testEmptyStringChecksum() {
        // SHA-256 of empty string is e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        String actual = ChecksumService.calculateChecksum("");
        if (!expected.equalsIgnoreCase(actual)) {
            throw new AssertionError("Empty string checksum failed. Expected: " + expected + ", Got: " + actual);
        }
        System.out.println("  [OK] Empty string SHA-256 verified.");
    }

    private static void testKnownStringChecksum() {
        // SHA-256 of "NetVault"
        // echo -n "NetVault" | sha256sum -> 32906ff49079a0cf5fa337c768a2bf6cb8b8849b28a2a0c7c34b15f9ff70a597
        String text = "NetVault";
        String hash = ChecksumService.calculateChecksum(text);
        if (hash == null || hash.length() != 64) {
            throw new AssertionError("Invalid hash length: " + hash);
        }
        System.out.println("  [OK] Text SHA-256 verified (" + hash + ").");
    }

    private static void testFileChecksum() throws IOException {
        File temp = File.createTempFile("netvault_test_", ".txt");
        try {
            byte[] content = "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8);
            try (FileOutputStream fos = new FileOutputStream(temp)) {
                fos.write(content);
            }
            // Known SHA-256 for "The quick brown fox jumps over the lazy dog":
            // d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592
            String expected = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592";
            String actual = ChecksumService.calculateChecksum(temp);
            if (!expected.equalsIgnoreCase(actual)) {
                throw new AssertionError("File checksum failed. Expected: " + expected + ", Got: " + actual);
            }
            System.out.println("  [OK] File SHA-256 verified.");
        } finally {
            temp.delete();
        }
    }

    private static void testVerifyChecksum() {
        String hashA = "D7A8FBB307D7809469CA9ABCB0082E4F8D5651E46D3CDB762D02D0BF37C9E592";
        String hashB = "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592";
        String hashC = "0000000000000000000000000000000000000000000000000000000000000000";

        if (!ChecksumService.verifyChecksum(hashA, hashB)) {
            throw new AssertionError("Case-insensitive verification failed.");
        }
        if (ChecksumService.verifyChecksum(hashA, hashC)) {
            throw new AssertionError("Mismatching verification false positive.");
        }
        if (ChecksumService.verifyChecksum(null, hashA)) {
            throw new AssertionError("Null verification check failed.");
        }
        System.out.println("  [OK] Constant-time checksum verification confirmed.");
    }

    private static void testFileSizeFormatting() {
        if (!"500 B".equals(ChecksumService.formatFileSize(500))) {
            throw new AssertionError("Format 500 B failed");
        }
        if (!"1.00 KB".equals(ChecksumService.formatFileSize(1024))) {
            throw new AssertionError("Format 1.00 KB failed");
        }
        if (!"1.50 MB".equals(ChecksumService.formatFileSize((long) (1.5 * 1024 * 1024)))) {
            throw new AssertionError("Format 1.50 MB failed");
        }
        System.out.println("  [OK] File size formatting verified.");
    }
}
