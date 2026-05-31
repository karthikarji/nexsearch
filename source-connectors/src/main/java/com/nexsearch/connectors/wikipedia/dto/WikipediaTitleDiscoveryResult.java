package com.nexsearch.connectors.wikipedia.dto;


import java.util.List;

public record WikipediaTitleDiscoveryResult(
        String dumpUrl,
        int discoveredCount,
        List<String> titles
) {
}