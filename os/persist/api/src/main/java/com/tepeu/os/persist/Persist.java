package com.tepeu.os.persist;

import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.session.AuditSink;
import com.tepeu.os.session.SessionStore;

/**
 * persist 引擎插头。compose 选这个，不是再写一份 {@link SessionStore}。
 * session / policy 仍只认领域端口。分库 / 分 schema 是隔离，不是第二套 JDBC 栈。
 * 本骨架插头在 {@code persist/sqlite}：{@link com.tepeu.os.persist.sqlite.SqlitePersist}。PG/MySQL 升版在 persist 下另开子模块。
 */
public interface Persist extends AutoCloseable {

    SessionStore sessions();

    ApprovalStore approvals();

    AuditSink audit();

    @Override
    void close();
}
