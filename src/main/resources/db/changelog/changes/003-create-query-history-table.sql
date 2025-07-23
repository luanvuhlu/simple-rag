--liquibase formatted sql

--changeset luanvv:003-create-query-history-table
--comment: Create query_history table to store user queries and responses

CREATE TABLE query_history (
    id BIGSERIAL PRIMARY KEY,
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    query_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    relevant_documents TEXT,
    processing_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--rollback DROP TABLE query_history;
