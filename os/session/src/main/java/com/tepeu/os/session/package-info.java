/**
 * session 组件 — 会话三 store + Inbox/claim + Metering + AuditSink +（可选）投影通知。
 * <p>
 * <b>是组件</b>（可单独拥有/测），不是词汇。本模块只暴露端口；JDBC/方言在 {@code persist}。
 * 会话内存夹具在 {@code src/test/.../memory}，不进发行 jar。
 * <p>
 * 真相分域：{@link com.tepeu.os.session.SessionLog}（entries）·
 * {@link com.tepeu.os.session.SessionRegisters} ·
 * {@link com.tepeu.os.session.SessionLedger} —
 * 「每个载荷恰好属于三者之一，没有第四个地方」。
 * {@link com.tepeu.os.session.AuditSink} 是人手旁路审计，<b>不</b>进 entries。
 * {@link com.tepeu.os.session.ProjectionBus} 是通知接口，不是真相；
 * 完成权读 entries，不在投影、不在工具自报。
 * {@link com.tepeu.os.session.local.LocalProjectionBus} 只是本机默认实现。
 * 其他组件可实现可不实现、可不订阅；MQ/Redis 升版再换，本骨架不引入 broker。
 * 模型可见管道（derive/normalize/shape）在 observation；本组件只提供 surface。
 * <p>
 * <b>日志口径</b>：会话真相进 entries / ledger；人手副作用进 AuditSink。
 * 本组件<b>不</b>引入 slf4j。失败可见用异常 fail-closed，
 * 禁止吞掉后假装写入成功。{@link com.tepeu.os.session.local.LocalProjectionBus} 用 JDK
 * {@code System.Logger} 记 drop / 消费者失败；persist 适配器记 schema 开库、recover 补洞
 * （DEBUG）与 replaceRange（DEBUG）。引擎结局在 loop，拦截在 bus，CLI 面在 {@code host/}，
 * 各记一层，不打 SQL / 正文。
 * <p>
 * 不做：Loop / Policy / syscall 分发 / UI / JDBC。内核不知道 turn。
 * 持久化适配器在 {@code session.persist}，只认 {@link com.tepeu.os.persist.Persist}。多副本须另实现 fencing。
 * 端口验收套件与会话内存夹具在 {@code src/test}，不进发行 jar。
 */
package com.tepeu.os.session;
