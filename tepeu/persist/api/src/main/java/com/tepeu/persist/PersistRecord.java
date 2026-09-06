package com.tepeu.persist;

import java.util.Map;
import java.util.Objects;

/**
 * 一条结构化记录。{@code key} 非空、非空白。{@code fields} 不可变副本，空合法。
 * 不做业务校验。
 */
public record PersistRecord(String key, Map<String, String> fields) {
    public PersistRecord {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("persist key blank");
        }
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
