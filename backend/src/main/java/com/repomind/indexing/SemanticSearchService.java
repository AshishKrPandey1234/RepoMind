package com.repomind.indexing;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import org.springframework.data.domain.PageRequest;

@Service
public class SemanticSearchService {
    private static final int MAX_EMBEDDING_BATCH_SIZE = 128;
    private static final int MAX_SEARCH_CANDIDATES = 100;
    private static final String EMBEDDING_CONTEXT_VERSION = "path-symbol-v1";

    private final IndexJobRepository jobs;
    private final CodeChunkRepository chunks;
    private final SemanticVectorIndex vectors;
    private final String modelName;
    private final int embeddingBatchSize;
    private final CodeChunkSearchRanker ranker = new CodeChunkSearchRanker();

    public SemanticSearchService(IndexJobRepository jobs, CodeChunkRepository chunks,
                                 SemanticVectorIndex vectors,
                                 org.springframework.core.env.Environment environment) {
        this.jobs = jobs;
        this.chunks = chunks;
        this.vectors = vectors;
        this.modelName = environment.getProperty("repomind.ai.embedding-model", "nomic-embed-text");
        this.embeddingBatchSize = environment.getProperty("repomind.ai.embedding-batch-size", Integer.class, 32);
        if (embeddingBatchSize < 1 || embeddingBatchSize > MAX_EMBEDDING_BATCH_SIZE) {
            throw new IllegalArgumentException("Embedding batch size must be between 1 and 128");
        }
    }

    public RepositorySearchResponse search(String githubUserId, long repositoryId,
                                           RepositorySearchRequest request) {
        if (githubUserId == null || !githubUserId.matches("\\d+")) {
            throw new SemanticSearchException(401, "AUTHENTICATION_REQUIRED");
        }
        IndexJobEntity job = jobs.findFirstByGithubRepositoryIdAndGithubUserIdAndStatusOrderByFinishedAtDesc(
                        repositoryId, githubUserId, IndexJobStatus.SUCCEEDED)
                .orElseThrow(() -> new SemanticSearchException(404, "REPOSITORY_NOT_INDEXED"));
        embedPending(job);

        int candidateLimit = Math.min(MAX_SEARCH_CANDIDATES, Math.max(40, request.resolvedLimit() * 5));
        List<SemanticVectorIndex.SemanticMatch> matches;
        try {
            matches = vectors.search(request.question().trim(), githubUserId, repositoryId,
                            job.getCommitSha(),
                            candidateLimit);
        } catch (SemanticSearchException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SemanticSearchException(503, "SEMANTIC_SEARCH_UNAVAILABLE");
        }
        List<SemanticVectorIndex.SemanticMatch> scopedMatches = matches.stream()
                .filter(match -> githubUserId.equals(match.githubUserId())
                        && repositoryId == match.repositoryId()
                        && job.getCommitSha().equals(match.commitSha()))
                .toList();
        Map<Long, Double> vectorScores = new HashMap<>();
        scopedMatches.forEach(match -> vectorScores.merge(match.databaseChunkId(), clampScore(match.score()), Math::max));
        List<Long> chunkIds = vectorScores.keySet().stream().toList();
        Map<Long, CodeChunkEntity> sourceChunks = new HashMap<>();
        try {
            if (!chunkIds.isEmpty()) {
                chunks.findByJob_IdAndIdIn(job.getId(), chunkIds).stream()
                        .filter(chunk -> githubUserId.equals(chunk.getGithubUserId())
                                && repositoryId == chunk.getGithubRepositoryId()
                                && job.getCommitSha().equals(chunk.getCommitSha()))
                        .forEach(chunk -> sourceChunks.put(chunk.getId(), chunk));
            }
            String lexicalPattern = CodeChunkSearchRanker.regexFor(request.question().trim());
            if (!"(?!)".equals(lexicalPattern)) {
                chunks.findLexicalCandidates(job.getId(), githubUserId, repositoryId, job.getCommitSha(), lexicalPattern)
                        .stream()
                        .filter(chunk -> githubUserId.equals(chunk.getGithubUserId())
                                && repositoryId == chunk.getGithubRepositoryId()
                                && job.getCommitSha().equals(chunk.getCommitSha()))
                        .forEach(chunk -> sourceChunks.putIfAbsent(chunk.getId(), chunk));
            }
        } catch (RuntimeException exception) {
            throw new SemanticSearchException(503, "SEMANTIC_SEARCH_UNAVAILABLE");
        }
        List<SearchResult> results = ranker.rank(request.question().trim(), List.copyOf(sourceChunks.values()),
                        vectorScores, request.resolvedLimit()).stream()
                .map(item -> new SearchResult(item.chunk().getFilePath(), item.chunk().getLineStart(),
                        item.chunk().getLineEnd(), item.chunk().getBranchName(), item.chunk().getCommitSha(),
                        item.chunk().getContent(), item.score()))
                .toList();
        return new RepositorySearchResponse(repositoryId, job.getRepositoryFullName(), job.getBranchName(),
                job.getCommitSha(), request.question().trim(), results);
    }

    private void embedPending(IndexJobEntity job) {
        try {
            String indexedModel = modelName + "+" + EMBEDDING_CONTEXT_VERSION;
            while (true) {
                List<CodeChunkEntity> pending = chunks.findNeedingEmbedding(job.getId(),
                        EmbeddingStatus.PENDING, indexedModel, PageRequest.of(0, 100));
                if (pending.isEmpty()) return;
                List<CodeChunkEntity> batch = pending.subList(0, Math.min(embeddingBatchSize, pending.size()));
                List<SemanticVectorIndex.IndexedCodeChunk> documents = new ArrayList<>(batch.size());
                for (CodeChunkEntity chunk : batch) {
                    documents.add(new SemanticVectorIndex.IndexedCodeChunk(
                            vectorId(chunk), chunk.getId(), chunk.getContent(), chunk.getGithubUserId(),
                            chunk.getGithubRepositoryId(), chunk.getBranchName(), chunk.getCommitSha(),
                            chunk.getFilePath(), chunk.getLineStart(), chunk.getLineEnd(),
                            chunk.getBlobSha(), chunk.getContentSha256()));
                }
                vectors.upsert(documents);
                batch.forEach(chunk -> chunk.markEmbedded(indexedModel));
                chunks.saveAll(batch);
            }
        } catch (RuntimeException exception) {
            throw new SemanticSearchException(503, "EMBEDDING_SERVICE_UNAVAILABLE");
        }
    }

    static String vectorId(CodeChunkEntity chunk) {
        String identity = String.join("\u0000", chunk.getGithubUserId(),
                Long.toString(chunk.getGithubRepositoryId()), chunk.getBranchName(), chunk.getCommitSha(),
                chunk.getFilePath(), Integer.toString(chunk.getChunkIndex()),
                Integer.toString(chunk.getLineStart()), Integer.toString(chunk.getLineEnd()),
                chunk.getContentSha256());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return "repomind-" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static double clampScore(double score) {
        return Math.max(0d, Math.min(1d, score));
    }
}
