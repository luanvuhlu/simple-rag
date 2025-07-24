--liquibase formatted sql

--changeset luanvv:006-add-document-content
--comment: Add extracted_text column to documents table

ALTER TABLE documents ADD COLUMN extracted_text TEXT;

--rollback ALTER TABLE documents DROP COLUMN extracted_text;
