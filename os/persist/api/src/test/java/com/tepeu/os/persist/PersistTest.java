package com.tepeu.os.persist;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PersistTest {

    @Test
    void closeClosesUnderlyingDataSourceOnce() {
        CountingDataSource ds = new CountingDataSource();
        Persist persist = Persist.jdbc(ds);
        persist.close();
        persist.close();
        assertEquals(1, ds.closes.get());
    }

    @Test
    void closeWithoutAutoCloseableDoesNotThrow() {
        assertDoesNotThrow(() -> Persist.jdbc(new PlainDataSource()).close());
    }

    private static final class PlainDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("unused");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("unused");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    private static class CountingDataSource implements DataSource, AutoCloseable {
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public void close() {
            closes.incrementAndGet();
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("unused");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("unused");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
