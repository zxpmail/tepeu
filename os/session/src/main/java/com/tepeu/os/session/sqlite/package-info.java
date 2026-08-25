/**
 * 会话端口的 SQLite WAL 发行插头 — 本机单写者持久化。
 * <p>
 * 与 {@code memory} 过同一套测试源 {@code session.conformance.SessionConformance}。
 * 单连接 + 互斥；schema v1；写失败 fail-closed。
 * <b>不是</b>多副本安全：多进程同开同一文件会静默损坏；fencing/steal 仍挂账，另实现再裁。
 * attrs 列用包内 {@link com.tepeu.os.session.sqlite.AttrsJson}（扁平 Map），不引 Jackson、不依赖 llm。
 */
package com.tepeu.os.session.sqlite;
