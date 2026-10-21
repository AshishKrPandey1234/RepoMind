package com.repomind.indexing;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryQuestionService {
    private static final int MAX_CONTEXT_CHARS = 12_000;
    private static final String INSUFFICIENT_ANSWER =
            "The indexed repository does not provide enough evidence to answer this question.";

    private final SemanticSearchService searchService;
    private final AnswerGenerator answerGenerator;

    public RepositoryQuestionService(SemanticSearchService searchService, AnswerGenerator answerGenerator) {
        this.searchService = searchService;
        this.answerGenerator = answerGenerator;
    }

    public RepositoryQuestionResponse ask(String githubUserId, long repositoryId, RepositoryQuestionRequest request) {
        RepositorySearchResponse retrieval = searchService.search(githubUserId, repositoryId,
                new RepositorySearchRequest(request.question(), request.resolvedLimit()));
        List<RepositoryAnswerSource> context = boundedSources(retrieval.results());
        if (context.isEmpty()) {
            return response(retrieval, INSUFFICIENT_ANSWER, true, List.of());
        }

        AnswerGenerator.GeneratedAnswer generated;
        try {
            generated = answerGenerator.generate(request.question().trim(), context);
        } catch (RepositoryQuestionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RepositoryQuestionException(503, "LOCAL_CHAT_MODEL_UNAVAILABLE");
        }
        Map<String, RepositoryAnswerSource> sourcesById = new HashMap<>();
        context.forEach(source -> sourcesById.put(source.sourceId(), source));
        List<RepositoryAnswerSource> cited = generated.sourceIds() == null ? List.of()
                : generated.sourceIds().stream().distinct().map(sourcesById::get).filter(source -> source != null).toList();
        String answer = generated.answer() == null ? "" : generated.answer().trim();
        if (generated.insufficientEvidence() || answer.isBlank() || cited.isEmpty()) {
            return response(retrieval, INSUFFICIENT_ANSWER, true, List.of());
        }
        return response(retrieval, answer, false, cited);
    }

    private List<RepositoryAnswerSource> boundedSources(List<SearchResult> results) {
        List<RepositoryAnswerSource> sources = new ArrayList<>();
        int remaining = MAX_CONTEXT_CHARS;
        for (SearchResult result : results) {
            int size = result.snippet().length();
            if (size > remaining) continue;
            sources.add(new RepositoryAnswerSource("S" + (sources.size() + 1), result.filePath(),
                    result.lineStart(), result.lineEnd(), result.branch(), result.commitSha(),
                    result.snippet(), result.score()));
            remaining -= size;
        }
        return List.copyOf(sources);
    }

    private RepositoryQuestionResponse response(RepositorySearchResponse retrieval, String answer,
                                                boolean insufficient, List<RepositoryAnswerSource> sources) {
        return new RepositoryQuestionResponse(retrieval.repositoryId(), retrieval.repositoryFullName(),
                retrieval.branch(), retrieval.commitSha(), retrieval.question(), answer, insufficient, sources);
    }
}
