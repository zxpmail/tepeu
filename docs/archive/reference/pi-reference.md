# Pi 源码对 Tepeu OS 的启示（对账与吸收清单）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../../../memory/decisions-log.md) + [os-baseplate.md](../../os-baseplate.md) 为准。
> **来源**：`E:\work\pi-main`（Pi agent harness，pi.dev / earendil-works，v0.84.2，TypeScript，10 包 monorepo）。2026-08-16 五路源码探查（agent-core / pi-ai 归一化 / 会话与压缩 / 扩展与安全 / 遥测契约与测试）。**证据链弱点**：非 git 仓库，版本号+日期弱钉，行号会漂；引用以机制为准、行号为辅。与 [claude-code-reference.md](./claude-code-reference.md)（CC=巨石参照）互补——**Pi 是极简参照，且是 tepeu `os/` 想成为的东西的 TypeScript 同行**。

---

## 1. 总判断

1. **三点定位**：CC = 一切内联的巨石；Pi = ~800 行核心循环 + 一切皆扩展的极简内核；tepeu = 显式缝的 OS，介于两者之间且比 Pi 更结构化（Policy/卫兵/双真相是 Pi 刻意不做的）。
2. **Pi 的 `packages/agent` 核心（agent-loop.ts 796 行）是「tepeu ③ LoopRuntime + ① 会话设施」的最小可行形状实证**：StreamFn 单注入、错误编码进流、事件 fold 出状态、双队列（steering/followUp）。
3. **Pi 给了 CC 给不了的三样东西**：① **entries/registers/ledger 三 store 模型**（对 tepeu「一切皆事件日志」的直接挑战，见 §5-C1）；② **pi-ai 归一化层**——tepeu 刚裁决的 `normalize`（ADR 第四轮）的完整正反面样板；③ **conformance 测试三件套**——tepeu ② 适配器测试的现成模式。
4. Pi v3 的坑**逐一反证 tepeu 已冻的更严决定**（显式 seq、append 点校验、claim 租约、原子迁移）；Pi format-4 的规范则与 tepeu 方向趋同（storage-assigned seq、torn-tail 截断、writer lease）——等于 tepeu 从 Pi 的演进终点出发。

---

## 2. 对账表：tepeu 冻结的规矩 × Pi 实现

