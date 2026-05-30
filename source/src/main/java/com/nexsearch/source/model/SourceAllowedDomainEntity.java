package com.nexsearch.source.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "source_allowed_domains",
        schema = "source",
        indexes = {
                @Index(name = "idx_source_allowed_domains_source_id", columnList = "source_id"),
                @Index(name = "idx_source_allowed_domains_domain", columnList = "domain")
        }
)
public class SourceAllowedDomainEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Domain allowed for crawling/importing under this source.
     *
     * Example:
     * en.wikipedia.org
     * stackoverflow.com
     */
    @Column(name = "domain", nullable = false, length = 255)
    private String domain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id", nullable = false)
    private SourceEntity source;
}