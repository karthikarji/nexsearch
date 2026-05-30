package com.nexsearch.source.dto;

import com.nexsearch.source.model.CrawlStrategy;
import com.nexsearch.source.model.DiscoveryStrategy;
import com.nexsearch.source.model.SourceType;

import java.util.List;

public record SourceResponse(
        Long id,
        String sourceKey,
        String name,
        SourceType sourceType,
        String baseUrl,
        DiscoveryStrategy discoveryStrategy,
        CrawlStrategy crawlStrategy,
        boolean enabled,
        Integer priority,
        List<String> allowedDomains
) {
}