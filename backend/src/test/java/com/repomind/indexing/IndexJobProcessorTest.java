package com.repomind.indexing;

import com.repomind.repository.GitHubContentClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndexJobProcessorTest {
    @Mock private GitHubContentClient github;
    @Mock private IndexJobStore store;

    @Test
    void persistsPreparedChunksOnlyAfterACompleteTreeIsProcessed() {
        var repository = new GitHubContentClient.RepositoryDetails(42, "octocat/Hello-World", "public", "main");
        when(github.getRecursiveTree("token", repository, "commit-sha")).thenReturn(new GitHubContentClient.TreeResponse(
                false, List.of(new GitHubContentClient.TreeEntry("src/App.java", "100644", "blob", "blob-sha", 16L))));
        when(github.getBlob("token", repository, "blob-sha"))
                .thenReturn("class App {}\n".getBytes(StandardCharsets.UTF_8));
        IndexingProperties properties = new IndexingProperties(50, 1024, 4096, 1000, 5,
                100, 10, 2, 600, new IndexingProperties.Executor(1, 2, 4));
        IndexJobProcessor processor = new IndexJobProcessor(github, new SourceFilePolicy(), new CodeChunker(),
                store, properties);

        processor.process("job-1", "github-user-1", repository, "main", "commit-sha", "token");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IndexJobStore.PreparedChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(store).complete(eq("job-1"), captor.capture(), eq(1), eq(1), eq(0), anyLong());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().getFirst().path()).isEqualTo("src/App.java");
        verify(store, never()).fail(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void failsTruncatedTreeInsteadOfPersistingPartialChunks() {
        var repository = new GitHubContentClient.RepositoryDetails(42, "octocat/Hello-World", "public", "main");
        when(github.getRecursiveTree("token", repository, "commit-sha"))
                .thenReturn(new GitHubContentClient.TreeResponse(true, List.of()));
        IndexingProperties properties = new IndexingProperties(50, 1024, 4096, 1000, 5,
                100, 10, 2, 600, new IndexingProperties.Executor(1, 2, 4));
        IndexJobProcessor processor = new IndexJobProcessor(github, new SourceFilePolicy(), new CodeChunker(),
                store, properties);

        processor.process("job-1", "github-user-1", repository, "main", "commit-sha", "token");

        verify(store).fail("job-1", "REPOSITORY_TREE_TOO_LARGE", 0, 0);
        verify(store, never()).complete(anyString(), anyList(), anyInt(), anyInt(), anyInt(), anyLong());
    }
}
