# Grok Bot 0.18 × Tepeu（对账与启发）

> **地位**：存档备查。机制可扫，**不是**内核规范，**不**因本文新增 ADR / 开 jar。  
> **来源**：本地 `C:\grok-bot-0.18-reconstructed-main`（非官方重建；pinned 公开 macOS 0.18.0，`com.anysphere.sand`）。证据锚：该仓 `README.md` / `PROVENANCE.md` / `source/host/` / `source/packages/`。  
> **日期**：2026-08-29。  
> **诚实度**：重建不是 Anysphere 原 monorepo；模块名由编译产物推断。Inference Router / Local Docker / Router Settings **是重建新增**，勿当 0.18 原版行为。

---

## 0. 总判断

Grok Bot 是 Cursor 族的 **Computer-Use 桌面**（Sand）：人看着一台隔离电脑（远程 box / VNC），Agent 在里面干活。产品层 = **⑤**，和 Terax / OpenCode 同族。

它和 Terax 的差别：host 里有一套**真的在跑的 Agent 运行时**——35 槽 extension DAG、transcript WAL、conversation blob DAG、always/never/ask + `directionEpoch`、box/external 双执行面。这些机制更接近 [claude-code-reference.md](./claude-code-reference.md) 的「实现事故册」，不是工作台交互扫一眼就够。

| | Grok Bot 0.18（重建） | Tepeu `os/` |
|--|----------------------|-------------|
| 层 | ⑤ 桌面 + host 进程图 + 远程 box | ①–③ 内核 + compose；⑤ 仅 CLI |
| 对象 | 人用 Computer-Use 助手 | 业务 Agent 运行时 OS |
| 完成 | 只有 `SendMessage` 工具到达用户可见面 | 证据在 entries，宣判在 ③ `CompletionGate` |
| 持久化 | JSONL+WAL 转录 + SQLite blob DAG + 每 agent worker | `Persist` 访问口；引擎 `PersistEngine`；三 store |
| 策略 | 全局 always/never/ask + hook 权限步 | Policy 封闭 union + 审批 `asked/decided` + argsDigest |
| 隔离 | 远程 box（默认）/ 本机 external；重建可加本地 Docker | execution **partial**（Job / bwrap） |
| LLM | 原版 Cursor session；重建加多供应商路由 | 自研 `llm.*` + derive 断言 |

**与 `os/` 不同线的部分**：Electron、utilityProcess coordinator、VNC、35 个产品 extension、远程 pod。  
**可当实现参照的部分**：WAL 两阶段写、blob 可达性 GC、审批世代、双执行面、完成通道唯一、记忆平面与转录分离。

---

## 1. 它实际怎么叠

```text
shipped renderer
    → preload RPC（window.desktop / coordinatorPort）
    → Electron main（settings / secrets / box connector）
    → coordinator（utilityProcess：SSE / OAuth / 流）
    → host extensions（35 槽 DAG）
         transcript → TurnExecutor（单绑）→ AnysphereAgent
    → box-exec-daemon（隔离电脑）∥ local-exec-daemon（用户本机，过审批）
```

对照洋葱：renderer/main = ⑤；coordinator = ⑤ 进程隔离（**不是**调度器）；host runner / transcript / session = ③+① 挤在一个 Node 进程；box daemon = ② execution 插头。

35 槽是**产品能力表**，不是 Tepeu 组件清单。`transcript` 一个 extension 里塞了 SendPipeline / TurnRuntime / RunnerRegistry / SessionRuntime——正好说明「按产品功能切 jar」会把 ① 和 ③ 焊死。

---

## 2. 对账表（tepeu 已冻 × Grok 同构）

