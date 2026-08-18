# Claude Code 源码对 Tepeu OS 的启示（对账与吸收清单）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../../../memory/decisions-log.md) + [os-baseplate.md](../../os-baseplate.md) 为准；本文只回答「CC 怎么解决的、tepeu 该吸收什么、该防什么」。
> **来源**：`E:\work\claude-code-main`（claude-code-best v2.8.4，Claude Code 完整工程化复原，TypeScript）。2026-08-16 六路源码探查（Loop / 会话日志与压缩 / 子代理 / Prompt 组装 / 权限与 Hook / Slash 与技能）。证据为 CC 仓库内 `文件:行号`，可回查。**证据链弱点**：该目录不是 git 仓库，无法钉 commit——版本号 + 探查日期只是弱钉，行号会随后续更新漂移；引用以机制为准、行号为辅。§7 是对本文件自身的严苛驳斥轮，两处冲突以 §7 为准。

---

## 1. 总判断

1. CC 是「①②③⑤ 挤在一个进程」的形态：没有显式内核与总线，但 tepeu 每一条用缝解决的问题，CC 都用内联机制解决过一遍并留下事故注释——等于一份被数千万次会话压力测试过的实现参照。
2. **tepeu 已冻的内核三件全部找到同构实现且方向一致**（封闭 union、事件日志、Inbox/claim）。CC 的多处事故反过来证明 tepeu 更严的决定是对的：journal-first、审批单次许可、delegationDepth 单调下界、工具只减不增。
3. 最大增量启发集中在两处：**PromptAssembly 的缓存经济学** 与 **Loop 的终态转移表/恢复路径**——恰好是下一刀（③ 编排空壳 / `llm.*` 断言）的设计输入。
4. CC 最大的结构性教训：**内存数组与磁盘两份权威 + 多遍归一化**，其源码注释大量在解释「哪个副本是真的」「哪遍归一化保证什么」。tepeu「日志派生投影 + 单遍重建」由构造消灭这类问题，`llm.*` 逐字节断言（底板 §6.7）是把 CC 的痛处变成机器检查。

---

## 2. 对账表：tepeu 冻结的规矩 × CC 同构实现

