package com.aiincident.logprocessor.fingerprint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorFingerprintGeneratorTest {

    private ErrorFingerprintGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ErrorFingerprintGenerator();
    }

    @Test
    @DisplayName("Equivalent errors with different dynamic UUIDs and timestamps must produce identical fingerprints")
    void testEquivalentErrorsWithDynamicUUIDsAndTimestamps() {
        String msg1 = "Failed to process payment for order 8f3d1b82-419b-4e12-b054-e67d28711e9a at 2026-08-23T14:03:18Z: DB connection timeout after 3000ms";
        String msg2 = "Failed to process payment for order 3c2a9810-75d1-4321-9988-f1e2d3c4b5a6 at 2026-08-23T15:22:45Z: DB connection timeout after 5000ms";

        ErrorFingerprint fp1 = generator.generateFingerprint("payment-service", "DB_TIMEOUT", msg1);
        ErrorFingerprint fp2 = generator.generateFingerprint("payment-service", "DB_TIMEOUT", msg2);

        // Normalized messages must match canonical template
        assertThat(fp1.normalizedMessage()).isEqualTo("failed to process payment for order <uuid> at <timestamp>: db connection timeout after <num>ms");
        assertThat(fp2.normalizedMessage()).isEqualTo("failed to process payment for order <uuid> at <timestamp>: db connection timeout after <num>ms");

        // Fingerprint hashes must be identical
        assertThat(fp1.fingerprintHash()).isEqualTo(fp2.fingerprintHash());
        assertThat(fp1.canonicalPattern()).isEqualTo(fp2.canonicalPattern());
    }

    @Test
    @DisplayName("Equivalent errors with varying IP addresses, ports, hex addresses, and numeric IDs must produce identical fingerprints")
    void testEquivalentErrorsWithIPsHexAndNumericIDs() {
        String msg1 = "Connection refused to database host 192.168.1.100:5432 with socket handle 0x7fff5fbff820 for user_id: 10421";
        String msg2 = "Connection refused to database host 10.0.0.5:5432 with socket handle 0x7fff5fbff940 for user_id: 99881";

        ErrorFingerprint fp1 = generator.generateFingerprint("order-service", "DB_FAILURE", msg1);
        ErrorFingerprint fp2 = generator.generateFingerprint("order-service", "DB_FAILURE", msg2);

        assertThat(fp1.normalizedMessage()).isEqualTo(fp2.normalizedMessage());
        assertThat(fp1.fingerprintHash()).isEqualTo(fp2.fingerprintHash());
    }

    @Test
    @DisplayName("Equivalent errors with varying hash numbers and execution latencies must produce identical fingerprints")
    void testEquivalentErrorsWithHashNumbersAndLatencies() {
        String msg1 = "Inventory reservation failed for item #48291: lock acquisition timed out after 250.5ms";
        String msg2 = "Inventory reservation failed for item #10928: lock acquisition timed out after 890.0ms";

        ErrorFingerprint fp1 = generator.generateFingerprint("inventory-service", "INVENTORY_RESERVATION_FAILED", msg1);
        ErrorFingerprint fp2 = generator.generateFingerprint("inventory-service", "INVENTORY_RESERVATION_FAILED", msg2);

        assertThat(fp1.normalizedMessage()).isEqualTo("inventory reservation failed for item_id=<id>: lock acquisition timed out after <num>ms");
        assertThat(fp1.fingerprintHash()).isEqualTo(fp2.fingerprintHash());
    }

    @Test
    @DisplayName("Different error types or services must produce distinct fingerprints")
    void testDistinctErrorsProduceDistinctFingerprints() {
        String msg = "Connection pool exhausted: active connections 100/100";

        ErrorFingerprint fpPayment = generator.generateFingerprint("payment-service", "POOL_EXHAUSTED", msg);
        ErrorFingerprint fpOrder = generator.generateFingerprint("order-service", "POOL_EXHAUSTED", msg);
        ErrorFingerprint fpTimeout = generator.generateFingerprint("payment-service", "DB_TIMEOUT", msg);

        assertThat(fpPayment.fingerprintHash()).isNotEqualTo(fpOrder.fingerprintHash());
        assertThat(fpPayment.fingerprintHash()).isNotEqualTo(fpTimeout.fingerprintHash());
    }

    @Test
    @DisplayName("Handles null and empty values gracefully")
    void testNullAndEmptyInputs() {
        ErrorFingerprint fpNull = generator.generateFingerprint(null, null, null);
        assertThat(fpNull.service()).isEqualTo("unknown");
        assertThat(fpNull.eventType()).isEqualTo("UNKNOWN");
        assertThat(fpNull.normalizedMessage()).isEmpty();
        assertThat(fpNull.fingerprintHash()).isNotBlank();
    }
}