| # | Tepeu（底板 / ADR-016） | Grok Bot | 判定 |
|---|-------------------------|----------|------|
| 1 | 完成权唯一：entries + `CompletionGate` | 纯 assistant 文本**不**进用户面；只有 `SendMessage` 到达 | ✅ 同形：**完成通道唯一**。⚔️ 落点不同——Grok 用产品工具当门，Tepeu 用账本门。勿抄「禁止纯文本回复」 |
| 2 | 三 store：entries / registers / ledger | JSONL 转录（append+WAL）∥ SQLite blob DAG（conversation state）∥ memory `profile.md` | ✅ 验证「没有第四个真相域」。记忆是 ⑤ 资产，不是第四 store |
| 3 | Policy `allow/deny/ask` + 审批单次 | 全局 `always/never/ask`；决议 `allow-once/deny/always/never` | ✅ 词汇同构 |
| 4 | 审批绑 `argsDigest` | `directionEpoch` 每 turn 递增，过期卡片作废 | ✅ 同一病：陈旧批准。Grok 用世代，Tepeu 用参数摘要——可互补，不互替 |
| 5 | Policy ≠ Sandbox；隔离在 spawn | `Shell/Read`（box）vs `ExternalShell/ExternalRead`（本机）两套工具名 | ✅ 验证双面。Tepeu 用 syscall 名族 + `SandboxPolicy`，不必再开一套 External* 工具 |
| 6 | 子代理工具只减不增 | Task/subagent 默认 **空工具集**，再按配置 fence | ✅ 比「从父集减法」更狠（默认零）。Tepeu 已冻「父有效集只减」——默认零是产品策略，不是内核不变量 |
| 7 | Compaction 不删事件；`surfaceEpoch` | mirror 是 append+checkpoint 派生，不原地改 JSONL；压缩走 `self-summary` + `preCompact` hook | ✅ 方向一致 |
| 8 | Observation.shape / redact | `PrivacyMode × DataClassification` 一等类型；redacted proto 视图 | ✅ 落点已有：`Observation.view`。勿把 80+ redacted proto 搬进 `os/` |
| 9 | Inbox/claim；内核无调度器 | coordinator 只管网关/流/OAuth；turn 在 host | ✅ 反证：多进程 ≠ 调度器 |
| 10 | compose 接线；组件不建连、不关库 | `defineHostExtension` + 拓扑启动 + 逆序 teardown；TurnExecutor **禁止 double-bind** | ✅ compose 纪律同构。35 槽本身不吸收 |
| 11 | DoomLoop / 序列熔断 | loop-detection **fail-open**（500ms）+ 注入 reminder | ⚔️ UX 可扫；完成/停机仍走 Tepeu 门，不要 fail-open 盖过账本 |
| 12 | Secret：compose 不读密钥 | Electron keychain → host applier → box env | ✅ 同向（应用层持钥） |
| 13 | `llm.*` 逐字节断言 | 无对等物；重建 router 更是实验 | ⚠️ 反证 Tepeu 更严；不要为了像 Grok 放松断言 |

---

## 3. 新启发（底板未写死；按落点，不立项）

### 3.1 → persist / session（当前刀口最值钱）

1. **WAL 两阶段**：`prepareCheckpoint` 写 `journal-pending.json` → `commitCheckpoint` 才 append JSONL；崩溃重放 pending。Tepeu 现在是 JDBC `tx()`。**不**立刻上 WAL 文件；要记的是：会话事实写入必须有「未提交可见 / 已提交不可丢」分界。SQLite 事务已经给一半；缺的是**跨文件**（entries + 附件 blob）的同一分界——底板已有 persist-before-event，本文只加强这条，不新开引擎。
2. **转录面 ≠ 状态面**：JSONL 给人/搜索/回放；blob DAG 给 Agent 可 GC 的 conversation 根。Tepeu 用 entries 当真相、投影当通知——对。Grok 用第二套存储形状服务 UI/搜索，等价于 Projection，**不是**第二份权威。
3. **每 agent 一个 SQLite worker**（池上限 64，idle 驱逐）：多会话并发不抢同一 Node SQLite 连接。Tepeu 本机单写者已钉；多副本 fencing 仍挂账。这篇只说明「隔离连接」是 persist 问题不是调度问题——**勿**为此开 worker 池 jar。
4. **blob 从 retained root 做可达性 GC**：压缩/摘要换根后，不可达 blob 可收。Tepeu spill/locator 已挂；GC 策略未写。可进 persist 远期，不进本轮。

### 3.2 → ③ Loop / 完成门

1. **用户可见出口唯一**：Grok 把「到达用户」收成一个工具。Tepeu 已把「宣布完成」收成一个门。两边都在防「多处都能说完了」。不要把 `SendMessage` 做成 syscall——那是 ⑤ 交互。
2. **TurnExecutor 单绑**：同一 session 禁止两个 runner。Tepeu claim 租约已覆盖「谁在跑」；可在 Loop conformance 加一条「双 bind 失败可见」，不必新类型。
3. **流式 first-token stall + checkpoint resume**：③ 恢复路径材料（CC 参照已有终态表）。Grok 是又一处「停在半流」的实现。不插队。

