package com.nexsearch.fetcher.dto;

public record PageFetchResult(
        String requestedUrl,
        String finalUrl,
        int statusCode,
        String contentType,
        String html
) {
}