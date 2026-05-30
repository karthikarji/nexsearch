package com.nexsearch.source.service;

import com.nexsearch.common.exception.AppException;
import com.nexsearch.common.exception.ErrorCode;
import com.nexsearch.source.dto.SourceResponse;
import com.nexsearch.source.model.SourceAllowedDomainEntity;
import com.nexsearch.source.model.SourceEntity;
import com.nexsearch.source.repository.SourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Provides source configuration data for NexSearch.
 * <p>
 * Why this module exists:
 * <p>
 * We do not want to hardcode Wikipedia, Stack Overflow, Reddit,
 * or Medium directly inside crawler logic.
 * <p>
 * Instead, crawler modules should read source configuration from here.
 */
@Service
public class SourceService {

    private final SourceRepository sourceRepository;

    public SourceService(SourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    /**
     * Returns all sources.
     * <p>
     * Used by admin/test APIs to inspect configured sources.
     */
    @Transactional(readOnly = true)
    public List<SourceResponse> findAll() {
        return sourceRepository.findAllByOrderByPriorityAscNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns only enabled sources.
     * <p>
     * Later, the crawler scheduler will use this method to know which
     * sources should actually be crawled/imported.
     */
    @Transactional(readOnly = true)
    public List<SourceResponse> findEnabled() {
        return sourceRepository.findByEnabledTrueOrderByPriorityAscNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Finds one source by sourceKey.
     * <p>
     * Example:
     * sourceKey = wikipedia
     * sourceKey = stack-overflow
     */
    @Transactional(readOnly = true)
    public SourceResponse findBySourceKey(String sourceKey) {
        return sourceRepository.findBySourceKey(sourceKey)
                .map(this::toResponse)
                .orElseThrow(() -> new AppException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "Source not found: " + sourceKey
                ));
    }

    /**
     * Converts entity into response DTO.
     * <p>
     * We avoid returning JPA entities directly from APIs.
     */
    private SourceResponse toResponse(SourceEntity entity) {
        List<String> domains = entity.getAllowedDomains()
                .stream()
                .map(SourceAllowedDomainEntity::getDomain)
                .toList();

        return new SourceResponse(
                entity.getId(),
                entity.getSourceKey(),
                entity.getName(),
                entity.getSourceType(),
                entity.getBaseUrl(),
                entity.getDiscoveryStrategy(),
                entity.getCrawlStrategy(),
                entity.isEnabled(),
                entity.getPriority(),
                domains
        );
    }
}