| # | tepeu 概念（底板章节） | CC 对应实现 | 判定 |
|---|------------------------|-------------|------|
| 1 | Policy 封闭 union `allow/deny/ask`，词汇外规范化为拒绝（§2） | `permissions.ts` 管线：整工具 deny → ask → 工具自检 → 内容级 ask → safetyCheck → 模式放行 → allow → **passthrough 兜底转 ask**（`permissions.ts:1320-1331`） | ✅ 验证 + **次序细化**：ask 夹在 deny 之后、模式放行之前；「bypass 也拦得住显式 ask 与安全检查」 |
| 2 | 审批 = `asked/decided` 事件对 + **严格单次许可**（§3.1） | `PermissionUpdate{addRules}` 与 `onAllow(input, [])` 一次性批；session/persistent 两档记住规则 | ⚔️ **双刃**（见 §7-A2）：CC 的「记住」是声明式**规则**+作用域（撤销=删规则），不是发放能力——它证明中间态存在且可用；tepeu 拒绝它需要 ADR 记账 |
| 3 | 取消时为未派发 call 写**合成错误结果**（§6.8） | `yieldMissingToolResultBlocks`（`query.ts:149`，三处异常路径调用）+ 发送前 `ensureToolResultPairing` 双向修补 + 链重建时恢复孤儿并行结果 | ✅ 验证 + 加强：不变量必须在**所有异常出口**兜底，不只在取消路径 |
| 4 | `llm.*` 入口断言：请求与日志派生**逐字节相等**（§6.7） | CC 无逐字节断言，只有**遥测级弱检查**（`checkResumeConsistency` messageCount 对账 + promptCacheBreakDetection）；字节稳定靠「替换记账存替换后原串 + seenIds 决策冻结 + resume 全标 seen」勉强维持 | ⚠️ 反证必要性；但 §7-B1 指出 §6.7 按字面执行会翻车——断言目标必须定义在「派生 ∘ 版本化 normalize == 实发」 |
| 5 | Compaction surface 替换代数：`surfaceOp{replace,start,end}`+`sourceEventSeqs`，不删事件（§3.1） | `compact_boundary`（parentUuid:null 截断 resume 链）+ `preservedSegment{head,tail,anchor}` 三点描述 + `content-replacement` 独立事件记账；磁盘永不重写 | ✅ 验证；tepeu 用显式 seq 区间比 CC 的 UUID 锚点三元组更干净 |
| 6 | `seq = log.length` 强制连续（§9） | CC 用 UUID+parentUuid 链，且**序列化键序成为磁盘契约**（`sessionStorage.ts:3426-3430` 注释自认脆弱不变量） | ⚠️ 反证：显式 seq 是对的 |
| 7 | 崩溃恢复补 `turn/end{kind:'interrupted'}`，不截断日志（§9） | `detectTurnInterruption`：未闭合 tool_use 整条丢弃（resume 用）；中断 turn 追加合成 meta user「Continue from where you left off」；末尾 user 无响应插 `NO_RESPONSE_REQUESTED` 哨兵 | ✅ 验证 + 两个合成形态可吸收进事件词汇 |
| 8 | fork/resume 写 `end-seed` 边界（§9） | `createFork` 换 sessionId、重打链；**事故**：fork 不复制 content-replacement 记录 → 永久 cache miss（`branch.ts:100-113`） | ✅ 验证 + 教训：**fork 必须携带全部派生记账**，不只是消息 |
| 9 | Inbox/claim（§4） | `messageQueueManager` 优先级 `now>next>later`；task-notification 按 agentId 路由；主线程只 drain `agentId===undefined`，子代理只 drain 发给自己的通知；turn 边界排水 | ✅ 验证（与 Inbox claim 语义同构）+ **优先级维度**可吸收 |
| 10 | LoopRuntime 三态，maintenance 独占 idle 窗口（§3.2） | REPL 层 `QueryGuard` **只有 idle/dispatching/running，无 maintenance 态**；cron「Jobs only fire while the REPL is idle」；stop-hook 后台工作是 fire-and-forget、与下一 turn 并发 | ⚔️ **不是验证，是反面对照**（见 §7-A1）：CC 只验证了「后台任务让路于运行中 turn」这条**策略**；tepeu 的 maintenance **态**是对 CC fire-and-forget 竞态的防御，且自身有两个未决（窗口强制上限、latch 重放机制） |
| 11 | Slash 必经 Command，不经模型/Loop（§2） | 三型命令 `local / local-jsx / prompt`；`shouldQuery` 布尔决定是否触发模型轮；全部展开发生在循环**之前**（`handlePromptSubmit`） | ✅ 验证 + 三型分类法直接吸收 |
| 12 | 技能目录/正文两段式懒加载，digest 驱动重发（§3.2） | 技能列表常驻附件（≈1% 上下文预算、三级降级、按 agent 增量发送 `sentSkillNames`）；正文调用时才展开 | ✅ 验证；CC 用名字集当 digest，tepeu 内容 digest 更强 |
| 13 | 压缩经总线 `llm.*`（§0） | `compactConversation` 优先走 forked-agent 路径以**复用主对话前缀缓存**（`compact.ts:1222-1240`） | ✅ 验证 + 缓存经济学论据：压缩请求与主对话共享前缀 |
| 14 | TurnContext 显式传递，禁单例 bind（§0） | `ToolUseContext` 全程显式透传；子代理用 `createSubagentContext` 显式克隆/裁剪 | ✅ 验证 |
| 15 | Subagent 工具只减不增（§6） | 黑名单/白名单/异步白名单三层**集合运算**；但运算对象是**按权限模式重建的全量池**而非父的有效池（`AgentTool.tsx:717-726`） | ⚠️ 验证 + **关键警告**：CC 存在「受限父派宽子」上浮洞；tepeu 必须对**父有效集**做减法 |
| 16 | delegationDepth 持久化为单调下界（§3.2） | CC 无数值深度限制：外部构建整体禁嵌套、内部无限；`queryTracking.depth` 只进 analytics | ⚠️ 反证 tepeu 更严；CC 的双保险（持久标记 + 抗压缩的标签扫描）值得抄 |
| 17 | 无人值守审批失败可见（v1 行为） | headless `shouldAvoidPermissionPrompts` → hook 无决策即自动 deny | ✅ 验证 |
| 18 | Metering 开 turn 前预算硬门（§2） | 引擎层逐消息查累计费用；token 预算续跑带**递减收益停机**（连续 3 次增量 <500 token 即停） | ✅ 验证 + 启发（见 §3.1） |
| 19 | 大工具结果 spill + locator + retrievalHint（§9） | `tool-results/<id>.txt`（`wx` 独占创建防重写）+ 2KB 预览 + 路径；Agent 工具 100K 上限→落盘换引用 | ✅ 验证，数字可直接参考 |

