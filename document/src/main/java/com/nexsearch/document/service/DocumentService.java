package com.nexsearch.document.service;

import com.nexsearch.common.util.UrlNormalizer;
import com.nexsearch.document.dto.DocumentResponse;
import com.nexsearch.document.dto.SaveDocumentCommand;
import com.nexsearch.document.model.CrawlStatus;
import com.nexsearch.document.model.DocumentEntity;
import com.nexsearch.document.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Handles persistence logic for crawled and parsed documents.
 * <p>
 * Current responsibility:
 * Fetcher + Parser produces data.
 * DocumentService stores that data into:
 * - document.documents
 * - document.document_headings
 * - document.document_links
 * <p>
 * Later, this service will also support:
 * - duplicate detection
 * - re-crawling updates
 * - indexing status updates
 * - stale document refresh
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Saves a parsed document into the database.
     * <p>
     * Flow:
     * 1. Choose the best URL identity for the document
     * 2. Normalize the URL to avoid duplicates
     * 3. Generate content hash from visible text
     * 4. Insert new document or update existing document
     * 5. Replace headings and links
     * 6. Return a lightweight response
     * <p>
     * Example:
     * https://EXAMPLE.com/page/?utm_source=google#top
     * <p>
     * becomes:
     * https://example.com/page
     */
    @Transactional
    public DocumentResponse save(SaveDocumentCommand command) {
        /*
         * chooseUrl decides which URL should represent this document.
         *
         * Priority:
         * 1. canonicalUrl from HTML, if present
         * 2. finalUrl after redirects
         * 3. requestedUrl from crawler
         *
         * UrlNormalizer then converts it into a stable deduplicated format.
         */
        String urlToStore = UrlNormalizer.normalize(chooseUrl(command));

        /*
         * contentHash helps detect duplicate or unchanged content.
         *
         * Example:
         * If the same page is crawled again and visible text is unchanged,
         * the SHA-256 hash will remain the same.
         */
        String contentHash = sha256(command.visibleText());

        /*
         * If a document with the same normalized URL already exists,
         * update it instead of inserting a duplicate row.
         */
        DocumentEntity entity = documentRepository
                .findByUrl(urlToStore)
                .orElseGet(DocumentEntity::new);


        /*
         * If the document already exists and the newly generated content hash
         * is the same as the stored content hash, it means the page content has
         * not changed since the last crawl.
         *
         * In that case, we do not need to update title, text, headings, or links.
         * More importantly, we can avoid unnecessary re-indexing later.
         *
         * We only update lastCrawledAt to record that the page was checked again.
         *
         * Example:
         * First crawl text hash  = abc123
         * Second crawl text hash = abc123
         *
         * Since both hashes are same, the page is unchanged.
         */
        if (entity.getId() != null && contentHash.equals(entity.getContentHash())) {
            entity.setLastCrawledAt(Instant.now());
            DocumentEntity saved = documentRepository.save(entity);
            return toResponse(saved);
        }

        /*
         * Store core document metadata and extracted content.
         */
        entity.setRequestedUrl(command.requestedUrl());
        entity.setUrl(urlToStore);
        entity.setFinalUrl(command.finalUrl());
        entity.setCanonicalUrl(command.canonicalUrl());
        entity.setTitle(command.title());
        entity.setMetaDescription(command.metaDescription());
        entity.setLanguage(command.language());
        entity.setContentType(command.contentType());
        entity.setHttpStatus(command.httpStatus());
        entity.setVisibleText(command.visibleText());
        entity.setContentHash(contentHash);
        entity.setCrawlStatus(CrawlStatus.SAVED);
        entity.setLastCrawledAt(Instant.now());

        /*
         * Headings and links are stored in separate child tables.
         *
         * replaceHeadings and replaceLinks clear old values and insert fresh ones.
         *
         * This is useful for re-crawling:
         * If a page changes, old headings/links should not remain attached.
         */
        entity.replaceHeadings(command.headings());
        entity.replaceLinks(command.links());

        /*
         * Because DocumentEntity owns child collections with cascade = ALL,
         * saving the document also saves headings and links.
         */
        DocumentEntity saved = documentRepository.save(entity);

        return toResponse(saved);
    }

    /**
     * Chooses the best URL to identify the document.
     * <p>
     * Example:
     * requestedUrl = https://example.com/article?id=123
     * finalUrl     = https://example.com/articles/java
     * canonicalUrl = https://example.com/java
     * <p>
     * We store canonicalUrl because it is the page's preferred identity.
     */
    private String chooseUrl(SaveDocumentCommand command) {
        if (command.canonicalUrl() != null && !command.canonicalUrl().isBlank()) {
            return command.canonicalUrl();
        }

        if (command.finalUrl() != null && !command.finalUrl().isBlank()) {
            return command.finalUrl();
        }

        return command.requestedUrl();
    }

    /**
     * Creates a SHA-256 hash from visible text.
     * <p>
     * Why:
     * A search engine needs to know whether page content changed after re-crawling.
     * <p>
     * Example:
     * Text: "Java is a programming language"
     * Hash: stable 64-character SHA-256 value
     * <p>
     * If text changes, the hash changes.
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();

            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    /**
     * Converts internal JPA entity into API response DTO.
     * <p>
     * We do not return the full entity directly because:
     * - entities may contain large text
     * - entities may contain lazy-loaded child collections
     * - API response should be controlled and lightweight
     */
    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getRequestedUrl(),
                entity.getUrl(),
                entity.getFinalUrl(),
                entity.getTitle(),
                entity.getContentHash(),
                entity.getHttpStatus(),
                entity.getContentType(),
                entity.getVisibleText() == null ? 0 : entity.getVisibleText().length(),
                entity.getLastCrawledAt()
        );
    }
}