--liquibase formatted sql

--changeset luanvv:005-increase-document-chunks
--comment: Increate document chunks size

ALTER TABLE document_chunks ALTER COLUMN embedding_vector TYPE vector(1024);

--rollback ALTER TABLE document_chunks ALTER COLUMN embedding_vector TYPE vector(768);
