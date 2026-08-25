/**
 * session 组件 — 会话三 store + Inbox/claim + Metering + AuditSink +（可选）投影通知。
 * <p>
 * <b>是组件</b>（可单独拥有/测），不是词汇。默认实现跟本模块：
 * {@code memory.*}（测试）与 {@code sqlite.*}（发行）。
 * <p>
 * 真相分域：{@link com.tepeu.os.session.SessionLog}（entries）·
 * {@link com.tepeu.os.session.SessionRegisters} ·
 * {@link com.tepeu.os.session.SessionLedger} —
 * 「每个载荷恰好属于三者之一，没有第四个地方」。
 * {@link com.tepeu.os.session.AuditSink} 是人手旁路审计，<b>不</b>进 entries。
 * {@link com.tepeu.os.session.ProjectionBus} 是通知，不是真相。
 * <p>
 * <b>日志口径</b>：会话真相进 entries / ledger；人手副作用进 AuditSink。
 * 本组件<b>不</b>引入 slf4j——运维诊断在 {@code host/}；失败可见用异常 fail-closed
 * （如 {@code SqliteStoreException}），禁止吞掉后假装写入成功。
 * <p>
 * 不做：Loop / Policy / syscall 分发 / UI。内核不知道 turn。
 * {@code memory}/{@code sqlite} = 单写者插头；多副本须另实现 fencing，禁止暗示可多开。
 * 端口验收套件在 {@code src/test/.../conformance}，不进发行 jar。
 */
package com.tepeu.os.session;
