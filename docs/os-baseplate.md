# Tepeu OS 底板（实施用）

> **地位**：本文件是 **develop 重写的实施底板**（投影）。规范裁决以 [`memory/decisions-log.md`](../memory/decisions-log.md) **ADR-016** 为准；冲突改本文件对齐 ADR。  
> **图示摘要**：[`kernel-layer.md`](./kernel-layer.md)（更短，勿当第二真相）。

---

## 0. 第三轮严苛补洞（已裁进底板）

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

## 0.7 OpenCode 对账落位（2026-08-16 第六轮，已裁进正文与 ADR-016 第六轮）

> 全量论证见 [`opencode-reference.md`](./opencode-reference.md)。EventV2 同构印证不另立条。

| 洞/候选 | 裁决 | 落点 |
|----|------|------|
| 事件词汇演进只有 required-fail 一条 | 三件：per-type 版本化 + 显式 manifest + 数量钉死测试 | §9；落码=② conformance |
| A2「记住」中间态将来是否可解禁 | 不改裁决，立备注：工具声明+会话内+不跨 session+显式 expiry，届时另裁 | ADR-016 第六轮-2 |

## 0.8 设计审计落位（2026-08-16 第七轮，已裁进正文与 ADR-016 第七轮）

> 六轮累积后的全面审计（矛盾/冗余/遗漏/错误）；E1 表格损坏、E4 抢占边界缺失、M3/M4/M6 措辞与 §5 滞后、O1 挂账清单均**已立即修复**。

| 洞 | 裁决 | 落点 |
|----|------|------|
| 压缩执行位置两裁决冲突（维护服务 vs mid-turn 必须即时压缩） | **双轨**：触发式压缩内联 turn 路径（占该 turn 预算）；主动/后台压缩走 maintenance 窗口（独立预算条目）；不存在无 Metering 门的 `llm.*` 调用。并调和第二轮「仪表」与第三轮「硬门」措辞：配额=卫兵，预算门=Metering 供数与 Policy 协作拦截 | §3.1 Compaction / §2 |
| fork 后 seq 空间未定（`seq=log.length` 与种子事件字面互斥） | 种子保留原 seq；自写从 end-seed 后续接同一单调空间；区间校验同空间、禁伸种子区 | §9 |
| C3「轮」单位未定义且按对账轮读法已违反 | **轮 = 落码切片轮**（合入 develop 的实现切片）；对账轮不计数。核对后处置：**冻结新对账轮直至 ② conformance 切片落地** | §6-9 / §8.5 |
| claim 租约崩溃后死锁未定 | 租约必带 TTL（进程存活+过期回收）；多副本升级 fencing token | §3.1 InboxClaim |

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

开 **Agent turn 前**：预算硬门——`Metering` 供数、与 Policy 协作拦截（超限则不得 claim/开跑；**Metering 自身不产生裁决**——ADR-016 第七轮调和措辞，此处为唯一正典表述）。

---

## 3. 缝清单（显式 · 分类）

### 3.1 驱动插头（②，挂总线或会话端口）

| 缝 | 职责 |
|----|------|
| `SessionStore` | 会话/事件持久化 |
| `InboxClaim` | claim 锁/租约，**租约带 TTL**（单机默认=持有进程存活 + 崩溃后过期可回收；turn 级租约随 turn 终态释放；多副本升级 fencing token，旧持有者写被 fence 拒绝——ADR-016 第七轮）；消息带 priority（now/next/later），now 级运行中到达即抢占当前流（合成闭合后让位，排队消息即上下文） |
| `Execution` | fs/shell 等执行世界；携带 `SandboxPolicy`（mode+workspaceRoot+sessionId），OS 级沙箱链在 spawn 点执行；`full\|partial` 诚实度 + 功能性 probe；**禁静默未沙箱直通**（Java 选型另立项）。**分工**：Policy=授权（进程内判定），Execution/Sandbox=隔离（OS 级）——隔离边界只在此缝，Policy 永不冒充隔离 |
| `LlmProvider` | 模型流式调用实现 |
| `Tool`* | 各工具插头（彼此禁止互引） |
| `McpBridge` | 外挂 MCP 工具桥 |
| `Policy` / `ApprovalStore` | 放行/拒绝/审批。返回值**封闭 union**（allow/deny/ask），词汇表外一律规范化为拒绝（fail-closed，禁异常穿透）；审批 = `asked/decided` 事件对落日志 + 必须 open turn 内 + 许可**严格单次**（不发放长期能力→结构上消灭撤销问题）；审批证据须持久，单机默认 SQLite，禁内存。**界线=事前声明 vs 会话中授予**：事前声明式规则走 Policy 配置面；拒绝运行中交互产生的记住型规则（ADR-016 第四轮，理由记账见该条） |
| `AuditSink` | 人手等审计真相 |
| `Metering` | token/费用/预算门（单机裁决：开 turn 前拦 + provider max_tokens 兜底，不逐消息查；企业多租户再升格逐消息） |
| `KnowledgeSource` | 知识→Section 的唯一内容源接口 |
| `Identity` / `OrgNamespace` / `Secret` | 身份、组织命名空间、密钥。Secret 四细则：配置只放 branded 引用；每次操作重解析禁缓存；解析结果永不进模型可见通道；文档诚实标注「克制品不是边界」 |
| `ProjectionBus` | 多副本/UI **通知**（禁止当真相；下发前按**查看者 ACL** 过滤） |
| `Compaction` | 摘要压缩；调 `llm.*`；**surface 替换代数**——不删事件，摘要带 `surfaceOp{replace,start,end}` + `sourceEventSeqs` 完整覆盖被遮蔽节点；模型读 surface、人类 transcript 读 append-origin（双读者）；工具大结果先确定性剪枝再摘要，均记 shadow 记账 |

