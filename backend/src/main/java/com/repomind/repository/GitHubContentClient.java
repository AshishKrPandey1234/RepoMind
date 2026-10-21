package com.repomind.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Base64;
import java.util.List;

import static com.repomind.repository.GitHubRepositoryService.GitHubApiException;

@Service
public class GitHubContentClient {
    private static final String API_BASE = "https://api.github.com";
    private static final String API_VERSION = "2022-11-28";

    private final RestClient restClient;

    public GitHubContentClient(RestClient.Builder builder) {
        this.restClient = builder.baseUrl(API_BASE).build();
    }

    public RepositoryDetails getPublicRepository(String accessToken, long repositoryId) {
        if (repositoryId <= 0) throw new IllegalArgumentException("repositoryId must be positive");
        try {
            RepositoryResponse response = restClient.get()
                    .uri(uri("repositories", Long.toString(repositoryId)))
                    .headers(headers -> applyGitHubHeaders(headers, accessToken))
                    .retrieve().body(RepositoryResponse.class);
            if (response == null || response.privateRepository() || response.fullName() == null) {
                throw new GitHubApiException(404, "GITHUB_RESOURCE_NOT_FOUND", null);
            }
            return new RepositoryDetails(response.id(), response.fullName(), response.visibility(), response.defaultBranch());
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    public BranchPage listBranches(String accessToken, RepositoryDetails repository, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid branch page");
        String[] name = ownerAndName(repository.fullName());
        try {
            ResponseEntity<List<BranchResponse>> response = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(API_BASE)
                            .pathSegment("repos", name[0], name[1], "branches")
                            .queryParam("per_page", size)
                            .queryParam("page", page + 1)
                            .build().encode().toUri())
                    .headers(headers -> applyGitHubHeaders(headers, accessToken))
                    .retrieve().toEntity(new ParameterizedTypeReference<>() { });
            List<BranchSummary> items = response.getBody() == null ? List.of()
                    : response.getBody().stream().map(branch -> new BranchSummary(
                            branch.name(), branch.commit() == null ? null : branch.commit().sha(),
                            branch.name().equals(repository.defaultBranch()))).toList();
            String link = response.getHeaders().getFirst(HttpHeaders.LINK);
            return new BranchPage(items, page, size, link != null && link.contains("rel=\"next\""));
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    public String resolveBranch(String accessToken, RepositoryDetails repository, String branchName) {
        String[] name = ownerAndName(repository.fullName());
        try {
            BranchResponse branch = restClient.get()
                    .uri(pathUri("repos", name[0], name[1], "branches", branchName))
                    .headers(headers -> applyGitHubHeaders(headers, accessToken))
                    .retrieve().body(BranchResponse.class);
            if (branch == null || branch.commit() == null || branch.commit().sha() == null) {
                throw new GitHubApiException(404, "GITHUB_RESOURCE_NOT_FOUND", null);
            }
            return branch.commit().sha();
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    public TreeResponse getRecursiveTree(String accessToken, RepositoryDetails repository, String commitSha) {
        String[] name = ownerAndName(repository.fullName());
        try {
            TreeResponse tree = restClient.get()
                    .uri(UriComponentsBuilder.fromUriString(API_BASE)
                            .pathSegment("repos", name[0], name[1], "git", "trees", commitSha)
                            .queryParam("recursive", "1").build().encode().toUri())
                    .headers(headers -> applyGitHubHeaders(headers, accessToken))
                    .retrieve().body(TreeResponse.class);
            if (tree == null) throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
            return tree;
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    public byte[] getBlob(String accessToken, RepositoryDetails repository, String blobSha) {
        String[] name = ownerAndName(repository.fullName());
        try {
            BlobResponse blob = restClient.get()
                    .uri(pathUri("repos", name[0], name[1], "git", "blobs", blobSha))
                    .headers(headers -> applyGitHubHeaders(headers, accessToken))
                    .retrieve().body(BlobResponse.class);
            if (blob == null || !"base64".equalsIgnoreCase(blob.encoding()) || blob.content() == null) {
                throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
            }
            return Base64.getMimeDecoder().decode(blob.content());
        } catch (RestClientResponseException exception) {
            throw GitHubApiException.from(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
    }

    private void applyGitHubHeaders(HttpHeaders headers, String token) {
        headers.setBearerAuth(token);
        headers.setAccept(List.of(org.springframework.http.MediaType.valueOf("application/vnd.github+json")));
        headers.set("X-GitHub-Api-Version", API_VERSION);
        headers.set(HttpHeaders.USER_AGENT, "RepoMind");
    }

    private java.net.URI uri(String... segments) {
        return UriComponentsBuilder.fromUriString(API_BASE).pathSegment(segments).build().encode().toUri();
    }

    private java.net.URI pathUri(String... segments) {
        return uri(segments);
    }

    private String[] ownerAndName(String fullName) {
        int slash = fullName.indexOf('/');
        if (slash <= 0 || slash == fullName.length() - 1) {
            throw new GitHubApiException(503, "GITHUB_UNAVAILABLE", null);
        }
        return new String[]{fullName.substring(0, slash), fullName.substring(slash + 1)};
    }

    public record RepositoryDetails(long id, String fullName, String visibility, String defaultBranch) { }
    public record BranchSummary(String name, String commitSha, boolean defaultBranch) { }
    public record BranchPage(List<BranchSummary> items, int page, int size, boolean hasNext) { }
    public record TreeResponse(boolean truncated, List<TreeEntry> tree) { }
    public record TreeEntry(String path, String mode, String type, String sha, Long size) { }

    private record RepositoryResponse(long id, @JsonProperty("full_name") String fullName,
                                      @JsonProperty("default_branch") String defaultBranch,
                                      String visibility, @JsonProperty("private") boolean privateRepository) { }
    private record BranchResponse(String name, Commit commit) { }
    private record Commit(String sha) { }
    private record BlobResponse(String content, String encoding) { }
}
