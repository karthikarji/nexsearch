package com.nexsearch.common.util;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility class used to convert different-looking URLs into one stable format.
 * <p>
 * Why this matters in a search engine:
 * <p>
 * These URLs may represent the same page:
 * - https://EXAMPLE.com/page/
 * - https://example.com/page
 * - https://example.com/page?utm_source=google
 * - https://example.com/page#section
 * <p>
 * If we do not normalize them, the crawler may store duplicate documents.
 */
public final class UrlNormalizer {

    /**
     * Query parameters used mostly for tracking/marketing.
     * <p>
     * Example:
     * https://example.com/page?utm_source=google&fbclid=123
     * <p>
     * These should not affect the identity of the page, so we remove them.
     */
    private static final Set<String> TRACKING_PARAMS = Set.of(
            "fbclid",
            "gclid",
            "msclkid",
            "mc_cid",
            "mc_eid"
    );

    /**
     * Private constructor because this is a utility class.
     * <p>
     * We do not want anyone to create:
     * new UrlNormalizer()
     * <p>
     * Instead, callers should use:
     * UrlNormalizer.normalize(...)
     */
    private UrlNormalizer() {
    }

    /**
     * Normalizes an absolute URL.
     * <p>
     * Example:
     * Input:
     * https://EXAMPLE.com/page/?utm_source=google#top
     * <p>
     * Output:
     * https://example.com/page
     */
    public static String normalize(String rawUrl) {
        return normalize(rawUrl, null);
    }

