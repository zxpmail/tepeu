/**
 * persist 的 SQLite 实现 — {@link com.tepeu.os.persist.sqlite.SqliteEngine}。
 * 契约在 persist/api。本包只做 PRAGMA / 测试夹具。领域 DDL 在 session / policy。
 * prepare 失败走 JDK {@code System.Logger}（无 URL）。成功开库由 host 记 {@code persist=kernel|approvals}。
 */
package com.tepeu.os.persist.sqlite;
