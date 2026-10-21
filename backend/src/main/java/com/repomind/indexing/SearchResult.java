package com.repomind.indexing;

public record SearchResult(String filePath, int lineStart, int lineEnd, String branch,
                           String commitSha, String snippet, double score) { }
