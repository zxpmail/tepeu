/**
 * policy 组件 — 封闭 union + 审批通道。
 * <p>
 * 适配器只认 {@link com.tepeu.os.persist.Persist}，不建连接、不关库。
 * JDBC / 方言在 persist；本包 SQL 在 {@code policy.persist}。
 * <p>
 * <b>日志口径</b>：审批真相在 {@link com.tepeu.os.policy.ApprovalStore}（asked/decided/consume）；
 * 人手 {@code /approve} 副作用进 {@code AuditSink}，不进会话 entries。
 * 本组件<b>不</b>引入 slf4j。SQL 行、args、digest 明文不进运维日志。
 * 开库迁移等无行诊断走 JDK {@code System.Logger}（{@code component=policy class=...}）。
 * 失败可见用异常 fail-closed，禁止吞掉后假装写入成功。
 */
package com.tepeu.os.policy;