### 3.3 → policy

1. **`directionEpoch`**：turn 一换，未点的 ask 卡作废。Tepeu `argsDigest` 防「同名不同参」；防不了「上轮的卡下轮还在」。若 ⑤ UI 做审批条，卡片必须绑 `surfaceEpoch` 或 turn seq，过期丢弃。现 CLI `/approve` 已校 sessionId——UI 刀再钉世代。
2. **hook 权限步**（shell/MCP/read/subagent/preToolUse）是 Gate 脚本，不是新 Policy 代数。安全系列已说：Gate = 边界脚本。空 jar 不预开。

### 3.4 → ⑤ 记忆 / UI（下一刀痛点，仅扫）

1. **memory 目录**：`profile.md` + `log/*.md` + 可选 dreaming 合成。注入 prompt 的是 recall 视图，不是 raw transcript。对 Tepeu = `KnowledgeSource` → Section，**不是**第四 store，**不是** entries。
2. **notify-bus / Projection**：SSE 推 automation/listener，reconnect + stall watchdog。`ProjectionBus` 已有接口；Grok 的是后端 SSE 产品总线，MQ 升版再对，本骨架不引入 broker。
3. **Computer 可视化**（VNC、forever-box）：⑤ 工作台交互，execution.* ≠ 远程桌面产品。

---

## 4. 可扫（不立项）

| Grok 点 | Tepeu 映射 | 何时才看 |
|---------|------------|----------|
| Extension DAG + 逆序 teardown | compose 装配序 | compose 再加插头时 |
| TurnExecutor 单绑 | claim + Loop 双跑失败可见 | Loop conformance 补刀 |
| WAL pending → commit | persist-before-event；跨附件+entries 同一分界 | persist 崩溃恢复加厚 |
| blob DAG + 根可达 GC | spill/locator；压缩换根 | 记忆/压缩平面 |
| `directionEpoch` | ⑤ 审批条绑 turn/surfaceEpoch | ⑤ UI/SSE |
| box vs external 双工具名 | syscall 名族 + SandboxPolicy | 已对齐，只防混名 |
| `PrivacyMode × classification` | `Observation.view` shape | shape 加档时 |
| loop-detect fail-open + reminder | DoomLoop；reminder 是观测 | 勿让 reminder 当完成 |
| memory `profile.md` | KnowledgeSource 资产 | 记忆平面刀 |
| coordinator 进程 | host 进程边界 | 勿开调度 jar |
| 重建的 evidence-only 纪律 | 诚实度 / gap | 写文档时 |

## 5. 不借

- Electron / utilityProcess / VNC / 远程 pod 进 `os/`
- 把 10 组件拆成 35 个 host extension
- `SendMessage` 当完成门或必经 syscall
- prompt-jsx / JSX 系统提示进 Java `os/`
- Inference Router、Local Docker、Router Settings（重建实验）当原版或内核依据
- 为 notify-bus / wallpaper / teach-recording / forever-box 预开 jar
- 用 Grok 的「默认空工具集」推翻「父有效集只减」——前者是产品默认，后者是不变量
- 把 reconstructed 模块名写成 Anysphere 官方架构

---

## 6. ⭐ 意外发现

1. **完成通道可以是工具，但权威不该是工具。** Grok 用 `SendMessage` 收口用户可见面，说明「多出口」是真病。Tepeu 把宣判放在读账本的门上，比把宣判放进某个 tool 更抗产品改版。
2. **host 的 35 槽是反面教材。** 能力很多，缝很少：`transcript` 一个槽同时拥有发送、turn、runner、session。Tepeu 把 session / loop / compose 拆开是对的；不要因为 Grok「能跑」就焊回去。
3. **重建仓自己的证据纪律**（`PROVENANCE.md`：不可用 typecheck 证明来源；缺口不许编造 UI）比它的 Router 实验更值得学——和 Tepeu「禁止称企业 Agent OS」是同一类诚实。

---

## 7. 已知盲区

