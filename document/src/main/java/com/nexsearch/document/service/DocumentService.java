package com.nexsearch.document.service;

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

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Transactional
    public DocumentResponse save(SaveDocumentCommand command) {
        String urlToStore = chooseUrl(command);
        String contentHash = sha256(command.visibleText());

        DocumentEntity entity = documentRepository
                .findByUrl(urlToStore)
                .orElseGet(DocumentEntity::new);

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

        entity.replaceHeadings(command.headings());
        entity.replaceLinks(command.links());

        DocumentEntity saved = documentRepository.save(entity);

        return toResponse(saved);
    }

    private String chooseUrl(SaveDocumentCommand command) {
        if (command.canonicalUrl() != null && !command.canonicalUrl().isBlank()) {
            return command.canonicalUrl();
        }

        if (command.finalUrl() != null && !command.finalUrl().isBlank()) {
            return command.finalUrl();
        }

        return command.requestedUrl();
    }

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