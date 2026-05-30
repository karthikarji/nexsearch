package com.nexsearch.app.dto;

import jakarta.validation.constraints.NotBlank;

public record NormalizeUrlRequest(
        @NotBlank(message = "URL is required")
        String url,

        String baseUrl
) {
}