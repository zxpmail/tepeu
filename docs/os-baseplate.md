# Tepeu OS 底板（实施用）

> **地位**：develop 实施投影。规范以 [`memory/decisions-log.md`](../memory/decisions-log.md) **ADR-016** 为准。  
> **蒸馏**：内核 = 冻结概念 + 不变量；能力 = 总线上的插头。细则在 ADR，此处一行指针。  
> **诚实度**：[`agent-os-gap.md`](./agent-os-gap.md)（当前=本机 Agent OS 骨架可演示；隔离仍是 partial）。v1 规格不是上级文档。  
> **阅读序**：`CONTEXT.md` → 本文件 → [`os-handbook.md`](./os-handbook.md)（独有章）→ ADR-016。  
> **对账存档**：[`archive/reference/`](./archive/reference/)（已吸入 ADR，不是规范）。

沿数据流垂直切片。DONE 由 §8.5 与 conformance 定义。内核不知道 turn。

---

## 1. 洋葱（四环）

```text
⑤ 应用     UI · SSE 投影 · Skill/记忆【资产】· 面板
③ 编排     兜底 Agent · Loop(turn/step) · Team/Subagent/LongTask
           PromptAssembly · ReasoningPresenter · Command(Slash)
           路由三决策（Thread/Flow/Model，默认透传）
② 适配     驱动插头 = syscall 处理器 + 内核必需端口的可换实现
① 内核     主体+命名空间 · 能力总线（入口钩〔Policy+卫兵〕）· 会话三 store
```

**底板 = 内核三件**。**依赖**：外→内；内不依赖外。② 是插头，不是①的父亲。
（④ 路由环已并入 ③；ADR-016 第八轮。）

Inbox/claim = 进场与租约，**不是**调度器。类比诚实度见 §8。

```text
 Agent 态 ≈ 用户态：Loop / Team / Slash / UI
        │  唯一通道 syscall（TurnContext）
 ╔══════╧══════╗
 ║ 能力总线     ║  取消 → 卫兵 → Policy → 分发 → 卫兵 after
 ╠═════════════╣
 ║ Inbox/claim · entries · registers · ledger · 主体×命名空间
 ╚══════╤══════╝
        │  端口 ≈ HAL
 ② 插头：llm.* / execution.* / Store / Claim / Policy / Metering
        │
 外部世界：LLM API · 文件系统 · Shell · SQLite
```

---

## 2. 三条进路（门对称）