- `packages/agent/actions/` 体量大，完整 action FSM 未逐文件复核。
- 模块边界由 asar/符号推断，可能与内部 monorepo 不一致。
- 未跑该仓的 `npm test` / 未启动 app；机制判断来自源码与文档，不是运行时观测。
- Windows 安装包在 LFS 里，本对账未解包对照。

---

## 索引

| 文档 | 关系 |
|------|------|
| [claude-code-reference.md](./claude-code-reference.md) | 同级：运行时事故册；Grok 补「桌面 Computer-Use + WAL/blob」面 |
| [terax-ai.md](./terax-ai.md) | 同族 ⑤ 工作台；Grok 的 host 比 Terax 厚，仍勿当内核切片 |
| [agent-runtime-security-series.md](./agent-runtime-security-series.md) | Gate=脚本；hook 权限步落这里，不新开 jar |
| [os-baseplate.md](../../os-baseplate.md) | 正典；本文只扫 |
| [agent-os-gap.md](../../agent-os-gap.md) | ⑤ UI / 记忆 / fencing 仍是痛点 |

**不**因本文新增 ADR。**不**把 Grok 列入「按它的模式改 Persist / Loop」的施工单。

---

## 8. 对「四条核心」归纳的裁定（2026-08-29）

外部叙述常把重建仓说成：推理路由 + 动态工具 + 多 Agent 工作群 + 本地 Docker。四条**不是同一层东西**，也不能都叫「官方 Grok Bot 的独到之处」。

| 归纳 | 实际归属 | 对 Tepeu |
|------|----------|----------|
| 推理路由器 | **重建新增**（`README.md` 自承；默认仍是 Cursor session） | ⑤ 供应商切换。**不**进 `os/llm`，不放松逐字节断言 |
| 动态工具 / hint 拉取 | **0.18 回收**（`partitionDynamicTools` + `SAND_DYNAMIC_TOOL_HINTS`） | ③ PromptAssembly 已有静/动段 + 技能目录；这是**工具 schema 版**的同一经济学 |
| 多 Agent 工作群 | **0.18 产品面**（长驻 Agent + 群聊 + executor 子代理，两层） | Team/Subagent **未落**；Inbox ≠ 调度器。只扫，不立项 |
| 本地 Docker 沙盒 | **重建新增**（默认仍是远程 box） | execution 已有本机 partial jail。Docker 开关不是内核依据 |
| 混血（TS 运行时 + 闭源 UI） | **重建仓自己的打包策略** | 与 `os/` 无关 |

### 8.1 动态工具（四条里唯一该进 PromptAssembly 笔记的）

证据（重建仓，机制回收自 shipped 规则）：

- 静态白名单 `BASE_STATIC_NATIVE_TOOL_IDENTIFIERS` **正好 18 个** id（`exclude-tools.ts`）：含 `READ`/`WRITE`/`SHELL`/`SEND_MESSAGE`/`CREATE_PLAN*` 等，**不是**「只有读写和 Shell」。`ASK_QUESTION` 另强制静态。
- 一行 hint 表 `SAND_DYNAMIC_TOOL_HINTS` **正好 9 条**（`turn-toolset.ts`）：`CLOUD_AGENT`、`SEARCH_PLUGINS`、`AUTHENTICATE_MCP_SERVER`、`COPY_TO_BOX`、`COPY_FROM_BOX`、`REQUEST_BOX_HELP`、`CHECK_SUBAGENT`、`MESSAGE_SUBAGENT`、`STOP_SUBAGENT`。
- 放置：`withDynamicToolPlacement` 给动态工具挂 `contextType: { type: "dynamic", conciseStaticContext: hint }`；模型先见一行，MCP 完整 schema 经 `GetMcpTools` 再取。
- ⚠️ 「单个定义超 12KB」「全量加载使成本 ×10」**源码树里没有**。机制成立，数字当博客推算，不当计量依据。

与已有参照重叠：CC 的 `deferred_tools_delta` / 前缀缓存稳定排序 / 技能两段懒加载；底板 syscall **确定性规范序**（顺序是缓存键）。Grok 多出来的只是：**肥且偶用的 native 工具也走「目录一行 + 按需取 schema」**，不只 MCP/技能。

