package com.repomind.indexing;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CodeChunkRepository extends JpaRepository<CodeChunkEntity, Long> {
    @Query("select c from CodeChunkEntity c where c.job.id = :jobId and "
            + "(c.embeddingStatus = :pending or c.embeddingModel is null or c.embeddingModel <> :model) "
            + "order by c.id")
    List<CodeChunkEntity> findNeedingEmbedding(@Param("jobId") String jobId,
                                               @Param("pending") EmbeddingStatus status,
                                               @Param("model") String model, Pageable pageable);
    List<CodeChunkEntity> findByJob_IdAndIdIn(String jobId, Collection<Long> ids);

    @Query(value = "select c.* from code_chunks c where c.job_id = :jobId "
            + "and c.github_user_id = :githubUserId and c.github_repository_id = :repositoryId "
            + "and c.commit_sha = :commitSha "
            + "and lower(concat(c.file_path, ' ', c.content)) regexp :pattern "
            + "order by c.id limit 100", nativeQuery = true)
    List<CodeChunkEntity> findLexicalCandidates(@Param("jobId") String jobId,
                                                 @Param("githubUserId") String githubUserId,
                                                 @Param("repositoryId") long repositoryId,
                                                 @Param("commitSha") String commitSha,
                                                 @Param("pattern") String pattern);
}
