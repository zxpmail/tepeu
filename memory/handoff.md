# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md` + `memory/decisions-log.md`（ADR-016）。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-23

## 当前阶段

- develop：**本机 Agent OS 骨架可演示**（ADR-016 第二十二轮）+ 审查修补（第二十三轮）
- 仍不是企业 Agent OS / 完整 OS；无 UI、无记忆平面
- 下一刀按痛点：⑤ UI、记忆平面、多副本 fencing

## 口径

- **组件（8）**：session / policy / bus / llm / loop / orchestration / execution / compose
- **不是组件**：identity、syscall（词汇）；conformance（harness）
- Loop **不**依赖 orchestration。CompactionWork 在 loop，经总线 llm.*。
- execution 隔离 = **partial**。spawn：Windows `CREATE_SUSPENDED` 入 Job 再跑；Linux bwrap + ro-bind-try。无 jail 时失败可见。
- 压缩后 `log.surfaceEpoch` 跳过上笔 llm digest 复核。审批绑 `argsDigest`。`/approve` 校验会话。
- compose **不**读 API 密钥。live 测试有 key 才烧。

## 已完成

- 第九–二十一轮：四端口、Loop、llm HTTP、SQLite 发行、Compaction 窗、DefaultRuleMatrix、execution 囚笼
- 第二十二轮：live 测试（opt-in）、turn 内 overflow 压缩、PLAN/FILE 完成门、OS jail、`/approve` + `policy.rules`
- 第二十三轮：surfaceEpoch、Job CREATE_SUSPENDED、env 白名单、stdout 封顶、argsDigest、jail NOFOLLOW、文档口径对齐

## 待办

- live 往返未在 CI 烧（无 key skip）
- DoomLoop 第三刀仍是 NUDGE 不是 NEED_APPROVAL
- ⑤ UI / 记忆平面
- 多副本 fencing / timer 轮 / 哈希链 — 远期

## 调试

```bash
mvn -f os/pom.xml test
# live（可选）：ANTHROPIC_API_KEY=... mvn -f os/pom.xml -pl llm test
```