Tepeu 落点：`PromptAssembly` 静段冻工具前缀；肥 schema 进 DYNAMIC 或 fetch syscall，**不**每轮塞进 `llm.generate` 的静态工具数组。不新开「动态工具」组件。`llm.*` 断言的 config 相等必须包含「本轮实际发给模型的工具集」，否则 hint↔展开会漂。

### 8.2 多 Agent（两层，勿混成「一个工作群」）

| 层 | Grok | Tepeu |
|----|------|-------|
| 长驻队友 | `CreateAgent` / `SendToAgent` / `UpdateAgent`；各有 chat、persona、`memory/`；群聊 `createGroup`；异步投递，回信另开 turn（cue `[agent]`） | ⑤ roster + Inbox 入队。**不是**内核调度。Team 未落，禁止为此开调度切片 |
| 一次性工人 | `Task` + `subagent_type=executor`：无用户 `SendMessage`，向父回报；主 Agent 当 dispatcher | `SubagentAdaptor`（工具只减不增、`delegationDepth`）。未落码 |
| 队列 | `TodoWrite` **允许多条 `in_progress`**（每条独立流一条） | 不是完成门。Todo ≠ `completed`。重试资格仍挂账本 |

`SendToAgent` 与 `SendMessage` 分通道：前者到 Agent/群，后者到用户。这加强「完成/对用户出口唯一」，不证明需要 Agent 消息总线。

Agent **不能**删除同伴（无 delete 工具；人在侧栏删）。创建有副作用、销毁收口在人——和 Tepeu「审批/完成权不放进工具自报」同向。

### 8.3 推理路由与本地 Docker（勿再写成官方创新）

- 路由：Cursor / Claude Code / Codex / OpenRouter、复用本地登录、本地 token 面板——重建实验。原版推理是 Cursor session。
- Docker：loopback、只读挂载——重建替代远程 box 的插头。官方隔离面是 **远程 Sand box**，不是本机容器。
- 「统一体验保留流式/思考/工具」是重建 router 的**目标**，不是已证的内核不变量；跨供应商 tool 语义对齐是 ⑤ 适配成本，Tepeu 用 `llm.*` 断言拦漂移，不靠「路由器保证一致」。

---

<!-- 2026-08-29 §8：裁定外部四条归纳；不立项 -->

---

## 9. 深挖：本轮发给模型的工具集（2026-08-29）

> 只写端口笔记。不改 `os/`，不新开 ADR。**不另挂底板。** tools 序已是 ADR-016 第十轮；落 `tools[]` 时按子列做。

### 9.1 Tepeu 现在实际发什么

| 层 | 现状 |
|----|------|
| 模型怎么「调工具」 | 输出首行 `syscall <name>`（`ToolDirective`）。**不是** HTTP `tool_use`。Loop README 写明：真 HTTP 后再映射 |
| `llm.generate` args | `model` / `family` / `system` / `max_tokens`。**拒绝** `messages`。**没有** `tools` |
| digest | `Observation.view(surface)` → 族投影 → `sha256(wireJson)`。Anthropic v2：单 `cache_control` 挂最后一条 message |
| ledger 复核 | 上笔 digest + family/model/system/maxTokens/throughSeq/surfaceEpoch。压缩换世代则跳过 |
| 总线 | `registeredSyscalls()` 已是规范序（ADR-016 第四/十轮：工具序是缓存键） |
| PromptAssembly | 有 `skillCatalog`（STATIC：name/description/digest）。ADR 写了 `tools_schema` Section，**没有**工厂方法 |
| compose | `PromptAssembly` 常空；`LoopConfig.system` 只转发 |

第十轮已裁：args 只带 config（含 **tools**）；断言 v1 = messages 派生 + **tools 序 = 总线规范序子列** + cache 布点。落码停在 messages + cache 断点。**tools 是已裁未落。**

### 9.2 Grok 实际发什么

发给模型的 `tools[]` = `ToolSetHandle.getStaticTools()`，**不是** `getAllTools()`。

```102:107:C:\grok-bot-0.18-reconstructed-main\source\packages\agent\tools\core.ts
    const hasDynamicMetaTools = tools.some(tool => tool.dynamicToolMetaRole === "discovery")
      && tools.some(tool => tool.dynamicToolMetaRole === "invocation");
    const canExposeDynamicTools = dynamicToolRegistry !== undefined && hasDynamicMetaTools;
    const effectiveDynamicTools = canExposeDynamicTools ? dynamicTools : [];
    const effectiveStaticTools = canExposeDynamicTools ? tools : [...tools, ...dynamicTools];
```

