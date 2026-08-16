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

## 0.5 CC 对账严苛补洞（2026-08-16 第四轮，已裁进正文与 ADR-016）

> 全量论证见 [`claude-code-reference.md`](./claude-code-reference.md) §7。裁决记录：ADR-016「CC 源码对账落位」。

| 洞 | 裁决 | 落点 |
|----|------|------|
| `llm.*` 字面断言会被 provider 合法变形打爆 | 断言 = `derive(log) ∘ normalize == sent`；normalize 版本化纯函数、版本落日志 | §6.7 |
| syscall 注册序不定 → 缓存键/断言重启即漂 | 注册表确定性规范序，内核不变量 | §4-2 |
| 多卫兵组合未定 | deny > ask > allow 格；allow 压不过 deny/ask；熔断作用域参数化 | §2 |
| Inbox 只有 claim，无优先级/抢占 | priority now/next/later；now 级抢占当前流 | §3.1 InboxClaim |
| replaceRange 跨 seed 边界未定 | fork 携带全部替换记账；区间禁伸进种子区 | §9 |
| 取消传播拓扑未定 | 沿父子边；后台子代理不挂父取消 | §3.2 SubagentAdaptor |
| API 错误归哪份真相 | 模型须可见的错误=会话事件；运维错误=AuditSink | §5 |
| 审批「记住」中间态 | 拒绝：事前声明走 Policy 配置面，会话中授予严格单次 | §3.1 Policy |
| maintenance 无上限、latch 无机制 | 强制上限 + 水位快照重放 | §3.2 LoopRuntime |
| Metering 拦截面 | 单机：turn 前 + max_tokens 兜底（有意取舍） | §3.1 Metering |
| Command 端口三型违反分层 | 端口两型；UI 命令=⑤ 注册 local 处理器 | §3.2 CommandDispatcher |

## 0.6 Pi 对账落位（2026-08-16 第五轮，已裁进正文与 ADR-016 第五轮）

> 全量论证见 [`pi-reference.md`](./pi-reference.md) §5。

| 洞 | 裁决 | 落点 |
|----|------|------|
| 「会话设施=事实日志」未说破可变状态归属 | 三 store：entries / registers（覆盖写、恢复点查）/ ledger；no third place | §4-3 |
| 配置类状态可能混入事件词汇表 | 配置与编排禁入词汇表（长期闸门）；现 8 类已合规 | §4-3 |
| Policy 可能被误读为隔离边界 | Policy=授权、Execution/Sandbox=隔离，分工写明 | §3.1 Execution |
| 底板规范先行有漂移风险（Pi 2941 行 spec 对 796 行实现） | 裁决限期落码；两轮未落码标「悬置」 | §6-9 |

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
**卫兵组合代数**（ADR-016 第四轮）：多卫兵/多 hook 决策聚合 **deny > ask > allow**；任何 allow（含卫兵/编排器批准）不得越过 Policy 的 deny/ask（「批准压不过拒绝」）；组件异常规范化为 deny。熔断卫兵作用域参数化（per syscall/provider/principal），禁全局单例。

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
| `InboxClaim` | claim 锁/租约；消息带 priority（now/next/later），now 级运行中到达即抢占当前流（合成闭合后让位，排队消息即上下文） |
| `Execution` | fs/shell 等执行世界；携带 `SandboxPolicy`（mode+workspaceRoot+sessionId），OS 级沙箱链在 spawn 点执行；`full\|partial` 诚实度 + 功能性 probe；**禁静默未沙箱直通**（Java 选型另立项）。**分工**：Policy=授权（进程内判定），Execution/Sandbox=隔离（OS 级）——隔离边界只在此缝，Policy 永不冒充隔离 |
| `LlmProvider` | 模型流式调用实现 |
| `Tool`* | 各工具插头（彼此禁止互引） |
| `McpBridge` | 外挂 MCP 工具桥 |
| `Policy` / `ApprovalStore` | 放行/拒绝/审批。返回值**封闭 union**（allow/deny/ask），词汇表外一律规范化为拒绝（fail-closed，禁异常穿透）；审批 = `asked/decided` 事件对落日志 + 必须 open turn 内 + 许可**严格单次**（不发放长期能力→结构上消灭撤销问题）；审批证据须持久，单机默认 SQLite，禁内存。**界线=事前声明 vs 会话中授予**：事前声明式规则走 Policy 配置面；拒绝运行中交互产生的记住型规则（ADR-016 第四轮，理由记账见该条）| `AuditSink` | 人手等审计真相 |
| `Metering` | token/费用/预算门（单机裁决：开 turn 前拦 + provider max_tokens 兜底，不逐消息查；企业多租户再升格逐消息） |
| `KnowledgeSource` | 知识→Section 的唯一内容源接口 |
| `Identity` / `OrgNamespace` / `Secret` | 身份、组织命名空间、密钥。Secret 四细则：配置只放 branded 引用；每次操作重解析禁缓存；解析结果永不进模型可见通道；文档诚实标注「克制品不是边界」 |
| `ProjectionBus` | 多副本/UI **通知**（禁止当真相；下发前按**查看者 ACL** 过滤） |
| `Compaction` | 摘要压缩；调 `llm.*`；**surface 替换代数**——不删事件，摘要带 `surfaceOp{replace,start,end}` + `sourceEventSeqs` 完整覆盖被遮蔽节点；模型读 surface、人类 transcript 读 append-origin（双读者）；工具大结果先确定性剪枝再摘要，均记 shadow 记账 |

