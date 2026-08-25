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
 * 不做：Loop / Policy / syscall 分发 / UI。内核不知道 turn；多副本 fencing 仍挂账。
 */
package com.tepeu.os.session;
