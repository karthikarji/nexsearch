package com.nexsearch.robots.dto;

public record RobotsCheckResult(
        String url,
        String robotsUrl,
        boolean allowed
) {
}