# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md` + `memory/decisions-log.md`（ADR-016）。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-23

## 当前阶段

- develop：本机单写者内核可发行（SQLite WAL schema v1；`SqliteAssembly` 为发行默认）。仍不是 Agent OS
- 下一刀：Compaction 挂 maintenance，或审批规则矩阵，或 execution.* 沙箱（黄灯）
- 禁止在 `legacy/` 加功能；禁止称骨架可演示

## 口径

- **组件（7）**：session / policy / bus / llm / loop / orchestration / compose
- **不是组件**：identity、syscall（词汇）；conformance（harness）
- 组件 ≠ 插件。洋葱是依赖方向。Loop **不**依赖 orchestration。
- **本机单写者内核可发行** ≠ 五层可演示 ≠ 多副本 ≠ 合规删除权。`MemoryAssembly` 仅测试。

## 已完成

- 第九轮起四端口 + fail-closed
- 第十二–十八轮：Loop / llm HTTP / 预算门 / maintenance / DoomLoop / PromptAssembly / Command
- 第十九轮：fork+END_SEED、卫兵 deny>ask>allow、AuditSink、recover 补 INTERRUPTED RESULT、ContentStore sha256
- 第二十轮：SQLite WAL 发行插头（SessionStore / ApprovalStore / AuditSink / ContentStore）；ledger 同连接 read-your-writes + close 后 fail-closed；compose `SqliteAssembly`

## 待办

- Compaction 作业挂上 maintenance 窗
- 默认规则矩阵 / 审批同 turn 回放（层5）
- live key 往返未在 CI 烧
- Slash 宿主副作用写 AuditSink（端口有，自动落账未接 Command）
- 多副本 fencing / timer 轮 / 哈希链 — 远期，禁止当现状
- 禁止对外「骨架可演示 / OS 成形」（五层未钉死）

## 调试

```bash
mvn -f os/pom.xml -pl session,policy,compose test
mvn -f os/pom.xml test
```
