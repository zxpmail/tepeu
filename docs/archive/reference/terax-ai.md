# Terax × Tepeu（短对账）

> **地位**：存档备查。**不是**内核规范；**不是**吸收参照。  
> **来源**：[crynta/terax-ai](https://github.com/crynta/terax-ai)（Terminal-first AI-native dev workspace；Tauri 2 + Rust + React）。  
> **日期**：2026-08-24。

---

## 总判断

| | Terax | Tepeu `os/` |
|--|-------|-------------|
| 层 | **⑤ 应用** — IDE/工作台（终端 + 编辑器 + Agent 侧栏） | ①–③ 内核 + compose |
| 对象 | 人用 ADE 写代码 | 业务 Agent 运行时 OS |
| Agent | 应用内 Plan/子代理/`TERAX.md`/bash 审批 | SessionLoop · syscall · Policy · 三 store |
| LLM | Vercel AI SDK v6 | 自研 `llm.*` + derive 断言 |

**与 `os/` 不同线** — 同 OpenCode/Cursor 产品族；将来做 **⑤ UI/工作台** 时可扫一眼交互，**勿**当内核切片依据。

---

## 可扫（不立项）

| Terax 点 | Tepeu 映射 |
|----------|------------|
| bash + 审批门 | 产品 HITL；内核已有 ApprovalStore |
| `TERAX.md` 项目记忆 | ⑤ 轻量索引资产；≠ 记忆平面 / entries |
| 密钥进 OS keychain | 与 Secret/compose 不读密钥同向（应用层） |
| spawn Claude Code + 跟进 | Subagent/外部引擎模式；Team 未落码 |
| 终端 + Agent 侧栏一体 | ⑤ 布局参考；execution.* ≠ PTY 产品 |

## 不借

- Tauri / Vercel AI SDK 进 `os/`  
- 用 Terax Agent 工作流推导 Context/Gate 切分  
- 当 Tepeu 架构对标或吸收清单条目  

---

## 索引

| 文档 | 关系 |
|------|------|
| [agent-os-gap.md](../../agent-os-gap.md) | ⑤ UI 未做 |
| [opencode-reference.md](./opencode-reference.md) | 同类 ADE 参照（若存在） |
| [tencent-harness-engineering.md](./tencent-harness-engineering.md) | 另一类「不同线」Coding 产品文 |

**不**因本文新增 ADR。
