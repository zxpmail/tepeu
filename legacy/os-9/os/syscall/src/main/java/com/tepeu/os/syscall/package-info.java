/**
 * syscall 词汇 — 「调什么 / 结果长什么样 / 用量槽 / args 指纹」。
 * <p>
 * <b>不是组件</b>：无注册表、无 Policy、不写 entries、不读密钥。
 * 能力总线分发在 {@code bus}；具体干活在 {@code llm} / {@code execution} 等注册的
 * {@link com.tepeu.os.syscall.SyscallHandler}。本包只是门上的信封，避免 session/policy/llm 循环依赖抢定义。
 * 与 {@code identity} 对称：identity = 谁/哪/哪次；syscall = 请求与结果形状。
 */
package com.tepeu.os.syscall;
