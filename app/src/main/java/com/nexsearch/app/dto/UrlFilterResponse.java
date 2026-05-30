package com.nexsearch.app.dto;

public record UrlFilterResponse(
        String url,
        boolean crawlable
) {
}