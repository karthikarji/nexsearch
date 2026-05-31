package com.nexsearch.app.dto;

import jakarta.validation.constraints.NotBlank;

public record WikipediaImportRequest(
        @NotBlank(message = "Wikipedia page title is required")
        String title
) {
}