package com.repomind.indexing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StartIndexJobRequest(
        @Positive long githubRepositoryId,
        @NotBlank @Size(max = 255) String branch) {
}
