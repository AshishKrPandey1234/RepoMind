package com.repomind.indexing;

import java.time.Instant;

public record IndexJobResponse(
        String id,
        IndexJobStatus status,
        String repositoryFullName,
        String branch,
        String commitSha,
        int filesSeen,
        int filesIndexed,
        int filesSkipped,
        int chunksWritten,
        long indexedBytes,
        String errorCode,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt) {

    public IndexJobResponse(String id, IndexJobStatus status, int filesSeen, int filesIndexed, int filesSkipped,
                            int chunksWritten, long indexedBytes, String errorCode, Instant createdAt,
                            Instant startedAt, Instant finishedAt) {
        this(id, status, null, null, null, filesSeen, filesIndexed, filesSkipped, chunksWritten, indexedBytes,
                errorCode, createdAt, startedAt, finishedAt);
    }

    static IndexJobResponse from(IndexJobEntity job) {
        return new IndexJobResponse(job.getId(), job.getStatus(), job.getRepositoryFullName(), job.getBranchName(),
                job.getCommitSha(), job.getFilesSeen(), job.getFilesIndexed(),
                job.getFilesSkipped(), job.getChunksWritten(), job.getIndexedBytes(), job.getErrorCode(),
                job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
    }
}