### 3.2 编排缝（③ 使用）

| 缝 | 职责 |
|----|------|
| `LoopRuntime` | 三态 `idle\|maintenance\|running`；maintenance 为独占 idle 窗口（后台/定时任务不得与模型 turn 抢执行面）；maintenance 有**强制上限**（超时让位 now 级等待消息）；唤醒 latch = 开窗快照 Inbox 水位、闭窗重放其后到达的 now/next 进 claim 队列。**now 级抢占边界**（AIOS/CC/Pi 三方收敛，ADR-016 第七轮收录）：只切**流式生成的 chunk 边界**；结构化输出与非幂等工具**不可无损切**——只能等完成或整体取消（合成闭合） |
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
3. **会话设施**：**三 store**（ADR-016 第五轮）——**entries**（append-only 对话事实（含审批 `asked/decided` 等模型可见事实；**人手审计归 AuditSink，不在此层**）；surface 替换只在此层；禁明文 secret；提供日志替换端口给 Compaction）+ **registers**（覆盖写可变状态；**恢复=点查非重放**。权威清单（初始，扩充须裁决）：claim/租约、分支 leaf、模型/思考档配置、进行时操作状态、孤儿压缩锁、delegationDepth）+ **ledger**（append-only 用量记账；消耗从 ledger 派生，预算上限属 Metering/Policy 配置面）。「每个载荷恰好属于三者之一，没有第四个地方」；**配置与编排的控制状态禁入事件词汇表**（判据：模型可见的事实进 entries——PLAN_STEP、COMPACTION_CHECKPOINT 属此类；机器控制状态进 registers）。主/子关系挂在 entries。

**不进内核**：Loop、Tool 实现、MCP、UI、Skill 文件、Router 策略正文。

---

## 5. 双真相域

| 域 | 内容 |
|----|------|
| 会话日志（entries） | 对话 + Agent 工具（+ reasoning/plan 事件）；模型可见 ⟺ 可还原 |
| ledger | 用量事实（token/费用）；append-only，是用量真相（第五轮三 store 之一） |
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

### 8.5 挂账与悬置清单（裁决债 ledger，ADR-016 第七轮设立）

> 对账轮产出但「归入某切片顺路兑现」的项集中登记；随对应切片落码后销账。此表就是 C3 纪律的账本——**裁决不许只活在参照文档里**。
> **对账冻结**（第七轮）：冻结 = **不新增 ADR 裁决与底板红线变更**（清点/吸收/审计类文档不受限）；解除条件 = ② conformance 切片合入。