| # | tepeu 概念 | Pi 对应实现 | 判定 |
|---|-----------|-------------|------|
| 1 | Loop 终态封闭 union（③ 转移表） | `StopReason` 封闭 union：`pending\|stop\|length\|toolUse\|error\|aborted\|deferred`；**「StreamFn 不许 throw，错误必须编码进流」**（`pi-ai types.ts:312-324`） | ✅ 验证；`deferred` 一员值得 tepeu 转移表预留（延迟工具加载） |
| 2 | tool_use↔result 配对 + 合成闭合（§6.8） | 循环结构一对一保证；**但批内 abort 直接 break 留孤儿**（agent-loop.ts:478,535）；provider 投影层 `transformMessages` 合成 `"No result provided"` 自愈（transform-messages.ts:158-222） | ✅ 双层印证：**写时合成与边界自愈两道都要**——pi 循环层的洞靠投影层救 |
| 3 | journal-first、append-only | v3「append-only trees」（session-manager.ts:844）；分支=移动 leaf 指针，不复制 | ✅ 验证 |
| 4 | `seq = log.length` 显式连续（§9） | v3 **无 seq**（文件序当序、时间戳当判断依据——出过坑）；format-4 加 storage-assigned seq 修复 | ✅ pi v3 反面 + format-4 正面，双重印证 tepeu |
| 5 | 未知/坏事件 required-fail（§9） | v3 malformed 行**静默跳过**、无告警无 checksum（session-manager.ts:303-311） | ⚠️ 反面：tepeu 更严是对的 |
| 6 | Inbox/claim 租约 | v3 双进程开同文件 = 静默损坏不可检测；format-4 SQLite `writer_lease` + fence 强制单写者 | ✅ 正证 claim/租约必要性 |
| 7 | 崩溃恢复合成闭合（§9） | Pi 相反：**存储保真，投影层自愈**（不落盘合成；error/aborted assistant 轮在投影时整条剔除） | ⚔️ 两种哲学；pi 证明投影层自愈无论如何都必须有（见 #2） |
| 8 | surface 替换代数（§3.1 Compaction） | v3 `firstKeptEntryId` 指针 → format-4 `retainedTail` 自包含 checkpoint；「**Context never reads past a compaction**」（harness.md:647） | 💡 tepeu surface 代数 ≈ 指针方案的加强版；应吸收「**投影不得读过 surface**」为显式不变量 |
| 9 | Policy 封闭 union + fail-closed | Pi **拒绝内置权限系统**（README/security.md）；但 `tool_call` hook 抛错=阻断、permission-gate 扩展无 UI 默认拒 | ✅ fail-closed 方向一致；pi 对进程内边界的批判见 §5-C4 |
| 10 | Slash 必经 Command、不经模型 | 三源统一（extension/prompt/skill）+ 循环外分发 + **流式中也可执行扩展命令** | ✅ 验证；「流式中可执行」= tepeu immediate 逃生口的实例 |
| 11 | 技能两段式懒加载 | agentskills.io 标准 + `<available_skills>` 清单进系统提示 + read 按需加载正文 + 自动注册 `/skill:name` 命令；可跨 harvest `~/.claude/skills` | ✅ 验证，实现比 CC 更简 |
| 12 | Metering/Usage 语义 | `Usage` 统一类型：`reasoning ⊆ output` 不重复计费、`input` 为扣除缓存后裸值、cacheRead/cacheWrite/cacheWrite1h 全覆盖、**中央计费 adapter 只报 token**（models.ts:878-898） | ✅ 字段语义细则值得直接抄 |
| 13 | ② 适配器可换 + 共同语义 | **conformance 套件模式**：契约接口 + no-op 默认 + in-memory 参考 + `/testing` 子路径发布套件；session-backends「One suite, three backends, identical results」 | ✅✅ 对 tepeu ② 最大单点收获（§3.4） |
| 14 | 子代理只减不增 | 非内建——示例扩展用 SDK `new Agent` 自建 | 💡 自举原则：重要功能走缝即缝完整性证明（§3.5） |
| 15 | 卫兵/Metering 兜底 | 核心循环**无 max turns、无预算**——「无人负责」，宿主 `shouldStopAfterTurn` 自理 | ⚠️ 反面：tepeu 不许留这种洞 |

---

## 3. 新启发（按落点）

### 3.1 → `llm.*` 断言 / LlmProvider（ADR 第四轮 B1 的落地细节）

1. **canonical 类型形状**（直接参考 pi-ai types.ts）：按角色三种消息（user/assistant/toolResult）+ content block 流（text/thinking/toolCall）+ **systemPrompt 独立字段不进消息数组** + 工具三件套分离。tepeu 的「日志派生 messages」投影目标就该长这样。
2. **归一化两层**：`transformMessages` 共享预处理（跨 provider 重放策略集中一处：孤儿合成、error/aborted 轮剔除、跨模型 thinking 降级、图片降级）+ 每 API adapter 各自 convert。tepeu 的 normalize 版本化纯函数应拆成「共享预处理（版本化）+ per-provider 投影（版本化）」两段。
3. **pi 的 normalize 不纯——tepeu 断言的反面样板**：OAuth 伪装注入（同一状态因 token 类型字节级不同）、env 变量改行为、`onPayload` 整体替换、`detectCompat` baseUrl 嗅探——pi 因此**不可能**做逐字节断言。tepeu 裁决的「normalize 版本化纯函数、版本落日志」正是 pi 缺的那块：**每引入一个非确定性来源，就亲手杀死自己的断言**。
4. **toolCallId 归一化必须在 normalize 层**：三家字符集/长度各不同（Anthropic 64 / OpenAI 40 / Responses `fc_` 前缀+hash）——id 适配是投影的一部分，不是存储的一部分。
5. **错误归一化要封闭 union**：pi 的 `errorMessage` 自由字符串 + 26 条正则测溢出（provider 改措辞就漏）是天花板低的样子。tepeu 的错误事件类型（ADR 第四轮 B9）应把 provider 错误码也收进封闭词汇。
6. `Usage` 字段语义细则照抄：reasoning 是 output 子集；input 扣缓存；cacheRead/Write/Write1h 分列；计费中央算。

