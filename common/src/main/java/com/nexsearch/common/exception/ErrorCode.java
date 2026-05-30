package com.nexsearch.common.exception;

public enum ErrorCode {

    INVALID_REQUEST("Invalid request", 400),
    RESOURCE_NOT_FOUND("Resource not found", 404),
    INTERNAL_SERVER_ERROR("Internal server error", 500),
    UNSUPPORTED_URL("Unsupported URL type", 400),
    ROBOTS_TXT_DISALLOWED("URL is disallowed by robots.txt", 403),
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