package com.tepeu.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PersistTest {

    private final Persist persist = new InMemoryPersist();

    @Test
    void recordRejectsBlankKeyAndCopiesFields() {
        assertThrows(NullPointerException.class, () -> new PersistRecord(null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new PersistRecord(" ", Map.of()));
        Map<String, String> raw = new HashMap<>();
        raw.put("k", "v");
        PersistRecord record = new PersistRecord("e1", raw);
        raw.put("k", "other");
        assertEquals("v", record.fields().get("k"));
        assertTrue(new PersistRecord("e1", null).fields().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> record.fields().put("x", "y"));
    }

    @Test
    void appendThenGetAndListInWriteOrder() {
        persist.append("events", new PersistRecord("2", Map.of("body", "b")));
        persist.append("events", new PersistRecord("1", Map.of("body", "a")));
        assertEquals("b", persist.get("events", "2").orElseThrow().fields().get("body"));
        assertTrue(persist.get("events", "missing").isEmpty());
        List<PersistRecord> listed = persist.list("events");
        assertEquals(List.of("2", "1"), listed.stream().map(PersistRecord::key).toList());
        assertTrue(persist.list("other").isEmpty());
    }

    @Test
    void appendSameKeyFailsPutOverwrites() {
        persist.append("events", new PersistRecord("1", Map.of("body", "a")));
        assertThrows(IllegalStateException.class,
                () -> persist.append("events", new PersistRecord("1", Map.of("body", "b"))));
        persist.put("regs", new PersistRecord("idle", Map.of("v", "0")));
        persist.put("regs", new PersistRecord("idle", Map.of("v", "1")));
        assertEquals("1", persist.get("regs", "idle").orElseThrow().fields().get("v"));
        assertEquals(1, persist.list("regs").size());
    }

    @Test
    void spaceRejectsBlank() {
        PersistRecord record = new PersistRecord("1", Map.of());
        assertThrows(NullPointerException.class, () -> persist.append(null, record));
        assertThrows(IllegalArgumentException.class, () -> persist.append(" ", record));
        assertThrows(IllegalArgumentException.class, () -> persist.get("", "1"));
        assertThrows(IllegalArgumentException.class, () -> persist.list("\t"));
    }
}
