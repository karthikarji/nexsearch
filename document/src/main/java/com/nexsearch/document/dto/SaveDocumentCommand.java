package com.nexsearch.document.dto;

import java.util.List;

public record SaveDocumentCommand(
        String requestedUrl,
        String finalUrl,
        Integer httpStatus,
        String contentType,
        String title,
        String visibleText,
        List<String> headings,
        List<String> links,
        String metaDescription,
        String canonicalUrl,
        String language
) {
}