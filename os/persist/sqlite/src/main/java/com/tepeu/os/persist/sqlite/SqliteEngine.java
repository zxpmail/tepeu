package com.tepeu.os.persist.sqlite;

import com.tepeu.os.persist.PersistEngine;

import javax.sql.DataSource;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Objects;

/** SQLite：建目录 + PRAGMA。经 {@code META-INF/services} 登记。 */
public final class SqliteEngine implements PersistEngine {

    private static final Logger LOG = System.getLogger(SqliteEngine.class.getName());
    private static final String COMPONENT = "persist";
    private static final String CLASS_NAME = SqliteEngine.class.getSimpleName();
    private static final String SQLITE = "jdbc:sqlite:";

    @Override
    public boolean accepts(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.strip().startsWith(SQLITE);
    }

    @Override
    public int maxPoolSize() {
        return 1;
    }

    @Override
    public String connectionInitSql() {
        return "PRAGMA foreign_keys=ON";
    }

    @Override
    public void prepare(DataSource dataSource, String jdbcUrl) {
        Objects.requireNonNull(dataSource, "dataSource");
        if (!accepts(jdbcUrl)) {
            return;
        }
        String url = jdbcUrl.strip();
        try {
            Path file = fileFromUrl(url);
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA busy_timeout=5000");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA synchronous=FULL");
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "component={0} class={1} prepare failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
            throw new IllegalStateException("prepare sqlite", e);
        }
    }

    static Path fileFromUrl(String url) {
        String path = url.substring(SQLITE.length());
        if (path.startsWith("file:")) {
            path = path.substring("file:".length());
        }
        return Path.of(path);
    }
}
