package com.nexsearch.robots.service;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.robots.dto.RobotsCheckResult;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles robots.txt checks for NexSearch.
 * <p>
 * Purpose:
 * Before crawling a page, a polite crawler should check whether the website
 * allows bots to access that URL.
 * <p>
 * Example:
 * URL:
 * https://example.com/articles/java
 * <p>
 * robots.txt location:
 * https://example.com/robots.txt
 * <p>
 * If robots.txt contains:
 * User-agent: *
 * Disallow: /private
 * <p>
 * Then:
 * https://example.com/articles/java  -> allowed
 * https://example.com/private/data   -> blocked
 */
@Service
public class RobotsTxtService {

    private static final String USER_AGENT = "NexSearchBot";
    private static final int TIMEOUT_SECONDS = 5;

    /*
     * Simple in-memory cache.
     *
     * Key:
     * https://example.com
     *
     * Value:
     * Parsed robots.txt rules for that origin.
     *
     * Why cache?
     * Without caching, every page fetch would also fetch robots.txt again.
     * That would be slow and impolite.
     */
    private final Map<String, RobotsRules> cache = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    /**
     * Returns whether a URL is allowed by robots.txt.
     * <p>
     * Example:
     * isAllowed("https://example.com/articles/java")
     * <p>
     * Internally checks:
     * https://example.com/robots.txt
     */
    public boolean isAllowed(String rawUrl) {
        URI pageUri = toUri(rawUrl);

        String origin = toOrigin(pageUri);
        String robotsUrl = toRobotsUrl(pageUri);

        RobotsRules rules = cache.computeIfAbsent(origin, key -> fetchAndParseRobots(robotsUrl));

        return rules.isAllowed(pageUri);
    }

    /**
     * Validates robots.txt access before crawling.
     * <p>
     * If disallowed, throws AppException so our GlobalExceptionHandler
     * returns a standard API error response.
     */
    public void validateAllowed(String rawUrl) {
        if (!isAllowed(rawUrl)) {
            throw new AppException(
                    ErrorCode.ROBOTS_TXT_DISALLOWED,
                    "URL is disallowed by robots.txt: " + rawUrl
            );
        }
    }

    /**
     * Used by test API to see:
     * - original URL
     * - robots.txt URL
     * - allowed or not
     */
    public RobotsCheckResult check(String rawUrl) {
        URI pageUri = toUri(rawUrl);

        return new RobotsCheckResult(
                rawUrl,
                toRobotsUrl(pageUri),
                isAllowed(rawUrl)
        );
    }

