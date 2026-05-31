package com.nexsearch.connectors.wikipedia.dto;

import java.util.List;

public record WikipediaBatchImportResult(
        int requestedCount,
        int successCount,
        int failedCount,
        List<WikipediaImportResult> imported,
        List<WikipediaImportFailure> failures
) {
}