### 3.2 编排缝（③ 使用）

| 缝 | 职责 |
|----|------|
| `LoopRuntime` | 三态 `idle\|maintenance\|running`；maintenance 为独占 idle 窗口（后台/定时任务不得与模型 turn 抢执行面）；maintenance 有**强制上限**（超时让位 now 级等待消息）；唤醒 latch = 开窗快照 Inbox 水位、闭窗重放其后到达的 now/next 进 claim 队列 |
| `SubagentAdaptor` | 委派子 Agent；工具集只减不增（**对父 TurnContext 快照的有效集**做减法，禁对全量池重建——防权限上浮）；`delegationDepth` 持久化为**单调下界**（重启不得降级）；取消沿父子边传播，**后台子代理不挂父取消**（显式 kill 才杀），兄弟连坐仅限执行类失败 |
| `TeamAdaptor` | Team preset 图 |
| `LongTaskAdaptor` | 长程状态机；续跑 = **预约-复核**（先持久 checkpoint，预约 `(taskId,revision,round)`，pre-step 前后各验一次，失效拒绝并归还被 claim 消息）；终态写权限来自**消息溯源**（host-attested user 源或精确机器轮次源） |
| `PromptAssembly` | Section 组装；**静态 Section（KV-cache 前缀稳定）/动态 PromptContext（sourced user-role 快照，变化或被遮蔽才重发）分离**；技能目录/正文两段式懒加载（digest 驱动重发）；超预算丢弃出账单（先宽泛后具体 + 显式通知） |
| `ReasoningPresenter` | reasoning 可见/持久化/是否回灌 |
| `CommandDispatcher` | Slash 等命令面；端口只见 `local`/`prompt` 两型，UI 面板命令 = ⑤ 注册的 local 处理器 |

### 3.3 路由缝（④）

| 缝 | 职责 |
|----|------|
| `ThreadRouter` | 当前子会话 / 是否建议新枝（默认贴当前） |
| `FlowRouter` | Agentic Loop vs 某 Team preset |
| `ModelRouter` | 输出 providerId（默认透传；不智能分类） |

---

## 4. 内核三件（冻住）

1. **主体 + 命名空间**：人/Agent × Workspace（及将来租户）；个人知识默认仅本人。  
2. **能力总线**：syscall 注册/分发；入口只负责调用 Policy+卫兵，不承载业务。syscall 注册表输出**确定性规范序**（顺序是缓存键与 `llm.*` config 相等断言的组成部分；禁实现迭代序泄漏——内核不变量）。  
3. **会话设施**：**三 store**（ADR-016 第五轮）——**entries**（append-only 对话事实+审计；surface 替换只在此层；禁明文 secret；提供日志替换端口给 Compaction）+ **registers**（覆盖写可变状态：Inbox/claim 租约、分支 leaf、模型/思考档配置、进行时操作；恢复=点查非重放）+ **ledger**（append-only 用量记账）。「每个载荷恰好属于三者之一，没有第四个地方」；配置与编排禁入事件词汇表。主/子关系挂在 entries。

