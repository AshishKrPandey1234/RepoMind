package com.repomind.indexing;

import com.repomind.repository.GitHubContentClient;
import com.repomind.repository.GitHubRepositoryService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.core.task.TaskRejectedException;

import java.util.concurrent.Executor;

@Service
public class IndexingService {
    private final GitHubRepositoryService repositories;
    private final GitHubContentClient github;
    private final IndexJobStore jobs;
    private final IndexJobProcessor processor;
    private final IndexingProperties properties;
    private final Executor executor;

    public IndexingService(GitHubRepositoryService repositories, GitHubContentClient github, IndexJobStore jobs,
                           IndexJobProcessor processor, IndexingProperties properties,
                           @Qualifier("indexingExecutor") Executor executor) {
        this.repositories = repositories;
        this.github = github;
        this.jobs = jobs;
        this.processor = processor;
        this.properties = properties;
        this.executor = executor;
    }

    public IndexJobResponse start(String githubUserId, OAuth2AuthorizedClient authorizedClient,
                                  StartIndexJobRequest request) {
        if (githubUserId == null || githubUserId.isBlank()) {
            throw new IndexingException(401, "AUTHENTICATION_REQUIRED");
        }
        String token = authorizedClient.getAccessToken().getTokenValue();
        repositories.verifyRepositoryListedForUser(authorizedClient, request.githubRepositoryId(),
                properties.maxAccessCheckPages());
        GitHubContentClient.RepositoryDetails repository = github.getPublicRepository(token,
                request.githubRepositoryId());
        if (!"public".equalsIgnoreCase(repository.visibility())) {
            throw new IndexingException(404, "GITHUB_RESOURCE_NOT_FOUND");
        }
        String branch = request.branch().trim();
        String commitSha = github.resolveBranch(token, repository, branch);

        synchronized (this) {
            if (jobs.countActiveForUser(githubUserId) >= properties.maxActiveJobsPerUser()) {
                throw new IndexingException(429, "ACTIVE_INDEX_JOB_LIMIT");
            }
            IndexJobEntity job = jobs.create(githubUserId, repository, branch, commitSha);
            try {
                executor.execute(() -> processor.process(job.getId(), githubUserId, repository, branch, commitSha, token));
            } catch (TaskRejectedException exception) {
                jobs.fail(job.getId(), "INDEX_QUEUE_FULL", 0, 0);
            }
            return jobs.findOwned(job.getId(), githubUserId);
        }
    }

    public IndexJobResponse getOwned(String jobId, String githubUserId) {
        if (githubUserId == null || githubUserId.isBlank()) {
            throw new IndexingException(401, "AUTHENTICATION_REQUIRED");
        }
        try {
            return jobs.findOwned(jobId, githubUserId);
        } catch (jakarta.persistence.EntityNotFoundException exception) {
            throw new IndexingException(HttpStatus.NOT_FOUND.value(), "INDEX_JOB_NOT_FOUND");
        }
    }

    public RepositoryIndexStatusResponse getRepositoryStatus(long repositoryId, String githubUserId) {
        if (githubUserId == null || githubUserId.isBlank()) {
            throw new IndexingException(401, "AUTHENTICATION_REQUIRED");
        }
        return jobs.findRepositoryStatus(repositoryId, githubUserId);
    }
}
