package com.tepeu.persist.sqlite;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tepeu.persist.Persist;
import com.tepeu.persist.PersistRecord;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * persist 的 SQLite 实现。{@link #open(Path)} 打开文件库，调用方关。
 * 日志不打 SQL、JDBC URL、路径、字段正文。
 */
public final class SqlitePersist implements Persist, AutoCloseable {

    private static final Logger LOG = System.getLogger(SqlitePersist.class.getName());
    private static final String COMPONENT = "persist";
    private static final String CLASS_NAME = SqlitePersist.class.getSimpleName();
    private static final int BUSY_TIMEOUT_MS = 5000;

    private static final String DDL = """
            CREATE TABLE IF NOT EXISTS persist_record (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              space TEXT NOT NULL,
              rec_key TEXT NOT NULL,
              fields TEXT NOT NULL,
              UNIQUE(space, rec_key)
            )
            """;

    private final DataSource dataSource;
    private final SqlSessionFactory factory;
    /** 非空 = {@link #memory()} 的临时文件，{@link #close()} 时删。文件库为 null。 */
    private final Path tempFile;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private SqlitePersist(DataSource dataSource, SqlSessionFactory factory, Path tempFile) {
        this.dataSource = dataSource;
        this.factory = factory;
        this.tempFile = tempFile;
    }

    /** 打开文件库。路径由调用方持有，这里不记。 */
    public static SqlitePersist open(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "component={0} class={1} open failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
            throw new UncheckedIOException(e);
        }
        return create(jdbcUrl(file), null);
    }

    /** 测试用临时文件。{@link #close()} 时删。 */
    public static SqlitePersist memory() {
        Path file = null;
        try {
            file = Files.createTempFile("tepeu-persist-", ".db");
            return create(jdbcUrl(file), file);
        } catch (IOException e) {
            deleteTemp(file);
            LOG.log(Level.WARNING, "component={0} class={1} open failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteTemp(file);
            throw e;
        }
    }

    /** 建库并装配。{@code tempFile} 非空则 close 时删。 */
    private static SqlitePersist create(String url, Path tempFile) {
        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(BUSY_TIMEOUT_MS);
        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl(url);
        initSchema(dataSource);
        SqlitePersist persist = new SqlitePersist(dataSource, factory(dataSource), tempFile);
        LOG.log(Level.INFO, "component={0} class={1} open kind={2}",
                COMPONENT, CLASS_NAME, tempFile == null ? "file" : "temp");
        return persist;
    }

    private static String jdbcUrl(Path file) {
        return "jdbc:sqlite:" + file.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    /** 建表 {@code persist_record}。已存在则跳过。 */
    private static void initSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DDL);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "component={0} class={1} schema failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
            throw new IllegalStateException("persist schema", e);
        }
    }

    /** 装配 MyBatis-Plus。关掉 SQL 日志。不打 URL。 */
    private static SqlSessionFactory factory(DataSource dataSource) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setLogImpl(NoLoggingImpl.class);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setSqlInjector(new DefaultSqlInjector());
        GlobalConfigUtils.setGlobalConfig(configuration, globalConfig);
        configuration.setEnvironment(new Environment("sqlite", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(PersistRowMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    @Override
    public void append(String space, PersistRecord record) {
        requireOpen();
        requireSpace(space);
        Objects.requireNonNull(record, "record");
        PersistRow row = row(space, record);
        try {
            withMapper(mapper -> mapper.insert(row));
        } catch (RuntimeException e) {
            if (uniqueViolation(e)) {
                throw new IllegalStateException("append exists: " + space + "/" + record.key());
            }
            LOG.log(Level.WARNING, "component={0} class={1} append failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
            throw e;
        }
    }

    @Override
    public Optional<PersistRecord> get(String space, String key) {
        requireOpen();
        requireSpace(space);
        Objects.requireNonNull(key, "key");
        PersistRow row = withMapper(mapper -> mapper.selectOne(Wrappers.<PersistRow>lambdaQuery()
                .eq(PersistRow::getSpace, space)
                .eq(PersistRow::getRecKey, key)));
        return Optional.ofNullable(row).map(SqlitePersist::toRecord);
    }

    @Override
    public void put(String space, PersistRecord record) {
        requireOpen();
        requireSpace(space);
        Objects.requireNonNull(record, "record");
        withMapper(mapper -> {
            PersistRow existing = mapper.selectOne(Wrappers.<PersistRow>lambdaQuery()
                    .eq(PersistRow::getSpace, space)
                    .eq(PersistRow::getRecKey, record.key()));
            if (existing == null) {
                mapper.insert(row(space, record));
            } else {
                existing.setFields(FieldsJson.write(record.fields()));
                mapper.updateById(existing);
            }
            return null;
        });
    }

    @Override
    public List<PersistRecord> list(String space) {
        requireOpen();
        requireSpace(space);
        return withMapper(mapper -> mapper.selectList(Wrappers.<PersistRow>lambdaQuery()
                        .eq(PersistRow::getSpace, space)
                        .orderByAsc(PersistRow::getId)))
                .stream()
                .map(SqlitePersist::toRecord)
                .toList();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        LOG.log(Level.INFO, "component={0} class={1} close kind={2}",
                COMPONENT, CLASS_NAME, tempFile == null ? "file" : "temp");
        try {
            checkpoint();
        } finally {
            deleteTemp(tempFile);
        }
    }

    /** 把 WAL 刷回主文件。关库前调用。 */
    private void checkpoint() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "component={0} class={1} checkpoint failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
        }
    }

    /** 一次调用一个 session，自动提交。 */
    private <T> T withMapper(Function<PersistRowMapper, T> work) {
        requireOpen();
        try (SqlSession session = factory.openSession(true)) {
            return work.apply(session.getMapper(PersistRowMapper.class));
        }
    }

    private void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("persist closed");
        }
    }

    private static PersistRow row(String space, PersistRecord record) {
        PersistRow row = new PersistRow();
        row.setSpace(space);
        row.setRecKey(record.key());
        row.setFields(FieldsJson.write(record.fields()));
        return row;
    }

    private static PersistRecord toRecord(PersistRow row) {
        return new PersistRecord(row.getRecKey(), FieldsJson.read(row.getFields()));
    }

    private static void requireSpace(String space) {
        Objects.requireNonNull(space, "space");
        if (space.isBlank()) {
            throw new IllegalArgumentException("persist space blank");
        }
    }

    /** 只认 UNIQUE / PRIMARY KEY 约束码，不扫消息文本。 */
    private static boolean uniqueViolation(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLiteException sqlite) {
                SQLiteErrorCode code = sqlite.getResultCode();
                if (code == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
                        || code == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static void deleteTemp(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(Path.of(tempFile + "-wal"));
            Files.deleteIfExists(Path.of(tempFile + "-shm"));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "component={0} class={1} temp delete failed type={2}",
                    COMPONENT, CLASS_NAME, e.getClass().getSimpleName());
        }
    }
}
