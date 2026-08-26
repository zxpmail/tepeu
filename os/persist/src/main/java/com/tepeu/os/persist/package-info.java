/**
 * persist 组件 — 库。领域只调用端口；连接、schema、方言在本组件。
 * <p>
 * 当前插头：{@link com.tepeu.os.persist.sqlite}。Postgres 等升版另插头，不把 JDBC 写回 session/policy。
 */
package com.tepeu.os.persist;
