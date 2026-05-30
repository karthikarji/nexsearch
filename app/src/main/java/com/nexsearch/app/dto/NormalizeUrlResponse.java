package com.nexsearch.app.dto;

public record NormalizeUrlResponse(
        String originalUrl,
        String baseUrl,
        String normalizedUrl
) {
}