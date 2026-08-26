/**
 * persist 的 SQLite 插头模块 — 本机单写者。对外入口 {@link com.tepeu.os.persist.sqlite.SqlitePersist}。
 * JDBC 只在 {@link com.tepeu.os.persist.sqlite.SqliteDb}；会话与审批分库，不是两套连接栈。
 * 契约在 {@link com.tepeu.os.persist.Persist}（{@code persist/api}，无 JDBC）。
 * <p>
 * 领域适配器实现 {@link com.tepeu.os.session.SessionStore} 与 {@link com.tepeu.os.policy.ApprovalStore}。
 * 过同一套端口 conformance（测试源内存夹具 vs 本插头）。
 * schema v1；写失败 fail-closed。
 * <b>不是</b>多副本安全；fencing/steal 仍挂账。
 * attrs 列用包内 {@link com.tepeu.os.persist.sqlite.AttrsJson}（扁平 Map），不引 Jackson、不依赖 llm。
 */
package com.tepeu.os.persist.sqlite;
