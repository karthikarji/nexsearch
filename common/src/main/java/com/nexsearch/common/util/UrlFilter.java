package com.nexsearch.common.util;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a URL is useful for crawling.
 * <p>
 * Why this matters:
 * <p>
 * NexSearch is currently a text search engine.
 * So we want to crawl pages like:
 * - https://example.com/article/java
 * - https://example.com/docs/search.html
 * <p>
 * But we should avoid files like:
 * - https://example.com/image.png
 * - https://example.com/video.mp4
 * - https://example.com/app.js
 * - https://example.com/archive.zip
 * <p>
 * This avoids wasting:
 * - crawler bandwidth
 * - database storage
 * - parser time
 * - indexing time
 */
public final class UrlFilter {

    /**
     * URL schemes that we allow for crawling.
     * <p>
     * We allow:
     * - http
     * - https
     * <p>
     * We reject:
     * - mailto:
     * - tel:
     * - javascript:
     * - data:
     */
    private static final Set<String> ALLOWED_SCHEMES = Set.of(
            "http",
            "https"
    );

    /**
     * File extensions that are not useful for our current text-search crawler.
     * <p>
     * Later, if we want PDF search, we can remove "pdf" from this list
     * and add a PDF parser module.
     */
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            // images
            "jpg", "jpeg", "png", "gif", "webp", "svg", "ico", "bmp", "tiff",

            // videos
            "mp4", "mov", "avi", "mkv", "webm", "flv",

            // audio
            "mp3", "wav", "ogg", "aac", "flac",

            // documents/binary files
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",

            // archives
            "zip", "rar", "tar", "gz", "7z",

            // code/static assets
            "css", "js", "json", "xml", "map",

            // fonts
            "woff", "woff2", "ttf", "otf", "eot"
    );

    private UrlFilter() {
    }

    /**
     * Returns true if URL is crawlable.
     * <p>
     * Example:
     * https://example.com/article/java -> true
     * <p>
     * https://example.com/image.png -> false
     */
    public static boolean isCrawlable(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());

            return hasAllowedScheme(uri)
                    && !hasBlockedFileExtension(uri);

        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validates URL before fetching.
     * <p>
     * If URL is not crawlable, we throw AppException so the API returns
     * our standard error response format.
     */
    public static void validateCrawlable(String rawUrl) {
        if (!isCrawlable(rawUrl)) {
            throw new AppException(
                    ErrorCode.UNSUPPORTED_URL,
                    "URL is not supported for crawling: " + rawUrl
            );
        }
    }

    /**
     * Checks whether the URL scheme is allowed.
     * <p>
     * Example:
     * https://example.com -> allowed
     * http://example.com  -> allowed
     * mailto:test@test.com -> rejected
     */
    private static boolean hasAllowedScheme(URI uri) {
        if (uri.getScheme() == null || uri.getScheme().isBlank()) {
            return false;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);

        return ALLOWED_SCHEMES.contains(scheme);
    }

    /**
     * Checks whether URL path ends with a blocked file extension.
     * <p>
     * Example:
     * /images/logo.png -> blocked
     * /videos/demo.mp4 -> blocked
     * /articles/java   -> allowed
     * /docs/page.html  -> allowed
     */
    private static boolean hasBlockedFileExtension(URI uri) {
        String path = uri.getPath();

        if (path == null || path.isBlank()) {
            return false;
        }

        String normalizedPath = path.toLowerCase(Locale.ROOT);

        int lastSlashIndex = normalizedPath.lastIndexOf('/');
        String fileName = lastSlashIndex >= 0
                ? normalizedPath.substring(lastSlashIndex + 1)
                : normalizedPath;

        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return false;
        }

        String extension = fileName.substring(dotIndex + 1);

        return BLOCKED_EXTENSIONS.contains(extension);
    }
}