package com.repomind.indexing;

public record RepositoryAnswerSource(String sourceId, String filePath, int lineStart, int lineEnd,
                                     String branch, String commitSha, String snippet, double score) { }
