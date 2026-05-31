package com.nexsearch.connectors.core;

/**
 * Common contract for source-specific connectors.
 * <p>
 * Examples:
 * - Wikipedia connector
 * - Stack Overflow connector
 * - Reddit connector
 * - Medium connector
 * <p>
 * Each connector knows how to import content from its own source.
 */
public interface SourceConnector {

    /**
     * Unique source key from the source configuration table.
     * <p>
     * Example:
     * wikipedia
     * stack-overflow
     */
    String sourceKey();
}