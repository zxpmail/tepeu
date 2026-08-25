/**
 * 身份词汇 — 「谁 / 在哪 / 哪次会话 / 本 turn」。
 * <p>
 * <b>不是组件</b>：无端口、无默认实现、无 Persistence；内核三件里「主体 + 命名空间」的类型面。
 * 业务、审批、会话存储不进本包。跨环传 {@link com.tepeu.os.identity.TurnContext}，禁止单例 bind。
 */
package com.tepeu.os.identity;
