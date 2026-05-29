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
        name = "document_headings",
        schema = "document",
        indexes = {
                @Index(name = "idx_document_headings_document_id", columnList = "document_id")
        }
)
public class DocumentHeadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "heading", nullable = false, columnDefinition = "TEXT")
    private String heading;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private DocumentEntity document;
}