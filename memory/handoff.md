# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md` + `memory/decisions-log.md`（ADR-016）。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-22

## 当前阶段

- develop：kernel + llm(fake) + **Loop 答复路径 + 工具循环**，不是 Agent OS
- 下一刀：llm 真 HTTP（一族一刀），或预算硬门进往返，或 maintenance
- 禁止在 `legacy/` 加功能；禁止称骨架可演示

## 口径

- **组件（6）**：session / policy / bus / llm / loop / compose
- **不是组件**：identity、syscall（词汇）；conformance（harness）
- 组件 ≠ 插件。洋葱是依赖方向。

## 已完成

- `llm.generate` fake 派生式断言
- 第十二轮：`SessionLoop` 阻塞式 + 显式门；`CompletionGate`；`SessionRegisters`
- 第十三轮：工具先落 `TOOL_CALL` 再总线执行；拦截合成 `TOOL_RESULT`；不 import Tool 类

## 待办

- llm 真 HTTP（Anthropic / OpenAI 各一刀）
- Loop maintenance / DoomLoop
- 预算硬门进真实往返
- 禁止对外「骨架可演示 / OS 成形」（五层未钉死）

## 调试

```bash
mvn -f os/pom.xml -pl loop test
mvn -f os/pom.xml test
```
