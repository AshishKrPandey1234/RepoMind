package com.repomind.repository;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.repomind.indexing.IndexingProperties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/repositories")
@Validated
public class RepositoryController {
    private final GitHubRepositoryService repositoryService;
    private final GitHubContentClient contentClient;
    private final IndexingProperties indexingProperties;

    public RepositoryController(GitHubRepositoryService repositoryService, GitHubContentClient contentClient,
                                IndexingProperties indexingProperties) {
        this.repositoryService = repositoryService;
        this.contentClient = contentClient;
        this.indexingProperties = indexingProperties;
    }

    @GetMapping
    public GitHubRepositoryService.RepositoryPage listRepositories(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return repositoryService.listRepositories(authorizedClient, page, size);
    }

    @GetMapping("/{repositoryId}/branches")
    public GitHubContentClient.BranchPage listBranches(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
            @PathVariable @Positive long repositoryId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        String token = authorizedClient.getAccessToken().getTokenValue();
        repositoryService.verifyRepositoryListedForUser(authorizedClient, repositoryId,
                indexingProperties.maxAccessCheckPages());
        GitHubContentClient.RepositoryDetails repository = contentClient.getPublicRepository(token, repositoryId);
        return contentClient.listBranches(token, repository, page, size);
    }
}
