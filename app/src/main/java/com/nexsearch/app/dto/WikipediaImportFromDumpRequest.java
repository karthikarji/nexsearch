package com.nexsearch.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record WikipediaImportFromDumpRequest(
        @Min(value = 1, message = "maxTitles must be at least 1")
        @Max(value = 100, message = "maxTitles cannot be more than 100 for one request")
        Integer maxTitles,

        @Min(value = 0, message = "delayMillis cannot be negative")
        @Max(value = 10000, message = "delayMillis cannot be more than 10000")
        Integer delayMillis
) {
}