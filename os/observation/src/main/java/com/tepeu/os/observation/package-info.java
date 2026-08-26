/**
 * observation 组件 — 模型可见管道。入口 {@link com.tepeu.os.observation.Observation}。
 * <p>
 * 读 {@code session.surface}，做 derive ∘ normalize ∘ shape。不存账、不是第四 store。
 * 压缩仍经 loop 的日志替换端口；PromptAssembly 静/动段仍独立。
 * llm 只负责投影 + 传输，依赖本组件，不得旁路拼 messages。
 */
package com.tepeu.os.observation;
