package com.aiincident.logprocessor.historical.embedding;

import com.aiincident.logprocessor.historical.KnowledgeDocument;
import com.aiincident.logprocessor.historical.KnowledgeDocumentType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Splits operational knowledge documents into structured text chunks optimized for vector embedding.
 */
@Component
public class DocumentChunker {

    private final ObjectMapper objectMapper;
    private final EmbeddingProperties properties;

    public DocumentChunker(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<DocumentChunk> chunkDocument(KnowledgeDocument document) {
        if (document == null || document.getContent() == null || document.getContent().isBlank()) {
            return List.of();
        }

        int maxChunkSize = properties.getChunkSize() > 100 ? properties.getChunkSize() : 500;
        int overlap = properties.getChunkOverlap() >= 0 ? properties.getChunkOverlap() : 100;

        List<DocumentChunk> chunks = new ArrayList<>();
        String fullContent = document.getContent().trim();
        String metadataJson = serializeMetadata(document);

        // Split markdown content by section headers (e.g. ## Symptoms, ## Root Cause)
        String[] sections = fullContent.split("(?=(?m)^##\\s+)");

        int chunkIndex = 0;
        for (String section : sections) {
            String trimmedSection = section.trim();
            if (trimmedSection.isBlank()) {
                continue;
            }

            if (trimmedSection.length() <= maxChunkSize) {
                String chunkText = formatChunkText(document.getTitle(), trimmedSection);
                chunks.add(new DocumentChunk(
                        document.getDocumentId(),
                        document.getDocumentType(),
                        fullContent,
                        chunkText,
                        metadataJson,
                        chunkIndex++
                ));
            } else {
                // Split large section into overlapping windows
                List<String> subChunks = splitTextWithOverlap(trimmedSection, maxChunkSize, overlap);
                for (String sub : subChunks) {
                    String chunkText = formatChunkText(document.getTitle(), sub);
                    chunks.add(new DocumentChunk(
                            document.getDocumentId(),
                            document.getDocumentType(),
                            fullContent,
                            chunkText,
                            metadataJson,
                            chunkIndex++
                    ));
                }
            }
        }

        // Fallback if no sections were parsed
        if (chunks.isEmpty()) {
            List<String> subChunks = splitTextWithOverlap(fullContent, maxChunkSize, overlap);
            for (String sub : subChunks) {
                chunks.add(new DocumentChunk(
                        document.getDocumentId(),
                        document.getDocumentType(),
                        fullContent,
                        sub,
                        metadataJson,
                        chunkIndex++
                ));
            }
        }

        return chunks;
    }

    private String formatChunkText(String title, String sectionText) {
        if (title != null && !title.isBlank() && !sectionText.startsWith("# " + title)) {
            return "[" + title + "]\n" + sectionText;
        }
        return sectionText;
    }

    private List<String> splitTextWithOverlap(String text, int maxChunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChunkSize, text.length());

            // Try breaking at paragraph or sentence boundary if possible
            if (end < text.length()) {
                int lastPara = text.lastIndexOf("\n\n", end);
                if (lastPara > start + (maxChunkSize / 2)) {
                    end = lastPara + 2;
                } else {
                    int lastPeriod = text.lastIndexOf(". ", end);
                    if (lastPeriod > start + (maxChunkSize / 2)) {
                        end = lastPeriod + 2;
                    }
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                result.add(chunk);
            }

            if (end >= text.length()) {
                break;
            }

            start = Math.max(start + 1, end - overlap);
        }

        return result;
    }

    private String serializeMetadata(KnowledgeDocument document) {
        Map<String, Object> meta = new HashMap<>(document.getMetadata());
        meta.put("documentId", document.getDocumentId());
        meta.put("documentType", document.getDocumentType().name());
        meta.put("title", document.getTitle());
        if (document.getCategory() != null) {
            meta.put("category", document.getCategory().name());
        }
        if (document.getSeverity() != null) {
            meta.put("severity", document.getSeverity().name());
        }
        if (document.getRelatedServices() != null) {
            meta.put("services", document.getRelatedServices());
        }
        if (document.getTags() != null) {
            meta.put("tags", document.getTags());
        }

        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public static class DocumentChunk {
        private final String documentId;
        private final KnowledgeDocumentType documentType;
        private final String fullContent;
        private final String chunkText;
        private final String metadata;
        private final int chunkIndex;

        public DocumentChunk(
                String documentId,
                KnowledgeDocumentType documentType,
                String fullContent,
                String chunkText,
                String metadata,
                int chunkIndex) {
            this.documentId = documentId;
            this.documentType = documentType;
            this.fullContent = fullContent;
            this.chunkText = chunkText;
            this.metadata = metadata;
            this.chunkIndex = chunkIndex;
        }

        public String getDocumentId() {
            return documentId;
        }

        public KnowledgeDocumentType getDocumentType() {
            return documentType;
        }

        public String getFullContent() {
            return fullContent;
        }

        public String getChunkText() {
            return chunkText;
        }

        public String getMetadata() {
            return metadata;
        }

        public int getChunkIndex() {
            return chunkIndex;
        }
    }
}
