package com.repomind.indexing;

import java.util.List;

public interface SemanticVectorIndex {
    void upsert(List<IndexedCodeChunk> chunks);

    List<SemanticMatch> search(String question, String githubUserId, long repositoryId,
                               String commitSha, int limit);

    record IndexedCodeChunk(String id, long databaseChunkId, String content, String githubUserId, long repositoryId,
                            String branch, String commitSha, String filePath, int lineStart,
                            int lineEnd, String blobSha, String contentSha256) { }

    record SemanticMatch(long databaseChunkId, String githubUserId,
                         long repositoryId, String commitSha, double score) { }
}
