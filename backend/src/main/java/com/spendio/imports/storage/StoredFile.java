package com.spendio.imports.storage;

/** A stored file: its opaque key and the byte size recorded at upload time. */
public record StoredFile(String key, long sizeBytes) {
}