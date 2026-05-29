package com.nexsearch.document.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "documents",
        schema = "document",
        indexes = {
                @Index(name = "idx_documents_url", columnList = "url"),
                @Index(name = "idx_documents_final_url", columnList = "final_url"),
                @Index(name = "idx_documents_content_hash", columnList = "content_hash")
        }
)
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requested_url", nullable = false, columnDefinition = "TEXT")
    private String requestedUrl;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(name = "final_url", columnDefinition = "TEXT")
    private String finalUrl;

    @Column(name = "canonical_url", columnDefinition = "TEXT")
    private String canonicalUrl;

    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;

    @Column(name = "language")
    private String language;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "visible_text", columnDefinition = "TEXT")
    private String visibleText;

    @Enumerated(EnumType.STRING)
    @Column(name = "crawl_status", nullable = false)
    private CrawlStatus crawlStatus;

    @Column(name = "last_crawled_at", nullable = false)
    private Instant lastCrawledAt;

    @Column(name = "last_indexed_at")
    private Instant lastIndexedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentHeadingEntity> headings = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentLinkEntity> links = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.lastCrawledAt == null) {
            this.lastCrawledAt = now;
        }

        if (this.crawlStatus == null) {
            this.crawlStatus = CrawlStatus.SAVED;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void replaceHeadings(List<String> headingValues) {
        this.headings.clear();

        if (headingValues == null) {
            return;
        }

        headingValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(value -> DocumentHeadingEntity.builder()
                        .document(this)
                        .heading(value)
                        .build())
                .forEach(this.headings::add);
    }

    public void replaceLinks(List<String> linkValues) {
        this.links.clear();

        if (linkValues == null) {
            return;
        }

        linkValues.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .map(value -> DocumentLinkEntity.builder()
                        .document(this)
                        .url(value)
                        .build())
                .forEach(this.links::add);
    }
}