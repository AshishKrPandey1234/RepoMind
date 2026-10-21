package com.repomind.indexing;

import java.util.List;

public record RepositorySearchResponse(long repositoryId, String repositoryFullName, String branch,
                                      String commitSha, String question, List<SearchResult> results) { }
