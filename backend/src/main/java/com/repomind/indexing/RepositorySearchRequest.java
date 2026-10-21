package com.repomind.indexing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RepositorySearchRequest(
        @NotBlank @Size(max = 1000) String question,
        @Min(1) @Max(20) Integer limit) {
    public int resolvedLimit() { return limit == null ? 8 : limit; }
}
