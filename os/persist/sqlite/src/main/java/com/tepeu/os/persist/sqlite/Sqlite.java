package com.tepeu.os.persist.sqlite;

import java.nio.file.Path;

/** SQLite URL 与脚本切分 — 包内。 */
final class Sqlite {

    private Sqlite() {
    }

    static String jdbcUrl(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    static boolean executable(String sql) {
        if (sql == null || sql.isBlank()) {
            return false;
        }
        return sql.lines().anyMatch(line -> {
            String t = line.strip();
            return !t.isEmpty() && !t.startsWith("--");
        });
    }
}
