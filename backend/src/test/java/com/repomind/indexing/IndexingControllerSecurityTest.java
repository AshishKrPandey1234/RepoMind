package com.repomind.indexing;

import com.repomind.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {IndexingController.class, RepositorySearchController.class}, properties = {
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "repomind.frontend-url=http://localhost:5173"
})
@Import(SecurityConfig.class)
class IndexingControllerSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndexingService indexingService;

    @MockitoBean
    private SemanticSearchService semanticSearchService;

    @Test
    void statusEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/index-jobs/job-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void repositoryIndexStatusRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/repositories/42/index-status")).andExpect(status().isUnauthorized());
    }

    @Test
    void repositoryIndexStatusUsesAuthenticatedOwnerAndReportsPreviousSnapshot() throws Exception {
        IndexJobResponse successful = new IndexJobResponse("job-1", IndexJobStatus.SUCCEEDED,
                "owner/repo", "main", "commit-123", 10, 8, 2, 20, 500,
                null, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH);
        when(indexingService.getRepositoryStatus(42, "12345"))
                .thenReturn(new RepositoryIndexStatusResponse(successful, successful));

        mockMvc.perform(get("/api/repositories/42/index-status")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestJob.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.latestJob.filesSkipped").value(2))
                .andExpect(jsonPath("$.latestSuccessfulSnapshot.branch").value("main"))
                .andExpect(jsonPath("$.latestSuccessfulSnapshot.commitSha").value("commit-123"));

        verify(indexingService).getRepositoryStatus(42, "12345");
    }

    @Test
    void passesAuthenticatedGitHubIdentityIntoOwnerScopedLookup() throws Exception {
        when(indexingService.getOwned("job-1", "12345")).thenReturn(new IndexJobResponse(
                "job-1", IndexJobStatus.RUNNING,
                3, 1, 1, 2, 100, null, Instant.EPOCH, Instant.EPOCH, null));

        mockMvc.perform(get("/api/index-jobs/job-1").with(oauth2Login().attributes(attributes ->
                        attributes.put("id", 12345))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.chunksWritten").value(2))
                .andExpect(jsonPath("$.content").doesNotExist());

        verify(indexingService).getOwned("job-1", "12345");
    }

    @Test
    void authenticatedCsrfProtectedPostQueuesTheIndexJob() throws Exception {
        IndexJobResponse queued = new IndexJobResponse("job-2", IndexJobStatus.QUEUED,
                0, 0, 0, 0, 0, null, Instant.EPOCH, null, null);
        when(indexingService.start(eq("12345"), any(), any())).thenReturn(queued);

        mockMvc.perform(post("/api/index-jobs")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(oauth2Client("github"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubRepositoryId\":42,\"branch\":\"main\"}"))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        verify(indexingService).start(eq("12345"), any(), any());
    }

    @Test
    void semanticSearchRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/repositories/42/search")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"where is auth?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semanticSearchPassesAuthenticatedOwnerAndReturnsCitedSnippet() throws Exception {
        when(semanticSearchService.search(eq("12345"), eq(42L), any())).thenReturn(
                new RepositorySearchResponse(42, "owner/repo", "main", "commit-sha",
                        "where is auth?", java.util.List.of(new SearchResult(
                        "src/Auth.java", 4, 9, "main", "commit-sha", "class Auth {}", 0.91))));

        mockMvc.perform(post("/api/repositories/42/search")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"where is auth?\",\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].filePath").value("src/Auth.java"))
                .andExpect(jsonPath("$.results[0].lineStart").value(4))
                .andExpect(jsonPath("$.results[0].commitSha").value("commit-sha"));

        verify(semanticSearchService).search(eq("12345"), eq(42L), any());
    }

    @Test
    void semanticSearchMapsProviderFailureToSafeDependencyMessage() throws Exception {
        when(semanticSearchService.search(eq("12345"), eq(42L), any()))
                .thenThrow(new SemanticSearchException(503, "EMBEDDING_SERVICE_UNAVAILABLE"));

        mockMvc.perform(post("/api/repositories/42/search")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"find authentication\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("EMBEDDING_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Start Ollama and Chroma")))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    void semanticSearchValidatesResultLimit() throws Exception {
        mockMvc.perform(post("/api/repositories/42/search")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"find authentication\",\"limit\":21}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("One or more request values are invalid."));
    }
}
