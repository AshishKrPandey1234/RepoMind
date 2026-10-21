package com.repomind.indexing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkSearchRankerTest {
    private final CodeChunkSearchRanker ranker = new CodeChunkSearchRanker();
    private final IndexJobEntity job = new IndexJobEntity("job", "123", 42, "owner/repo", "main", "commit");

    @Test
    void springSecurityAndOAuthQuestionRanksTheActualConfigurationClassAheadOfDocumentation() {
        CodeChunkEntity security = chunk(1, "backend/src/main/java/com/repomind/config/SecurityConfig.java",
                "@Bean SecurityFilterChain securityFilterChain(HttpSecurity http) {\n"
                        + "  http.oauth2Login(login -> login.successHandler((request, response, auth) -> {}));\n"
                        + "}");
        CodeChunkEntity docs = chunk(2, "docs/PROJECT_SPEC.md",
                "RepoMind provides a dashboard and repository indexing workflow. This document describes planned features.");

        List<CodeChunkSearchRanker.RankedChunk> ranked = ranker.rank(
                "Which Java class configures Spring Security and GitHub OAuth login?",
                List.of(docs, security), Map.of(2L, 0.83, 1L, 0.62), 2);

        assertThat(ranked).extracting(item -> item.chunk().getFilePath())
                .containsExactly(security.getFilePath(), docs.getFilePath());
    }

    @Test
    void githubRestApiQuestionRanksTheClientImplementationAheadOfGeneralDocumentation() {
        CodeChunkEntity client = chunk(3, "backend/src/main/java/com/repomind/repository/GitHubContentClient.java",
                "private static final String API_BASE = \"https://api.github.com\";\n"
                        + "restClient.get().uri(pathUri(\"repos\", owner, repository, \"branches\"));");
        CodeChunkEntity docs = chunk(4, "docs/ARCHITECTURE.md",
                "The architecture describes components and their responsibilities in the project.");

        List<CodeChunkSearchRanker.RankedChunk> ranked = ranker.rank(
                "Which class calls the GitHub REST API to list branches?",
                List.of(docs, client), Map.of(4L, 0.88, 3L, 0.67), 2);

        assertThat(ranked).extracting(item -> item.chunk().getFilePath())
                .containsExactly(client.getFilePath(), docs.getFilePath());
    }

    @Test
    void OAuthCallbackQuestionRanksTheSpringSecurityHandlerWhenItsSourceChunkExists() {
        CodeChunkEntity security = chunk(5, "backend/src/main/java/com/repomind/config/SecurityConfig.java",
                ".oauth2Login(oauth2 -> oauth2.successHandler((request, response, authentication) ->\n"
                        + "  response.sendRedirect(frontendUrl + \"/\")))");
        CodeChunkEntity docs = chunk(6, "README.md", "The project has local development instructions.");

        List<CodeChunkSearchRanker.RankedChunk> ranked = ranker.rank(
                "Which handler processes the GitHub OAuth callback?", List.of(docs, security),
                Map.of(6L, 0.9, 5L, 0.58), 2);

        assertThat(ranked.getFirst().chunk().getFilePath()).isEqualTo(security.getFilePath());
    }

    private CodeChunkEntity chunk(long id, String path, String content) {
        CodeChunkEntity chunk = new CodeChunkEntity(job, 42, "123", "main", "commit",
                new CodeChunker.CodeChunk(0, content, 1, 4, "hash" + id), path, "blob" + id);
        org.springframework.test.util.ReflectionTestUtils.setField(chunk, "id", id);
        return chunk;
    }
}
