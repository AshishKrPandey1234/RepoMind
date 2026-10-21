package com.repomind.indexing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "repomind.indexing")
public record IndexingProperties(
        @Positive @Max(5000) int maxFiles,
        @Positive @Max(1_048_576) long maxFileBytes,
        @Positive @Max(50_000_000) long maxTotalBytes,
        @Positive @Max(100_000) int maxTreeEntries,
        @Positive @Max(100) int maxAccessCheckPages,
        @Min(256) @Max(10_000) int chunkChars,
        @Min(0) int chunkOverlapChars,
        @Positive int maxActiveJobsPerUser,
        @Positive int maxDurationSeconds,
        @Valid Executor executor) {

    public IndexingProperties {
        if (chunkOverlapChars >= chunkChars) {
            throw new IllegalArgumentException("chunkOverlapChars must be less than chunkChars");
        }
        if (executor == null || executor.maxPoolSize() < executor.corePoolSize()) {
            throw new IllegalArgumentException("indexing executor maxPoolSize must be at least corePoolSize");
        }
    }

    public record Executor(@Positive @Max(8) int corePoolSize,
                           @Positive @Max(8) int maxPoolSize,
                           @Min(0) @Max(100) int queueCapacity) {
    }
}
