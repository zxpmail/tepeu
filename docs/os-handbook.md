# OS 读者手册（独有投影）

> 不开新裁决。红线与三路见 [`os-baseplate.md`](./os-baseplate.md)；规范 [ADR-016](../memory/decisions-log.md)。诚实度 [`agent-os-gap.md`](./agent-os-gap.md)。  
> 本文件**只留底板没有的章**。冲突改底板 / ADR，改这里对齐。

---

## 术语

| 词 | 含义 | 不是 |
|----|------|------|
| 底板 | 内核规范投影 | 产品规格 |
| 环 | ①内核 ②插头 ③编排 ⑤应用（④已并入③） | Product-Spec 七层 |
| syscall | 过唯一门的能力调用 | HTTP 路由 |
| 三 store | entries / registers / ledger | 第四个抽屉 |
| Inbox/claim | 进场与租约 | 调度器 |
| Policy | 授权 | 隔离 |
| 完成 | 证据写入 | SSE 结束 / Todo 勾完 |
| Harness | v1 能力壳 | develop 内核 |

---

## Loop 三态（答复+工具+maintenance 窗已落）

```text
 idle ──claim──► running ──释放 / 合成闭合──► idle
                    │
                    │ SessionLoop.maintain（独立预算；不 claim）
                    ▼
              maintenance（强制上限；latch=开窗 Inbox 水位）
                    │ NOW 到达或上限到点 → 让位；其后 now/next 仍在 Inbox
                    ▼
                  idle
```

`loop.state` / `loop.latch` 在 registers。无效转移则停（running/maintenance 中不得再 run / maintain）。
DoomLoop：同工具同输入连续 3 次 → `DoomLoopGuardHook` 返回 NEED_APPROVAL（停执行 + APPROVAL 入 TOOL_RESULT）；批准后 `consumeDecision` 单次许可可重试。

now 级抢占只切流式 chunk；本刀无流式。

---

## 完成证据（无新事件类型）

完成 = 声称之物能从 entries / locator / ledger 还原。SSE 结束、Todo 勾完、回到 idle **不是**完成。`CompletionGate` 钉答复、工具成对、PLAN_STEP、FILE locator（须能从 ContentStore 取回）。Loop 主路永远过 REPLY；其余声称按本 turn 实际发生推断。

| 声称 | 载体 | 不够则不得 completed |
|------|------|----------------------|
| 答复 | `ASSISTANT_MESSAGE` | 只有 reasoning/plan |
| 工具 | `TOOL_CALL`+`TOOL_RESULT` 成对 | 缺 result（取消须合成错误） |
| 文件/大结果 | sha256 locator | 只有路径字符串 |
| 计划 | `PLAN_STEP` | 只勾了 UI |
| 用量 | ledger | 未知却报数字 → n/a |

7 类模型可见事件无 `completed`。第 8 类 `END_SEED` 只在审计日志，不进 surface。加类型走 manifest。

---

## PromptAssembly Section

禁止 Orchestrator 巨型 system 串。

| id | 来源 |
|----|------|
| `base` | 预设，版本化 |
| `workspace_rules` | 项目规则 |
| `skills` | 目录/正文两段懒加载 |
| `memory_hits` | 须来源 id；无平面则本段不存在 |
| `tools_schema` | 总线规范序 |
| `team_role` | preset |
| `task_brief` | 长程唤醒摘要 |
| `prior_reasoning` | 默认关 |

ADR 所写「prompt_assembly 快照事件」**未入**词汇表。落地须 manifest 加类型或证明现有事件能还原 section id。本刀收据 = `includedIds` + `bill`，不新开事件类型。

---

## 开机档（规范 vs conformance）

四端口必须齐：Store / Claim / Policy·Approval / Metering。缺 Policy = 全拒。

| 端口 | 规范单机 | conformance | 发行 |
|------|----------|-------------|------|
| SessionStore | SQLite WAL schema v1 | 内存 | `SqliteSessionStore`（单写者） |
| InboxClaim | 进程内锁+TTL | 内存领取 | SQLite 同进程 TTL；fencing 远期 |
| ApprovalStore | SQLite（**禁内存默认**） | 内存仅测试 | `SqliteApprovalStore`；`MemoryAssembly` 不得发行 |
| Metering | 供数 | 端口有 | 未知价 n/a |

---

## 威胁（资产 / 误用）

Policy≠沙箱。配额=入口拒。Metering 不裁决。Secret 不进模型通道。克制品不是边界。

| 资产 | 落点 |
|------|------|
| 会话事实 | entries |
| 用量 | ledger |
| 审批证据 | ApprovalStore（须持久） |
| 密钥 | Secret 缝 |
| 工作区文件 | execution.*（路径囚笼；隔离 partial） |

禁止：模型当策略引擎；静默未沙箱直通；日志当密钥柜；v1 无认证当 develop 已开门；caller 可选审计；ProjectionBus 当真相。

删除权 vs append-only：**未裁**，不得称合规。

---

## 仍不写

`llm.*` 具体名 · 快照事件类型 · Java 沙箱 · 调度公平 / tamper-evidence · 多副本 fencing。TOOL 落账归属与 RegisterStore 薄端口已随第十二轮裁。SQLite schema v1 已落（第二十轮）。见底板 §8.5。
