package com.tepeu.persist;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** 测试夹具。不是发行实现。 */
final class InMemoryPersist implements Persist {

    private final Map<String, LinkedHashMap<String, PersistRecord>> spaces = new LinkedHashMap<>();

    @Override
    public void append(String space, PersistRecord record) {
        LinkedHashMap<String, PersistRecord> rows = rows(space);
        Objects.requireNonNull(record, "record");
        if (rows.containsKey(record.key())) {
            throw new IllegalStateException("append exists: " + space + "/" + record.key());
        }
        rows.put(record.key(), record);
    }

    @Override
    public Optional<PersistRecord> get(String space, String key) {
        requireSpace(space);
        Objects.requireNonNull(key, "key");
        LinkedHashMap<String, PersistRecord> rows = spaces.get(space);
        if (rows == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rows.get(key));
    }

    @Override
    public void put(String space, PersistRecord record) {
        LinkedHashMap<String, PersistRecord> rows = rows(space);
        Objects.requireNonNull(record, "record");
        rows.put(record.key(), record);
    }

    @Override
    public List<PersistRecord> list(String space) {
        requireSpace(space);
        LinkedHashMap<String, PersistRecord> rows = spaces.get(space);
        if (rows == null) {
            return List.of();
        }
        return List.copyOf(rows.values());
    }

    private LinkedHashMap<String, PersistRecord> rows(String space) {
        requireSpace(space);
        return spaces.computeIfAbsent(space, s -> new LinkedHashMap<>());
    }

    private static void requireSpace(String space) {
        Objects.requireNonNull(space, "space");
        if (space.isBlank()) {
            throw new IllegalArgumentException("persist space blank");
        }
    }
}
