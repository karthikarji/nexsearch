package com.nexsearch.parser.service;

import com.nexsearch.parser.dto.ParsedPage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HtmlParserService {

    public ParsedPage parse(String html, String baseUrl) {
        Document document = Jsoup.parse(html, baseUrl);

        String title = document.title();
        String visibleText = document.body() != null ? document.body().text() : "";

        List<String> headings = document.select("h1, h2, h3, h4, h5, h6")
                .stream()
                .map(Element::text)
                .filter(text -> text != null && !text.isBlank())
                .distinct()
                .toList();

        List<String> links = document.select("a[href]")
                .stream()
                .map(link -> link.absUrl("href"))
                .filter(link -> link != null && !link.isBlank())
                .distinct()
                .toList();

        String metaDescription = document.selectFirst("meta[name=description]") != null
                ? document.selectFirst("meta[name=description]").attr("content")
                : null;

        String canonicalUrl = document.selectFirst("link[rel=canonical]") != null
                ? document.selectFirst("link[rel=canonical]").absUrl("href")
                : null;

        String language = document.selectFirst("html") != null
                ? document.selectFirst("html").attr("lang")
                : null;

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