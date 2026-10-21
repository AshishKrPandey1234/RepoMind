ALTER TABLE code_chunks
    ADD COLUMN embedding_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN embedding_model VARCHAR(120) NULL,
    ADD INDEX idx_code_chunks_embedding (job_id, embedding_status, id);
