/**
 * persist 组件 — 库引擎契约 {@link com.tepeu.os.persist.Persist}。无 JDBC。
 * compose 选本接口，不是再写一份 SessionStore。session / policy 只认领域端口。
 * <p>
 * SQLite 插头在 {@code persist/sqlite}（{@link com.tepeu.os.persist.sqlite.SqlitePersist}）。
 * Postgres / MySQL 升版另模块实现本接口。
 */
package com.tepeu.os.persist;