三层集合：

| 集合 | Grok | 谁看见 |
|------|------|--------|
| **offered** | `getStaticTools()` → 推理 API 的 `tools` | 模型 |
| **executable** | `getAllTools()` = static + 已 offload 的 dynamic | 运行时；Policy/审批仍闸 |
| **registered** | 本 turn 能装进 handle 的全名 | 比 offered 大 |

肥工具不进 offered：`withDynamicToolPlacement` 只挂一行 `conciseStaticContext`；完整 schema 经 `GetMcpTools`（`dynamicToolMetaRole: "discovery"`）写进 **transcript**，再经 `CallMcpTool`（`invocation`）执行。

**失败模式（必须防）**：没有 discovery+invocation 这对 meta 工具时，Grok **把 dynamic 全部并回 static**。offered = 全集，前缀缓存碎。Tepeu 上 tool_use 时若只开 `tools[]`、不开「目录/拉取」对，会走出同一条路。

MCP 排序：`stabilizeMcpToolOrder` = 非 MCP 保持相对序，MCP 按名排。与 ADR「内置连续前缀 + 按名排序」同形。

### 9.3 三个集合，不要焊成一个

| 集合 | Tepeu 落点 | 进不进 digest |
|------|------------|---------------|
| **registered** | `bus.registeredSyscalls()` | 否。表变了不自动改本轮 wire |
| **offered** | 本轮实际写入投影 `tools` 的名字（规范序**子列**） | **必须**。进 `prepare` / ledger attrs |
| **executable** | offered ∪ 本轮已拉取、Policy 仍放行的名字 | 否。执行面，不是模型可见面 |

第十轮「tools 序 = 总线规范序子列」说的是 **offered ⊆ registered（序保留）**，不是 offered ≡ registered。Grok 证明：子列可以**短很多**；短才是缓存。

`skillCatalog` 已是 offered 的文本形态（目录进 STATIC system）。`tools_schema` 不该做成「把总线全量 JSON schema 倒进 system」——ADR 表里「随注册变化」若按字面做，等于每加一个 MCP 就碎一次前缀。正确读法：目录随注册变（一行 hint）；**schema 正文**只在 entries 里出现过拉取结果之后，才允许进入 offered 或留在 messages。

### 9.4 展开落哪里

两条，只准选能还原的：

| 法 | 做法 | 缓存 | 断言 |
|----|------|------|------|
| **A. schema 进 messages** | 拉取 syscall 的 `TOOL_RESULT` 带全文；下轮 `tools[]` **不动** | 前缀稳 | 已有 surface digest 覆盖 |
| **B. schema 并入 tools[]** | 下轮 offered 变长 | `tools` 字段必 miss | 必须记新 offered；旧笔按当时 offered 复核 |

Grok 的 `GetMcpTools` 是 **A**（结果进对话）。B 只在「这轮起把某工具升为常驻核心」时用——等于改静态白名单，应罕见、显式。

禁止第三条：编排器把肥 schema 塞进 `LoopConfig.system` 而不落 entries。system 已在 ledger 里，字面能对上，但静段被每轮改写，缓存经济学全毁。那是用断言掩盖组装错误。

### 9.5 将来 tool_use 切片的验收句（现在不必做）

1. `LlmTransport.prepare` 增加 offered（规范序子列）。`tools` 进 wireJson → 进 digest。无 offered = 今天（文本 `syscall` 行）。
2. `verifyPrevious` 用**当时** offered 重放，不拿当前总线全表。
3. 没有「发现 + 调用」成对 syscall 时，**禁止**把未进目录的肥 schema 写入 offered（防 Grok 的并回 static）。
4. PromptAssembly：`tools_schema` = `skillCatalog` 同形（name / 一行 / digest），STATIC。全文只从 entries 的拉取结果来。
5. Loop 仍不 import Tool 类。offered 是名字列表，从总线规范序过滤，不是对象图。
6. 不新开动态工具组件、不抄 `GetMcpTools` 产品名。发现口是普通 syscall，结果进 entries。

**现在就做会空转**：模型还在写 `syscall ls`，wire 没有 `tools`。三层名字留本文，不进底板。


