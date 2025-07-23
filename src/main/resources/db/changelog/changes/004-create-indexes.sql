--liquibase formatted sql

--changeset luanvv:004-create-indexes
--comment: Create indexes for better query performance

-- Index for document chunks vector similarity search
CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding 
ON document_chunks USING ivfflat (embedding_vector vector_cosine_ops) 
WITH (lists = 100);

-- Index for document chunks by document_id
CREATE INDEX IF NOT EXISTS idx_document_chunks_document_id 
ON document_chunks(document_id);

-- Index for documents by upload_date
CREATE INDEX IF NOT EXISTS idx_documents_upload_date 
ON documents(upload_date DESC);

-- Index for query_history by query_date
CREATE INDEX IF NOT EXISTS idx_query_history_query_date 
ON query_history(query_date DESC);

--rollback DROP INDEX IF EXISTS idx_document_chunks_embedding;
--rollback DROP INDEX IF EXISTS idx_document_chunks_document_id;
--rollback DROP INDEX IF EXISTS idx_documents_upload_date;
--rollback DROP INDEX IF EXISTS idx_query_history_query_date;
