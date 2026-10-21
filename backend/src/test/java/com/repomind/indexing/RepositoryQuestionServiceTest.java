package com.repomind.indexing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryQuestionServiceTest {
    @Mock private SemanticSearchService search;
    @Mock private AnswerGenerator generator;

    private RepositoryQuestionService service() { return new RepositoryQuestionService(search, generator); }

    @Test
    void answersFromRetrievedContextAndMapsModelSourceIdsToVerifiedMetadata() {
        when(search.search("123", 42, new RepositorySearchRequest("How is login configured?", 6)))
                .thenReturn(retrieval(List.of(new SearchResult("backend/SecurityConfig.java", 10, 22,
                        "main", "commit-1", "http security oauth login", .91))));
        when(generator.generate(org.mockito.ArgumentMatchers.eq("How is login configured?"),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new AnswerGenerator.GeneratedAnswer("OAuth login is configured in the security filter chain.", List.of("S1"), false));

        RepositoryQuestionResponse response = service().ask("123", 42,
                new RepositoryQuestionRequest("How is login configured?", null));

        assertThat(response.insufficientEvidence()).isFalse();
        assertThat(response.answer()).contains("security filter chain");
        assertThat(response.sources()).singleElement().satisfies(source -> {
            assertThat(source.filePath()).isEqualTo("backend/SecurityConfig.java");
            assertThat(source.lineStart()).isEqualTo(10);
            assertThat(source.lineEnd()).isEqualTo(22);
            assertThat(source.commitSha()).isEqualTo("commit-1");
        });
    }

    @Test
    void abstainsWithoutCallingModelWhenRetrievalHasNoEvidence() {
        when(search.search("123", 42, new RepositorySearchRequest("unrelated?", 6)))
                .thenReturn(retrieval(List.of()));

        RepositoryQuestionResponse response = service().ask("123", 42,
                new RepositoryQuestionRequest("unrelated?", null));

        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).contains("not provide enough evidence");
        verify(generator, never()).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void rejectsModelCitationsThatAreNotRetrievedSourceIds() {
        when(search.search("123", 42, new RepositorySearchRequest("question", 6)))
                .thenReturn(retrieval(List.of(new SearchResult("src/A.java", 1, 2, "main", "sha", "code", .8))));
        when(generator.generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new AnswerGenerator.GeneratedAnswer("Unsupported answer", List.of("S99"), false));

        RepositoryQuestionResponse response = service().ask("123", 42, new RepositoryQuestionRequest("question", null));

        assertThat(response.insufficientEvidence()).isTrue();
        assertThat(response.sources()).isEmpty();
    }

    @Test
    void requiresIndexWhenRepositoryHasNoCompletedSnapshot() {
        when(search.search("123", 42, new RepositorySearchRequest("question", 6)))
                .thenThrow(new SemanticSearchException(404, "REPOSITORY_NOT_INDEXED"));

        assertThatThrownBy(() -> service().ask("123", 42, new RepositoryQuestionRequest("question", null)))
                .isInstanceOf(SemanticSearchException.class)
                .hasMessage("REPOSITORY_NOT_INDEXED");
        verify(generator, never()).generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void convertsModelFailuresToSafeServiceError() {
        when(search.search("123", 42, new RepositorySearchRequest("question", 6)))
                .thenReturn(retrieval(List.of(new SearchResult("src/A.java", 1, 2, "main", "sha", "code", .8))));
        when(generator.generate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new RepositoryQuestionException(503, "LOCAL_CHAT_MODEL_UNAVAILABLE"));

        assertThatThrownBy(() -> service().ask("123", 42, new RepositoryQuestionRequest("question", null)))
                .isInstanceOf(RepositoryQuestionException.class)
                .hasMessage("LOCAL_CHAT_MODEL_UNAVAILABLE");
    }

    private RepositorySearchResponse retrieval(List<SearchResult> results) {
        return new RepositorySearchResponse(42, "owner/repo", "main", "commit-1", "question", results);
    }
}
