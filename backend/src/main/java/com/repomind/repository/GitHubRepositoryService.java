package com.repomind.repository;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

@Service
public class GitHubRepositoryService {
    private static final String GITHUB_API_VERSION = "2022-11-28";

    private final RestClient restClient;

    public GitHubRepositoryService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("https://api.github.com").build();
    }

    public RepositoryPage listRepositories(OAuth2AuthorizedClient authorizedClient, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        }

        String token = authorizedClient.getAccessToken().getTokenValue();
        var uri = UriComponentsBuilder.fromUriString("https://api.github.com/user/repos")
                .queryParam("visibility", "public")
                .queryParam("affiliation", "owner,collaborator,organization_member")
                .queryParam("sort", "updated")
                .queryParam("direction", "desc")
                .queryParam("per_page", size)
                .queryParam("page", page + 1)
                .build()
                .encode()
                .toUri();

        try {
            ResponseEntity<List<GitHubRepository>> response = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                    .header(HttpHeaders.USER_AGENT, "RepoMind")
                    .retrieve()
                    .toEntity(new ParameterizedTypeReference<>() { });

            List<RepositorySummary> items = response.getBody() == null ? List.of()
                    : response.getBody().stream().map(GitHubRepository::toSummary).toList();
            return new RepositoryPage(items, page, size, hasNextPage(response.getHeaders()));
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    public void verifyRepositoryListedForUser(OAuth2AuthorizedClient authorizedClient, long repositoryId,
                                               int maxPages) {
        for (int page = 0; page < maxPages; page++) {
            RepositoryPage repositories = listRepositories(authorizedClient, page, 100);
            if (repositories.items().stream().anyMatch(repository -> repository.githubRepositoryId() == repositoryId)) {
                return;
            }
            if (!repositories.hasNext()) {
                throw new GitHubApiException(404, "GITHUB_RESOURCE_NOT_FOUND", null);
            }
        }
        throw new GitHubApiException(422, "GITHUB_ACCESS_CHECK_LIMIT", null);
    }

    private boolean hasNextPage(HttpHeaders headers) {
        String link = headers.getFirst(HttpHeaders.LINK);
        return link != null && (link.contains("rel=\"next\"") || link.contains("rel=next"));
    }

    private record GitHubRepository(
            long id,
            @JsonProperty("full_name") String fullName,
            String description,
            String visibility,
            @JsonProperty("language") String primaryLanguage,
            @JsonProperty("html_url") String url,
            @JsonProperty("default_branch") String defaultBranch) {

        RepositorySummary toSummary() {
            return new RepositorySummary(id, fullName, description, visibility, primaryLanguage, url, defaultBranch);
        }
    }

    public record RepositorySummary(
            long githubRepositoryId,
            String fullName,
            String description,
            String visibility,
            String primaryLanguage,
            String url,
            String defaultBranch) {
    }

    public record RepositoryPage(List<RepositorySummary> items, int page, int size, boolean hasNext) {
    }

    public static class GitHubApiException extends RuntimeException {
        private final int status;
        private final String code;
        private final Long retryAfterSeconds;

        public GitHubApiException(int status, String code, Long retryAfterSeconds) {
            super(code);
            this.status = status;
            this.code = code;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int status() {
            return status;
        }

        public String code() {
            return code;
        }

        public Long retryAfterSeconds() {
            return retryAfterSeconds;
        }

        public static GitHubApiException from(RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            HttpHeaders headers = exception.getResponseHeaders();
            if (status == 401) {
                return new GitHubApiException(401, "GITHUB_AUTHORIZATION_EXPIRED", null);
            }
            if (status == 404) {
                return new GitHubApiException(404, "GITHUB_RESOURCE_NOT_FOUND", null);
            }
            if (status == 403 || status == 429) {
                String remaining = headers == null ? null : headers.getFirst("X-RateLimit-Remaining");
                String reset = headers == null ? null : headers.getFirst("X-RateLimit-Reset");
                String retryAfterHeader = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
                if (status == 429 || "0".equals(remaining) || retryAfterHeader != null) {
                    Long retryAfterSeconds = retryAfter(headers, reset);
                    return new GitHubApiException(429, "GITHUB_RATE_LIMITED", retryAfterSeconds);
                }
                return new GitHubApiException(403, "GITHUB_ACCESS_DENIED", null);
            }
            return new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }

        private static Long retryAfter(HttpHeaders headers, String reset) {
            String retryHeader = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
            if (retryHeader != null) {
                try {
                    return Long.parseLong(retryHeader);
                } catch (NumberFormatException ignored) {
                    // GitHub's reset timestamp below is used when Retry-After is not numeric.
                }
            }
            if (reset != null) {
                try {
                    return Math.max(0, Long.parseLong(reset) - Instant.now().getEpochSecond());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
    }
}
