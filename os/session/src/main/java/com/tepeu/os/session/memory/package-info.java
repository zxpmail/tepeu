/**
 * 会话端口的进程内实现 — conformance / 单测 / {@code MemoryAssembly} 接线。
 * <p>
 * 与 {@code sqlite} 过同一套端口合同；重启即空。
 * <b>不是</b>产品「记忆平面」，也<b>不是</b>多副本共享存储（多副本另开插头 + fencing）。
 * 发行禁止默认本包。
 */
package com.tepeu.os.session.memory;