---

## 3. 新启发（底板未有，建议吸收；按落点归类）

### 3.1 → ③ LoopRuntime（编排空壳的第一版端口就该带上）

1. **终态转移表**：显式 `Terminal` 枚举（10 种：completed / max_turns_reached / aborted_streaming / aborted_tools / stop_hook_prevented / hook_stopped / model_error / prompt_too_long …）+ `Continue` 原因枚举，写进循环状态 `state.transition`。测试无需检查消息内容即可断言走了哪条恢复路径（`src/query/transitions.ts`）。tepeu 对应物：`LoopOutcome` sealed 类型。
2. **续跑判定不信 stop_reason，信「流中是否出现 tool_use block」**（`query.ts:751-755` 注释明说 stop_reason 不可靠）。tepeu 的 LlmProvider 驱动契约应直接写成：续跑信号 = 流内出现 tool_use。
3. **恢复路径分级**：`max_tokens` 先一次性升档重试，再注入「继续写」meta user 消息（上限 3 次）；`prompt_too_long` 头部截断重试。每条恢复是命名转移，不是 ad-hoc 重试。
4. **分区并发**：连续 concurrency-safe 工具合并成并行批（默认上限 10，环境变量可调），非 safe 各自串行；`isConcurrencySafe` **按输入判定**（如 Read 只读时 safe）；失败语义——只有 Bash 失败连坐取消兄弟，读工具失败不连坐。流中执行（工具块一到就跑、结果按序产出）v1 不做，但端口别堵死。
5. **熔断器是一等卫兵**：autocompact 连败 3 次停（曾单会话 3272 次失败调用）；529 三振切 fallback；全局「同类恢复只允许尝试一次」latch（`hasAttemptedReactiveCompact` 不得在 stop-hook 重试时重置——CC 烧过数千次调用的死亡螺旋：API 错 × stop hook × 压缩互相触发）。tepeu 卫兵清单应加 `CircuitBreaker` 类型。
6. **错误合成消息而非抛出**：重试耗尽的 API 错误不抛，合成 assistant 错误消息进历史；`model_error` 终态跳过 stop hooks（防死亡螺旋）。
7. **递减收益停机**：token 预算驱动续跑时，连续 3 次增量 <500 token 判定收益递减即停。可直接用于 LongTask 的「值得继续吗」判据。
8. **turn 内排水优先级**：工具回合结束后按 now/next/later 排水队列；后台通知默认 later，Sleep 类等待工具可提前到 next。

### 3.2 → ② LlmProvider + `llm.*` 断言（ADR Forward 第一项的实现技术）

