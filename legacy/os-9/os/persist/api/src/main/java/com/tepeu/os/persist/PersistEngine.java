package com.tepeu.os.persist;

import javax.sql.DataSource;

/**
 * 一份 JDBC 引擎的连接定制。不知道 Session / Approval。
 * 连接本身是 {@link DataSource}；本接口只回答「认不认这个 URL、打开后怎么准备」。
 */
public interface PersistEngine {

    boolean accepts(String jdbcUrl);

    void prepare(DataSource dataSource, String jdbcUrl);

    /** 0 = 不改池大小。 */
    default int maxPoolSize() {
        return 0;
    }

    default String connectionInitSql() {
        return "";
    }
}
