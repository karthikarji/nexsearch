package com.nexsearch.connectors.wikipedia.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.connectors.core.SourceConnector;
import com.nexsearch.connectors.wikipedia.dto.WikipediaBatchImportResult;
import com.nexsearch.connectors.wikipedia.dto.WikipediaImportFailure;
import com.nexsearch.connectors.wikipedia.dto.WikipediaImportResult;
import com.nexsearch.connectors.wikipedia.dto.WikipediaTitleDiscoveryResult;
import com.nexsearch.document.dto.DocumentResponse;
import com.nexsearch.document.dto.SaveDocumentCommand;
import com.nexsearch.document.service.DocumentService;
import com.nexsearch.parser.dto.ParsedPage;
import com.nexsearch.parser.service.HtmlParserService;
import com.nexsearch.source.dto.SourceResponse;
import com.nexsearch.source.service.SourceService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Wikipedia-specific connector.
 * <p>
 * Why this exists:
 * <p>
 * Wikipedia should not be imported only through the generic crawler.
 * It has the official MediaWiki API, so for small imports we can fetch
 * rendered article HTML directly through the API.
 * <p>
 * Current PR scope:
 * - Accept one Wikipedia page title
 * - Fetch rendered article HTML using MediaWiki action=parse
 * - Parse that HTML using HtmlParserService
 * - Save the result using DocumentService
 * <p>
 * Future scope:
 * - Bulk import using Wikimedia dumps
 * - Category-based discovery
 * - API search by keyword
 * - Better Wikipedia-specific metadata extraction
 */
@Service
public class WikipediaConnectorService implements SourceConnector {

    private static final String ALL_TITLES_DUMP_URL =
            "https://dumps.wikimedia.org/enwiki/latest/enwiki-latest-all-titles-in-ns0.gz";

    private static final int MAX_BATCH_IMPORT_TITLES = 10;
    private static final int MAX_TITLE_DISCOVERY_LIMIT = 1000;
    private static final int DEFAULT_DELAY_MILLIS = 200;

    private static final String SOURCE_KEY = "wikipedia";
    private static final String USER_AGENT = "NexSearchBot/1.0";
    private static final int TIMEOUT_SECONDS = 10;

    private final SourceService sourceService;
    private final HtmlParserService htmlParserService;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    public WikipediaConnectorService(
            SourceService sourceService,
            HtmlParserService htmlParserService,
            DocumentService documentService
    ) {
        this.sourceService = sourceService;
        this.htmlParserService = htmlParserService;
        this.documentService = documentService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * Imports one Wikipedia article by title.
     * <p>
     * Example input:
     * Java_(programming_language)
     * <p>
     * Flow:
     * 1. Read Wikipedia source config from DB
     * 2. Fetch rendered article HTML from MediaWiki API
     * 3. Parse HTML into title, text, headings, and links
     * 4. Save as a normal NexSearch document
     */
    public WikipediaImportResult importArticle(String title) {
        if (title == null || title.isBlank()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Wikipedia page title is required"
            );
        }

        /*
         * Load source config from database.
         *
         * This avoids hardcoding source details in connector logic.
         */
        SourceResponse wikipediaSource = sourceService.findBySourceKey(SOURCE_KEY);

        if (!wikipediaSource.enabled()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Wikipedia source is disabled"
            );
        }

        WikipediaApiArticle apiArticle = fetchArticleFromApi(
                wikipediaSource.baseUrl(),
                title.trim()
        );

        /*
         * Public Wikipedia article URL.
         *
         * This becomes the document identity URL in DocumentService.
         */
        String articleUrl = buildArticleUrl(
                wikipediaSource.baseUrl(),
                apiArticle.resolvedTitle()
        );

        /*
         * The MediaWiki API returns rendered HTML.
         * We reuse the generic HtmlParserService to extract text, headings, and links.
         */
        ParsedPage parsedPage = htmlParserService.parse(
                apiArticle.html(),
                articleUrl
        );

