package com.repomind.indexing;

import jakarta.persistence.EntityNotFoundException;
import com.repomind.repository.GitHubContentClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IndexJobStore {
    private static final List<IndexJobStatus> ACTIVE = List.of(IndexJobStatus.QUEUED, IndexJobStatus.RUNNING);

    private final IndexJobRepository jobs;
    private final CodeChunkRepository chunks;

    public IndexJobStore(IndexJobRepository jobs, CodeChunkRepository chunks) {
        this.jobs = jobs;
        this.chunks = chunks;
    }

    @Transactional(readOnly = true)
    public long countActiveForUser(String githubUserId) {
        return jobs.countByGithubUserIdAndStatusIn(githubUserId, ACTIVE);
    }

    @Transactional
    public IndexJobEntity create(String githubUserId, GitHubContentClient.RepositoryDetails repository,
                                 String branch, String commitSha) {
        return jobs.saveAndFlush(new IndexJobEntity(UUID.randomUUID().toString(), githubUserId,
                repository.id(), repository.fullName(), branch, commitSha));
    }

    @Transactional(readOnly = true)
    public IndexJobResponse findOwned(String id, String githubUserId) {
        return jobs.findByIdAndGithubUserId(id, githubUserId)
                .map(IndexJobResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Index job not found"));
    }

    @Transactional(readOnly = true)
    public RepositoryIndexStatusResponse findRepositoryStatus(long repositoryId, String githubUserId) {
        var latest = jobs.findFirstByGithubRepositoryIdAndGithubUserIdOrderByCreatedAtDesc(repositoryId, githubUserId)
                .map(IndexJobResponse::from).orElse(null);
        var successful = jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                repositoryId, githubUserId, IndexJobStatus.SUCCEEDED).map(IndexJobResponse::from).orElse(null);
        return new RepositoryIndexStatusResponse(latest, successful);
    }

    @Transactional
    public void markRunning(String id) {
        jobs.findById(id).ifPresent(IndexJobEntity::start);
    }

    @Transactional
    public void updateProgress(String id, int filesSeen, int filesIndexed, int filesSkipped, long indexedBytes) {
        jobs.findById(id).ifPresent(job -> job.progress(filesSeen, filesIndexed, filesSkipped, indexedBytes));
    }

    @Transactional
    public void complete(String id, List<PreparedChunk> preparedChunks, int filesSeen, int filesIndexed,
                         int filesSkipped, long indexedBytes) {
        IndexJobEntity job = jobs.findById(id).orElseThrow(() -> new EntityNotFoundException("Index job not found"));
        List<CodeChunkEntity> entities = preparedChunks.stream().map(item -> new CodeChunkEntity(job,
                job.getGithubRepositoryId(), job.getGithubUserId(), job.getBranchName(), job.getCommitSha(),
                item.chunk(), item.path(), item.blobSha())).toList();
        chunks.saveAll(entities);
        job.succeed(filesSeen, filesIndexed, filesSkipped, preparedChunks.size(), indexedBytes);
    }

    @Transactional
    public void fail(String id, String errorCode, int filesSeen, int filesSkipped) {
        jobs.findById(id).ifPresent(job -> {
            job.progress(filesSeen, 0, filesSkipped, 0);
            job.fail(errorCode);
        });
    }

    @Transactional
    public int failInterruptedJobs() {
        List<IndexJobEntity> interrupted = jobs.findAllByStatusIn(ACTIVE);
        for (IndexJobEntity job : interrupted) job.fail("SERVER_RESTARTED");
        return interrupted.size();
    }

    public record PreparedChunk(String path, String blobSha, CodeChunker.CodeChunk chunk) {
    }
}
