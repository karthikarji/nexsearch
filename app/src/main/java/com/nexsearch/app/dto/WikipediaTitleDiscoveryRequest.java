package com.nexsearch.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record WikipediaTitleDiscoveryRequest(
        @Min(value = 1, message = "maxTitles must be at least 1")
        @Max(value = 1000, message = "maxTitles cannot be more than 1000")
        Integer maxTitles
) {
}