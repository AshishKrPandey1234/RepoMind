package com.repomind.indexing;

import com.repomind.repository.GitHubContentClient;
import com.repomind.repository.GitHubRepositoryService.GitHubApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IndexJobProcessor {
    private final GitHubContentClient github;
    private final SourceFilePolicy filePolicy;
    private final CodeChunker chunker;
    private final IndexJobStore store;
    private final IndexingProperties properties;

    public IndexJobProcessor(GitHubContentClient github, SourceFilePolicy filePolicy, CodeChunker chunker,
                             IndexJobStore store, IndexingProperties properties) {
        this.github = github;
        this.filePolicy = filePolicy;
        this.chunker = chunker;
        this.store = store;
        this.properties = properties;
    }

    public void process(String jobId, String githubUserId, GitHubContentClient.RepositoryDetails repository,
                        String branch, String commitSha, String accessToken) {
        long startNanos = System.nanoTime();
        int filesSeen = 0;
        int filesIndexed = 0;
        int filesSkipped = 0;
        long indexedBytes = 0;
        try {
            store.markRunning(jobId);
            GitHubContentClient.TreeResponse response = github.getRecursiveTree(accessToken, repository, commitSha);
            List<GitHubContentClient.TreeEntry> entries = response.tree() == null ? List.of() : response.tree();
            if (response.truncated() || entries.size() > properties.maxTreeEntries()) {
                throw new IndexingException(422, "REPOSITORY_TREE_TOO_LARGE");
            }
            checkDeadline(startNanos);

            List<GitHubContentClient.TreeEntry> candidates = new ArrayList<>();
            for (GitHubContentClient.TreeEntry entry : entries) {
                if ("tree".equals(entry.type())) continue;
                filesSeen++;
                if (!"blob".equals(entry.type()) || !("100644".equals(entry.mode()) || "100755".equals(entry.mode()))) {
                    filesSkipped++;
                    continue;
                }
                SourceFilePolicy.PathDecision pathDecision = filePolicy.evaluatePath(entry.path());
                if (!pathDecision.eligible() || entry.size() == null || entry.size() < 0
                        || entry.size() > properties.maxFileBytes()) {
                    filesSkipped++;
                    continue;
                }
                candidates.add(entry);
                if (candidates.size() > properties.maxFiles()) {
                    throw new IndexingException(422, "REPOSITORY_FILE_LIMIT_EXCEEDED");
                }
            }

            List<IndexJobStore.PreparedChunk> chunks = new ArrayList<>();
            for (GitHubContentClient.TreeEntry entry : candidates) {
                checkDeadline(startNanos);
                byte[] bytes = github.getBlob(accessToken, repository, entry.sha());
                if (bytes.length > properties.maxFileBytes()) {
                    filesSkipped++;
                    store.updateProgress(jobId, filesSeen, filesIndexed, filesSkipped, indexedBytes);
                    continue;
                }
                SourceFilePolicy.ContentDecision content = filePolicy.evaluateContent(bytes);
                if (!content.eligible() || content.text().isBlank()) {
                    filesSkipped++;
                    store.updateProgress(jobId, filesSeen, filesIndexed, filesSkipped, indexedBytes);
                    continue;
                }
                long nextTotal = indexedBytes + bytes.length;
                if (nextTotal > properties.maxTotalBytes()) {
                    throw new IndexingException(422, "REPOSITORY_CONTENT_LIMIT_EXCEEDED");
                }
                List<CodeChunker.CodeChunk> fileChunks = chunker.chunk(content.text(),
                        properties.chunkChars(), properties.chunkOverlapChars());
                for (CodeChunker.CodeChunk chunk : fileChunks) {
                    chunks.add(new IndexJobStore.PreparedChunk(entry.path(), entry.sha(), chunk));
                }
                indexedBytes = nextTotal;
                filesIndexed++;
                store.updateProgress(jobId, filesSeen, filesIndexed, filesSkipped, indexedBytes);
            }
            checkDeadline(startNanos);
            store.complete(jobId, chunks, filesSeen, filesIndexed, filesSkipped, indexedBytes);
        } catch (IndexingException exception) {
            store.fail(jobId, exception.code(), filesSeen, filesSkipped);
        } catch (GitHubApiException exception) {
            store.fail(jobId, exception.code(), filesSeen, filesSkipped);
        } catch (RuntimeException exception) {
            store.fail(jobId, "INDEXING_FAILED", filesSeen, filesSkipped);
        }
    }

    private void checkDeadline(long startNanos) {
        long elapsed = System.nanoTime() - startNanos;
        if (elapsed > properties.maxDurationSeconds() * 1_000_000_000L) {
            throw new IndexingException(504, "INDEXING_TIMEOUT");
        }
    }
}
