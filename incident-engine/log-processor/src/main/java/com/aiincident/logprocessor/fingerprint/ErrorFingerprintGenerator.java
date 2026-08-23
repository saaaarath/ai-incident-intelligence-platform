package com.aiincident.logprocessor.fingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic error normalizer and fingerprint generator.
 * Converts variable dynamic values (UUIDs, timestamps, numeric IDs, IP addresses, durations)
 * into canonical template tokens and computes a deterministic SHA-256 fingerprint hash.
 */
@Component
public class ErrorFingerprintGenerator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"
    );

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?\\b"
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "\\b\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\b"
    );

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}\\b"
    );

    private static final Pattern IP_PORT_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d+)?\\b"
    );

    private static final Pattern HEX_PATTERN = Pattern.compile(
            "\\b0x[0-9a-fA-F]+\\b|\\b[0-9a-fA-F]{16,64}\\b"
    );

    private static final Pattern NAMED_ID_PATTERN = Pattern.compile(
            "(?i)\\b(order|user|account|payment|item|session|request|txn|tx|customer|record|trace)[_-]?(id|num|number)?\\s*[:=#]\\s*([a-zA-Z0-9_-]+)"
    );

    private static final Pattern HASH_NUM_PATTERN = Pattern.compile(
            "#[0-9]+"
    );

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(ms|s|m|h|seconds?|minutes?|millis(?:econds?)?|bytes?|kb|mb|gb)\\b"
    );

    private static final Pattern NUMBER_PATTERN = Pattern.compile(
            "\\b\\d+(?:\\.\\d+)?\\b"
    );

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile(
            "\\s+"
    );

    /**
     * Normalize dynamic parameters in the message string.
     */
    public String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }

        String result = message.trim();

        // 1. UUIDs
        result = UUID_PATTERN.matcher(result).replaceAll("<UUID>");

        // 2. ISO / RFC Timestamps
        result = TIMESTAMP_PATTERN.matcher(result).replaceAll("<TIMESTAMP>");

        // 3. Time formats (e.g. 20:03:18)
        result = TIME_PATTERN.matcher(result).replaceAll("<TIME>");

        // 4. Date formats
        result = DATE_PATTERN.matcher(result).replaceAll("<DATE>");

        // 5. IP Addresses & Ports
        result = IP_PORT_PATTERN.matcher(result).replaceAll("<IP>");

        // 6. Hex codes / memory addresses
        result = HEX_PATTERN.matcher(result).replaceAll("<HEX>");

        // 7. Named IDs (e.g. order_id=123, userId: 456)
        result = NAMED_ID_PATTERN.matcher(result).replaceAll("$1_id=<ID>");

        // 8. #1234 hash IDs
        result = HASH_NUM_PATTERN.matcher(result).replaceAll("#<ID>");

        // 9. Duration units (e.g. 3000ms -> <NUM>ms)
        result = DURATION_PATTERN.matcher(result).replaceAll("<NUM>$1");

        // 10. Standalone numeric values
        result = NUMBER_PATTERN.matcher(result).replaceAll("<NUM>");

        // 11. Normalize whitespaces and lowercase
        result = WHITESPACE_PATTERN.matcher(result).replaceAll(" ").trim().toLowerCase();

        return result;
    }

    /**
     * Generate normalized error fingerprint from service, eventType, and message.
     */
    public ErrorFingerprint generateFingerprint(String service, String eventType, String message) {
        String canonicalService = service != null ? service.toLowerCase().trim() : "unknown";
        String canonicalEventType = eventType != null ? eventType.toUpperCase().trim() : "UNKNOWN";
        String normalizedMsg = normalizeMessage(message);

        String canonicalPattern = String.format("%s:%s:%s", canonicalService, canonicalEventType, normalizedMsg);
        String hash = sha256Hex(canonicalPattern);

        return new ErrorFingerprint(hash, canonicalService, canonicalEventType, normalizedMsg, canonicalPattern);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
