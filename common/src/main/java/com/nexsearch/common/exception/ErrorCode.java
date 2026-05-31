package com.nexsearch.common.exception;

public enum ErrorCode {

    INVALID_REQUEST("Invalid request", 400),
    RESOURCE_NOT_FOUND("Resource not found", 404),
    INTERNAL_SERVER_ERROR("Internal server error", 500),
    UNSUPPORTED_URL("Unsupported URL type", 400),
    ROBOTS_TXT_DISALLOWED("URL is disallowed by robots.txt", 403),
    SITEMAP_FETCH_FAILED("Failed to fetch sitemap", 500),
    SITEMAP_PARSE_FAILED("Failed to parse sitemap", 500),
    WIKIPEDIA_FETCH_FAILED("Failed to fetch Wikipedia article", 500),
    WIKIPEDIA_PARSE_FAILED("Failed to parse Wikipedia API response", 500),
    PAGE_FETCH_FAILED("Failed to fetch page", 500);

    private final String defaultMessage;
    private final int httpStatus;

    ErrorCode(String defaultMessage, int httpStatus) {
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}