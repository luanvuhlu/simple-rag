--liquibase formatted sql

--changeset luanvv:002-create-document-chunks-table
--comment: Create document_chunks table to store text chunks with vector embeddings

CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_text TEXT NOT NULL,
    chunk_index INTEGER NOT NULL,
    embedding_vector vector(768),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

--rollback DROP TABLE document_chunks;
