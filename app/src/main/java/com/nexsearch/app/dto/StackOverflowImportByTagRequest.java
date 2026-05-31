package com.nexsearch.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record StackOverflowImportByTagRequest(
        @NotBlank(message = "Tag is required")
        String tag,

        @Min(value = 1, message = "pageSize must be at least 1")
        @Max(value = 20, message = "pageSize cannot be more than 20")
        Integer pageSize
) {
}