# Tepeu OS 底板（实施用）

> **地位**：本文件是 **develop 重写的实施底板**（投影）。规范裁决以 [`memory/decisions-log.md`](../memory/decisions-log.md) **ADR-016** 为准；冲突改本文件对齐 ADR。  
> **图示摘要**：[`kernel-layer.md`](./kernel-layer.md)（更短，勿当第二真相）。

---

## 0. 最后一轮严苛补洞（已裁进底板）

| 洞 | 裁决 |
|----|------|
| ①框「真压缩」像内核自调 LLM | **压缩引擎属缝 `Compaction`（②/维护服务）**；①会话只提供「日志区间替换」端口。压缩经总线 `llm.*`，同受 Policy+卫兵+Metering。 |
| 预算硬门禁未入环 | **开对话 turn 前**经 `Metering`（可与 Policy 协作）拦截；不经模型。人手旁路不走 Token 预算，但仍走 Policy+卫兵+AuditSink。 |
| Policy 画在①里 | **钩点在总线入口；实现属②**。 |
| Adaptor 清单把驱动与编排缝混装 | 底板分三类缝：**驱动插头 / 编排缝 / 路由缝**。 |
| TurnContext 未入底板 | **跨环显式上下文**（principal、workspace、session、delegation、cancel）；禁止单例 bind。 |
| 附件/@文件 | 先落会话事件或 attachment 元数据，再经 PromptAssembly，禁止私自拼进 Prompt。 |

---

## 1. 由内向外（OS 洋葱）

```text
⑤ 应用     UI · SSE投影 · Skill/记忆【资产】· 面板
④ 路由     ThreadRouter · FlowRouter · ModelRouter（只选型）
③ 编排     兜底Agent · Loop(turn/step) · Team/Subagent/LongTask
           PromptAssembly · ReasoningPresenter · Command(Slash)
② 适配     驱动插头 + 编排/路由的可换实现（见 §3）
① 内核     主体+命名空间 · 能力总线 · 会话(日志+Inbox+主/子)
           总线入口钩〔Policy+卫兵〕；会话端口含「日志替换」（供压缩）
```

**底板 = 内核三件**（不是「最内三环」）。  
**依赖**：外→内；内不依赖外。② 是插头，不是①的父亲。

---

## 2. 三条进路（门对称）

