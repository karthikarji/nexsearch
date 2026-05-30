-- ============================================================================
-- V4__create_source_tables.sql
--
-- Purpose:
-- Creates source configuration tables.
--
-- Tables:
-- 1. source.sources
-- 2. source.source_allowed_domains
--
-- Future usage:
-- - Crawler source selection
-- - Source-specific connectors
-- - Domain allowlisting
-- - Crawl strategy decisions
-- ============================================================================

CREATE TABLE source.sources
(
    id                 BIGSERIAL PRIMARY KEY,

    source_key         VARCHAR(100) NOT NULL,
    name               VARCHAR(255) NOT NULL,

    source_type        VARCHAR(50)  NOT NULL,
    base_url           TEXT         NOT NULL,

    discovery_strategy VARCHAR(50)  NOT NULL,
    crawl_strategy     VARCHAR(50)  NOT NULL,

    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    priority           INTEGER      NOT NULL DEFAULT 100,

    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,

    CONSTRAINT uk_sources_source_key UNIQUE (source_key),
    CONSTRAINT uk_sources_name UNIQUE (name)
);

CREATE TABLE source.source_allowed_domains
(
    id        BIGSERIAL PRIMARY KEY,
    source_id BIGINT       NOT NULL,
    domain    VARCHAR(255) NOT NULL,

    CONSTRAINT fk_source_allowed_domains_source
        FOREIGN KEY (source_id)
            REFERENCES source.sources (id)
            ON DELETE CASCADE,

    CONSTRAINT uk_source_allowed_domains_source_domain
        UNIQUE (source_id, domain)
);

CREATE INDEX idx_sources_source_key
    ON source.sources (source_key);

CREATE INDEX idx_sources_enabled
    ON source.sources (enabled);

CREATE INDEX idx_source_allowed_domains_source_id
    ON source.source_allowed_domains (source_id);

CREATE INDEX idx_source_allowed_domains_domain
    ON source.source_allowed_domains (domain);