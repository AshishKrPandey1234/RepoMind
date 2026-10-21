package com.repomind.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GitHubContentClientTest {
    private MockRestServiceServer server;
    private GitHubContentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubContentClient(builder);
    }

    @Test
    void resolvesPublicRepositoryUsingNumericIdentity() {
        server.expect(requestTo("https://api.github.com/repositories/42"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer test-access-token"))
                .andRespond(withSuccess("""
                        {"id":42,"full_name":"octocat/Hello-World","default_branch":"main",
                         "visibility":"public","private":false}
                        """, MediaType.APPLICATION_JSON));

        var repository = client.getPublicRepository("test-access-token", 42);
        assertThat(repository.fullName()).isEqualTo("octocat/Hello-World");
        assertThat(repository.visibility()).isEqualTo("public");
        server.verify();
    }

    @Test
    void encodesBranchAsPathComponentAndResolvesCommit() {
        server.expect(requestTo("https://api.github.com/repos/octocat/Hello-World/branches/feature%2Fdemo"))
                .andRespond(withSuccess("""
                        {"name":"feature/demo","commit":{"sha":"0123456789abcdef"}}
                        """, MediaType.APPLICATION_JSON));

        String sha = client.resolveBranch("test-access-token",
                new GitHubContentClient.RepositoryDetails(42, "octocat/Hello-World", "public", "main"),
                "feature/demo");
        assertThat(sha).isEqualTo("0123456789abcdef");
        server.verify();
    }

    @Test
    void mapsGitHubNotFoundWithoutReturningUpstreamBody() {
        server.expect(requestTo("https://api.github.com/repositories/42"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND)
                        .body("private upstream diagnostic"));

        assertThatThrownBy(() -> client.getPublicRepository("test-access-token", 42))
                .isInstanceOf(GitHubRepositoryService.GitHubApiException.class)
                .hasMessage("GITHUB_RESOURCE_NOT_FOUND")
                .hasMessageNotContaining("private upstream diagnostic");
        server.verify();
    }
}
