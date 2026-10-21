package com.repomind.repository;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/repositories")
@Validated
public class RepositoryController {
    private final GitHubRepositoryService repositoryService;

    public RepositoryController(GitHubRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @GetMapping
    public GitHubRepositoryService.RepositoryPage listRepositories(
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int size) {
        return repositoryService.listRepositories(authorizedClient, page, size);
    }
}