    /**
     * Converts string URL to URI and performs basic validation.
     */
    private URI toUri(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl);

            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid URL for robots.txt check: " + rawUrl);
            }

            return uri;

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid URL for robots.txt check: " + rawUrl);
        }
    }

    /**
     * Builds origin key.
     * <p>
     * Example:
     * https://example.com/articles/java
     * <p>
     * Origin:
     * https://example.com
     */
    private String toOrigin(URI uri) {
        int port = uri.getPort();

        if (port == -1) {
            return uri.getScheme().toLowerCase(Locale.ROOT)
                    + "://"
                    + uri.getHost().toLowerCase(Locale.ROOT);
        }

        return uri.getScheme().toLowerCase(Locale.ROOT)
                + "://"
                + uri.getHost().toLowerCase(Locale.ROOT)
                + ":"
                + port;
    }

    /**
     * Builds robots.txt URL from page URL.
     * <p>
     * Example:
     * https://example.com/articles/java
     * <p>
     * robots URL:
     * https://example.com/robots.txt
     */
    private String toRobotsUrl(URI uri) {
        return toOrigin(uri) + "/robots.txt";
    }

    /**
     * Downloads robots.txt and parses rules.
     * <p>
     * Important behavior:
     * If robots.txt is missing, for example 404, we allow crawling.
     * <p>
     * Reason:
     * No robots.txt usually means the site has not declared crawling restrictions.
     */
    private RobotsRules fetchAndParseRobots(String robotsUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(robotsUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            /*
             * If robots.txt does not exist, allow all.
             *
             * Example:
             * HTTP 404 from /robots.txt
             */
            if (response.statusCode() == 404) {
                return RobotsRules.allowAll();
            }

            /*
             * If the server returns an error, we currently allow crawling.
             *
             * Later we may change this to a more conservative behavior:
             * "if robots.txt cannot be checked, skip crawling temporarily."
             */
            if (response.statusCode() >= 400) {
                return RobotsRules.allowAll();
            }

            return parse(response.body());

        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            /*
             * Current MVP behavior:
             * If robots.txt cannot be fetched due to network issue, allow.
             *
             * Future improvement:
             * For large-scale production crawling, we may fail closed or retry.
             */
            return RobotsRules.allowAll();
        }
    }

    /**
     * Parses robots.txt content.
     * <p>
     * Supported rules in this first version:
     * - User-agent
     * - Allow
     * - Disallow
     * <p>
     * Example:
     * User-agent: *
     * Disallow: /private
     * Allow: /private/public
     */
    private RobotsRules parse(String robotsText) {
        List<RobotsRule> rules = new ArrayList<>();

        boolean currentGroupMatches = false;
        boolean seenDirectiveInCurrentGroup = false;

        for (String rawLine : robotsText.split("\\R")) {
            String line = removeComment(rawLine).trim();

            if (line.isBlank()) {
                continue;
            }

            String[] parts = line.split(":", 2);

            if (parts.length < 2) {
                continue;
            }

            String key = parts[0].trim().toLowerCase(Locale.ROOT);
            String value = parts[1].trim();

            if ("user-agent".equals(key)) {
                /*
                 * If directives were already seen, a new user-agent starts a new group.
                 */
                if (seenDirectiveInCurrentGroup) {
                    currentGroupMatches = false;
                    seenDirectiveInCurrentGroup = false;
                }

                String agent = value.toLowerCase(Locale.ROOT);

                /*
                 * Match either our bot specifically or wildcard *.
                 */
                if ("*".equals(agent) || USER_AGENT.toLowerCase(Locale.ROOT).equals(agent)) {
                    currentGroupMatches = true;
                }

                continue;
            }

            if ("allow".equals(key) || "disallow".equals(key)) {
                seenDirectiveInCurrentGroup = true;

                if (!currentGroupMatches) {
                    continue;
                }

                /*
                 * Empty Disallow means allow everything.
                 *
                 * Example:
                 * Disallow:
                 */
                if ("disallow".equals(key) && value.isBlank()) {
                    continue;
                }

                rules.add(new RobotsRule(
                        "allow".equals(key),
                        value
                ));
            }
        }

        return new RobotsRules(rules);
    }

    /**
     * Removes comments from a robots.txt line.
     * <p>
     * Example:
     * Disallow: /private # internal pages
     * <p>
     * becomes:
     * Disallow: /private
     */
    private String removeComment(String line) {
        int commentIndex = line.indexOf('#');

        if (commentIndex < 0) {
            return line;
        }

        return line.substring(0, commentIndex);
    }

    /**
     * Represents parsed robots.txt rules for one website origin.
     */
    private record RobotsRules(List<RobotsRule> rules) {

        static RobotsRules allowAll() {
            return new RobotsRules(List.of());
        }

        boolean isAllowed(URI pageUri) {
            String rawPath = pageUri.getRawPath();

            /*
             * Lambda expressions in Java can only use variables that are final
             * or effectively final.
             *
             * So instead of reassigning the same variable, we create a new final
             * variable called normalizedPath.
             *
             * Example:
             * rawPath = null or ""
             * normalizedPath = "/"
             *
             * rawPath = "/private/page"
             * normalizedPath = "/private/page"
             */
            final String normalizedPath = (rawPath == null || rawPath.isBlank())
                    ? "/"
                    : rawPath;

            /*
             * Find the most specific matching rule.
             *
             * Example:
             * Disallow: /private
             * Allow: /private/public
             *
             * /private/public/page should be allowed because
             * /private/public is more specific than /private.
             */
            return rules.stream()
                    .filter(rule -> normalizedPath.startsWith(rule.path()))
                    .max(Comparator
                            .comparingInt((RobotsRule rule) -> rule.path().length())
                            .thenComparing(RobotsRule::allow))
                    .map(RobotsRule::allow)
                    .orElse(true);
        }
    }

    /**
     * One parsed robots.txt rule.
     * <p>
     * allow = true:
     * Allow: /public
     * <p>
     * allow = false:
     * Disallow: /private
     */
    private record RobotsRule(boolean allow, String path) {
    }
}