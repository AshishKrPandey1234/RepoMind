package com.repomind.common;

import com.repomind.repository.GitHubRepositoryService.GitHubApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;

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

    @ExceptionHandler(OAuth2AuthorizationException.class)
    ResponseEntity<Map<String, String>> handleOAuthAuthorization(OAuth2AuthorizationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("code", "GITHUB_AUTHORIZATION_EXPIRED", "message", "Reconnect your GitHub account."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleInvalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "The requested page or page size is invalid."));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "The requested page or page size is invalid."));
    }

    private String safeMessage(String code) {
        return switch (code) {
            case "GITHUB_AUTHORIZATION_EXPIRED" -> "Reconnect your GitHub account.";
            case "GITHUB_RATE_LIMITED" -> "GitHub rate limit reached. Try again later.";
            case "GITHUB_ACCESS_DENIED" -> "GitHub denied access to the repository list. Check the OAuth app permissions.";
            default -> "GitHub could not complete the repository request.";
        };
    }
}
