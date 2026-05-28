package com.nexsearch.parser.dto;

import java.util.List;

public record ParsedPage(
        String title,
        String visibleText,
        List<String> headings,
        List<String> links,
        String metaDescription,
        String canonicalUrl,
        String language
) {
}