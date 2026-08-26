/**
 * persist 组件的 SQLite 插头 — 本机单写者。JDBC 只在本包，不进 session/policy。
 * <p>
 * 实现 {@link com.tepeu.os.session.SessionStore} 与 {@link com.tepeu.os.policy.ApprovalStore}。
 * 过同一套端口 conformance（测试源内存夹具 vs 本插头）。
 * 单连接 + 互斥；schema v1；写失败 fail-closed。
 * <b>不是</b>多副本安全；fencing/steal 仍挂账。
 * attrs 列用包内 {@link com.tepeu.os.persist.sqlite.AttrsJson}（扁平 Map），不引 Jackson、不依赖 llm。
 */
package com.tepeu.os.persist.sqlite;
