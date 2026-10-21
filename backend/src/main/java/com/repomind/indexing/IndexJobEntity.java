package com.repomind.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "index_jobs")
public class IndexJobEntity {
    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "github_user_id", length = 64, nullable = false)
    private String githubUserId;

    @Column(name = "github_repository_id", nullable = false)
    private long githubRepositoryId;

    @Column(name = "repository_full_name", length = 255, nullable = false)
    private String repositoryFullName;

    @Column(name = "branch_name", length = 255, nullable = false)
    private String branchName;

    @Column(name = "commit_sha", length = 40, nullable = false)
    private String commitSha;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private IndexJobStatus status;

    @Column(name = "files_seen", nullable = false)
    private int filesSeen;

    @Column(name = "files_indexed", nullable = false)
    private int filesIndexed;

    @Column(name = "files_skipped", nullable = false)
    private int filesSkipped;

    @Column(name = "chunks_written", nullable = false)
    private int chunksWritten;

    @Column(name = "indexed_bytes", nullable = false)
    private long indexedBytes;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected IndexJobEntity() {
    }

    public IndexJobEntity(String id, String githubUserId, long githubRepositoryId,
                          String repositoryFullName, String branchName, String commitSha) {
        this.id = id;
        this.githubUserId = githubUserId;
        this.githubRepositoryId = githubRepositoryId;
        this.repositoryFullName = repositoryFullName;
        this.branchName = branchName;
        this.commitSha = commitSha;
        this.status = IndexJobStatus.QUEUED;
        this.createdAt = Instant.now();
    }

    public void start() {
        status = IndexJobStatus.RUNNING;
        startedAt = Instant.now();
    }

    public void progress(int filesSeen, int filesIndexed, int filesSkipped, long indexedBytes) {
        this.filesSeen = filesSeen;
        this.filesIndexed = filesIndexed;
        this.filesSkipped = filesSkipped;
        this.indexedBytes = indexedBytes;
    }

    public void succeed(int filesSeen, int filesIndexed, int filesSkipped, int chunksWritten, long indexedBytes) {
        progress(filesSeen, filesIndexed, filesSkipped, indexedBytes);
        this.chunksWritten = chunksWritten;
        this.status = IndexJobStatus.SUCCEEDED;
        this.errorCode = null;
        this.finishedAt = Instant.now();
    }

    public void fail(String errorCode) {
        this.status = IndexJobStatus.FAILED;
        this.errorCode = errorCode;
        this.finishedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getGithubUserId() { return githubUserId; }
    public long getGithubRepositoryId() { return githubRepositoryId; }
    public String getRepositoryFullName() { return repositoryFullName; }
    public String getBranchName() { return branchName; }
    public String getCommitSha() { return commitSha; }
    public IndexJobStatus getStatus() { return status; }
    public int getFilesSeen() { return filesSeen; }
    public int getFilesIndexed() { return filesIndexed; }
    public int getFilesSkipped() { return filesSkipped; }
    public int getChunksWritten() { return chunksWritten; }
    public long getIndexedBytes() { return indexedBytes; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
}
