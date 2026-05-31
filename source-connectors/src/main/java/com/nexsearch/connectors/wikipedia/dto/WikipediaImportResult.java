package com.nexsearch.connectors.wikipedia.dto;

import com.nexsearch.document.dto.DocumentResponse;

public record WikipediaImportResult(
        String requestedTitle,
        String resolvedTitle,
        Long pageId,
        String articleUrl,
        DocumentResponse document
) {
}