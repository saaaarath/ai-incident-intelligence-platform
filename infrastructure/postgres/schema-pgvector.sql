-- Schema for storing document embeddings in PostgreSQL with pgvector
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS document_embeddings (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL,
    document_type VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    chunk TEXT NOT NULL,
    embedding vector,
    metadata TEXT,
    chunk_index INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_embed_doc_id ON document_embeddings(document_id);
CREATE INDEX IF NOT EXISTS idx_doc_embed_type ON document_embeddings(document_type);
