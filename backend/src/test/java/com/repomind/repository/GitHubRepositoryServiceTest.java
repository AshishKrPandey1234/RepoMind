package com.repomind.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.http.HttpMethod.GET;

class GitHubRepositoryServiceTest {
    private MockRestServiceServer server;
    private GitHubRepositoryService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GitHubRepositoryService(builder);
    }

    @Test
    void mapsGitHubRepositoryFieldsAndPagination() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://api.github.com/user/repos?")))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer test-access-token"))
                .andExpect(header("Accept", "application/vnd.github+json"))
                .andRespond(withSuccess("""
                        [{"id":42,"full_name":"octocat/Hello-World","description":"Example project",
                          "visibility":"public","language":"Java","html_url":"https://github.com/octocat/Hello-World",
                          "default_branch":"main"}]
                        """, MediaType.APPLICATION_JSON).header("Link", "<https://api.github.com/user/repos?page=2>; rel=\"next\""));

        GitHubRepositoryService.RepositoryPage page = service.listRepositories(authorizedClient(), 0, 30);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(30);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.items()).containsExactly(new GitHubRepositoryService.RepositorySummary(
                42, "octocat/Hello-World", "Example project", "public", "Java",
                "https://github.com/octocat/Hello-World", "main"));
        server.verify();
    }

    @Test
    void returnsEmptyPageWithoutInventingRepositories() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://api.github.com/user/repos?")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        GitHubRepositoryService.RepositoryPage page = service.listRepositories(authorizedClient(), 0, 30);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasNext()).isFalse();
        server.verify();
    }

    @Test
    void translatesGitHubRateLimitResponse() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://api.github.com/user/repos?")))
                .andRespond(withStatus(FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .header("Retry-After", "60"));

        try {
            service.listRepositories(authorizedClient(), 0, 30);
        } catch (GitHubRepositoryService.GitHubApiException exception) {
            assertThat(exception.status()).isEqualTo(429);
            assertThat(exception.code()).isEqualTo("GITHUB_RATE_LIMITED");
            assertThat(exception.retryAfterSeconds()).isEqualTo(60);
            server.verify();
            return;
        }
        throw new AssertionError("Expected GitHub rate-limit response to be translated");
    }

    @Test
    void translatesGitHubPermissionFailureWithoutExposingUpstreamBody() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString("https://api.github.com/user/repos?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .body("sensitive upstream diagnostic"));

        try {
            service.listRepositories(authorizedClient(), 0, 30);
        } catch (GitHubRepositoryService.GitHubApiException exception) {
            assertThat(exception.status()).isEqualTo(403);
            assertThat(exception.code()).isEqualTo("GITHUB_ACCESS_DENIED");
            assertThat(exception.getMessage()).doesNotContain("sensitive upstream diagnostic");
            server.verify();
            return;
        }
        throw new AssertionError("Expected GitHub permission failure to be translated");
    }

    private OAuth2AuthorizedClient authorizedClient() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("github")
                .clientId("test-client")
                .clientSecret("test-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/github")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .scope(List.of("read:user", "public_repo"))
                .build();
        OAuth2AccessToken token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER,
                "test-access-token", Instant.now(), Instant.now().plusSeconds(600));
        return new OAuth2AuthorizedClient(registration, "octocat", token);
    }
}
