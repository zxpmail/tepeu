package com.tepeu.os.persist.sqlite;

/** persist SQLite 插头失败 — fail-closed，禁止吞掉后假装写入成功。 */
public final class SqliteStoreException extends RuntimeException {
    public SqliteStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public SqliteStoreException(Throwable cause) {
        super(cause);
    }
}
