package com.tepeu.os.session.persist;

import com.tepeu.os.persist.Persist;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;

/** 一份会话库：只认 {@link Persist}。 */
final class SessionDb {

    final JdbcTemplate jdbc;
    private final Persist persist;

    SessionDb(Persist persist) {
        this.persist = persist;
        this.jdbc = persist.jdbc();
    }

    <T> T tx(TransactionCallback<T> work) {
        return persist.tx(work);
    }
}
