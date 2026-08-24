package com.aiincident.logprocessor.rca;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of validating an AI-generated RCA report for schema compliance and evidence grounding.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RcaValidationResult(
        @JsonProperty("isValid") boolean isValid,
        @JsonProperty("isGrounded") boolean isGrounded,
        @JsonProperty("status") RcaValidationStatus status,
        @JsonProperty("errors") List<String> errors,
        @JsonProperty("groundingViolations") List<String> groundingViolations,
        @JsonProperty("warnings") List<String> warnings
) {

    public enum RcaValidationStatus {
        VALID,
        UNGROUNDED,
        INVALID_SCHEMA,
        CONFIDENCE_MISMATCH,
        MALFORMED_JSON,
        REJECTED
    }

    // Convenience alias getters for Jackson / JSON-path compatibility
    @JsonProperty("valid")
    public boolean getValid() {
        return isValid;
    }

    @JsonProperty("grounded")
    public boolean getGrounded() {
        return isGrounded;
    }

    public static RcaValidationResult valid() {
        return new RcaValidationResult(true, true, RcaValidationStatus.VALID, List.of(), List.of(), List.of());
    }

    public static RcaValidationResult validWithWarnings(List<String> warnings) {
        return new RcaValidationResult(true, true, RcaValidationStatus.VALID, List.of(), List.of(), warnings != null ? warnings : List.of());
    }

    public static RcaValidationResult invalid(RcaValidationStatus status, List<String> errors, List<String> groundingViolations, List<String> warnings) {
        return new RcaValidationResult(
                false,
                groundingViolations == null || groundingViolations.isEmpty(),
                status != null ? status : RcaValidationStatus.REJECTED,
                errors != null ? errors : List.of(),
                groundingViolations != null ? groundingViolations : List.of(),
                warnings != null ? warnings : List.of()
        );
    }
}
