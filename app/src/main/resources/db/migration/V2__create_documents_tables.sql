-- ============================================================================
-- V1__create_documents_tables.sql
--
-- Stores:
-- 1. Crawled documents
-- 2. Extracted headings
-- 3. Extracted links
--
-- Future Usage:
-- - Search indexing
-- - Link graph analysis
-- - PageRank calculation
-- - Crawl deduplication
-- ============================================================================


-- ============================================================================
-- DOCUMENTS
--
-- Stores the canonical representation of a crawled page.
--
-- One row = one unique document/page.
-- ============================================================================

CREATE SCHEMA IF NOT EXISTS migration;
CREATE SCHEMA IF NOT EXISTS document;

CREATE TABLE document.documents
(
    id               BIGSERIAL PRIMARY KEY,
    requested_url    TEXT        NOT NULL,
    url              TEXT        NOT NULL,
    final_url        TEXT,
    canonical_url    TEXT,
    title            TEXT,
    meta_description TEXT,
    language         VARCHAR(50),
    content_type     VARCHAR(255),
    http_status      INTEGER,

    -- SHA-256 hash of visible text
    -- Used for duplicate detection
    content_hash     VARCHAR(64) NOT NULL,
    visible_text     TEXT,
    crawl_status     VARCHAR(50) NOT NULL,
    last_crawled_at  TIMESTAMP   NOT NULL,
    last_indexed_at  TIMESTAMP,
    created_at       TIMESTAMP   NOT NULL,
    updated_at       TIMESTAMP   NOT NULL
);

-- Prevent duplicate documents
ALTER TABLE document.documents
    ADD CONSTRAINT uk_documents_url
        UNIQUE (url);



-- ============================================================================
-- DOCUMENT HEADINGS
--
-- Stores H1-H6 headings extracted from HTML.
--
-- Future Usage:
-- - Heading boosting in ranking
-- - Snippet generation
-- - Content structure analysis
-- ============================================================================
CREATE TABLE document.document_headings
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    heading     TEXT   NOT NULL,

    CONSTRAINT fk_document_headings_document
        FOREIGN KEY (document_id)
            REFERENCES document.documents (id)
            ON DELETE CASCADE
);



-- ============================================================================
-- DOCUMENT LINKS
--
-- Stores outgoing links discovered in a page.
--
-- Future Usage:
-- - Crawl discovery
-- - Link graph creation
-- - PageRank calculation
-- - Authority scoring
-- ============================================================================
CREATE TABLE document.document_links
(
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    url         TEXT   NOT NULL,

    CONSTRAINT fk_document_links_document
        FOREIGN KEY (document_id)
            REFERENCES document.documents (id)
            ON DELETE CASCADE
);



-- ============================================================================
-- INDEXES
--
-- Created based on anticipated search engine access patterns.
-- ============================================================================

-- Find document by canonical URL
CREATE INDEX idx_documents_url
    ON document.documents (url);

-- Find document after redirects
CREATE INDEX idx_documents_final_url
    ON document.documents (final_url);

-- Duplicate content detection
CREATE INDEX idx_documents_content_hash
    ON document.documents (content_hash);

-- Fast heading lookup for document
CREATE INDEX idx_document_headings_document_id
    ON document.document_headings (document_id);

-- Fast link lookup for document
CREATE INDEX idx_document_links_document_id
    ON document.document_links (document_id);

-- Future reverse-link lookups
CREATE INDEX idx_document_links_url
    ON document.document_links (url);