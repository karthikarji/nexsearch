package com.nexsearch.document.dto;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String requestedUrl,
        String url,
        String finalUrl,
        String title,
        String contentHash,
        Integer httpStatus,
        String contentType,
        Integer textLength,
        Instant lastCrawledAt
) {
}