| 项 | 来源 | 归属切片 | 状态 |
|----|------|----------|------|
| `SyscallResult` 基座计量槽位（usage/latency；两参照独立收敛） | TriniOS + AIOS C5 | `llm.*` 断言 / Metering | 挂账 |
| ledger 写入协议 + read-your-writes barrier 超时语义（倾向 fail-closed；OpenCode 双轨不变式为候选答案） | AIOS C6 + OpenCode | ledger / Metering | 挂账 |
| DoomLoop 熔断（同工具同输入 N 次→ask）入卫兵类型 | OpenCode | ③ Loop | 挂账 |
| CC §3 吸收项（终态转移表/恢复分级/分区并发/熔断/递减收益停机） | CC | ③ Loop 端口设计说明 | 挂账 |
| 工具经总线组合调用另一工具是否算互引 | 第七轮审计 O5 | Tool 缝 | 挂账 |
| 子代理审批 `asked/decided` 落哪个会话（父/子/delegationId） | 第七轮审计 O6 | SubagentAdaptor | 挂账 |
| §6-7「config 相等」的外延（cache_control 布点须入 normalize 确定性） | 第七轮审计 O8 | `llm.*` 断言 | 挂账 |
| `SYSTEM_NOTE` 语义定义或从词汇表删除 | 第七轮审计 O11 | 事件最小集 / conformance | 挂账 |
| 多设备同步 fencing（单写者→fencing token/steal） | OpenCode C4 | 远期（多副本） | 挂账 |
| **审批端口形态**：`PolicyHook` 同步 evaluate 无法表达 ask（现唯一实现 ASK≡DENY）；ask-then-wait 需端口演化或 ApprovalStore 独立通道 | 代码审计二 C1 | kernel 端口演化（conformance 后） | 挂账 |
| **未装配 Policy 的默认语义**：现默认 ALLOW（fail-open，与身份陈述抵触）；须裁 deny / 显式 NoPolicy / ask 三选一 | 代码审计二 C2 | kernel 端口演化 | 挂账 |
| **失败双通道契约**：Policy/卫兵=异常通道、handler=结果通道并存；异常通道的 catch 方与日志归属（entries vs AuditSink）未定义 | 代码审计二 C3 | kernel 端口演化 + ③ Loop | 挂账 |
| **timer 基础设施**：租约 TTL/卫兵超时的到期机制——per-domain 优先队列起步，租约量 > 万级再升时间轮（O(1) 惰性取消；到期≠执行，动作投回 turn 执行面）；解「死租约可回收」drift | Netty 参照 §2.1 | ① session（conformance 后） | 挂账 |
| **采样泄漏检测**：租约/spill/寄存器生命周期审计——弱引用+GC 探测、1/N 采样、测试期 PARANOID 生产 SIMPLE | Netty 参照 §2.2 | ①/② 生命周期审计切片 | 挂账 |
| 抢占边界三参照收敛规则 | AIOS C4 | — | ✅ 本轮已落 §3.2 |

**设计-代码 drift 现状**（conformance 切片的用例来源；消除即销账）：

| drift | 现状位置 | 消除切片 |
|-------|----------|----------|
| 租约 TTL 记而不执——同进程持有者异常退出后消息永久卡死（死租约可回收未实现） | `InMemorySession:129` | conformance 用例「死租约可回收」 |
| `GuardHook` 无 verdict 载体（void+抛异常；组合代数 deny>ask>allow 无实现前提） | `InMemoryCapabilityBus` | conformance 用例 / 端口演化 |
| `InboxMessage` 无 priority（now/next/later 未落） | `InMemorySession:115` | Inbox 契约落码 |
| fork 未实现（`forkFromEventId` 恒 empty；种子区校验缺位） | `InMemorySession:64` | ① session fork 切片 |
| `SyscallResult` 无 usage/latency 计量槽位 | kernel bus | 随 AIOS C5 挂账项 |

---

## 9. 事件日志立规（dsh 对账）

- **seq 由 append 点分配**，本日志内严格单调连续且唯一（无种子日志等值于 `log.length`；fork 场景种子事件**保留原 seq**、自身写入从 end-seed 后**续接同一单调空间**——ADR-016 第七轮）；append 点做 lossless 校验，坏事件在 append 失败，不在 flush 处。
- 未知事件默认 **required-fail**：无 `ignorable: true` 标记时读者必须拒绝重建，禁静默丢弃（事件词汇演进的兼容规则）。
- **词汇表机制**（ADR-016 第六轮，三件）：per-type 版本化（schema 变更 bump `version`，持久化键 `type.version`，旧版本留作历史 decode，发布走 latest）；显式 manifest（重复定义启动失败）；**数量钉死测试**（新增事件必须显式改测试）。
- 崩溃恢复**补合成闭合**：open turn 补 `turn/end{kind:'interrupted'}`，事件全保留，**不截断日志**。
- fork/resume 写 `end-seed` 边界事件区分种子历史与本生命周期写入（孤儿压缩锁、重开判定都依赖它）。fork 必须携带全部替换/surface 记账（缺携带 = 永久 cache miss）；自身写入的 `replaceRange` 区间**不得伸进种子区**，越界 append 失败。
- 附件 **persist-before-event**：二进制先落内容寻址存储（sha256），事件里只放 opaque 引用，禁 objectURL/base64/临时路径；超大工具输出 **spill** 落盘 + locator + retrievalHint，模型按需取回。
