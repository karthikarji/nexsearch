package com.nexsearch.sitemap.dto;

import java.util.List;

public record SitemapDiscoveryResult(
        String sitemapUrl,
        int discoveredUrlCount,
        List<SitemapUrl> urls
) {
}