    /**
     * Normalizes either an absolute URL or a relative URL.
     * <p>
     * If rawUrl is relative, baseUrl is required.
     * <p>
     * Example:
     * rawUrl  = /java/
     * baseUrl = https://example.com/docs/index.html
     * <p>
     * Output:
     * https://example.com/java
     */
    public static String normalize(String rawUrl, String baseUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "URL is required");
        }

        try {
            /*
             * Step 1:
             * Convert relative URL to absolute URL if needed.
             *
             * Example:
             * rawUrl  = /java
             * baseUrl = https://example.com/docs/index.html
             *
             * resolvedUri = https://example.com/java
             */
            URI resolvedUri = resolveUri(rawUrl.trim(), baseUrl);

            /*
             * Step 2:
             * Normalize path parts like "." and "..".
             *
             * Example:
             * https://example.com/docs/../java
             *
             * becomes:
             * https://example.com/java
             */
            URI normalizedUri = resolvedUri.normalize();

            /*
             * Step 3:
             * Normalize each part of the URL separately.
             */
            String scheme = normalizeScheme(normalizedUri);
            String host = normalizeHost(normalizedUri);
            int port = normalizePort(scheme, normalizedUri.getPort());
            String path = normalizePath(normalizedUri.getPath());
            String query = normalizeQuery(normalizedUri.getRawQuery());

            /*
             * Step 4:
             * Rebuild the URL without fragment.
             *
             * Fragment means the part after #.
             *
             * Example:
             * https://example.com/page#section
             *
             * We remove #section because it points to a section inside the same page,
             * not a different document.
             */
            URI result = new URI(
                    scheme,
                    null,
                    host,
                    port,
                    path,
                    query,
                    null
            );

            return result.toASCIIString();

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Invalid URL: " + rawUrl
            );
        }
    }

    /**
     * Resolves a URL.
     * <p>
     * If rawUrl is already absolute, return it directly.
     * <p>
     * Example:
     * https://example.com/page
     * <p>
     * If rawUrl is relative, resolve it against baseUrl.
     * <p>
     * Example:
     * rawUrl  = /java
     * baseUrl = https://example.com/docs/index.html
     * <p>
     * Output:
     * https://example.com/java
     */
    private static URI resolveUri(String rawUrl, String baseUrl) {
        URI uri = URI.create(rawUrl);

        if (uri.isAbsolute()) {
            return uri;
        }

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Base URL is required for relative URL: " + rawUrl
            );
        }

        return URI.create(baseUrl).resolve(uri);
    }

    /**
     * Normalizes URL scheme.
     * <p>
     * Example:
     * HTTPS://example.com
     * <p>
     * becomes:
     * https://example.com
     */
    private static String normalizeScheme(URI uri) {
        if (uri.getScheme() == null || uri.getScheme().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "URL scheme is required");
        }

        return uri.getScheme().toLowerCase(Locale.ROOT);
    }

    /**
     * Normalizes host/domain.
     * <p>
     * Example:
     * https://EXAMPLE.com
     * <p>
     * becomes:
     * https://example.com
     * <p>
     * IDN.toASCII also supports internationalized domain names.
     */
    private static String normalizeHost(URI uri) {
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "URL host is required");
        }

        return IDN.toASCII(uri.getHost().toLowerCase(Locale.ROOT));
    }

    /**
     * Removes default ports.
     * <p>
     * Example:
     * http://example.com:80/page
     * becomes:
     * http://example.com/page
     * <p>
     * https://example.com:443/page
     * becomes:
     * https://example.com/page
     * <p>
     * Non-default ports are preserved.
     */
    private static int normalizePort(String scheme, int port) {
        if (("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443)) {
            return -1;
        }

        return port;
    }

    /**
     * Normalizes the URL path.
     * <p>
     * Rules:
     * - Empty path becomes "/"
     * - Multiple slashes become one slash
     * - Trailing slash is removed except for root "/"
     * <p>
     * Example:
     * /docs//java/
     * <p>
     * becomes:
     * /docs/java
     */
    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }

        String cleanedPath = path.replaceAll("/{2,}", "/");

        if (cleanedPath.length() > 1 && cleanedPath.endsWith("/")) {
            return cleanedPath.substring(0, cleanedPath.length() - 1);
        }

        return cleanedPath;
    }

    /**
     * Normalizes query parameters.
     * <p>
     * Rules:
     * - Remove tracking parameters like utm_source, fbclid, gclid
     * - Decode parameters first
     * - Remove duplicate parameters
     * - Sort parameters by name and value
     * - Re-encode parameters
     * <p>
     * Example:
     * ?b=2&utm_source=google&a=1
     * <p>
     * becomes:
     * ?a=1&b=2
     */
    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return null;
        }

        List<QueryParam> params = Arrays.stream(rawQuery.split("&"))
                .map(UrlNormalizer::toQueryParam)
                .filter(Objects::nonNull)
                .filter(param -> !isTrackingParam(param.name()))
                .distinct()
                .sorted(Comparator
                        .comparing(QueryParam::name)
                        .thenComparing(QueryParam::value))
                .toList();

        if (params.isEmpty()) {
            return null;
        }

        return String.join("&", params.stream()
                .map(param -> encode(param.name()) + "=" + encode(param.value()))
                .toList());
    }

    /**
     * Converts a raw query string piece into a QueryParam object.
     * <p>
     * Example:
     * rawParam = "a=1"
     * <p>
     * becomes:
     * new QueryParam("a", "1")
     * <p>
     * If parameter has no value:
     * rawParam = "debug"
     * <p>
     * becomes:
     * new QueryParam("debug", "")
     */
    private static QueryParam toQueryParam(String rawParam) {
        if (rawParam == null || rawParam.isBlank()) {
            return null;
        }

        String[] parts = rawParam.split("=", 2);
        String name = decode(parts[0]);

        if (name.isBlank()) {
            return null;
        }

        String value = parts.length > 1 ? decode(parts[1]) : "";

        return new QueryParam(name, value);
    }

    /**
     * Checks whether a query parameter is used for tracking.
     * <p>
     * Examples removed:
     * utm_source
     * utm_campaign
     * fbclid
     * gclid
     */
    private static boolean isTrackingParam(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);

        return normalizedName.startsWith("utm_")
                || TRACKING_PARAMS.contains(normalizedName);
    }

    /**
     * Decodes URL-encoded text.
     * <p>
     * Example:
     * java%20spring
     * <p>
     * becomes:
     * java spring
     */
    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    /**
     * Encodes text so it is safe inside a URL query.
     * <p>
     * Example:
     * java spring
     * <p>
     * becomes:
     * java%20spring
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    /**
     * Small immutable data holder for one query parameter.
     * <p>
     * This is a Java record, not a normal method.
     * <p>
     * This:
     * private record QueryParam(String name, String value) {}
     * <p>
     * automatically creates:
     * - constructor
     * - name()
     * - value()
     * - equals()
     * - hashCode()
     * - toString()
     * <p>
     * That is why the body is empty.
     */
    private record QueryParam(String name, String value) {
    }
}