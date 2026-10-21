package com.repomind.indexing;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class SpringAiChromaVectorIndexTest {
    @Test
    void embedsChunkWithFileAndSymbolContextAndDeterministicRepositoryMetadata() {
        VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        SpringAiChromaVectorIndex index = new SpringAiChromaVectorIndex(vectorStore);
        SemanticVectorIndex.IndexedCodeChunk chunk = new SemanticVectorIndex.IndexedCodeChunk(
                "repomind-chunk-12", 12L, "class Auth {}", "123", 42, "main", "sha",
                "src/Auth.java", 1, 1, "blob", "content-hash");

        index.upsert(List.of(chunk));

        org.mockito.ArgumentCaptor<List<Document>> documents = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documents.capture());
        assertThat(documents.getValue()).singleElement().satisfies(document -> {
            assertThat(document.getId()).isEqualTo("repomind-chunk-12");
            assertThat(document.getText()).contains("Repository source file: Auth.java", "Language: Java",
                    "class Auth", "Source code:", "class Auth {}");
            assertThat(document.getMetadata()).containsEntry("githubUserId", "123")
                    .containsEntry("chunkId", 12L)
                    .containsEntry("repositoryId", "42")
                    .containsEntry("filePath", "src/Auth.java")
                    .containsEntry("lineStart", 1)
                    .containsEntry("commitSha", "sha");
        });
    }

    @Test
    void queryUsesOwnerAndRepositoryFiltersAndRejectsMismatchedMetadata() {
        VectorStore vectorStore = org.mockito.Mockito.mock(VectorStore.class);
        SpringAiChromaVectorIndex index = new SpringAiChromaVectorIndex(vectorStore);
        Document owned = document("chunk-1", "owned snippet", "123", "42", 7L);
        Document otherOwner = document("chunk-2", "must not leak", "999", "42", 8L);
        org.mockito.Mockito.when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(owned, otherOwner));

        List<SemanticVectorIndex.SemanticMatch> matches = index.search("question", "123", 42, "commit", 3);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.databaseChunkId()).isEqualTo(7L);
            assertThat(match.githubUserId()).isEqualTo("123");
            assertThat(match.repositoryId()).isEqualTo(42);
        });
        org.mockito.ArgumentCaptor<SearchRequest> request = org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(request.capture());
        assertThat(request.getValue().getQuery()).isEqualTo("question");
        assertThat(request.getValue().getTopK()).isEqualTo(3);
        assertThat(String.valueOf(request.getValue().getFilterExpression()))
                .contains("githubUserId", "123", "repositoryId", "42", "commitSha", "commit");
    }

    private static Document document(String id, String content, String owner, String repository, long chunkId) {
        return new Document(id, content, Map.of("githubUserId", owner, "chunkId", chunkId, "repositoryId", repository,
                "branch", "main", "commitSha", "commit", "filePath", "src/Auth.java",
                "lineStart", 3, "lineEnd", 6, "blobSha", "blob"));
    }
}
