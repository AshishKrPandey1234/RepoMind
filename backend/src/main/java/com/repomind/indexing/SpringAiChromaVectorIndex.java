package com.repomind.indexing;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SpringAiChromaVectorIndex implements SemanticVectorIndex {
    private final VectorStore vectorStore;

    public SpringAiChromaVectorIndex(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void upsert(List<IndexedCodeChunk> chunks) {
        List<Document> documents = chunks.stream().map(chunk -> new Document(chunk.id(),
                CodeEmbeddingText.build(chunk.filePath(), chunk.content()), Map.of(
                "githubUserId", chunk.githubUserId(),
                "chunkId", chunk.databaseChunkId(),
                "repositoryId", Long.toString(chunk.repositoryId()),
                "branch", chunk.branch(),
                "commitSha", chunk.commitSha(),
                "filePath", chunk.filePath(),
                "lineStart", chunk.lineStart(),
                "lineEnd", chunk.lineEnd(),
                "blobSha", chunk.blobSha(),
                "contentSha256", chunk.contentSha256()))).toList();
        if (!documents.isEmpty()) vectorStore.add(documents);
    }

    @Override
    public List<SemanticMatch> search(String question, String githubUserId, long repositoryId,
                                      String commitSha, int limit) {
        String filter = "githubUserId == '" + escapeFilter(githubUserId) + "' && repositoryId == '"
                + repositoryId + "' && commitSha == '" + escapeFilter(commitSha) + "'";
        return vectorStore.similaritySearch(SearchRequest.builder()
                        .query(question)
                        .topK(limit)
                        .filterExpression(filter)
                        .build()).stream()
                .filter(document -> githubUserId.equals(string(document, "githubUserId")))
                .filter(document -> Long.toString(repositoryId).equals(string(document, "repositoryId")))
                .filter(document -> commitSha.equals(string(document, "commitSha")))
                .filter(document -> longValue(document, "chunkId") > 0)
                .map(document -> new SemanticMatch(longValue(document, "chunkId"),
                        string(document, "githubUserId"), repositoryId, string(document, "commitSha"),
                        document.getScore() == null ? 0 : document.getScore()))
                .toList();
    }

    private static String string(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value instanceof String text ? text : "";
    }

    private static long longValue(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try { return Long.parseLong(text); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private static String escapeFilter(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
