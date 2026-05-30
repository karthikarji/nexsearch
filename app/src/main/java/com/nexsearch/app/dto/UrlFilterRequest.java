package com.nexsearch.app.dto;

import jakarta.validation.constraints.NotBlank;

public record UrlFilterRequest(
        @NotBlank(message = "URL is required")
        String url
) {
}