package com.repomind.indexing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class IndexingController {
    private final IndexingService indexingService;

    public IndexingController(IndexingService indexingService) {
        this.indexingService = indexingService;
    }

    @PostMapping("/index-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public IndexJobResponse start(
            @AuthenticationPrincipal OAuth2User user,
            @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient authorizedClient,
            @Valid @RequestBody StartIndexJobRequest request) {
        return indexingService.start(String.valueOf((Object) user.getAttribute("id")), authorizedClient, request);
    }

    @GetMapping("/index-jobs/{jobId}")
    public IndexJobResponse getStatus(@AuthenticationPrincipal OAuth2User user, @PathVariable String jobId) {
        return indexingService.getOwned(jobId, String.valueOf((Object) user.getAttribute("id")));
    }

    @GetMapping("/repositories/{repositoryId}/index-status")
    public RepositoryIndexStatusResponse getRepositoryStatus(@AuthenticationPrincipal OAuth2User user,
                                                              @PathVariable @Positive long repositoryId) {
        return indexingService.getRepositoryStatus(repositoryId,
                String.valueOf((Object) user.getAttribute("id")));
    }
}
