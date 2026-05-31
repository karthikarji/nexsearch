package com.nexsearch.connectors.wikipedia.dto;

public record WikipediaImportFailure(
        String title,
        String error
) {
}