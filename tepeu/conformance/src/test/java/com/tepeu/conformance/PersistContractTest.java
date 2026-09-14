package com.tepeu.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.persist.Persist;
import com.tepeu.persist.PersistRecord;
import com.tepeu.persist.sqlite.SqlitePersist;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * persist 契约。对 {@link Persist} 接口钉语义，任何实现都要满足：
 * append 撞键失败；put 新键尾插、旧键<b>就地覆盖不挪位</b>；list 恒按写入序；
 * 关库后一切操作失败；重开库见全部已提交写。
 */
class PersistContractTest {

    @TempDir
    Path dir;

    @Test
    void appendThenGetRoundTripsFields() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            persist.append("sp", new PersistRecord("k1", Map.of("a", "1", "b", "2")));
            assertEquals(new PersistRecord("k1", Map.of("a", "1", "b", "2")),
                    persist.get("sp", "k1").orElseThrow());
        }
    }

    @Test
    void appendDuplicateKeyFails() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            persist.append("sp", new PersistRecord("k1", Map.of("a", "1")));
            assertThrows(IllegalStateException.class,
                    () -> persist.append("sp", new PersistRecord("k1", Map.of("a", "2"))));
        }
    }

    @Test
    void listFollowsWriteOrder() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            persist.append("sp", new PersistRecord("a", Map.of()));
            persist.append("sp", new PersistRecord("b", Map.of()));
            persist.append("sp", new PersistRecord("c", Map.of()));
            assertEquals(List.of("a", "b", "c"),
                    persist.list("sp").stream().map(PersistRecord::key).toList());
        }
    }

    @Test
    void putOverwriteKeepsPositionAndNewKeyAppends() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            persist.append("sp", new PersistRecord("a", Map.of("v", "1")));
            persist.append("sp", new PersistRecord("b", Map.of()));
            persist.put("sp", new PersistRecord("a", Map.of("v", "2")));
            persist.put("sp", new PersistRecord("c", Map.of()));
            assertEquals(List.of("a", "b", "c"),
                    persist.list("sp").stream().map(PersistRecord::key).toList());
            assertEquals("2", persist.get("sp", "a").orElseThrow().fields().get("v"));
        }
    }

    @Test
    void getMissingIsEmptyAndSpacesAreSeparate() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            assertFalse(persist.get("sp", "nope").isPresent());
            persist.append("one", new PersistRecord("k", Map.of()));
            assertFalse(persist.get("other", "k").isPresent());
            assertTrue(persist.list("other").isEmpty());
            assertEquals(1, persist.list("one").size());
            assertTrue(persist.list("empty").isEmpty());
        }
    }

    @Test
    void blankSpaceRejected() throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> persist.append(" ", new PersistRecord("k", Map.of())));
        }
    }

    @Test
    void operationsAfterCloseFail() throws Exception {
        SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"));
        persist.close();
        assertThrows(IllegalStateException.class,
                () -> persist.append("sp", new PersistRecord("k", Map.of())));
        assertThrows(IllegalStateException.class, () -> persist.list("sp"));
        persist.close();
    }

    @Test
    void reopenSeesAllCommittedWrites() throws Exception {
        Path db = dir.resolve("s.db");
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            persist.append("sp", new PersistRecord("a", Map.of("v", "1")));
            persist.put("sp", new PersistRecord("b", Map.of("v", "2")));
        }
        try (SqlitePersist persist = SqlitePersist.open(db)) {
            assertEquals(List.of("a", "b"),
                    persist.list("sp").stream().map(PersistRecord::key).toList());
            assertEquals("1", persist.get("sp", "a").orElseThrow().fields().get("v"));
        }
    }
}
