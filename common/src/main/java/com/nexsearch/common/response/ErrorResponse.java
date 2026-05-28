package com.nexsearch.common.response;

import java.util.Map;

public record ErrorResponse(
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public ErrorResponse(String code, String message, String path) {
        this(code, message, path, null);
    }
}