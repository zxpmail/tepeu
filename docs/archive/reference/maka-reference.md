# Apache Maka × Tepeu（对账与启发）

> **地位**：存档备查。机制可扫，**不是**内核规范，**不**因本文新增 ADR / 开 jar。  
> **来源**：[apache/maka](https://github.com/apache/maka)（Incubating，Apache-2.0）。主锚：`ARCHITECTURE.md`、`docs/architecture/runtime-core-architecture-draft.md`（2026-08-23）、`llm-compaction-events-log-projection-draft.md`（2026-08-28）、`runtime-resume-architecture.md`、`agent-graph-stream-scheduling-draft.md`、`SECURITY.md`。ASF 提案：[Maka Proposal](https://cwiki.apache.org/confluence/spaces/INCUBATOR/pages/446070971/Maka+Proposal)。  
> **日期**：2026-08-31。  
> **诚实度**：孵化中，格式/CLI 仍会变；未 clone、未跑其测试。§0–§7 以公开架构稿为准；§8 对过 `packages/core` / `packages/runtime` 源文件（GitHub `main` raw，2026-08-31）。Desktop Nightly ≠ ASF 发行。

---

## 0. 总判断

Maka 是 **local-first Agent 工作台 + 单一 Runtime Host**。口号和 Tepeu ① 同线：日志是语义真相；UI / 下一轮模型上下文 / 恢复都是投影；压缩不删事实。

它不是 Java Agent OS。产品层是 Electron Desktop + TUI/CLI + Eval，运行时是 TypeScript + AI SDK。和 Terax 比：Maka **把运行时说清楚了**。和 Grok Bot 比：Maka **没有把重建实验写成官方内核**。和 OpenCode EventV2 比：同属 log-first，Maka 的文档把「完成必须有终态事件」「Resume ≠ Retry」写得更硬。

| | Maka | Tepeu `os/` |
|--|------|-------------|
| 层 | ⑤ 工作台 + 单一 Runtime Host | ①–③ 内核 + compose；⑤ 仅 CLI |
| 对象 | 人在本机指挥 Agent 干活 | 业务 Agent 运行时 OS |
| 真相 | Runtime Event Log（append-only） | entries；registers / ledger 分职责 |
| 完成 | Run 头不能自报完；须有 terminal RuntimeEvent | 证据在 entries，宣判在 ③ `CompletionGate` |
| 压缩 | checkpoint 是有覆盖边界的投影；日志不改写 | `replaceRange` + `surfaceEpoch`；不删事件 |
| 恢复 | Repair / Resume / Reconcile 三分；证不了就 park | 崩溃补合成 `TOOL_RESULT{INTERRUPTED}`；续跑未做产品开关 |
| LLM | AI SDK `streamText` + ModelAdapter | 自研 `llm.*`；`derive ∘ normalize == sent` |
| 隔离 | Seatbelt / bwrap / AppContainer（矩阵有限，缺则 fail-closed） | execution **partial**（Job / bwrap） |
| 多面 | Desktop / TUI / CLI / Eval **都进** Runtime Host | host CLI；无第二套 Runtime |

**与 `os/` 同线、可当实现事故册**：log-first、压缩=投影、完成须终态事实、Resume≠Retry、Policy≠OS 边界、Eval 不拥有执行。  
**不同线、勿搬**：Electron、AI SDK 进 `os/llm`、Agent Graph 当内核调度器、`runtime.sqlite` 一库包办、明文 `credentials.json` 当 Secret 方案、开 `eval/` jar。

---

## 1. 它实际怎么叠

```text
Desktop / TUI / CLI / Bot / Eval
        → Runtime Host（唯一执行权）
        → SessionManager → RuntimeKernel → AgentRun
                → AgentBackend（模型环）+ ToolRuntime
                → Runtime Event Log
                → Context / Session / UI / Recovery 投影

Eval：Experiment → Cells → Attempts → Results
      Maka subject 仍过 Runtime Host；Eval 只拥有实验语义
```

对照洋葱：Host = ⑤ 进程边界 + 部分 compose；Kernel/AgentRun = ③；Event Log = ① entries；ToolRuntime/sandbox = ②。Maka 把「谁拥有执行」收成一个 Host，避免 Desktop 和 CLI 各养一套环——Tepeu 已用 compose 接线、host 注入 Persist/密钥，方向同。

`runtime.sqlite` 同时扛事件、会话、Graph 控制面、用量、Artifact 元数据、Automations。Tepeu 拆 `Persist` 访问口 + 三 store，比这干净。**不要**为了像 Maka 把会话库做成杂物抽屉。

---

## 2. 对账表

| # | Tepeu（底板 / ADR-016） | Maka | 判定 |
|---|-------------------------|------|------|
| 1 | entries 是对话事实；投影不是真相 | `State(t) = Project(RuntimeEvents[0..t], …)`；UI/上下文/恢复都是投影 | ✅ 同线。Maka 把口号写成公式，可当对外解释 |
| 2 | 完成权唯一：`CompletionGate` 读账本 | Run header 不能自报完成；须 terminal RuntimeEvent；后到事件不得把结果改成 completed | ✅ 同形。Maka 多写一句「Stop 之后晚到的 provider 事件」——Loop 收流时用 |
| 3 | 压缩不删事件；`surfaceEpoch` | `HistoryCompactCheckpoint`：coverage + `sourceDigest` + lineage；日志不截断 | ✅。checkpoint 的覆盖边界/摘要比「只 bump 世代」细，压缩刀可扫 |
| 4 | `llm.*`：`derive ∘ normalize == sent` | 明确承认：**不算** bit-exact wire replay；system / tools / 投影策略不在事件里 | ⚔️ Tepeu **更严**。勿用 Maka 这句话放松断言。Maka 证明「只记语义、不记 wire」以后对不齐请求 |
| 5 | 崩溃补合成闭合，不截断 | Repair 旧 Run 终态；缺 TOOL_RESULT ≠ 没跑过；证不了就 park | ✅ 加强：合成 INTERRUPTED 是 Repair，不是 Resume。盲续 = 重复副作用 |
| 6 | Policy ≠ Sandbox | `SECURITY.md`：对对抗模型，**唯一强制边界是 OS**；权限引擎是启发式 UX | ✅ 比多数产品文诚实。已对齐 |
| 7 | 审批单次；`argsDigest` | 权限模式默认 `ask`；出沙箱须批；决定进日志 | ✅ 词汇同。细节未逐文件复核 |
| 8 | Inbox/claim；内核无调度器 | Agent Graph：**日程，不是第二 Runtime**；激活仍回同一 Runtime；admission 恰好一次 | ⚔️ 机制可扫（Team 未落）。**禁止**为此开调度 jar。Graph 自己也说不要第二套环 |
| 9 | 子代理工具只减不增 | 子 Session 复用 Runtime 快照/权限/压缩 | ✅ 方向：孩子不是新宇宙 |
| 10 | compose 不读密钥 | 密钥在 workspace `credentials.json`（0o700/0o600）；renderer 永不回传明文 | ✅ 应用层持钥。明文文件不是要抄的 Secret 形态 |
| 11 | conformance 在测试 | `@maka/eval` 是产品：cell × attempt，最早合法 attempt 为权威 | 💡 评测语义可扫。**勿**开 `os/eval`。conformance ≠ 实验平台 |
| 12 | Loop 有界 `maxSteps` | `maxSteps` 可选；未设则无界，模型自己停 | ⚠️ 反面。Tepeu 有界是对的 |
| 13 | 隔离如实报 partial | Seatbelt / bwrap / AppContainer；覆盖矩阵外 fail-closed，禁静默回落到宿主 | ✅ 同 fail-closed。Windows AppContainer 目前只管文件系统 worker，任意 Bash 不可用——比「声称全隔离」诚实 |

---

## 3. 新启发（不立项）

### 3.1 → session / loop（最值钱）

1. **Turn ≠ Run。** Maka：Turn 是人看见的一轮往来；Run 是一次可结束的执行信封。Tepeu 用 `SessionLoop.run` + `loop.state` 兼了两者。不必立刻拆类型；要防的是「有消息」被说成「执行结束了」。`CompletionGate` 已经挡完成口。文档/CLI 别把 turn 和 run 混称。
2. **第一条终态赢，其后静默 drain。** Maka Kernel：先被接受的 terminal 算数，Backend 流继续排空但不改结果。对 Tepeu：Stop / 超时之后，迟到的 `llm.generate` 成功不得改写 entries 终态。
3. **Resume ≠ Retry。** 恢复三问分开：旧 Run 怎么闭合；每个工具处在哪；能不能安全再开一轮。缺结果有四种解释（没启动 / 没写完 / 写了没入账 / 写完又被改）。一律重试会双写。Tepeu 现合成 INTERRUPTED = Repair。产品级 Safe resume（Maka 默认关，要烧 token）不要当本机默认。
4. **压缩 checkpoint 要带 coverage。** `through {run,turn,event}` + `sourceDigest` + 前一 checkpoint 血统。Tepeu 有 `surfaceEpoch` 和 replace 区间；缺的是「这份摘要覆盖到哪条 seq、源哈希是什么」。压缩加厚时抄覆盖字段，不抄 V3 provider 密文状态。

### 3.2 → llm / observation

1. Maka 自认 wire 不进日志 → 正好反证第十轮断言。system、offered 工具集、normalize 版本必须另记（ledger attrs / prepare digest）。见 [grok-bot-reference.md](./grok-bot-reference.md) §9 的 offered 三层。
2. 每轮 prior history **从日志重投影**，不复用上一轮拼好的 provider messages。与 `Observation.view(surface)` 同形。保持。
3. AI SDK 是 Maka 的传输黑盒。Tepeu 已禁 Spring AI `ChatModel`。不要为了「像 Maka」把 SDK 环引进 `os/llm`。

### 3.3 → ⑤ / eval（只扫）

1. **一个 Host，多面复用。** Desktop 和 CLI 不各养 Agent。Tepeu 将来 UI/SSE 应是 Runtime 客户端，不是第二套 Loop。`ProjectionBus` 已是缝。
2. **Eval 不拥有执行。** 实验单元格不可变；多 attempt 取**最早合法**，操作员不能挑好看的。对 Tepeu：conformance 已是「最早失败即失败」。产品评测平台不进 `os/`。
3. ⑤ UI：从 Turn 分支、工具时间线、失败分类——工作台交互可扫，不进内核。`DESIGN.md` 是设计系统，不是运行时规范。

### 3.4 → execution / policy

1. `SECURITY.md` 的分层可当对外诚实模板：OS 才是边界；权限/脱敏/URL 白名单是启发式。Tepeu gap 文可借句式，不新开安全 jar。
2. 受限剖面下「没有 jail 就失败可见」——Tepeu 已做。Maka 把 PTY / 集成终端划在托管边界**外**，避免把产品终端冒充囚笼。

---

## 4. 可扫 / 不借

| Maka | Tepeu | 何时看 |
|------|--------|--------|
| Log = Runtime；State = Project(log) | entries + Observation + ProjectionBus | 对外解释、文档 |
| terminal 事件封 Run | CompletionGate | 已对齐。现 Loop 同步 invoke，无「Stop 后晚到流」竞态 |
| checkpoint coverage + sourceDigest | surfaceEpoch / replaceRange | 对照留本文。不进底板。切点是本地压缩实现问题 |
| Repair / Resume / Reconcile | 合成闭合 vs 盲续 | 续跑产品开关前翻本文，不进底板 |
| Graph = 日程，激活回同一 Runtime | Team 未落；Inbox/claim | 禁调度 jar |
| Eval 最早合法 attempt | conformance | 勿开 eval 组件 |
| Seatbelt / AppContainer 矩阵 + fail-closed | execution partial | 加厚隔离时 |
| 密钥不进 renderer | compose 不读密钥 | 已对齐 |

**不借**

- Electron / AI SDK / Vercel 风格模型环进 `os/`
- 为 Graph / Eval / Automations / Daily Review 预开 jar
- 把 `runtime.sqlite` 做成唯一杂物库
- 明文凭证文件当 Secret 正典
- 无界 `maxSteps`（模型自己停）
- 默认 Safe resume（重启自动再打模型）
- 用「我们也不做 bit-exact replay」否定 `llm.*` 断言
- 把孵化 Nightly 当成可对标的发行内核

---

## 5. ⭐ 意外发现

1. **Maka 把 Tepeu 已冻的三条写成了可对外讲的句子。** 日志是真相、压缩是投影、完成须终态事实——不是新发明，是同线产品验证。有用的是措辞和失败故事（Stop 后晚到 completed、缺 RESULT 的四种解释），不是新组件。
2. **它比 Tepeu 松的地方，正好是 Tepeu 不该松的。** 不做 wire 断言、可选无界步数、AI SDK 黑盒。当反面标本，不当降级理由。
3. **Graph 章的标题已经帮 Tepeu 挡了一刀。** 「Graph 是日程，不是第二 Runtime」。有人要用 Maka Graph 论证「内核该有调度器」时，用它自己的句子挡回去。

---

## 6. 已知盲区

- §8 已对权限表 / 延迟工具 / loop-gate。未通读 `packages/runtime` 其余（AI SDK 环、Graph、Eval）。
- Resume Phase 3 调和 / Phase 4 Git checkpoint **未实现**（稿自己写的）。
- 未跑 `npm test`，未装 Desktop，未 clone。

---

## 索引

| 文档 | 关系 |
|------|------|
| [opencode-reference.md](./opencode-reference.md) | 同代 log-first；Maka 补完成/恢复措辞 |
| [claude-code-reference.md](./claude-code-reference.md) | 压缩/恢复事故册；Maka checkpoint 覆盖字段可并读 |
| [grok-bot-reference.md](./grok-bot-reference.md) | offered 工具集；Maka 承认 tools 不在事件里 |
| [agent-runtime-security-series.md](./agent-runtime-security-series.md) | Policy≠Sandbox；Maka SECURITY.md 句式可借 |
| [terax-ai.md](./terax-ai.md) | 同族 ⑤；Maka 运行时文档厚得多 |
| [os-baseplate.md](../../os-baseplate.md) | 正典 |

**不**因本文新增 ADR。**不**把 Maka 列进 Persist / Loop 施工单。

---

## 7. 深挖：checkpoint coverage × `surfaceEpoch`（2026-08-31）

> 只写端口笔记。不改 `os/`。**不进底板挂账。** 切点不拆对是 `CompactionWork` 本地实现问题，不是新不变量。

### 7.1 两边现在各记什么

| | Maka `HistoryCompactCheckpoint` | Tepeu 今天 |
|--|----------------------------------|------------|
| 覆盖终点 | `through { runId, turnId, runtimeEventId }` + eventCount | `COMPACTION_CHECKPOINT` attrs：`from` / `to`（seq 闭区间） |
| 源是否还对 | SHA-256 `sourceDigest`，对不上 → `source_hash_mismatch`，不用这份投影 | **无**。只信当时写下的 from/to |
| 血统 | `previousCheckpointId`；滚动 = S(旧摘要, 新挤出的事件) | **无显式血统**。下一次若 prefix 以旧 checkpoint 打头，会把摘要再送进 `llm.generate`，碰巧像滚动 |
| 世代 / 失效 | 校验失败就回落原始日志 | `log.surfaceEpoch`（registers）：`replaceRange` 后 +1；上笔 llm digest 跳过复核 |
| checkpoint 落哪 | **不是** RuntimeEvent。记在 AgentRun 操作账 + 有界投影表 | **是** entries 事件 `COMPACTION_CHECKPOINT`，surface 用它换掉区间 |
| 谁定覆盖 | Runtime。LLM 只写摘要正文，不决定 fold 哪些、能不能用 | 同：`CompactionWork` 选 prefix，LLM 只出 `body` |
| 切边界 | 不切断 partial、不拆 TOOL 对 | `keepLast` 按 **条数** 切。可以切在 CALL 与 RESULT 之间 |

`surfaceEpoch` 和 `sourceDigest` **不是同一个东西**：

- **世代**：告诉 `llm.generate`「投影换了，别拿上笔 digest 对现在的 surface」。防的是 ASSERTION。
- **源哈希**：告诉恢复/滚动「这份摘要声称覆盖 [from,to]，这些事件的字节还是当时那些吗」。防的是用错投影。

append-only 下本机单写者很少「区间内事件被改」。哈希仍有用：fork 丢了替换记账（CC 事故）、损坏的 surface 表、滚动后说不清「新折进去的是哪一段」。

### 7.2 Tepeu 已经对的（别改掉）

1. **checkpoint 进 entries。** 底板：模型可见 ⟺ 日志可还原。`COMPACTION_CHECKPOINT` 是模型看见的事实，进 entries 是对的。不要抄 Maka「checkpoint 不是 RuntimeEvent、另开 AgentRun 账」——那是他们 Run 信封的产品账，Tepeu 没有第二套执行日志。
2. **双读者。** `surface()` 给模型；`readAll()` 给人。Maka 的「日志不截断 + 投影另存」同形。
3. **LLM 不是投影权威。** `from`/`to` 由 `CompactionWork` 定。保持。
4. **`surfaceEpoch` 留在 registers。** 控制态，不是第四 store。不要把世代改成哈希顶替——跳过 digest 复核仍然需要廉价的「换过面」信号。

### 7.3 压缩加厚时该补的（三字段 + 一切）

`replaceRange` 已有 `from`/`to`。加厚只扩 attrs，不新事件类型、不新 jar：

| 字段 | 含义 | 来源 |
|------|------|------|
| `sourceDigest` | 覆盖区间内事件的稳定序列化 SHA-256（seq + type + body，规范序） | Maka coverage 校验 |
| `prev` | 上一份 checkpoint 的 seq；无则空 | Maka `previousCheckpointId` |
| `from` / `to` | 已有 | 保持 |

验收句（现在不必做）：

1. 写 checkpoint 时算 digest；恢复/再次压缩前 `match(from,to,digest)`，失败则 **忽略该投影、用原始区间**，不 ASSERTION、不假装摘要还有效。
2. 滚动：送给压缩模型的是「`prev` 正文 + (旧 `to`, 新 `to`]」，**不要**再把 0..旧 `to` 的原文送进去。今天碰巧靠 surface 上的旧 checkpoint 做到一半，要写成显式规则。
3. **安全前缀**：切点不得落在未配对 `TOOL_CALL`/`TOOL_RESULT` 之间，不得切 `END_SEED` 里。`keepLast` 仍是条数上限，但切点向左收到最近的成对边界。这是 Maka「safe prefix never splits a pair」；Tepeu 现在按条切，是真缺口。
4. 摘要质量门（Goal/Progress 四段、过短拒绝）是产品启发式，**不进内核**。压缩失败今天已抛；不要抄 Maka 的 malformed 指纹 fail-open。
5. **禁止** V3 provider 密文 compact state（绑 connection/model，换模型即废）。和 `llm.*` 族投影冲突，也不进 entries。

### 7.4 和 `llm.*` 断言怎么接

压缩后 surface 变了 → `surfaceEpoch` bump → 上笔 generate 的 digest **故意不对**。这是对的。

加厚后不要改成「用 sourceDigest 重算旧请求」：旧请求看见的是旧 surface，不是「checkpoint + 原文」。世代继续当跳过键。

新请求：`Observation.view(surface)` 已含 checkpoint 正文 + 未覆盖尾巴，digest 照旧盖整份 wire。`sourceDigest` 不进 `prepare`，它校验的是 **投影对日志**，不是 **请求对投影**。

### 7.5 现在不要做

`CompactionWork` + `from`/`to` + epoch 已经能证伪「不删审计、种子区不压」。缺的是校验和成对切点。空加 digest 字段、没有 match、没有滚动规则，只是 attrs 噪音。

等压缩加厚刀一次做：attrs + match + 安全切点。Loop 仍不 import 摘要器实现；LLM 仍只出 body。

---

## 8. 实现抽检：权限 / 延迟工具 / loop-gate（2026-08-31）

> 对着 §6 三个盲区读源码。结论：**没有新吸收项。** 差异记对照，不进 §8.5。

锚点：`packages/core/src/permission.ts`、`permission-profile.ts`、`packages/runtime/src/tool-runtime.ts`（`LOOP_GATE_IDENTICAL_THRESHOLD`、`setGating`）、`src/__tests__/loop-gate.test.ts`、`deferred-guard.test.ts`。

### 8.1 权限：没有「安全 shell」这档

`categorizeBash` **永不**返回 `shell_safe`。注释写明：从字符串证明 shell 安全是不可判定的；漏掉的模式只误标确认理由，不改变 allow-vs-prompt。`shell_safe` 槽位保留，政策 fail-closed。

模式是 `explore` / `ask` / `bypass`；旧值 `execute` 读回折成 `ask`。分类名是 Claude SDK 词（Read/Bash/…）。另有 `PermissionProfile`（read-only / workspace-write / danger-full-access）管沙箱形状，和「要不要问人」不是同一张表。

对 Tepeu：`DefaultRuleMatrix` 已是写盘/进程 ASK、未知 DENY。同方向，不必抄 14 个 category，也不抄 `rememberForTurn`（Tepeu 审批单次 consume）。沙箱形状继续留在 execution，不要和 Policy 焊成一张「profile」。

### 8.2 延迟工具：同 step 陷阱，不是新三层

`setGating({ gatedNames, activeNames })`：名字在目录里、本 step 快照还没有 → impl 不跑，仍写 call/result 对，错误是「先 tool_search，下 step 再调」。未装 gating 时卫兵惰性。`tool_search` 返回的名字**当步不能执行**。

和已挂的 offered / executable 同形。不新开「动态工具」组件。CodeMode 嵌套调用标 `modelVisibility: hidden`、不进回放——**不抄**。Tepeu 是可见 ⟺ 可还原。

### 8.3 loop-gate ≠ DoomLoop

两边阈值都是 3，形状不同：

| | Maka `LOOP_GATE` | Tepeu `DoomLoop` |
|--|------------------|------------------|
| 闸什么 | 连续**失败**的同工具同参 | 日志里连续同指纹 `TOOL_CALL`（成败都算） |
| 成功 / 轮询 | 永不闸；成功清零 | `git status` 连打 3 次也会 ASK |
| 换工具 / 换参 | 清零 | 清零（指纹变了） |
| 作用域 | 进程内 streak；**必须** `resetTurnState()`，turn id 不管 | 扫全 log，跨 turn 也算 |
| 嵌套 | CodeMode 失败不计入 provider streak | 无嵌套执行面 |
| 处置 | 合成错误，impl 不跑 | 卫兵 `NEED_APPROVAL` |

Maka 更偏产品（轮询合法）。Tepeu 更严。这是偏好，不是缺口。不要为了对齐把 DoomLoop 改成「只闸失败」——除非轮询误伤成了痛点。

参数键：Maka 用稳定哈希（大 Write 不留原文）；不可哈希则退回 call id，避免假阳性。Tepeu 剥 timestamp/random 后拼字符串。tool_use 带大 payload 时再看要不要改指纹，现在文本 `syscall` 用不上。

### 8.4 不借（再钉一次）

- 14 类 Claude 工具分类表进 Policy
- CodeMode 隐藏嵌套事实
- `rememberForTurn` 覆盖单次 consume
- 用 Maka loop-gate 否定现有 DoomLoop
- 为 tool_search 预开 jar

**这轮之后：** 三个盲区对过。再挖是 AI SDK / Graph / Eval，与 `os/` 不同线。

**吸收裁定（2026-08-31）：** 不对账项进底板 / gap / project-memory。对照留本文。

