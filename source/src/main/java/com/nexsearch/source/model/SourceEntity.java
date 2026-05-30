package com.nexsearch.source.model;

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
        name = "sources",
        schema = "source",
        indexes = {
                @Index(name = "idx_sources_source_key", columnList = "source_key"),
                @Index(name = "idx_sources_enabled", columnList = "enabled")
        }
)
public class SourceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Stable internal key used in code and APIs.
     *
     * Example:
     * wikipedia
     * stack-overflow
     * reddit
     */
    @Column(name = "source_key", nullable = false, unique = true, length = 100)
    private String sourceKey;

    /*
     * Human-readable source name.
     *
     * Example:
     * Wikipedia
     * Stack Overflow
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /*
     * Type of source.
     *
     * This allows us to handle Wikipedia, Stack Overflow, Reddit,
     * Medium, or generic websites differently later.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 50)
    private SourceType sourceType;

    /*
     * Main URL for the source.
     *
     * Example:
     * https://en.wikipedia.org
     * https://stackoverflow.com
     */
    @Column(name = "base_url", nullable = false, columnDefinition = "TEXT")
    private String baseUrl;

    /*
     * How URLs/documents are discovered for this source.
     *
     * Example:
     * Wikipedia may use DUMP_OR_API.
     * Stack Overflow may use API_OR_SITEMAP.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discovery_strategy", nullable = false, length = 50)
    private DiscoveryStrategy discoveryStrategy;

    /*
     * How the source should be crawled/imported.
     *
     * GENERIC_CRAWLER means normal fetch + parse.
     * SOURCE_CONNECTOR means dedicated source-specific logic.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "crawl_strategy", nullable = false, length = 50)
    private CrawlStrategy crawlStrategy;

    /*
     * Whether this source is active.
     *
     * Disabled sources should not be crawled.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /*
     * Lower number means higher priority.
     *
     * Example:
     * Wikipedia priority = 10
     * Medium priority = 40
     */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /*
     * Domains that are allowed for this source.
     *
     * Example:
     * Wikipedia source may allow:
     * en.wikipedia.org
     *
     * Stack Overflow source may allow:
     * stackoverflow.com
     * api.stackexchange.com
     */
    @Builder.Default
    @OneToMany(mappedBy = "source", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SourceAllowedDomainEntity> allowedDomains = new ArrayList<>();

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        this.createdAt = now;
        this.updatedAt = now;

        if (this.priority == null) {
            this.priority = 100;
        }
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}