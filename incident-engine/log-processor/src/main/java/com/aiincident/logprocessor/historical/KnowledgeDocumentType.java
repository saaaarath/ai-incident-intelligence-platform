package com.aiincident.logprocessor.historical;

/**
 * Types of operational knowledge documents available for search and embedding.
 */
public enum KnowledgeDocumentType {
    HISTORICAL_INCIDENT,
    POSTMORTEM,
    RUNBOOK;

    public static KnowledgeDocumentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace("-", "_").replace(" ", "_");
        for (KnowledgeDocumentType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
