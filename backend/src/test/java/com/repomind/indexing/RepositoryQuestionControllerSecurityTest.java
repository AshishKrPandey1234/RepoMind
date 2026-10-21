package com.repomind.indexing;

import com.repomind.config.SecurityConfig;
import com.repomind.common.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RepositoryQuestionController.class, properties = {
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "repomind.frontend-url=http://localhost:5173"
})
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class RepositoryQuestionControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private RepositoryQuestionService questionService;

    @Test
    void questionEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/repositories/42/ask").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"where is auth?\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passesAuthenticatedGitHubOwnerAndReturnsSourceReferences() throws Exception {
        when(questionService.ask(eq("12345"), eq(42L), any())).thenReturn(new RepositoryQuestionResponse(
                42, "owner/repo", "main", "commit-sha", "where is auth?", "Configured in the filter chain.",
                false, List.of(new RepositoryAnswerSource("S1", "src/SecurityConfig.java", 4, 8,
                "main", "commit-sha", "http.oauth2Login()", .9))));

        mockMvc.perform(post("/api/repositories/42/ask")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"where is auth?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Configured in the filter chain."))
                .andExpect(jsonPath("$.sources[0].filePath").value("src/SecurityConfig.java"))
                .andExpect(jsonPath("$.sources[0].lineStart").value(4));
        verify(questionService).ask(eq("12345"), eq(42L), any());
    }

    @Test
    void rejectsBlankAndOversizedQuestions() throws Exception {
        for (String body : List.of("{\"question\":\" \"}", "{\"question\":\"" + "x".repeat(1001) + "\"}")) {
            mockMvc.perform(post("/api/repositories/42/ask")
                            .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void returnsSafeModelUnavailableResponse() throws Exception {
        when(questionService.ask(eq("12345"), eq(42L), any()))
                .thenThrow(new RepositoryQuestionException(503, "LOCAL_CHAT_MODEL_NOT_CONFIGURED"));

        mockMvc.perform(post("/api/repositories/42/ask")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"where is auth?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LOCAL_CHAT_MODEL_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("REPOMIND_CHAT_MODEL")));
    }

    @Test
    void returnsClearIndexRequiredResponseWhenNoCompletedSnapshotExists() throws Exception {
        when(questionService.ask(eq("12345"), eq(42L), any()))
                .thenThrow(new SemanticSearchException(404, "REPOSITORY_NOT_INDEXED"));

        mockMvc.perform(post("/api/repositories/42/ask")
                        .with(oauth2Login().attributes(attributes -> attributes.put("id", 12345)))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"where is auth?\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPOSITORY_NOT_INDEXED"))
                .andExpect(jsonPath("$.message").value("This repository has no completed index yet. Index it before searching."));
    }
}
