package com.nexsearch.fetcher.service;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.common.util.UrlFilter;
import com.nexsearch.fetcher.dto.PageFetchResult;
import com.nexsearch.robots.service.RobotsTxtService;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Responsible for downloading raw HTML content from a given URL.
 * <p>
 * This service does not parse the HTML.
 * It only fetches the page and returns:
 * - requested URL
 * - final URL after redirects
 * - HTTP status code
 * - content type
 * - raw HTML response body
 * <p>
 * Example:
 * Input URL:
 * https://example.com
 * <p>
 * Output:
 * statusCode = 200
 * contentType = text/html
 * html = "<html>...</html>"
 */
@Service
public class PageFetcherService {

    private final RobotsTxtService robotsTxtService;

    public PageFetcherService(RobotsTxtService robotsTxtService) {
        this.robotsTxtService = robotsTxtService;
    }

    /**
     * User-Agent identifies our crawler/bot to websites.
     * <p>
     * Every HTTP client sends a User-Agent header.
     * Browsers send values like Chrome, Safari, Firefox, etc.
     * <p>
     * Since NexSearch is a crawler, we identify ourselves clearly as:
     * NexSearchBot/1.0
     * <p>
     * Later we can improve it to:
     * NexSearchBot/1.0 (+https://nexsearch.com/bot-info)
     */
    private static final String USER_AGENT = "NexSearchBot/1.0";

    /**
     * Maximum time allowed for fetching a page.
     * <p>
     * If the website does not respond within 10 seconds,
     * the request fails instead of waiting forever.
     */
    private static final int TIMEOUT_MS = 10_000;

    /**
     * Fetches a web page from the given URL.
     * <p>
     * Steps:
     * 1. Connect to the URL using Jsoup
     * 2. Send NexSearchBot user-agent
     * 3. Wait up to TIMEOUT_MS
     * 4. Follow redirects automatically
     * 5. Return response details and raw HTML
     * <p>
     * Example:
     * requested URL = https://example.com
     * final URL     = https://example.com/
     * <p>
     * finalUrl can be different when the website redirects.
     */
    public PageFetchResult fetch(String url) {
        /*
         * Before making the HTTP request, check whether this URL is useful
         * and safe for our current crawler.
         *
         * NexSearch is currently focused on text-based web pages, so we allow
         * normal article/page URLs and reject unsupported files like images,
         * videos, archives, JavaScript, CSS, PDFs, etc.
         *
         * Example:
         * https://example.com/article/java  -> allowed
         * https://example.com/image.png     -> rejected
         *
         * If the URL is not crawlable, validateCrawlable throws AppException
         * with error code UNSUPPORTED_URL. The GlobalExceptionHandler then
         * converts it into a clean API error response.
         */
        UrlFilter.validateCrawlable(url);

        /*
         * Before fetching the page, check robots.txt rules for the website.
         *
         * Example:
         * For https://example.com/article/java
         * we check https://example.com/robots.txt
         *
         * If robots.txt disallows the path, we stop before making the page request.
         */
        robotsTxtService.validateAllowed(url);
        try {
            Connection.Response response = Jsoup.connect(url)
                    /*
                     * Identifies our crawler to the target website.
                     */
                    .userAgent(USER_AGENT)

                    /*
                     * Prevents the crawler from hanging forever
                     * on slow or unresponsive websites.
                     */
                    .timeout(TIMEOUT_MS)

                    /*
                     * Allows Jsoup to follow redirects.
                     *
                     * Example:
                     * http://example.com
                     * redirects to:
                     * https://example.com
                     */
                    .followRedirects(true)

                    /*
                     * Prevents Jsoup from throwing an exception for HTTP errors
                     * like 404 or 500.
                     *
                     * This allows us to capture the status code and decide later
                     * how to handle failed pages.
                     */
                    .ignoreHttpErrors(true)

                    /*
                     * Executes the HTTP request.
                     */
                    .execute();

            /*
             * Return a structured result instead of returning raw Jsoup response.
             * This keeps the fetcher module independent and easier to test.
             */
            return new PageFetchResult(
                    url,
                    response.url().toString(),
                    response.statusCode(),
                    response.contentType(),
                    response.body()
            );

        } catch (IOException ex) {
            /*
             * Convert low-level IO exception into our application-level exception.
             *
             * This allows GlobalExceptionHandler to return a clean API error response.
             */
            throw new AppException(
                    ErrorCode.PAGE_FETCH_FAILED,
                    "Failed to fetch page: " + url
            );
        }
    }
}