### 3.2 → ③ LoopRuntime / Inbox

1. **「错误不许 throw，编码进流」契约**：StreamFn/工具/hook 的失败全部变成流内 `error` 事件 + 终态 stopReason——循环只需检查一次终态。tepeu 的 LoopOutcome 转移表配上这条，异常路径面积大幅缩小。
2. **steering / followUp 双队列 + 排空模式**：工作中插话 vs 结束后接续，两队列一开关（`all | one-at-a-time`）——tepeu Inbox priority（now/next/later）的最小模型。注意 pi 的局限：**steering 不能打断正在流式的响应**（§5-C5）。
3. **截断即全批失败**：`stopReason=length` 时该批 toolCall **全部判错不执行**（agent-loop.ts:207-214）——流式 salvage 可能让截断参数「看起来合法」。tepeu Loop 必抄。
4. **terminate 全批一致才停**：防单个工具误停整个 agent。
5. **`content` vs `details` 分离**：工具结果的 `details`（结构化）只给日志/UI **不进模型上下文**——tepeu 事件→模型消息投影的天然字段边界。
6. **declaration merging 自定义消息 + `convertToLlm` 纯函数桥**：应用自定义事件与 LLM 消息同一份 transcript、LLM 边界是一个可测试纯函数——tepeu「日志派生投影」的具体形状。

### 3.3 → ① 会话设施

1. **效果三明治 + 预留 id**（format-4 §0.5）：intent（预留 response/usage id）→ 执行 → settlement（entry+usage+状态一事务）；崩溃后在**预留 id 下**合成结算——「每个 tool call 永远有结果」且不重复执行不可重放的工具。强化 tepeu §6.8 与 LongTask 预约-复核：**id 先占位，结算后补**。
2. **append-only 上下文不变量**（harness.md §2.5）：同一会话的请求间上下文**只在尾部增长**，中途写 defer 到 checkpoint——KV cache 保护写进存储设计。tepeu 投影/PromptAssembly 的纪律条款。
3. **entry id 作持久游标**：RPC `get_entries(since)` 跨客户端重启有效。tepeu ProjectionBus/增量投影同型。
4. **fork 三粒度**（/tree 原地分支、/fork 新文件、/clone 抽枝）+ 教训：**label/facts 走寄存器不走树**（v3 label 是树节点导致抽枝要 re-chain）。
5. **切点规则显式枚举、绝不在 toolResult 切 + split-turn 双摘要**（compaction.ts:308-321,795-919）——tepeu Compaction 切点算法直接参考。
6. **摘要请求独立 sessionId + 禁 cache 写**（一次性 prompt 不值得进 cache）——tepeu 压缩走总线时的 Metering/缓存细节。

### 3.4 → ② 适配器 conformance 测试（最可直接开工）

Pi 模式（telemetry 包 + session-backends）四件套：

1. **契约接口 + no-op 默认 + in-memory 参考 + `/testing` 子路径发布 conformance 套件**。套件 runner 无关（只用 assert，不 import 测试框架），返回 `{group, name, run()}` 数组，消费者三行桥接进任何 runner。
2. **fixture 归一化快照**：各后端只负责把自己的记录映射成统一快照类型（可 async，允许先 flush），**断言只针对快照，从不针对后端私有结构**——两个实现共用套件的全部秘密。
3. **passivity 作为一等契约**：适配器读数据失败必须原子化静默、业务回调仍恰好执行一次——用 **`unreadable()` 抛错 Proxy** 测（任何属性访问都炸）。
4. **措辞式契约清单 → 逐条落成 case**：恰好一次、错误值原样传播、last-write-wins、关闭后调用惰性化、确定性结束序（`endSequence` 替代时间戳）。

