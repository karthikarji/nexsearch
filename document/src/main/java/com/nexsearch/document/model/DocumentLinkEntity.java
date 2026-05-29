package com.nexsearch.document.model;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "document_links",
        schema = "document",
        indexes = {
                @Index(name = "idx_document_links_document_id", columnList = "document_id"),
                @Index(name = "idx_document_links_url", columnList = "url")
        }
)
public class DocumentLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "url", nullable = false, columnDefinition = "TEXT")
    private String url;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;
}