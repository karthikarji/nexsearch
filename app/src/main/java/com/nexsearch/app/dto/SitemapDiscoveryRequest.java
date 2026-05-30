package com.nexsearch.app.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record SitemapDiscoveryRequest(
        @NotBlank(message = "Sitemap URL is required")
        @URL(message = "Sitemap URL must be valid")
        String sitemapUrl,

        @Min(value = 1, message = "maxUrls must be at least 1")
        @Max(value = 1000, message = "maxUrls cannot be more than 1000")
        Integer maxUrls
) {
}