**tepeu 落法**：`os/kernel` 提供 `SessionConformance` / `BusConformance` 套件（随包发布），`InMemory*` 与未来 SQLite 后端共跑；后端特有行为（迁移、崩溃恢复、跨进程锁）留在各自 test/。这把现有 `KernelPortsSmokeTest` 升格为可扩展资产。

### 3.5 → 工程纪律

1. **自举证明**：plan mode、permission gate、subagent、llama.cpp provider 全是普通扩展——内核作者用「自己最重要的功能都走缝」逼接缝保持完整。tepeu 对应规则：**⑤ 的功能只准经 ③/② 缝实现**，谁直连内核谁违规。
2. **schema 即数据 + 文档由 schema 生成 + 测试锁定逐字节相等**——tepeu 的事件词汇表/遥测字段可以照此消灭文档漂移。
3. **`env -i` 白名单测试脚本**：测试永远跑在空环境（隔离 HOME/GIT、无 key 自动 skip）——「无 key 可重复 CI」+「测试不读用户真实凭据」一次解决。
4. **eval 对照实验**：baseline × candidate × repetition、judge 分数与 CI 失败解耦（阈值 null 只观测）、run 产物 JSONL 化（0600）。

---

## 4. Pi 之坑（反面教材）

| 坑 | tepeu 对应防御 |
|----|----------------|
| 批内 abort 直接 break → 孤儿 toolCall 进上下文，provider 400 | §6.8 写时合成（所有异常出口）+ 投影层自愈（两道） |
| `agentLoop` 的 EventStream 无 `.catch` → reject 时流永不 end、永久挂起 | 转移表终态封闭：每条路径必达某 Terminal |
| steering 不能打断流（只能整轮 abort） | ADR 第四轮：now 级抢占（§5-C5 警告别退化成这样） |
| 核心无 max turns / 无预算，「无人负责」 | 卫兵（超时/配额）+ Metering 是内核义务不是宿主可选 |
| v3 迁移 `openSync("w")` 非原子重写；malformed 静默跳过；双开静默损坏；时间戳当序 | tepeu：禁重写、append 点校验、claim 租约、显式 seq（全部已冻） |
| label/配置进树 → 抽枝 re-chain 复杂、format-4 认错（不变式 8） | §5-C2 检查 tepeu 事件词汇表 |
| 压缩不减文件 + retainedTail 存储放大（压缩≠擦除） | tepeu surface 不删事件同有此问题——GDPR Open 裁决时记得 pi 的教训 |
| `AgentHarness` 空壳已从 index 导出（公开 API 撒谎） | 端口未实现就不导出 |
| `tool_call` 改参后**不再 schema 校验**（扩展成注入面） | tepeu：卫兵/hook 改写参数后必须重校验 |
| 命令重名静默加后缀 `/review:1` | 注册冲突显式失败 |

---

## 5. 对 tepeu 的挑战（严苛节，对本对账的自我驳斥与对底板的诘问）

> **裁决状态（同日）**：C1/C2/C3 已裁入 ADR-016 第五轮并投影底板（§0.6 索引）；C4 分工句已落 §3.1 Execution；C5 无需新裁决（第四轮已裁 now 级抢占），本节转为裁决依据存档。

**C1（最重要）寄存器 vs 事件——tepeu「会话设施 = 事实日志」说得不够全。** Pi format-4 的三 store：「Every payload is in an entry, a register, or the ledger; **there is no third place**」（harness.md:123）+「**Recovery is a read. No reducer exists to have a bug.**」（:610）。可变状态（leaf 指针、配置、进行中操作）不进日志、用寄存器覆盖写、恢复=点查。tepeu 的 surface 替换代数是 reducer/投影哲学——功能等价但复杂度落在「投影正确性」上。**tepeu 的 Inbox/claim 本来就是寄存器形态**，底板只是没说破。候选裁决：**对话事实 + 审计 = entries；leaf/claim/模型配置/进行时操作 = 寄存器（覆盖写、恢复点查）；token/费用 = ledger（append-only）**——把二分说破，以后每个新状态不再争论归属。

