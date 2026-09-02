package com.tepeu.os.persist;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Spring JDBC 访问。连接在 {@link DataSource} 里，这里不对外暴露。不知道 kernel / approvals。 */
final class JdbcPersist implements Persist {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private volatile boolean closed;

    JdbcPersist(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.jdbc = new JdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    public <T> T tx(TransactionCallback<T> work) {
        return tx.execute(work);
    }

    @Override
    public void script(String ddl) {
        Objects.requireNonNull(ddl, "ddl");
        DatabasePopulatorUtils.execute(
                new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))),
                dataSource);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (dataSource instanceof AutoCloseable c) {
            try {
                c.close();
            } catch (Exception e) {
                throw new IllegalStateException("close persist", e);
            }
        }
    }
}
