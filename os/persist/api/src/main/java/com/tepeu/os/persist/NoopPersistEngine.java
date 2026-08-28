package com.tepeu.os.persist;

import javax.sql.DataSource;

/** 未登记的 JDBC URL：不准备、不改池。 */
final class NoopPersistEngine implements PersistEngine {

    static final NoopPersistEngine INSTANCE = new NoopPersistEngine();

    private NoopPersistEngine() {
    }

    @Override
    public boolean accepts(String jdbcUrl) {
        return false;
    }

    @Override
    public void prepare(DataSource dataSource, String jdbcUrl) {
        // generic JDBC: DataSource 已由调用方建好
    }
}