碰**能力总线**时一律：〔**Policy + 卫兵**（超时/取消/不变量/**配额限流**）〕→ ②插头。配额按 principal/namespace 限速率、并发、token 硬顶，**入口处拒绝**；Metering 只是事后仪表。

| 路 | 路径 | 事实落点 |
|----|------|----------|
| **对话主路** | 应用→④→③ Loop/Team→① claim/syscall→② | **会话日志**（对话+Agent 工具） |
| **人手旁路** | 应用 REST/终端→① 总线→②（**跳过④与 Loop**） | **AuditSink**（非会话对话事实） |
| **Slash** | 应用→③ **Command**（必经；不经模型/Loop）→若动宿主再走总线 | 宿主副作用 → AuditSink |

开 **Agent turn 前**：`Metering` 预算硬门（超限则不得 claim/开跑）。

---

## 3. 缝清单（显式 · 分类）

### 3.1 驱动插头（②，挂总线或会话端口）

| 缝 | 职责 |
|----|------|
| `SessionStore` | 会话/事件持久化 |
| `InboxClaim` | claim 锁/租约 |
| `Execution` | fs/shell 等执行世界 |
| `LlmProvider` | 模型流式调用实现 |
| `Tool`* | 各工具插头（彼此禁止互引） |
| `McpBridge` | 外挂 MCP 工具桥 |
| `Policy` / `ApprovalStore` | 放行/拒绝/审批状态（**审批证据须持久**，单机默认 SQLite，禁内存） || `AuditSink` | 人手等审计真相 |
| `Metering` | token/费用/预算门 |
| `KnowledgeSource` | 知识→Section 的唯一内容源接口 |
| `Identity` / `OrgNamespace` / `Secret` | 身份、组织命名空间、密钥 |
| `ProjectionBus` | 多副本/UI **通知**（禁止当真相；下发前按**查看者 ACL** 过滤） |
| `Compaction` | 摘要压缩；调 `llm.*`；写回经会话「日志替换」端口 |

### 3.2 编排缝（③ 使用）

| 缝 | 职责 |
|----|------|
| `SubagentAdaptor` | 委派子 Agent |
| `TeamAdaptor` | Team preset 图 |
| `LongTaskAdaptor` | 长程状态机 |
| `PromptAssembly` | Section 组装 |
| `ReasoningPresenter` | reasoning 可见/持久化/是否回灌 |
| `CommandDispatcher` | Slash 等命令面 |

### 3.3 路由缝（④）

| 缝 | 职责 |
|----|------|
| `ThreadRouter` | 当前子会话 / 是否建议新枝（默认贴当前） |
| `FlowRouter` | Agentic Loop vs 某 Team preset |
| `ModelRouter` | 输出 providerId（默认透传；不智能分类） |

---

## 4. 内核三件（冻住）

1. **主体 + 命名空间**：人/Agent × Workspace（及将来租户）；个人知识默认仅本人。  
2. **能力总线**：syscall 注册/分发；入口只负责调用 Policy+卫兵，不承载业务。  
3. **会话设施**：事实日志 + Inbox/claim + 主/子关系；禁明文 secret；提供日志替换端口给 Compaction。

**不进内核**：Loop、Tool 实现、MCP、UI、Skill 文件、Router 策略正文。

---

## 5. 双真相域

| 域 | 内容 |
|----|------|
| 会话日志 | 对话 + Agent 工具（+ reasoning/plan 事件）；模型可见 ⟺ 可还原 |
| AuditSink | 人手 REST/终端等宿主操作审计；企业导出必含 |
| ProjectionBus | 不是真相 |

---

## 6. 硬规矩（实现红线）

1. 禁止编排器旁路拼消息（必经 Inbox/claim）。  
2. 禁止 Loop import 具体 Tool 类；工具互引禁止。  
3. 禁止 Orchestrator 巨型系统提示词字符串（经 PromptAssembly）。  
4. TurnContext 显式传递；禁止单例 Tool bind 当跨请求真相。  
5. 压缩/LLM 走总线，受卫兵与 Metering。  
6. 开发可活、固化求稳准效率；dsh 插件不可直接加载。

---

## 7. 代码骨架落点（develop）

```text
os/
  kernel/           ① 端口与不变式
  adaptors/         ② 接口 + 单机默认实现（先空）
  orchestration/    ③ Loop/Command/Prompt…
  routing/          ④ 三 Router
  compose/          开机接线
  README.md         本底板索引
legacy/             v1 只读标本
```

先挂目录与说明，再填实现；**禁止在 legacy/ 加功能**。

---

## 8. 显式债务与待裁决（企业评审）

**类比诚实度**：当前内核 = syscall 表 + 事件日志 + 卫兵（journal-first），**不是**完整 OS。以下是欠账，不许靠 OS 类比暗示已具备：

| 债务 | 说明 |
|------|------|
| 调度公平 / 优先级队列 | 多租户 Agent 竞争预算与执行队列时的公平性 |
| Agent 资源隔离边界 | 一个 Agent 失控不得饿死他人（现有超时/取消只是部分覆盖） |
| 运行中能力撤销 | 会话中途吊销工具授权，已发 syscall 如何处置 |
| 日志 tamper-evidence | 哈希链防篡改，审计可信的前提 |
| 投影 per-viewer ACL 细则 | 缝上已声明（§3.1），实现细则待定 |

**待裁决（Open，未决不动）**：append-only 会话日志 vs 删除权（GDPR/个保法）——候选 crypto-shredding（按租户密钥加密日志段，删租户=销毁密钥）。未裁决前不得向企业声称合规。