**C2 事件词汇表检查。** Pi 不变式 8：「Configuration and orchestration never enter the tree」（:2833）——v3 把 model_change/thinking_level_change 写进树被 format-4 判为错误。**tepeu 需检查 `SessionEventType` 是否混入配置类事件**；若是，趁词汇表还小迁到寄存器。

**C3 规范先行漂移——tepeu 正在走 pi 的老路。** pi：harness.md 2941 行规范 vs 796 行循环实现，类是空壳，文档自认「unfinished and replaced in place」。tepeu：底板+ADR 已四轮裁决、代码只落地 ①②。**每条裁决必须限期落进代码/测试**，否则底板变成 harness.md。可操作规则：连续两轮未落码的裁决标「悬置」，新裁决须指认落码切片。

**C4 pi 会批评 tepeu 的进程内 Policy。** security.md 原话：「A partial in-process sandbox would be easy to misunderstand as a security boundary」。tepeu 的回应其实已在底板里但没说破：**Policy = 授权（谁可请求什么，封闭 union）；Execution/SandboxPolicy = 隔离（OS 级机制在 spawn 点执行）**——两层不混，Policy 从不冒充隔离边界。这句话应写进底板防误读。

**C5 抢占别退化。** pi 的 steering 只在 turn 边界生效是实测局限。tepeu ADR 第四轮裁了 now 级抢占——实现时必须真打断流（abort + 合成闭合 + 排队消息即上下文），不许做成「等下一轮」。

**证据链弱点（自我声明）**：五路报告行号只抽查 6 处（三 store / Recovery is a read / never reads past / 不变式 8 / No result provided / 行数比）；其余未复核；pi 两代格式并存，**借鉴时须确认引用的是 v3 现役还是 format-4 设计**（探查报告已逐处标注，落文档时已区分）。

---

## 6. 不照搬清单

- **两代格式并存**的演进路径（tepeu 一步到位：显式 seq + surface + 租约从第一天起）
- **CBOR wire 协议 / server/client 多连接快照广播**（单机版不需要）
- **8-hex 短 id**（碰撞空间小；tepeu 用 UUID+seq）
- **零权限立场**（tepeu 的 Policy 是产品差异，见 C4 的分工而非取消）
- **typebox 编译期条件类型复杂度**（~280 行类型体操换零运行时校验；Java 生态不适用）
- **models.dev 生成模型目录**（tepeu 经 Spring AI 模型抽象，吸收 Usage 语义即可）
- **jiti 零构建扩展加载**（TS 专属便利；tepeu 扩展面=技能/资产，非代码注入）

---

## 7. 对下一刀的直接影响

1. **② conformance 套件是最便宜且最对齐的一刀**：把 `KernelPortsSmokeTest` 升格为「契约接口 + no-op + in-memory + 随包 conformance 套件」四件套（§3.4），为 SQLite 后端铺轨——不动任何裁决，纯工程加固。
2. **`llm.*` 断言切片**：§3.1 是 normalize 的形状细节（canonical 类型、两段归一化、toolCallId 归一化、Usage 语义）；pi 的不纯 normalize 是「什么会杀死断言」的反面清单。
3. **③ Loop 端口**：§3.2 的 1/3/4（错误进流不 throw、截断全批失败、terminate 全批一致）应进第一版契约；steering/followUp 双队列是 Inbox priority 的实现参照。
4. **需要一轮小裁决**：C1（寄存器二分）+ C2（事件词汇表检查）——建议并入 ADR-016 第五轮；C3 的「裁决限期落码」可作为工程规矩写入底板。
