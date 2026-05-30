package com.nexsearch.source.repository;

import com.nexsearch.source.model.SourceEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SourceRepository extends JpaRepository<SourceEntity, Long> {

    @EntityGraph(attributePaths = "allowedDomains")
    List<SourceEntity> findAllByOrderByPriorityAscNameAsc();

    @EntityGraph(attributePaths = "allowedDomains")
    List<SourceEntity> findByEnabledTrueOrderByPriorityAscNameAsc();

    @EntityGraph(attributePaths = "allowedDomains")
    Optional<SourceEntity> findBySourceKey(String sourceKey);
}