碰**能力总线**时一律：〔**Policy + 卫兵**（超时/取消/不变量/**配额限流**）〕→ ②插头。配额按 principal/namespace 限速率、并发、token 硬顶，**入口处拒绝**；Metering 只是事后仪表。
**卫兵组合代数**（ADR-016 第四轮）：多卫兵/多 hook 决策聚合 **deny > ask > allow**；任何 allow（含卫兵/编排器批准）不得越过 Policy 的 deny/ask（「批准压不过拒绝」）；组件异常规范化为 deny。熔断卫兵作用域参数化（per syscall/provider/principal），禁全局单例。

| 路 | 路径 | 事实落点 |
|----|------|----------|
| **对话主路** | 应用→③ Loop/Team→① claim/syscall→② | **会话三 store**（事实入 entries、用量入 ledger） |
| **人手旁路** | 应用 REST/终端→① 总线→②（**跳过③与 Loop**） | **AuditSink**（非会话对话事实） |
| **Slash** | 应用→③ **Command**（必经；不经模型/Loop）→若动宿主再走总线 | 宿主副作用 → AuditSink |

开 **Agent turn 前**：预算硬门——`Metering` 供数、与 Policy 协作拦截（超限则不得 claim/开跑；**Metering 自身不产生裁决**——ADR-016 第七轮调和措辞，此处为唯一正典表述）。

---

## 3. 端口与插头

### 3.1 内核必需端口（内核契约直接依赖，4 个）

| 端口 | 职责 |
|------|------|
| `SessionStore` | 三 store 持久化（entries / registers / ledger） |
| `InboxClaim` | 消息入会话 + claim 租约（priority now/next/later；租约带 TTL；now 级抢占只切流式 chunk 边界） |
| `Policy` / `ApprovalStore` | 封闭 union（allow/deny/ask）判定 + 审批 `asked/decided` 事件对（open turn 内、严格单次、证据持久） |
| `Metering` | 用量供数，与 Policy 协作拦预算 |

细则：ADR-016 第三/四/五/七轮。

### 3.2 syscall 命名族（注册进总线即存在；内核不感知类型）

- `llm.*`：受 §6-6 逐字节断言约束（一切 llm.* 必过 Metering 门：turn 内随 turn，maintenance 按窗口预算条目）。
- `execution.*`：携带 `SandboxPolicy`（OS 级隔离在 spawn 点，`full|partial` 如实报告，禁静默直通）。**分工**：Policy=授权（进程内），Sandbox=隔离——隔离边界只在此，Policy 永不冒充隔离。
- 其余（fs/shell/mcp/tool/compaction/…）：普通注册处理器；行为契约随各切片设计落 ADR。

### 3.3 ③ 编排缝（一行 + 指针）

`LoopRuntime`（三态 idle|maintenance|running；维护窗强制上限与 latch；抢占边界）· `SubagentAdaptor`（工具对父有效集只减不增；delegationDepth 单调下界；取消拓扑）· `TeamAdaptor`（preset 图）· `LongTaskAdaptor`（预约-复核；终态消息溯源）· `PromptAssembly`（静态 Section/动态 PromptContext 分离；技能两段懒加载；超预算丢弃出账单）· `ReasoningPresenter` · `CommandDispatcher`（local/prompt 两型）· **路由三决策**（ThreadRouter 贴当前 / FlowRouter 选 Loop-vs-preset / ModelRouter 透传选型）。
细则：ADR-016 第一/三/四轮。对账存档见 [`archive/reference/`](./archive/reference/)。

### 3.4 ⑤/② 支撑服务（非内核契约）

`ProjectionBus`（通知非真相；下发前按查看者 ACL 过滤）· `Secret`（branded 引用/重解析禁缓存/结果不进模型通道/克制品不是边界）· `Identity`/`OrgNamespace` · `KnowledgeSource`（知识→Section 唯一内容源）。

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

## 6. 硬规矩（实现红线，7 条）

1. 禁止编排器旁路拼消息（必经 Inbox/claim）。  
2. 禁止 Loop import 具体 Tool 类；工具互引禁止。  
3. 禁止 Orchestrator 巨型系统提示词字符串（经 PromptAssembly）。  
4. TurnContext 显式传递；禁止单例 Tool bind 当跨请求真相。  
5. 压缩/LLM 走总线，受卫兵与 Metering。  
6. `llm.*` 入口断言：`derive(log) ∘ normalize == sent` **逐字节相等**（`normalize` 为版本化纯函数、版本号落日志；provider 侧合法变形只准发生在 normalize 内）、config 与 folded header 相等（「模型可见⟺日志可还原」的机器检查，违反即失败可见）。  
7. 取消时为未派发 call 写**合成错误结果**（保 tool_call↔result 配对与 replay 有效）；调度器自身故障**不伪造**结果，只 drain 后抛——哪类失败可补占位、哪类必须诚实缺口，显式分界。

（dsh 双模原则与裁决限期落码已移出红线：前者见 ADR-016；后者见 §8.5 头部。）

---

## 7. 代码骨架落点（develop）

洋葱是依赖方向，不是 jar。物理单元 = **组件**（一件事一个 Maven 模块；默认实现跟组件走）。组件 ≠ 插件。

```text
os/
  identity/         词汇：谁 / 在哪 / 哪次会话 / TurnContext
  syscall/          词汇：调用信封 + Usage
  conformance/      测试 harness
  session/          组件：会话三 store + Metering 端口
  policy/           组件：Policy + 审批
  bus/              组件：能力总线 + 卫兵
  llm/              组件：llm.* 派生式断言 + fake / HTTP 薄壳（compose 默认 fake）
  loop/             组件：③ SessionLoop（claim / 有界 turn / 工具 / 完成门）
  orchestration/    组件：PromptAssembly + CommandDispatcher（Team 未落）
  execution/        组件：工作区囚笼 + Job Object / bwrap（隔离 partial）
  compose/          组件：开机接线
  README.md         本底板索引
legacy/             v1 只读标本
```

调试：`mvn -f os/pom.xml test`；单模块 `-pl session` / `policy` / `bus` / `llm` / `loop` / `orchestration` / `execution` / `compose`。禁止在 `legacy/` 加功能。

---

## 8. 显式债务与待裁决

**类比诚实度**：当前内核 = syscall 表 + 事件日志 + 卫兵（journal-first），**不是**完整 OS。以下是欠账，不许靠 OS 类比暗示已具备：

| 债务 | 说明 |
|------|------|
| 调度公平 / 优先级队列 | 多租户 Agent 竞争预算与执行队列时的公平性 |
| Agent 资源隔离边界 | 一个 Agent 失控不得饿死他人（现有超时/取消只是部分覆盖） |
| 运行中能力撤销 | **大半关闭**：审批单次许可制从结构上消灭长期能力发放；残余=沙箱类运行中资源回收 |
| 日志 tamper-evidence | 哈希链防篡改，审计可信的前提 |
| 投影 per-viewer ACL 细则 | 已声明（§3.4），实现细则待定 |

**待裁决（Open，未决不动）**：append-only 会话日志 vs 删除权（GDPR/个保法）——候选 A crypto-shredding；候选 B 导出侧脱敏、canonical 日志永不重写。未裁决前不得向企业声称合规。

### 8.5 挂账与悬置清单（裁决债 ledger，ADR-016 第七轮设立）

> 对账轮产出但「归入某切片顺路兑现」的项集中登记；随对应切片落码后销账。此表就是 C3 纪律的账本——**裁决不许只活在参照文档里**。
> **对账冻结**（第七轮）：冻结 = **不新增 ADR 裁决与底板红线变更**（清点/吸收/审计类不受限）；解除条件 = ② conformance 切片合入。**（2026-08-16 已解除：② conformance 切片合入 develop。）**

| 项 | 来源 | 归属切片 | 状态 |
|----|------|----------|------|
| `SyscallResult` 基座计量槽位（usage/latency；两参照独立收敛） | TriniOS + AIOS C5 | `llm.*` 断言 / Metering | ✅ 已落码（第九轮：槽位 + `Usage` inclusive 双轨；cost 细化随 `llm.*`） |
| ledger 写入协议 + read-your-writes barrier 超时语义（倾向 fail-closed） | AIOS C6 + OpenCode + gnex3（usage 只增禁负禁篡改；写失败 tepeu 倾向 fail-closed——预算门供数源不可丢账，gnex3 降级放行记备选） | ledger / Metering | ✅ 单写者 SQLite WAL：同连接 record 后 readAll 可见；close 后再写 fail-closed（第二十轮）。多副本 barrier/超时仍挂账 |
| DoomLoop 熔断（同工具同输入 N 次→ask）入卫兵类型 | OpenCode + gnex3（形状：指纹归一化剥时间戳/随机参数 + NUDGE 模型可见） | ③ Loop | ◐ 熔断+NUDGE 已落（第十七轮）；卫兵 verdict 已落（第十九轮）；DoomLoop 尚未把第三刀改成 NEED_APPROVAL |
| CC §3 吸收项（终态转移表/恢复分级/分区并发/熔断/递减收益停机） | CC | ③ Loop 端口设计说明 | 挂账 |
| 工具经总线组合调用另一工具是否算互引 | 审计 O5 | Tool 契约 | 挂账 |
| 子代理审批 `asked/decided` 落哪个会话（父/子/delegationId） | 审计 O6 | SubagentAdaptor | 挂账 |
| §6-6「config 相等」的外延（cache_control 布点须入 normalize 确定性） | 审计 O8 | `llm.*` 断言 | 挂账 |
| `SYSTEM_NOTE` 从词汇表砍除（语义未定义=垃圾抽屉） | 审计 O11 + 第八轮 | conformance（manifest 钉 8 类，含 END_SEED） | ✅ 已落码（第八轮钉可见类；第十九轮 END_SEED → 8） |
| 多设备同步 fencing（单写者→fencing token/steal） | OpenCode C4 | 远期（多副本） | 挂账 |
| 抢占边界三参照收敛规则 | AIOS C4 | — | ✅ 已落 §3.1/§3.3 |
| **审批端口形态**：`PolicyHook` 同步 evaluate 无法表达 ask（现唯一实现 ASK≡DENY） | 代码审计二 C1 | kernel 端口演化 | ✅ 已落码（第九轮：`ApprovalStore` 同步重试式 ask + 严格单次 consume） |
| **未装配 Policy 的默认语义**（现默认 ALLOW=fail-open；须裁 deny/显式 NoPolicy/ask） | 代码审计二 C2 | kernel 端口演化 | ✅ 已落码（第九轮：fail-closed，未装配 Policy/审批通道即拒） |
| **失败双通道契约**（异常通道 catch 方与日志归属未定义） | 代码审计二 C3 | kernel 端口演化 + ③ Loop | ✅ 通道已定（第九轮）；落账归属本刀裁：③ 写 entries，总线不自动落；拦截失败不写 ASSISTANT、不 completed |
| ~~总线是否自动落 TOOL_CALL/TOOL_RESULT 事件~~ | ~~③ 编排落 entries~~ | ✅ 已裁并落码（第十二轮归属 ③；第十三轮：先落 CALL 再 invoke，拦截合成 RESULT） |
| ~~registers 是否建通用 RegisterStore 端口~~ | ~~随 ③ Loop~~ | ✅ 已裁（第十二轮：`SessionRegisters` 挂在 session，不另开 jar；快照锁存仍挂账） |
| ~~SessionLoop 会话串行域~~ | ~~倾向阻塞式+显式门~~ | ✅ 已裁（第十二轮：阻塞 `SessionLoop.run` + `loop.state` 门；非事件驱动） |
| **LlmProvider 实现选型**：自研双协议族优先 vs Spring AI 驱动 | 选型问询 + 五参照 + gnex3 | `llm.*` 断言切片 | ✅ 契约+fake 已落码；Anthropic HTTP 薄壳已落（第十四轮）；OpenAI HTTP 薄壳已落（第十五轮，JDK HttpClient，离线 stub） |
| **timer 基础设施**：per-domain PQ 起步，>万级升时间轮；解「死租约可回收」 | Netty §2.1 | ① session | 挂账 |
| **采样泄漏检测**（弱引用+GC 探测+1/N 采样） | Netty §2.2 | 生命周期审计切片 | 挂账 |
| `llm.*` 重试协议：可重试分类封闭集 + requestId 幂等（流式从零重放）+ 熔断开路禁重试 | gnex3 | llm transport 切片 | 挂账 |
| registers 终态单调：终态值禁被晚到/重放旧值回退 | gnex3 | registers / ③ Loop | 挂账 |
| TIMED_OUT 一等结果：超时产成对 TOOL_RESULT 标记（禁静默成功/禁悬挂/禁自动重试） | gnex3 | Tool 契约 | 挂账 |
| 修剪证物保护 + spill 容量纪律（TTL/单条上限/证物类不可修剪） | gnex3 | Compaction | 挂账 |
| 注册表/工具集版本化快照锁存：增量=新版本，in-flight turn 锁存旧快照 | gnex3 | ③/RegisterStore（同裁） | 挂账 |

**设计-代码 drift 现状**（conformance 切片的用例来源；消除即销账）：

| drift | 现状位置 | 消除切片 |
|-------|----------|----------|
| ~~租约 TTL 记而不执~~ | ~~`InMemorySession`~~ | ✅ 已落码（时钟注入 + 惰性回收 + conformance 用例，2026-08-16） |
| ~~syscall 注册表无确定性规范序~~ | ~~`InMemoryCapabilityBus.handlers`~~ | ✅ 已落码（ConcurrentSkipListMap + `registeredSyscalls()` + conformance，2026-08-22） |
| ~~`GuardHook` 无 verdict 载体（组合代数无实现前提）~~ | ~~`InMemoryCapabilityBus`~~ | ✅ 已落码（第十九轮：`before` 返回 PolicyVerdict；deny>ask>allow；ASK 走 ApprovalStore） |
| ~~`InboxMessage` 无 priority（now/next/later 未落）~~ | ~~`InMemorySession`~~ | ✅ 已落码（第九轮：`Priority` 入签名 + 领取序用例） |
| ~~ledger store 零代码（三 store 之一，内核必需端口 Metering 同缺）~~ | ~~kernel session 包~~ | ✅ 已落码（第九轮：`SessionLedger`/`LedgerEntry`/`Usage` + `Metering` 端口 + conformance 2 用例） |
| ~~fork 未实现（`forkFromEventId` 恒 empty；种子区校验缺位）~~ | ~~`InMemorySession`~~ | ✅ 已落码（第十九轮：`SessionStore.fork` + END_SEED + 禁伸种子区） |
| ~~`SyscallResult` 无 usage/latency 计量槽位~~ | ~~kernel bus~~ | ✅ 已落码（第九轮：基座字段 + `Usage` 双轨） |

---

## 9. 事件日志立规

- **seq 由 append 点分配**，本日志内严格单调连续且唯一（无种子日志等值于 `log.length`；fork 场景种子事件**保留原 seq**、自身写入从 end-seed 后**续接同一单调空间**——ADR-016 第七轮）；append 点做 lossless 校验，坏事件在 append 失败，不在 flush 处。
- 未知事件默认 **required-fail**：无 `ignorable: true` 标记时读者必须拒绝重建，禁静默丢弃。
- **词汇表机制**（ADR-016 第六轮，三件）：per-type 版本化（schema 变更 bump `version`，持久化键 `type.version`，旧版本留作历史 decode，发布走 latest）；显式 manifest（重复定义启动失败）；**数量钉死测试**（新增事件必须显式改测试）。
- 崩溃恢复**补合成闭合**：`Session.recover()` 为未配对 `TOOL_CALL` 补 `TOOL_RESULT{INTERRUPTED}`，不截断；`loop.state` 点查回 IDLE。内核不知 turn，不写 `turn/end` 事件。
- fork/resume 写 `end-seed` 边界事件区分种子历史与自身写入。fork 必须携带全部替换/surface 记账；自身写入的 `replaceRange` 区间**不得伸进种子区**，越界 append 失败。
- 附件 **persist-before-event**：二进制先落内容寻址存储（sha256），事件里只放 opaque 引用；超大工具输出 **spill** 落盘 + locator + retrievalHint。
- `SYSTEM_NOTE` 已砍除（ADR-016 第八轮）：语义未定义者不入词汇表；需要时按 manifest 流程显式加回。
