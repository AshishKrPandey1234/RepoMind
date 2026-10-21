package com.repomind.indexing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
@Validated
public class RepositorySearchController {
    private final SemanticSearchService searchService;

    public RepositorySearchController(SemanticSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping("/{repositoryId}/search")
    public RepositorySearchResponse search(@AuthenticationPrincipal OAuth2User user,
                                           @PathVariable @Positive long repositoryId,
                                           @Valid @RequestBody RepositorySearchRequest request) {
        return searchService.search(String.valueOf((Object) user.getAttribute("id")), repositoryId, request);
    }
}