1. **决策冻结（decision freezing）**：替换决策一经做出（seenIds 记录），之后**每轮字节一致重放**；记账存「替换后的原串」而非可重推导的模板（模板改版不破缓存）；resume 时把所有候选标 seen → 恢复后做同样的决策。tepeu 的 `llm.*` 断言要过，日志必须记**实际发出的字节**，这是前提技术。
2. **单 cache 断点 + 稳定排序**：每请求恰好一个 cache_control 标记；工具数组按「内置连续前缀 + 按名排序」排（为 prompt cache 稳定）；beta header 一旦发送整个会话锁存（中途开关 = 50-70K token 重算）。→ PromptAssembly 的 Section 顺序与工具顺序应是**冻住的不变量**，进 `llm.*` 断言的 config 相等检查。
3. **缓存破坏要主动检测**：CC 有 promptCacheBreakDetection 记录影响缓存键的一切并监控命中率。tepeu Metering 侧可记 cache hit 率作健康指标。

### 3.3 → ③ PromptAssembly

1. **缓存成本编码进 API**：`systemPromptSection(name, compute)`（缓存）与 `DANGEROUS_uncachedSystemPromptSection(name, compute, reason)`（每轮重算、必须写理由）二元函数——组织性约束优于口头约定（`systemPromptSections.ts`）。tepeu 静态 Section 表可以直接采用这对 API 形状。
2. **静态/动态边界标记 + 2^N 变体防护**：显式 `SYSTEM_PROMPT_DYNAMIC_BOUNDARY`；所有运行时条件位（是否交互、有无某工具…）集中放在边界**之后**——否则 N 个布尔位产生 2^N 个前缀哈希变体，缓存全 miss。
3. **附件持久化一次 + delta 重发**：动态注入（todo 提醒、技能列表、工具 delta、日期变化）作为 attachment 消息**写进日志一次**，不每轮重发；下次发送前扫历史重建「已公告集」再 diff，只发新增（`sentSkillNames` / `deferred_tools_delta` / `agent_listing_delta`）。这正是底板「动态 PromptContext 变化或被遮蔽才重发」的可抄实现。
4. **附件位置重排**：注入内容上浮到最近的 tool_result/assistant 之后，避免打断配对结构。
5. **token 经济学注释纪律**：CC 每条上下文决策都标 Gtok/周收益（如 Explore 砍 CLAUDE.md 省 5-15 Gtok/周）。tepeu ADR/PromptAssembly 决策照此量化。

### 3.4 → ③ CommandDispatcher

1. **三型命令分类法**：`local`（纯本地执行，零 token，结果进 transcript 不进模型）/ `prompt`（展开为该轮 user 输入，触发模型轮）/ UI 面板型。**分层修正（§7-B10）**：③ 的 CommandDispatcher 端口只该见 `local`/`prompt` 两型；CC 的 `local-jsx` 是 ⑤ 层关注，吸收方式 = ⑤ 向 ③ 注册 local 命令处理器，而非在 ③ 端口上开 UI 型。
2. **`immediate` 逃生口**：turn 运行中的输入进优先级队列等待停止点；少数命令（UI 态）可绕队列立即执行。
3. **`allowed-tools` 是轮级许可**：技能声明的工具白名单只在展开的那一轮生效，下一轮重置——与 tepeu 单次许可制天然同构，比 CC 的 session/persistent 档更安全。
4. **命令启动后台任务**：命令展开物可带 `context: fork`（独立子代理跑）或 cron（只在 idle 窗口触发）→ 完成后经 Inbox 通知。与 maintenance 窗口语义闭环。

### 3.5 → ①/② 会话与审批

