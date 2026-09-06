package com.tepeu.os.persist;

import java.util.ServiceLoader;

/**
 * 按 JDBC URL 选 {@link PersistEngine}。classpath 上的实现经 {@link ServiceLoader} 登记。
 * 没有匹配实现时用空操作（通用 JDBC，不写死 sqlite）。
 */
public final class PersistEngines {

    private PersistEngines() {
    }

    public static PersistEngine forUrl(String jdbcUrl) {
        for (PersistEngine engine : ServiceLoader.load(PersistEngine.class)) {
            if (engine.accepts(jdbcUrl)) {
                return engine;
            }
        }
        return NoopPersistEngine.INSTANCE;
    }
}
