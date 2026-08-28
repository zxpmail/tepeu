package com.tepeu.os.persist.sqlite;

import com.tepeu.os.persist.PersistEngine;
import com.tepeu.os.persist.PersistEngines;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteDataSourcesTest {

    @Test
    void openPathCreatesFileWithoutDomainTypes() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-persist-engine-");
        Path kernelFile = dir.resolve("kernel.sqlite");
        Path approvalsFile = dir.resolve("approvals.sqlite");
        try (SqliteDataSources.SqliteDataSource kernel = SqliteDataSources.openFile(kernelFile);
                SqliteDataSources.SqliteDataSource approvals = SqliteDataSources.openFile(approvalsFile)) {
            JdbcTemplate jdbc = new JdbcTemplate(kernel);
            SqliteDataSources.script(kernel, "CREATE TABLE IF NOT EXISTS t(k TEXT PRIMARY KEY, v TEXT)");
            jdbc.update("INSERT INTO t(k, v) VALUES (?,?)", "a", "1");
            assertEquals("1", jdbc.queryForObject("SELECT v FROM t WHERE k=?", String.class, "a"));
            SqliteDataSources.script(approvals, "CREATE TABLE IF NOT EXISTS t(k TEXT)");
            assertTrue(Files.isRegularFile(kernelFile));
            assertTrue(Files.isRegularFile(approvalsFile));
        }
    }

    @Test
    void closeThenWriteFailClosed() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-persist-closed-");
        SqliteDataSources.SqliteDataSource kernel = SqliteDataSources.openFile(dir.resolve("kernel.sqlite"));
        SqliteDataSources.script(kernel, "CREATE TABLE IF NOT EXISTS t(k TEXT)");
        kernel.close();
        assertThrows(DataAccessException.class,
                () -> new JdbcTemplate(kernel).execute("CREATE TABLE IF NOT EXISTS u(k TEXT)"));
    }

    @Test
    void closeTwiceDoesNotThrow() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-persist-reclose-");
        SqliteDataSources.SqliteDataSource kernel = SqliteDataSources.openFile(dir.resolve("kernel.sqlite"));
        kernel.close();
        assertDoesNotThrow(kernel::close);
    }

    @Test
    void serviceLoaderSelectsSqliteEngine() {
        PersistEngine engine = PersistEngines.forUrl("jdbc:sqlite:mem.db");
        assertTrue(engine.accepts("jdbc:sqlite:mem.db"));
        assertEquals(1, engine.maxPoolSize());
        assertEquals("PRAGMA foreign_keys=ON", engine.connectionInitSql());
    }

    @Test
    void prepareIgnoresNonSqliteUrl() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-persist-prepare-");
        try (SqliteDataSources.SqliteDataSource ds = SqliteDataSources.openFile(dir.resolve("k.sqlite"))) {
            assertDoesNotThrow(() -> SqliteDataSources.prepare(ds, "jdbc:mysql://127.0.0.1:3306/tepeu"));
        }
    }
}