1. **审批多通道竞速 + resolve-once**：本地用户 / 远程 / hook / 分类器并行，`claim()` 原子一次性守卫防双 resolve。tepeu ApprovalStore 的 `decided` 事件应只有一个胜者——端口语义上写死。
2. **resume 一致性遥测**：turn 收尾 checkpoint 记 messageCount，resume 时对账发 delta——把「日志往返漂移」变成可监控指标。tepeu 可在 AuditSink/Metering 加同型探针。
3. **两个合成恢复事件**（充实 §9 词汇表）：`continue_from_interruption`（meta user 消息，供无人值守续跑）；`no_response_requested`（末尾 user 无 assistant 响应的哨兵，保证 replay 时消息数组对 API 合法）。
4. **正向安全白名单**（`SAFE_SKILL_PROPERTIES`）：技能/资产的新增元数据字段**默认要求权限**，白名单外的字段出现即升级为 ask——与 §9「未知事件 required-fail」同型原则，扩展到资产加载面。
5. **终态先行**：后台任务先置终态再做可能挂死的清理（gh-20236：清理挂死导致等待方永远 block）。tepeu LongTask 终态写权限的时序照此。

---

## 4. 反面教材（CC 之坑 = tepeu 之戒）

| CC 的坑 | 后果 | tepeu 的结构性防御 |
|---------|------|--------------------|
| 可变消息数组当权威 + 原地回填 stop_reason/usage | 消费者拿到 null；依赖「写队列恰好晚于回写」的脆弱契约 | journal-first：事件不可变，投影只读派生 |
| 多遍归一化（源码自认 "inherently fragile"） | 每遍制造下一遍要处理的新情况 | 单遍「日志 → 派生 messages」+ `llm.*` 断言 |
| 键序即磁盘契约 | 改对象字面量键序静默破坏快路径 | 显式 `seq`，append 点校验 |
| 子代理池对全量池重建 | 受限父（plan 模式）派出 acceptEdits 池的子代理——权限上浮 | 「只减不增」对**父有效集**做减法（写进 SubagentAdaptor 契约） |
| 附件收集 1s 超时静默丢弃 | todo/日期注入悄悄消失难排查 | 卫兵失败必须失败可见（fail-closed 已立规，执行面别忘） |
| 记忆文件 >40K 只警告不截断 | 失控 CLAUDE.md 无限吃上下文 | PromptAssembly 超预算丢弃出账单（已立规，且要硬执行） |
| 缓存破坏事故（agent 列表曾占 10.2% cache_creation；header 翻转 50-70K） | 全局成本敏感面无人察觉 | 成本编码进 API（§3.3-1）+ cache 健康指标 |
| 死亡螺旋（API 错 × stop hook × 压缩） | 单会话数千次无效调用 | CircuitBreaker 卫兵 + 「同类恢复一次」latch（§3.1-5） |
| 两个权威副本（内存数组 vs 磁盘） | 注释全在解释哪个是真的 | 只留日志一个真相；投影即弃 |

---

## 5. 不照搬清单（CC 的复杂度是超大规模的税）

- **流中工具执行**（v1 后置；端口留缝即可）
- **cache_edits 服务端微压缩**、provider 专属 context management beta
- **无人值守 6 小时无限重试**
- **多 marketplace 插件依赖图**（存在性保证 + 跨市场阻断——tepeu 单机市场用不上）
- **Statsig/GrowthBook 特性开关矩阵**、Langfuse/OTel 全家桶（tepeu 用 Metering/AuditSink 端口）
- **CCB 扩展功能**（Goal / Artifacts / 群控 / voice / LAN）——⑤ 层产品特性，不进底板
- **磁盘分块 100MB / >5MB 快路径 / 字节级链扫描**等规模优化（等 tepeu 有规模再说）

---

## 6. 对下一刀的直接影响

- **若切 ③ 编排空壳**：第一版端口应带上 §3.1 的 1/3/5/6（转移表、恢复分级、熔断卫兵、错误合成消息）与 §3.4 的两型命令分类法；LoopRuntime 三态照底板不动。
- **若切 `llm.*` 断言**：**先裁决 §7-B1**（断言的规范形态）再动手——按 §6.7 字面实现会误伤合法的 provider 侧归一化；§3.2 的决策冻结 + 记实际字节 + 冻住的顺序不变量是断言能过的前提。
- **吸收方式**：本文 §3 各项落位到 os-baseplate 对应小节做**小幅增补**（不动 ADR 裁决）；§4 表可作为卫兵/契约测试的 checklist 来源；§7-B 各洞需要底板/ADR 补裁决。

