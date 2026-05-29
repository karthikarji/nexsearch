package com.nexsearch.document.repository;

import com.nexsearch.document.model.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentRepository extends JpaRepository<DocumentEntity, Long> {

    Optional<DocumentEntity> findByUrl(String url);

    Optional<DocumentEntity> findByFinalUrl(String finalUrl);

    boolean existsByContentHash(String contentHash);
}