package com.spendio.imports.storage;

/**
 * File backing for CSV imports (ARCHITECTURE.md D.12). The storage layer is an
 * interface so tests can use a temp directory and the implementation can swap to S3
 * without touching the services.
 */
public interface CsvStorage {

    /**
     * Persists raw bytes under a fresh key.
     *
     * @return the storage key to later {@link #read(String)} or {@link #delete(String)}
     */
    StoredFile store(byte[] content);

    byte[] read(String key);

    void delete(String key);
}