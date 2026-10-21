package com.repomind.indexing;

import com.repomind.repository.GitHubContentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticSearchServiceTest {
    @Mock private IndexJobRepository jobs;
    @Mock private CodeChunkRepository chunks;
    @Mock private SemanticVectorIndex vectors;

    private SemanticSearchService service;
    private IndexJobEntity job;
    private CodeChunkEntity chunk;

    @BeforeEach
    void setUp() {
        service = new SemanticSearchService(jobs, chunks, vectors, new MockEnvironment());
        job = new IndexJobEntity("job-1", "12345", 42,
                "owner/repo", "main", "commit-sha");
        job.succeed(1, 1, 0, 1, 80);
        chunk = new CodeChunkEntity(job, 42, "12345", "main", "commit-sha",
                new CodeChunker.CodeChunk(0, "public void authenticate() {}", 4, 4, "hash"),
                "src/Auth.java", "blob-sha");
        ReflectionTestUtils.setField(chunk, "id", 7L);
    }

    @Test
    void lazilyEmbedsExistingChunksOnceUsingDeterministicIdAndReturnsSourceMetadata() {
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(job));
        when(chunks.findNeedingEmbedding(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(EmbeddingStatus.PENDING), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(chunk), List.of());
        when(vectors.search("where is auth?", "12345", 42, "commit-sha", 40)).thenReturn(List.of(
                new SemanticVectorIndex.SemanticMatch(7L,
                        "12345", 42, "commit-sha", 0.91)));
        when(chunks.findByJob_IdAndIdIn("job-1", List.of(7L))).thenReturn(List.of(chunk));
        when(chunks.findLexicalCandidates("job-1", "12345", 42, "commit-sha", "(auth)"))
                .thenReturn(List.of());

        RepositorySearchResponse response = service.search("12345", 42,
                new RepositorySearchRequest("where is auth?", 5));

        assertThat(response.repositoryFullName()).isEqualTo("owner/repo");
        assertThat(response.results()).singleElement().satisfies(result -> {
            assertThat(result.filePath()).isEqualTo("src/Auth.java");
            assertThat(result.lineStart()).isEqualTo(4);
            assertThat(result.commitSha()).isEqualTo("commit-sha");
            assertThat(result.snippet()).isEqualTo("public void authenticate() {}");
            assertThat(result.score()).isBetween(0.4, 1.0);
        });
        verify(vectors).upsert(org.mockito.ArgumentMatchers.argThat(items -> items.size() == 1
                && items.get(0).id().equals(SemanticSearchService.vectorId(chunk))
                && items.get(0).databaseChunkId() == 7L
                && items.get(0).blobSha().equals("blob-sha")));
        verify(chunks).saveAll(List.of(chunk));

        CodeChunkEntity duplicateFromRetry = new CodeChunkEntity(job, 42, "12345", "main", "commit-sha",
                new CodeChunker.CodeChunk(0, "public void authenticate() {}", 4, 4, "hash"),
                "src/Auth.java", "blob-sha");
        ReflectionTestUtils.setField(duplicateFromRetry, "id", 999L);
        assertThat(SemanticSearchService.vectorId(duplicateFromRetry))
                .isEqualTo(SemanticSearchService.vectorId(chunk));
    }

    @Test
    void returnsIndexRequiredWhenNoOwnedCompletedSnapshotExists() {
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.search("12345", 42,
                new RepositorySearchRequest("question", null)))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessage("REPOSITORY_NOT_INDEXED");
        verify(vectors, never()).search(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void returnsEmptyResultsWhenNoChunkMatches() {
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(job));
        when(chunks.findNeedingEmbedding(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(EmbeddingStatus.PENDING), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of());
        when(vectors.search("unmatched question", "12345", 42, "commit-sha", 40)).thenReturn(List.of());

        assertThat(service.search("12345", 42, new RepositorySearchRequest("unmatched question", null))
                .results()).isEmpty();
        verify(vectors, never()).upsert(anyList());
    }

    @Test
    void retainsPendingStatusAndReturnsSafeFailureWhenEmbeddingServiceFails() {
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(job));
        when(chunks.findNeedingEmbedding(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(EmbeddingStatus.PENDING), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(chunk));
        org.mockito.Mockito.doThrow(new IllegalStateException("external error with sensitive detail"))
                .when(vectors).upsert(anyList());

        assertThatThrownBy(() -> service.search("12345", 42,
                new RepositorySearchRequest("question", null)))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessage("EMBEDDING_SERVICE_UNAVAILABLE");
        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.PENDING);
        verify(chunks, never()).saveAll(anyList());
    }

    @Test
    void reembedsChunksWhenConfiguredModelChanges() {
        service = new SemanticSearchService(jobs, chunks, vectors,
                new MockEnvironment().withProperty("repomind.ai.embedding-model", "new-local-model"));
        chunk.markEmbedded("previous-model");
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(job));
        when(chunks.findNeedingEmbedding(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(EmbeddingStatus.PENDING),
                org.mockito.ArgumentMatchers.eq("new-local-model+path-symbol-v1"), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(List.of(chunk), List.of());
        when(vectors.search("question", "12345", 42, "commit-sha", 40)).thenReturn(List.of());

        service.search("12345", 42, new RepositorySearchRequest("question", null));

        assertThat(chunk.getEmbeddingStatus()).isEqualTo(EmbeddingStatus.INDEXED);
        assertThat(chunk.getEmbeddingModel()).isEqualTo("new-local-model+path-symbol-v1");
        verify(vectors).upsert(anyList());
    }

    @Test
    void reembedsPreviouslyIndexedChunksWhenPathAndSymbolContextIsIntroduced() {
        chunk.markEmbedded("nomic-embed-text");
        when(jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                42, "12345", IndexJobStatus.SUCCEEDED)).thenReturn(Optional.of(job));
        when(chunks.findNeedingEmbedding(org.mockito.ArgumentMatchers.eq("job-1"),
                org.mockito.ArgumentMatchers.eq(EmbeddingStatus.PENDING),
                org.mockito.ArgumentMatchers.eq("nomic-embed-text+path-symbol-v1"),
                org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(List.of(chunk), List.of());
        when(vectors.search("implementation question", "12345", 42, "commit-sha", 40)).thenReturn(List.of());
        when(chunks.findLexicalCandidates("job-1", "12345", 42, "commit-sha", "(implementation|question)"))
                .thenReturn(List.of());

        service.search("12345", 42, new RepositorySearchRequest("implementation question", null));

        assertThat(chunk.getEmbeddingModel()).isEqualTo("nomic-embed-text+path-symbol-v1");
        verify(vectors).upsert(org.mockito.ArgumentMatchers.argThat(items ->
                items.size() == 1 && items.get(0).content().equals(chunk.getContent())));
    }
}
