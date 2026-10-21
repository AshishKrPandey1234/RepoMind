package com.repomind.indexing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IndexJobRepository extends JpaRepository<IndexJobEntity, String> {
    Optional<IndexJobEntity> findByIdAndGithubUserId(String id, String githubUserId);
    long countByGithubUserIdAndStatusIn(String githubUserId, Collection<IndexJobStatus> statuses);
    List<IndexJobEntity> findAllByStatusIn(Collection<IndexJobStatus> statuses);
    Optional<IndexJobEntity> findFirstByGithubRepositoryIdAndGithubUserIdOrderByCreatedAtDesc(
            long githubRepositoryId, String githubUserId);
    Optional<IndexJobEntity> findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
            long githubRepositoryId, String githubUserId, IndexJobStatus status);
}
