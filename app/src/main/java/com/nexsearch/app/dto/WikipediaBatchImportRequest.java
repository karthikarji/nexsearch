package com.nexsearch.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WikipediaBatchImportRequest(
        @NotEmpty(message = "At least one title is required")
        List<String> titles,

        @Min(value = 0, message = "delayMillis cannot be negative")
        @Max(value = 10000, message = "delayMillis cannot be more than 10000")
        Integer delayMillis
) {
}