**不进内核**：Loop、Tool 实现、MCP、UI、Skill 文件、Router 策略正文。

---

## 5. 双真相域

| 域 | 内容 |
|----|------|
| 会话日志 | 对话 + Agent 工具（+ reasoning/plan 事件）；模型可见 ⟺ 可还原 |
| AuditSink | 人手 REST/终端等宿主操作审计；企业导出必含 |
| ProjectionBus | 不是真相 |

**错误事件归属**（ADR-016 第四轮）：需 resume 后模型可见的错误（API/传输失败，续跑须知道先前失败）= **会话事件**（独立事件类型）；纯人看的宿主/运维错误 → AuditSink。

---

## 6. 硬规矩（实现红线）

1. 禁止编排器旁路拼消息（必经 Inbox/claim）。  
2. 禁止 Loop import 具体 Tool 类；工具互引禁止。  
3. 禁止 Orchestrator 巨型系统提示词字符串（经 PromptAssembly）。  
4. TurnContext 显式传递；禁止单例 Tool bind 当跨请求真相。  
5. 压缩/LLM 走总线，受卫兵与 Metering。  
6. 开发可活、固化求稳准效率；dsh 插件不可直接加载。  
7. `llm.*` 入口断言：`derive(log) ∘ normalize == sent` **逐字节相等**（`normalize` 为版本化纯函数、版本号落日志；provider 侧合法变形只准发生在 normalize 内）、config 与 folded header 相等（「模型可见⟺日志可还原」的机器检查，违反即失败可见）。  
8. 取消时为未派发 call 写**合成错误结果**（保 tool_call↔result 配对与 replay 有效）；调度器自身故障**不伪造**结果，只 drain 后抛——哪类失败可补占位、哪类必须诚实缺口，显式分界。
9. **裁决限期落码**（ADR-016 第五轮）：每条新裁决指认落码切片；连续两轮未落码的裁决标「悬置」，悬置裁决不得作为后续裁决的前提。

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
| 运行中能力撤销 | ~~会话中途吊销工具授权~~ **大半关闭**：审批单次许可制（allowed-once）从结构上消灭长期能力发放；残余=沙箱类运行中资源回收（如 dispose 撤销 ACL grant） |
| 日志 tamper-evidence | 哈希链防篡改，审计可信的前提 |
| 投影 per-viewer ACL 细则 | 缝上已声明（§3.1），实现细则待定 |

**待裁决（Open，未决不动）**：append-only 会话日志 vs 删除权（GDPR/个保法）——候选 A crypto-shredding（按租户密钥加密日志段，删租户=销毁密钥）；候选 B 导出侧脱敏、canonical 日志永不重写（dsh telemetry 先例）。未裁决前不得向企业声称合规。

---

## 9. 事件日志立规（dsh 对账）

- `seq = log.length` **强制连续**；append 点做 lossless 校验，坏事件在 append 失败，不在 flush 处。
- 未知事件默认 **required-fail**：无 `ignorable: true` 标记时读者必须拒绝重建，禁静默丢弃（事件词汇演进的兼容规则）。
- 崩溃恢复**补合成闭合**：open turn 补 `turn/end{kind:'interrupted'}`，事件全保留，**不截断日志**。
- fork/resume 写 `end-seed` 边界事件区分种子历史与本生命周期写入（孤儿压缩锁、重开判定都依赖它）。fork 必须携带全部替换/surface 记账（缺携带 = 永久 cache miss）；自身写入的 `replaceRange` 区间**不得伸进种子区**，越界 append 失败。
- 附件 **persist-before-event**：二进制先落内容寻址存储（sha256），事件里只放 opaque 引用，禁 objectURL/base64/临时路径；超大工具输出 **spill** 落盘 + locator + retrievalHint，模型按需取回。