        /*
         * Convert Wikipedia-specific API data into our generic document command.
         * DocumentService does not need to know that this came from Wikipedia.
         */
        SaveDocumentCommand command = new SaveDocumentCommand(
                articleUrl,
                articleUrl,
                200,
                "text/html; charset=UTF-8",
                parsedPage.title() == null || parsedPage.title().isBlank()
                        ? apiArticle.resolvedTitle()
                        : parsedPage.title(),
                parsedPage.visibleText(),
                parsedPage.headings(),
                parsedPage.links(),
                parsedPage.metaDescription(),
                articleUrl,
                parsedPage.language() == null || parsedPage.language().isBlank()
                        ? "en"
                        : parsedPage.language()
        );

        DocumentResponse savedDocument = documentService.save(command);

        return new WikipediaImportResult(
                title,
                apiArticle.resolvedTitle(),
                apiArticle.pageId(),
                articleUrl,
                savedDocument
        );
    }

    /**
     * Calls the MediaWiki Action API.
     * <p>
     * Example API URL:
     * <p>
     * https://en.wikipedia.org/w/api.php
     * ?action=parse
     * &page=Java_(programming_language)
     * &prop=text|displaytitle
     * &format=json
     * &formatversion=2
     */
    private WikipediaApiArticle fetchArticleFromApi(String baseUrl, String title) {
        String apiUrl = buildApiUrl(baseUrl, title);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 400) {
                throw new AppException(
                        ErrorCode.WIKIPEDIA_FETCH_FAILED,
                        "Failed to fetch Wikipedia article: " + title + " with status: " + response.statusCode()
                );
            }

            return parseApiResponse(response.body(), title);

        } catch (AppException ex) {
            throw ex;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new AppException(
                    ErrorCode.WIKIPEDIA_FETCH_FAILED,
                    "Failed to fetch Wikipedia article: " + title
            );
        }
    }

    /**
     * Parses MediaWiki API JSON response.
     * <p>
     * Expected successful response:
     * <p>
     * {
     * "parse": {
     * "title": "Java (programming language)",
     * "pageid": 15881,
     * "text": "<div>...</div>"
     * }
     * }
     */
    private WikipediaApiArticle parseApiResponse(String responseBody, String requestedTitle) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            if (root.has("error")) {
                String errorMessage = root.path("error")
                        .path("info")
                        .asText("Unknown Wikipedia API error");

                throw new AppException(
                        ErrorCode.WIKIPEDIA_FETCH_FAILED,
                        "Wikipedia API error for title '" + requestedTitle + "': " + errorMessage
                );
            }

            JsonNode parseNode = root.path("parse");

            if (parseNode.isMissingNode() || parseNode.isNull()) {
                throw new AppException(
                        ErrorCode.WIKIPEDIA_PARSE_FAILED,
                        "Wikipedia API response does not contain parse data for: " + requestedTitle
                );
            }

            String resolvedTitle = parseNode.path("title").asText(requestedTitle);

            Long pageId = parseNode.path("pageid").isNumber()
                    ? parseNode.path("pageid").asLong()
                    : null;

            String html = parseNode.path("text").asText("");

            if (html.isBlank()) {
                throw new AppException(
                        ErrorCode.WIKIPEDIA_PARSE_FAILED,
                        "Wikipedia API response does not contain article HTML for: " + requestedTitle
                );
            }

            return new WikipediaApiArticle(
                    resolvedTitle,
                    pageId,
                    html
            );

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.WIKIPEDIA_PARSE_FAILED,
                    "Failed to parse Wikipedia API response for: " + requestedTitle
            );
        }
    }

    /**
     * Builds the MediaWiki API URL.
     * <p>
     * We use action=parse because it returns rendered article HTML.
     */
    private String buildApiUrl(String baseUrl, String title) {
        String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);

        return baseUrl
                + "/w/api.php"
                + "?action=parse"
                + "&page=" + encodedTitle
                + "&prop=text%7Cdisplaytitle"
                + "&format=json"
                + "&formatversion=2"
                + "&redirects=true";
    }

    /**
     * Builds the public Wikipedia article URL from the resolved title.
     * <p>
     * Example:
     * Java (programming language)
     * <p>
     * becomes:
     * https://en.wikipedia.org/wiki/Java_%28programming_language%29
     */
    private String buildArticleUrl(String baseUrl, String resolvedTitle) {
        String titleForPath = resolvedTitle.trim().replace(" ", "_");

        String encodedTitle = URLEncoder.encode(titleForPath, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return baseUrl + "/wiki/" + encodedTitle;
    }

    /**
     * Imports multiple Wikipedia articles from a provided title list.
     * <p>
     * This is still controlled and safe:
     * - max 100 titles per request
     * - optional delay between API calls
     * - one failed title does not stop the full batch
     * <p>
     * Example:
     * titles = ["Java_(programming_language)", "Search_engine"]
     */
    public WikipediaBatchImportResult importArticles(List<String> titles, Integer delayMillis) {
        if (titles == null || titles.isEmpty()) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "At least one Wikipedia title is required"
            );
        }

        if (titles.size() > MAX_BATCH_IMPORT_TITLES) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "Batch import cannot contain more than " + MAX_BATCH_IMPORT_TITLES + " titles"
            );
        }

        int safeDelayMillis = delayMillis == null
                ? DEFAULT_DELAY_MILLIS
                : Math.max(delayMillis, 0);

        List<WikipediaImportResult> imported = new ArrayList<>();
        List<WikipediaImportFailure> failures = new ArrayList<>();

        for (String title : titles) {
            try {
                WikipediaImportResult result = importArticle(title);
                imported.add(result);
            } catch (Exception ex) {
                failures.add(new WikipediaImportFailure(
                        title,
                        ex.getMessage()
                ));
            }

            sleepBetweenRequests(safeDelayMillis);
        }

        return new WikipediaBatchImportResult(
                titles.size(),
                imported.size(),
                failures.size(),
                imported,
                failures
        );
    }

    /**
     * Discovers article titles from Wikimedia's official all-titles dump.
     * <p>
     * Current dump:
     * enwiki-latest-all-titles-in-ns0.gz
     * <p>
     * This file contains article titles from namespace 0.
     * <p>
     * For this MVP, we read only the first maxTitles entries.
     * We do not download/store the full dump locally yet.
     */
    public WikipediaTitleDiscoveryResult discoverTitles(Integer maxTitles) {
        int safeMaxTitles = maxTitles == null ? 100 : maxTitles;

        if (safeMaxTitles < 1 || safeMaxTitles > MAX_TITLE_DISCOVERY_LIMIT) {
            throw new AppException(
                    ErrorCode.INVALID_REQUEST,
                    "maxTitles must be between 1 and " + MAX_TITLE_DISCOVERY_LIMIT
            );
        }

        List<String> titles = new ArrayList<>();

        try (
                InputStream inputStream = new URL(ALL_TITLES_DUMP_URL).openStream();
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(gzipInputStream, StandardCharsets.UTF_8)
                )
        ) {
            String line;

            while ((line = reader.readLine()) != null && titles.size() < safeMaxTitles) {
                String title = line.trim();

                if (title.isBlank()) {
                    continue;
                }

                titles.add(title);
            }

            return new WikipediaTitleDiscoveryResult(
                    ALL_TITLES_DUMP_URL,
                    titles.size(),
                    titles
            );

        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.WIKIPEDIA_FETCH_FAILED,
                    "Failed to discover Wikipedia titles from dump"
            );
        }
    }

    /**
     * Discovers first N titles from Wikimedia all-titles dump
     * and imports them through the existing API-based article importer.
     * <p>
     * This is useful for testing a controlled mini Wikipedia import.
     */
    public WikipediaBatchImportResult importFromTitlesDump(Integer maxTitles, Integer delayMillis) {
        WikipediaTitleDiscoveryResult discoveryResult = discoverTitles(maxTitles);

        return importArticles(
                discoveryResult.titles(),
                delayMillis
        );
    }

    /**
     * Adds a small delay between API calls.
     * <p>
     * This keeps our connector polite and prevents aggressive API usage.
     */
    private void sleepBetweenRequests(int delayMillis) {
        if (delayMillis <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();

            throw new AppException(
                    ErrorCode.WIKIPEDIA_FETCH_FAILED,
                    "Wikipedia import interrupted"
            );
        }
    }

    /**
     * Small internal holder for data returned by Wikipedia API.
     * <p>
     * This is not exposed outside this connector.
     */
    private record WikipediaApiArticle(
            String resolvedTitle,
            Long pageId,
            String html
    ) {
    }
}