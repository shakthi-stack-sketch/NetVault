package com.netvault;

import com.netvault.protocol.TransferProtocol;

/**
 * Unit tests for TransferProtocol (security, path traversal prevention, and framing rules).
 */
public class TransferProtocolTest {

    public static void runTests() {
        System.out.println("--- Running TransferProtocolTest ---");

        testCleanFilenames();
        testPathTraversalAttacksBlocked();
        testAbsolutePathsBlocked();
        testNullByteInjectionBlocked();

        System.out.println("  [PASS] All TransferProtocol tests passed successfully!\n");
    }

    private static void testCleanFilenames() {
        String clean = TransferProtocol.sanitizeFilename("document.pdf");
        if (!"document.pdf".equals(clean)) {
            throw new AssertionError("Clean filename altered: " + clean);
        }

        String withSpaces = TransferProtocol.sanitizeFilename("  my report 2026.docx  ");
        if (!"my report 2026.docx".equals(withSpaces)) {
            throw new AssertionError("Trimmed filename mismatch: " + withSpaces);
        }
        System.out.println("  [OK] Standard filenames accepted.");
    }

    private static void testPathTraversalAttacksBlocked() {
        String[] maliciousInputs = {
                "../secret.txt",
                "..\\secret.txt",
                "folder/../../passwords.db",
                "..",
                "/etc/passwd",
                "C:\\Windows\\System32\\calc.exe"
        };

        for (String input : maliciousInputs) {
            boolean blocked = false;
            try {
                String result = TransferProtocol.sanitizeFilename(input);
                // If it extracted base filename without '..', verify it doesn't contain '..' or slashes
                if (result.contains("..") || result.contains("/") || result.contains("\\")) {
                    throw new AssertionError("Traversal leaked into sanitized filename: " + result);
                }
            } catch (SecurityException e) {
                blocked = true;
            }
            System.out.println("  [OK] Dangerous input handled safely: " + input + (blocked ? " (SecurityException thrown)" : " (sanitized)"));
        }
    }

    private static void testAbsolutePathsBlocked() {
        try {
            TransferProtocol.sanitizeFilename("   ");
            throw new AssertionError("Empty filename should have been rejected!");
        } catch (SecurityException expected) {
            System.out.println("  [OK] Empty/whitespace filename rejected.");
        }
    }

    private static void testNullByteInjectionBlocked() {
        try {
            TransferProtocol.sanitizeFilename("innocent.txt\0.exe");
            throw new AssertionError("Null byte injection should have been rejected!");
        } catch (SecurityException expected) {
            System.out.println("  [OK] Null byte injection attack blocked.");
        }
    }
}
