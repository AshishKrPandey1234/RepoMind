package com.repomind.common;

import com.repomind.repository.GitHubRepositoryService.GitHubApiException;
import com.repomind.indexing.IndexingException;
import com.repomind.indexing.SemanticSearchException;
import com.repomind.indexing.RepositoryQuestionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(GitHubApiException.class)
    ResponseEntity<Map<String, Object>> handleGitHubApi(GitHubApiException exception) {
        HttpHeaders headers = new HttpHeaders();
        if (exception.retryAfterSeconds() != null) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()));
        }
        return ResponseEntity.status(exception.status()).headers(headers)
                .body(Map.of("code", exception.code(), "message", safeMessage(exception.code())));
    }

    @ExceptionHandler(IndexingException.class)
    ResponseEntity<Map<String, String>> handleIndexing(IndexingException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("code", exception.code(), "message", safeMessage(exception.code())));
    }

    @ExceptionHandler(SemanticSearchException.class)
    ResponseEntity<Map<String, String>> handleSemanticSearch(SemanticSearchException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("code", exception.code(), "message", safeMessage(exception.code())));
    }

    @ExceptionHandler(RepositoryQuestionException.class)
    ResponseEntity<Map<String, String>> handleRepositoryQuestion(RepositoryQuestionException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("code", exception.code(), "message", safeMessage(exception.code())));
    }

    @ExceptionHandler(OAuth2AuthorizationException.class)
    ResponseEntity<Map<String, String>> handleOAuthAuthorization(OAuth2AuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "GITHUB_AUTHORIZATION_EXPIRED", "message", "Reconnect your GitHub account."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "One or more request values are invalid."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> handleInvalidRequestBody(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "One or more request values are invalid."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "One or more request values are invalid."));
    }

    private String safeMessage(String code) {
        return switch (code) {
            case "GITHUB_AUTHORIZATION_EXPIRED" -> "Reconnect your GitHub account.";
            case "GITHUB_RATE_LIMITED" -> "GitHub rate limit reached. Try again later.";
            case "GITHUB_ACCESS_DENIED" -> "GitHub denied access to the repository list. Check the OAuth app permissions.";
            case "GITHUB_ACCESS_CHECK_LIMIT" -> "The repository list is too large to verify this selection safely. Choose a repository from an earlier page.";
            case "REPOSITORY_TREE_TOO_LARGE" -> "This repository tree exceeds the current indexing limit.";
            case "REPOSITORY_FILE_LIMIT_EXCEEDED" -> "This repository has more eligible files than the indexing limit.";
            case "REPOSITORY_CONTENT_LIMIT_EXCEEDED" -> "This repository exceeds the total indexed content limit.";
            case "ACTIVE_INDEX_JOB_LIMIT" -> "Too many indexing jobs are already active for this account.";
            case "INDEX_JOB_NOT_FOUND" -> "The indexing job was not found.";
            case "INDEX_QUEUE_FULL" -> "The indexing queue is busy. Try again shortly.";
            case "SERVER_RESTARTED" -> "The indexing job was interrupted by a server restart. Start a new job.";
            case "INDEXING_TIMEOUT" -> "Indexing exceeded the configured time limit. Choose a smaller repository or try again later.";
            case "REPOSITORY_NOT_INDEXED" -> "This repository has no completed index yet. Index it before searching.";
            case "EMBEDDING_SERVICE_UNAVAILABLE" -> "Local embedding service or vector store is unavailable. Start Ollama and Chroma, confirm the configured embedding model is installed, then retry.";
            case "SEMANTIC_SEARCH_UNAVAILABLE" -> "Semantic search is temporarily unavailable. Start Ollama and Chroma, then retry.";
            case "LOCAL_CHAT_MODEL_NOT_CONFIGURED" -> "Set REPOMIND_CHAT_MODEL to a chat model installed in local Ollama, then retry.";
            case "LOCAL_CHAT_MODEL_UNAVAILABLE" -> "The local Ollama chat model is unavailable. Check that Ollama is running and the configured model is installed.";
            default -> "GitHub could not complete the repository request.";
        };
    }
}
