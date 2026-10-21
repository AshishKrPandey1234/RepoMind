package com.repomind.auth;

import com.repomind.config.SecurityConfig;
import com.repomind.health.HealthController;
import com.repomind.indexing.IndexingProperties;
import com.repomind.repository.GitHubRepositoryService;
import com.repomind.repository.GitHubContentClient;
import com.repomind.repository.RepositoryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AuthController.class, HealthController.class, RepositoryController.class}, properties = {
        "spring.security.oauth2.client.registration.github.client-id=test-client",
        "spring.security.oauth2.client.registration.github.client-secret=test-secret",
        "repomind.frontend-url=http://localhost:5173"
})
@Import(SecurityConfig.class)
class AuthSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GitHubRepositoryService repositoryService;

    @MockitoBean
    private GitHubContentClient contentClient;

    @MockitoBean
    private IndexingProperties indexingProperties;

    @Test
    void currentUserReturnsUnauthenticatedStateWithoutSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void currentUserReturnsOnlySafeGitHubProfileFields() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(oauth2Login().attributes(attributes -> {
                    attributes.put("id", 12345);
                    attributes.put("login", "octocat");
                    attributes.put("name", "The Octocat");
                    attributes.put("avatar_url", "https://avatars.githubusercontent.com/u/12345");
                    attributes.put("access_token", "must-not-be-returned");
                })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.id").value("12345"))
                .andExpect(jsonPath("$.user.githubLogin").value("octocat"))
                .andExpect(jsonPath("$.user.displayName").value("The Octocat"))
                .andExpect(jsonPath("$.user.access_token").doesNotExist());
    }

    @Test
    void repositoryListRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/repositories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(user("octocat")))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutWithCsrfInvalidatesSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(user("octocat")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void healthEndpointRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }
}
