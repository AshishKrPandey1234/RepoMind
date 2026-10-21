package com.repomind.indexing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

@Entity
@Table(name = "code_chunks", indexes = @Index(name = "idx_code_chunks_job", columnList = "job_id"))
public class CodeChunkEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private IndexJobEntity job;

    @Column(name = "github_repository_id", nullable = false)
    private long githubRepositoryId;

    @Column(name = "github_user_id", length = 64, nullable = false)
    private String githubUserId;

    @Column(name = "branch_name", length = 255, nullable = false)
    private String branchName;

    @Column(name = "commit_sha", length = 40, nullable = false)
    private String commitSha;

    @Column(name = "file_path", length = 1024, nullable = false)
    private String filePath;

    @Column(name = "blob_sha", length = 40, nullable = false)
    private String blobSha;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "line_start", nullable = false)
    private int lineStart;

    @Column(name = "line_end", nullable = false)
    private int lineEnd;

    @Column(name = "content_sha256", length = 64, nullable = false)
    private String contentSha256;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "embedding_status", length = 20, nullable = false)
    private EmbeddingStatus embeddingStatus = EmbeddingStatus.PENDING;

    @Column(name = "embedding_model", length = 120)
    private String embeddingModel;

    protected CodeChunkEntity() {
    }

    public CodeChunkEntity(IndexJobEntity job, long githubRepositoryId, String githubUserId, String branchName,
                           String commitSha, CodeChunker.CodeChunk chunk, String filePath, String blobSha) {
        this.job = job;
        this.githubRepositoryId = githubRepositoryId;
        this.githubUserId = githubUserId;
        this.branchName = branchName;
        this.commitSha = commitSha;
        this.filePath = filePath;
        this.blobSha = blobSha;
        this.chunkIndex = chunk.index();
        this.lineStart = chunk.lineStart();
        this.lineEnd = chunk.lineEnd();
        this.contentSha256 = chunk.sha256();
        this.content = chunk.content();
    }

    public void markEmbedded(String model) {
        this.embeddingStatus = EmbeddingStatus.INDEXED;
        this.embeddingModel = model;
    }

    public Long getId() { return id; }
    public IndexJobEntity getJob() { return job; }
    public long getGithubRepositoryId() { return githubRepositoryId; }
    public String getGithubUserId() { return githubUserId; }
    public String getBranchName() { return branchName; }
    public String getCommitSha() { return commitSha; }
    public String getFilePath() { return filePath; }
    public String getBlobSha() { return blobSha; }
    public int getChunkIndex() { return chunkIndex; }
    public int getLineStart() { return lineStart; }
    public int getLineEnd() { return lineEnd; }
    public String getContentSha256() { return contentSha256; }
    public String getContent() { return content; }
    public EmbeddingStatus getEmbeddingStatus() { return embeddingStatus; }
    public String getEmbeddingModel() { return embeddingModel; }
}
