package com.tepeu.os.persist;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;

import javax.sql.DataSource;

/**
 * 统一访问口。不知道 Session / Approval。
 * 调用方只跑 SQL / 事务，不建连接、不关库。关库由持有本对象的宿主（host / 测试）负责。
 */
public interface Persist extends AutoCloseable {

    JdbcTemplate jdbc();

    <T> T tx(TransactionCallback<T> work);

    void script(String ddl);

    static Persist jdbc(DataSource dataSource) {
        return new JdbcPersist(dataSource);
    }

    @Override
    void close();
}
