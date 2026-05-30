package com.nexsearch.sitemap.service;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.common.util.UrlFilter;
import com.nexsearch.common.util.UrlNormalizer;
import com.nexsearch.sitemap.dto.SitemapDiscoveryResult;
import com.nexsearch.sitemap.dto.SitemapUrl;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SitemapService {

    private static final String USER_AGENT = "NexSearchBot/1.0";
    private static final int TIMEOUT_SECONDS = 10;
    private static final int MAX_SITEMAP_INDEX_DEPTH = 2;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .build();

    /**
     * Discovers crawlable URLs from a sitemap.
     * <p>
     * Supports:
     * - Normal sitemap: <urlset>
     * - Sitemap index: <sitemapindex>
     * <p>
     * Example:
     * https://example.com/sitemap.xml
     */
    public SitemapDiscoveryResult discover(String sitemapUrl, int maxUrls) {
        if (sitemapUrl == null || sitemapUrl.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Sitemap URL is required");
        }

        String normalizedSitemapUrl = UrlNormalizer.normalize(sitemapUrl);

        Map<String, SitemapUrl> discoveredUrls = new LinkedHashMap<>();

        discoverInternal(
                normalizedSitemapUrl,
                discoveredUrls,
                maxUrls,
                0
        );

        return new SitemapDiscoveryResult(
                normalizedSitemapUrl,
                discoveredUrls.size(),
                new ArrayList<>(discoveredUrls.values())
        );
    }

    /**
     * Recursive sitemap discovery.
     * <p>
     * If the sitemap is a sitemap index, this method fetches child sitemaps.
     * Depth limit prevents infinite recursion.
     */
    private void discoverInternal(
            String sitemapUrl,
            Map<String, SitemapUrl> discoveredUrls,
            int maxUrls,
            int depth
    ) {
        if (discoveredUrls.size() >= maxUrls) {
            return;
        }

        if (depth > MAX_SITEMAP_INDEX_DEPTH) {
            return;
        }

        String xml = fetchSitemap(sitemapUrl);
        Document document = parseXml(xml);

        Element root = document.getDocumentElement();

        if (root == null) {
            return;
        }

        String rootName = root.getTagName();

        if ("urlset".equalsIgnoreCase(rootName)) {
            extractUrlsFromUrlSet(document, discoveredUrls, maxUrls);
            return;
        }

        if ("sitemapindex".equalsIgnoreCase(rootName)) {
            extractUrlsFromSitemapIndex(document)
                    .forEach(childSitemapUrl -> {
                        if (discoveredUrls.size() < maxUrls) {
                            discoverInternal(
                                    childSitemapUrl,
                                    discoveredUrls,
                                    maxUrls,
                                    depth + 1
                            );
                        }
                    });
        }
    }

    /**
     * Downloads sitemap XML.
     */
    private String fetchSitemap(String sitemapUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sitemapUrl))
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
                        ErrorCode.SITEMAP_FETCH_FAILED,
                        "Failed to fetch sitemap: " + sitemapUrl + " with status: " + response.statusCode()
                );
            }

            return response.body();

        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.SITEMAP_FETCH_FAILED,
                    "Failed to fetch sitemap: " + sitemapUrl
            );
        }
    }

    /**
     * Parses XML safely into a DOM document.
     */
    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            /*
             * Security hardening:
             * Disable external entity loading to avoid XXE-style XML attacks.
             */
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            factory.setNamespaceAware(false);

            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

        } catch (Exception ex) {
            throw new AppException(
                    ErrorCode.SITEMAP_PARSE_FAILED,
                    "Failed to parse sitemap XML. The response may not be valid XML or may be compressed/non-standard."
            );
        }
    }

    /**
     * Extracts page URLs from normal sitemap files.
     * <p>
     * Example:
     * <url>
     * <loc>https://example.com/page</loc>
     * <lastmod>2026-05-30</lastmod>
     * </url>
     */
    private void extractUrlsFromUrlSet(
            Document document,
            Map<String, SitemapUrl> discoveredUrls,
            int maxUrls
    ) {
        NodeList urlNodes = document.getElementsByTagName("url");

        for (int i = 0; i < urlNodes.getLength() && discoveredUrls.size() < maxUrls; i++) {
            Element urlElement = (Element) urlNodes.item(i);

            String loc = getChildText(urlElement, "loc");
            String lastModified = getChildText(urlElement, "lastmod");

            if (loc == null || loc.isBlank()) {
                continue;
            }

            String normalizedUrl = UrlNormalizer.normalize(loc);

            if (!UrlFilter.isCrawlable(normalizedUrl)) {
                continue;
            }

            discoveredUrls.putIfAbsent(
                    normalizedUrl,
                    new SitemapUrl(normalizedUrl, lastModified)
            );
        }
    }

    /**
     * Extracts child sitemap URLs from sitemap index files.
     * <p>
     * Example:
     * <sitemap>
     * <loc>https://example.com/post-sitemap.xml</loc>
     * </sitemap>
     */
    private List<String> extractUrlsFromSitemapIndex(Document document) {
        List<String> sitemapUrls = new ArrayList<>();

        NodeList sitemapNodes = document.getElementsByTagName("sitemap");

        for (int i = 0; i < sitemapNodes.getLength(); i++) {
            Element sitemapElement = (Element) sitemapNodes.item(i);

            String loc = getChildText(sitemapElement, "loc");

            if (loc == null || loc.isBlank()) {
                continue;
            }

            sitemapUrls.add(UrlNormalizer.normalize(loc));
        }

        return sitemapUrls;
    }

    /**
     * Reads text content from a child tag.
     * <p>
     * Example:
     * getChildText(urlElement, "loc")
     */
    private String getChildText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);

        if (nodeList.getLength() == 0) {
            return null;
        }

        return nodeList.item(0).getTextContent().trim();
    }
}