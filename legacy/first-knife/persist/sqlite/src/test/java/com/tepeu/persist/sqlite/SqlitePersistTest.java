package com.tepeu.persist.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.persist.PersistRecord;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePersistTest {

    @Test
    void appendThenGetAndListInWriteOrder() {
        try (SqlitePersist persist = SqlitePersist.memory()) {
            persist.append("events", new PersistRecord("2", Map.of("body", "b")));
            persist.append("events", new PersistRecord("1", Map.of("body", "a")));
            assertEquals("b", persist.get("events", "2").orElseThrow().fields().get("body"));
            assertTrue(persist.get("events", "missing").isEmpty());
            List<PersistRecord> listed = persist.list("events");
            assertEquals(List.of("2", "1"), listed.stream().map(PersistRecord::key).toList());
            assertTrue(persist.list("other").isEmpty());
        }
    }

    @Test
    void appendSameKeyFailsPutOverwrites() {
        try (SqlitePersist persist = SqlitePersist.memory()) {
            persist.append("events", new PersistRecord("1", Map.of("body", "a")));
            assertThrows(IllegalStateException.class,
                    () -> persist.append("events", new PersistRecord("1", Map.of("body", "b"))));
            persist.put("regs", new PersistRecord("idle", Map.of("v", "0")));
            persist.put("regs", new PersistRecord("idle", Map.of("v", "1")));
            assertEquals("1", persist.get("regs", "idle").orElseThrow().fields().get("v"));
            assertEquals(1, persist.list("regs").size());
        }
    }

    @Test
    void spaceRejectsBlankAndFieldsRoundTripQuotes() {
        try (SqlitePersist persist = SqlitePersist.memory()) {
            PersistRecord record = new PersistRecord("1", Map.of());
            assertThrows(NullPointerException.class, () -> persist.append(null, record));
            assertThrows(IllegalArgumentException.class, () -> persist.append(" ", record));
            persist.put("s", new PersistRecord("q", Map.of("k", "say \"hi\"\nnext")));
            assertEquals("say \"hi\"\nnext", persist.get("s", "q").orElseThrow().fields().get("k"));
        }
    }

    @Test
    void fileSurvivesReopen(@TempDir Path dir) {
        Path file = dir.resolve("t.db");
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            persist.append("events", new PersistRecord("1", Map.of("body", "kept")));
        }
        assertTrue(Files.exists(file));
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            assertEquals("kept", persist.get("events", "1").orElseThrow().fields().get("body"));
        }
    }

    @Test
    void closeThenUseFails() {
        SqlitePersist persist = SqlitePersist.memory();
        persist.append("events", new PersistRecord("1", Map.of("body", "a")));
        persist.close();
        persist.close();
        PersistRecord record = new PersistRecord("2", Map.of("body", "b"));
        assertThrows(IllegalStateException.class, () -> persist.append("events", record));
        assertThrows(IllegalStateException.class, () -> persist.get("events", "1"));
        assertThrows(IllegalStateException.class, () -> persist.list("events"));
        assertThrows(IllegalStateException.class,
                () -> persist.put("events", new PersistRecord("1", Map.of("body", "x"))));
    }
}
