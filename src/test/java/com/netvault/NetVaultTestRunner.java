package com.netvault;

/**
 * Master test runner for NetVault automated unit and integration tests.
 */
public class NetVaultTestRunner {

    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("   NetVault Automated Verification Test Suite");
        System.out.println("==================================================\n");

        int testsPassed = 0;
        int testsFailed = 0;

        // 1. Checksum Tests
        try {
            ChecksumServiceTest.runTests();
            testsPassed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] ChecksumServiceTest failed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        }

        // 2. Protocol & Security Tests
        try {
            TransferProtocolTest.runTests();
            testsPassed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] TransferProtocolTest failed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        }

        // 3. Network Integration Tests
        try {
            NetworkTransferIntegrationTest.runTests();
            testsPassed++;
        } catch (Throwable t) {
            System.err.println("  [FAIL] NetworkTransferIntegrationTest failed: " + t.getMessage());
            t.printStackTrace();
            testsFailed++;
        }

        System.out.println("==================================================");
        System.out.println(String.format("   Summary: %d Test Suites Passed, %d Failed", testsPassed, testsFailed));
        System.out.println("==================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }
}
