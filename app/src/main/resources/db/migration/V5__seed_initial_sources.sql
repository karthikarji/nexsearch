-- ============================================================================
-- V5__seed_initial_sources.sql
--
-- Purpose:
-- Seeds initial source configurations for NexSearch.
--
-- These sources are not hardcoded in Java.
-- They live in the database and can later be managed through admin APIs.
-- ============================================================================

INSERT INTO source.sources
(
    source_key,
    name,
    source_type,
    base_url,
    discovery_strategy,
    crawl_strategy,
    enabled,
    priority,
    created_at,
    updated_at
)
VALUES
    (
        'wikipedia',
        'Wikipedia',
        'WIKIPEDIA',
        'https://en.wikipedia.org',
        'DUMP_OR_API',
        'SOURCE_CONNECTOR',
        TRUE,
        10,
        NOW(),
        NOW()
    ),
    (
        'stack-overflow',
        'Stack Overflow',
        'STACK_OVERFLOW',
        'https://stackoverflow.com',
        'API_OR_SITEMAP',
        'SOURCE_CONNECTOR',
        TRUE,
        20,
        NOW(),
        NOW()
    ),
    (
        'reddit',
        'Reddit',
        'REDDIT',
        'https://www.reddit.com',
        'API',
        'SOURCE_CONNECTOR',
        FALSE,
        30,
        NOW(),
        NOW()
    ),
    (
        'medium',
        'Medium',
        'MEDIUM',
        'https://medium.com',
        'HTML_LINKS',
        'SOURCE_CONNECTOR',
        FALSE,
        40,
        NOW(),
        NOW()
    )
    ON CONFLICT (source_key) DO NOTHING;


INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'en.wikipedia.org'
FROM source.sources
WHERE source_key = 'wikipedia'
    ON CONFLICT (source_id, domain) DO NOTHING;

INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'dumps.wikimedia.org'
FROM source.sources
WHERE source_key = 'wikipedia'
    ON CONFLICT (source_id, domain) DO NOTHING;

INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'stackoverflow.com'
FROM source.sources
WHERE source_key = 'stack-overflow'
    ON CONFLICT (source_id, domain) DO NOTHING;

INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'api.stackexchange.com'
FROM source.sources
WHERE source_key = 'stack-overflow'
    ON CONFLICT (source_id, domain) DO NOTHING;

INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'www.reddit.com'
FROM source.sources
WHERE source_key = 'reddit'
    ON CONFLICT (source_id, domain) DO NOTHING;

INSERT INTO source.source_allowed_domains(source_id, domain)
SELECT id, 'medium.com'
FROM source.sources
WHERE source_key = 'medium'
    ON CONFLICT (source_id, domain) DO NOTHING;