---

## 7. 严苛轮：对本对账的自我驳斥（2026-08-16）

> 对 §1–§6 的对抗审查。与上文冲突处，**以本节为准**。
>
> **裁决状态（同日）**：A1/A2/B1–B9 已裁入 ADR-016「CC 源码对账落位」（第四轮）并投影至 os-baseplate 正文（§0.5 有洞→裁决→落点索引表）；B10 已在 §3.4 原地修正。本节由「待裁决」转为**裁决依据存档**。

### A. §2 对账表的过度声明

**A1. LoopRuntime「✅验证」是误标。** `QueryGuard` 实测只有 `idle|dispatching|running` 三态（`QueryGuard.ts:30`），**没有 maintenance 态**。CC 真实参照是反面的：stop-hook 挂的 memory extraction / prompt suggestion 等 fire-and-forget 后台工作**与下一 turn 并发执行**——正是 tepeu maintenance 独占窗口要防的竞态。CC 只验证了「后台任务让路于运行中 turn」这一**策略**（cron 只在 idle 触发）。连带暴露 tepeu maintenance 自身两个未决：① 窗口有没有**强制上限**（用户消息等 maintenance 多久必须打断它？）；② 「唤醒 latch 重放」的机制（重放什么进哪个 Inbox）未写。

**A2. 审批单次许可「✅验证」过度慷慨，实为双刃。** CC 的 remember 档不是历史包袱而是**可用性刚需**：200 轮会话每条 `git status` 都问一次不可用。CC 的解法是「声明式规则 + 作用域（session/localSettings/prefix）」——撤销 = 删规则，规则不是发放的能力。这**部分反驳** tepeu 单次教条：单次制下自主长任务要么每步问人（自主性死），要么运维预配 allow 规则（审批事件对形同虚设）。ADR-016 该补一条：**为什么拒绝「有界 TTL 的声明式规则」这个中间态**（或接受它作为 Policy 的一种 verdict 附件）。不裁决就是埋雷。

**A3. 「CC 无此断言」不精确。** CC 有两个遥测级弱检查：`checkResumeConsistency`（checkpoint messageCount vs 重建位置对账发 delta）、promptCacheBreakDetection（缓存键漂移检测）。精确表述：CC **没有逐字节机器断言**，一致性感靠弱遥测 + 大量注释人肉维持——tepeu 把它变成断言仍然是对的，但 CC 的弱检查也值得抄做**健康指标**（断言防破坏、遥测看趋势）。

### B. CC 暴露、但 §3 漏报的底板洞（按危害排序）

**B1. `llm.*` 断言的规范形态有洞——第一优先。** §6.7 字面是「请求 messages 与日志派生逐字节相等」。但 CC 的现实：发送前存在**合法的运行时变形**——Bedrock 要求连续 user 消息合并、media >100 剥离、provider 差异归一化、cache_control 每请求布点、工具引用按模型开关剥块。若断言按字面执行：要么**误伤合法发送**（断言炸 → 正常请求被阻断），要么放宽成「diff 后人工看」→ 漏掉真漂移，硬规矩退化成摆设。修正案：断言目标 = `derive(log) ∘ normalize == sent`，其中 **normalize 是版本化的纯函数且其版本号进日志**。这一句必须先落底板，`llm.*` 断言切片才可动手。

**B2. syscall 注册表需要确定性规范序。** 工具数组及其**顺序是缓存键的一部分**（CC 教训：agent 列表挪出工具描述省全站 10.2% cache_creation；工具池排序注释明写 for prompt-cache stable）。tepeu 工具在总线上**动态注册**——若注册顺序不确定（HashMap 迭代序 / 配置扫描序），`llm.*` 的 config 相等断言**重启后必炸**。这是**内核级不变量**（syscall 表的枚举序稳定），不是 ② 实现细节。

