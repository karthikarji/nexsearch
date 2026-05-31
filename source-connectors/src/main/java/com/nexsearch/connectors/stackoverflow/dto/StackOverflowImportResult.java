package com.nexsearch.connectors.stackoverflow.dto;

import java.util.List;

public record StackOverflowImportResult(
        String tag,
        int requestedCount,
        int importedCount,
        List<StackOverflowQuestionImportResult> questions
) {
}