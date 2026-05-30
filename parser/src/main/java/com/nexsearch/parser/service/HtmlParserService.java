package com.nexsearch.parser.service;

import com.nexsearch.parser.dto.ParsedPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Responsible for extracting useful searchable content from raw HTML.
 * <p>
 * This service does not fetch pages.
 * Fetching is handled by PageFetcherService.
 * <p>
 * This service only parses already fetched HTML and extracts:
 * - page title
 * - visible body text
 * - headings
 * - links
 * - meta description
 * - canonical URL
 * - page language
 * <p>
 * Example:
 * Raw HTML:
 * <html>
 * <head>
 * <title>Java Tutorial</title>
 * <meta name="description" content="Learn Java basics">
 * </head>
 * <body>
 * <h1>Java Basics</h1>
 * <p>Java is a programming language.</p>
 * <a href="/spring">Spring</a>
 * </body>
 * </html>
 * <p>
 * Parsed output:
 * title = Java Tutorial
 * visibleText = Java Basics Java is a programming language. Spring
 * headings = [Java Basics]
 * links = [absolute URL for /spring]
 */
@Service
public class HtmlParserService {

    /**
     * Parses raw HTML and converts it into a structured ParsedPage object.
     *
     * @param html    raw HTML returned by the fetcher
     * @param baseUrl final URL of the page, used to convert relative links into absolute links
     * @return parsed page content
     * <p>
     * Example:
     * html contains:
     * <a href="/docs/java">Java Docs</a>
     * <p>
     * baseUrl:
     * https://example.com/tutorial
     * <p>
     * extracted link:
     * https://example.com/docs/java
     */
    public ParsedPage parse(String html, String baseUrl) {
        /*
         * Jsoup parses the raw HTML into a Document object.
         *
         * baseUrl is important because it helps Jsoup resolve relative URLs.
         *
         * Example:
         * href="/java"
         *
         * with baseUrl = https://example.com/docs
         *
         * becomes:
         * https://example.com/java
         */
        Document document = Jsoup.parse(html, baseUrl);

        /*
         * Extracts the content inside the <title> tag.
         *
         * Example:
         * <title>Java - Wikipedia</title>
         *
         * title = "Java - Wikipedia"
         */
        String title = document.title();

        /*
         * Extracts visible text from the <body>.
         *
         * Jsoup's body().text() removes HTML tags and returns human-readable text.
         *
         * Example:
         * <p>Java is popular.</p>
         *
         * visibleText = "Java is popular."
         *
         * If body is missing, we return an empty string to avoid NullPointerException.
         */
        String visibleText = document.body() != null ? document.body().text() : "";

        /*
         * Extracts all headings from h1 to h6.
         *
         * Headings are important because text inside headings usually describes
         * the main topics of the page.
         *
         * Later, we can use headings for ranking boost.
         *
         * Example:
         * <h1>Java</h1>
         * <h2>History</h2>
         *
         * headings = ["Java", "History"]
         */
        List<String> headings = document.select("h1, h2, h3, h4, h5, h6")
                .stream()
                .map(Element::text)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();

        /*
         * Extracts all anchor links from the page.
         *
         * document.select("a[href]") finds all <a> tags that have href.
         *
         * absUrl("href") converts relative URLs into absolute URLs using baseUrl.
         *
         * Example:
         * <a href="/spring">Spring</a>
         *
         * baseUrl = https://example.com/java
         *
         * extracted link = https://example.com/spring
         *
         * These links will later be used by the crawler to discover new pages.
         */
        List<String> links = document.select("a[href]")
                .stream()
                .map(link -> link.absUrl("href"))
                .filter(link -> link != null && !link.isBlank())
                .distinct()
                .toList();

        /*
         * Extracts meta description.
         *
         * Meta description is often a short summary of the page.
         *
         * Example:
         * <meta name="description" content="Learn Java programming">
         *
         * metaDescription = "Learn Java programming"
         *
         * Later, this can be useful for snippets and search result previews.
         */
        String metaDescription = document.selectFirst("meta[name=description]") != null
                ? document.selectFirst("meta[name=description]").attr("content")
                : null;

        /*
         * Extracts canonical URL.
         *
         * A canonical URL tells search engines the preferred identity of a page.
         *
         * Example:
         * Current URL:
         * https://example.com/page?utm_source=google
         *
         * Canonical:
         * https://example.com/page
         *
         * This helps avoid duplicate documents.
         */
        String canonicalUrl = document.selectFirst("link[rel=canonical]") != null
                ? document.selectFirst("link[rel=canonical]").absUrl("href")
                : null;

        /*
         * Extracts page language from the <html lang="..."> attribute.
         *
         * Example:
         * <html lang="en">
         *
         * language = "en"
         *
         * Later, language can help us choose the correct tokenizer/stemmer.
         */
        String language = document.selectFirst("html") != null
                ? document.selectFirst("html").attr("lang")
                : null;

        /*
         * Return all extracted data as a DTO.
         *
         * We do not save anything here.
         * Persistence is handled by DocumentService.
         */
        return new ParsedPage(
                title,
                visibleText,
                headings,
                links,
                metaDescription,
                canonicalUrl,
                language
        );
    }
}