package com.aiincident.logprocessor.historical;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentEmbeddingRepository extends JpaRepository<DocumentEmbedding, Long> {

    List<DocumentEmbedding> findByDocumentId(String documentId);

    List<DocumentEmbedding> findByDocumentType(KnowledgeDocumentType documentType);

    long countByDocumentId(String documentId);

    void deleteByDocumentId(String documentId);
}
