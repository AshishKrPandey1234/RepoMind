CREATE TABLE index_jobs (
    id CHAR(36) NOT NULL PRIMARY KEY,
    github_user_id VARCHAR(64) NOT NULL,
    github_repository_id BIGINT NOT NULL,
    repository_full_name VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    commit_sha CHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    files_seen INT NOT NULL DEFAULT 0,
    files_indexed INT NOT NULL DEFAULT 0,
    files_skipped INT NOT NULL DEFAULT 0,
    chunks_written INT NOT NULL DEFAULT 0,
    indexed_bytes BIGINT NOT NULL DEFAULT 0,
    error_code VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6) NULL,
    finished_at TIMESTAMP(6) NULL,
    INDEX idx_index_jobs_owner_created (github_user_id, created_at),
    INDEX idx_index_jobs_status (status)
);

CREATE TABLE code_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    job_id CHAR(36) NOT NULL,
    github_repository_id BIGINT NOT NULL,
    github_user_id VARCHAR(64) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    commit_sha CHAR(40) NOT NULL,
    file_path VARCHAR(1024) NOT NULL,
    blob_sha CHAR(40) NOT NULL,
    chunk_index INT NOT NULL,
    line_start INT NOT NULL,
    line_end INT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    INDEX idx_code_chunks_job (job_id),
    CONSTRAINT fk_code_chunks_job FOREIGN KEY (job_id) REFERENCES index_jobs(id) ON DELETE CASCADE
);