**B3. 多卫兵组合代数未定。** 总线入口挂 Policy+卫兵：3 个卫兵，1 个 deny、1 个 allow、1 个抛异常，合成什么？d7e053f 做了异常规范化（fail-closed，对），但**组合次序/短路/优先级**没写。CC 的答案可借：聚合格 **deny > ask > allow**（多 hook 决策聚合）；且 **hook-allow 不得越过 deny/ask 规则**（`resolveHookPermissionDecision` 强制再过规则子集）——「编排器的批准压不过策略的拒绝」。底板 §2 需要补一行组合律。

**B4. Inbox 缺优先级与抢占语义。** claim/租约有了，但「用户消息在 turn 运行中到达 → 是否抢占当前流」未定。CC：turn 中提交新输入 → `abort('interrupt')` 当前流，排队消息即成为上下文；优先级 `now > next > later`。对聊天产品这是**核心体验语义**，不是实现细节——Inbox 端口该有 priority 字段与抢占裁决。

**B5. fork 跨 seed 边界的替换记账。** §9 end-seed 区分种子历史与自身写入，但 **replaceRange 的区间校验对跨 seed 边界的区间是什么态度**未写。CC 事故（fork 不复制 content-replacement 记录 → 永久 cache miss）给出答案的形状：① fork 必须携带全部替换记账；② 自身写入的 replace 区间**不得伸进种子区**。写进 §9。

**B6. 取消传播拓扑未定。** TurnContext 有 cancel 字段，但「父 turn 取消是否取消子代理」未定。CC 的裁决：**后台 agent 的控制器不挂父**（ESC 杀主线程不杀后台，显式 kill 才杀）+ 兄弟连坐仅限 Bash 类。tepeu 需要在 delegation 树上写明取消传播边（父子/兄弟/后台豁免）。

**B7. CircuitBreaker 的作用域与重置。** 若熔断进卫兵类型：作用域（per syscall / per provider / per principal / global）与重置语义（半开探测？手动？超时？）不写清楚，**一个主体的过载会 trips 全局**——这正好是 §8「调度公平」债务的具体化，至少要把作用域参数化。

**B8. Metering 只在开 turn 前拦不够。** CC 在引擎层**逐消息**查累计费用（一个超大 turn 就能穿预算）。tepeu 至少要 provider 级 max_tokens 兜底，并裁决是否逐消息查（单机版可以只开 turn 前拦 + max_tokens，但要写明这是裁决不是遗漏）。

**B9. 错误事件的可见性决策。** CC 把 API 错**合成 assistant 消息进历史**（resume 后模型自己看到先前失败）。tepeu 双真相逼问：API 错误是**会话事件**还是 **AuditSink**？「模型可见⟺日志可还原」要求：若 resume 后模型要看到错误 → 错误必须落会话日志（成为事件类型）；若只是人看 → AuditSink。目前事件词汇表没有这个决定。

**B10. CommandDispatcher「三型留口」分层错误**（已在 §3.4 原地修正）：`local-jsx` 是 ⑤ 层关注，③ 端口只见 local/prompt 两型，UI 面板命令 = ⑤ 注册的 local 命令。

### C. 证据链弱点

- **C1**：claude-code-main **不是 git 仓库**，无 commit 可钉；文档头已记 v2.8.4 + 日期弱钉，行号会漂。关键论断引用时**先重验再依赖**。
- **C2**：六路报告的行号只抽查复核了 7 处（Terminal 表 / DANGEROUS 段 / 配对兜底 / passthrough→ask / 全量池重建 / cron idle / QueryGuard 状态），其余未复核；CCB 是三方复原工程（内含未跑 lint 的损坏代码），**引用以机制为准、行号为辅**。
- **C3**：本文所有「CC 证明 X 可行」的论断都带规模前提——CC 的方案在千万级会话下成立，不代表单机 tepeu 需要同款复杂度；§5 不照搬清单是护栏，但每条「吸收」也该问一句：tepeu 的规模下这笔复杂度买回什么。
