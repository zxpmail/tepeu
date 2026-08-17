# gnex3 对 Tepeu 的启示（影子时间线参照）

> **地位**：参照材料，不是规范；gnex3 是**平行设计语料**（零代码），非运行时、非依赖、非竞品承诺。裁决以 [ADR-016](../memory/decisions-log.md) + [os-baseplate.md](../os-baseplate.md) 为准。
> **来源**：`E:\work\gnex3`（80 份 SDD、8337 行、2026-08-17 单日产出，全部「实现状态：规划中」；GNEX 2.0 九服务 + dsh 组合语义融合，自研 cordis-jvm 插件内核，67 插件六层）。2026-08-18 全量探查（model/session/storage/context/agent/plugin-runtime/tools/goals-skills/delegation/enterprise 九域 + `_work/` 两份选型文档）。
> **七参照定位**：CC 巨石 · Pi 极简 · TriniOS 真 OS · AIOS 学术同名 · OpenCode 工程同代 · Netty 工程理念源 · **gnex3 影子时间线**——第一个「同题不同解」参照：吃同一批 dsh 输入，在每个 tepeu 说「不」的地方走了「是」。价值 = 反例印证 + 规则层局部收割；**姿态由用户指令钉死：只吸取有利**。  
> **近亲**：同姿态的 [`work-docs-absorption.md`](./work-docs-absorption.md)（`E:\work\docs` 闸门与诚实）。

---

## 1. 总判断

1. **反例价值大于正面参照价值**。gnex3 的四个大选择——规范先行（8337 行规范对 0 行实现）、生态自研（cordis-jvm 移植 Cordis 语义）、插件仪式（67 插件 × 三角色 ≈ 接口森林）、model 层接受 Spring AI 黑盒（wire 零可观测、无 cache_control、单协议族）——恰是 tepeu 各条对冲纪律的反面镜像：conformance 先行、不引 Cordis、蒸馏砍缝、§6-6 红线。同题平行设计里各自收获反面印证，这是六源参照给不了的。
2. **运维规则层有真金**。重试错误分类、超时诚实、终态单调、证物保护、先落日志再执行——这些是 gnex2 九服务企业运营经验的结晶，与架构形态无关，可直接收割（§2 八条，各指认归属切片）。
3. **内部矛盾两处是活教材**：session-store 允许「背压可 DROP 低优先条目」 vs compaction「可从日志重建进模输入」+ P0-a「证物不丢」；「append 不得阻塞热路径（异步缓冲）」 vs 「tool/call 必须先入日志再执行（时间戳断言）」。must 清单不交联自检 = 伪精确——tepeu 挂账表 + conformance 的存在理由再证。
4. **「模型可见⟺已落盘」与「固定链序」获独立同构印证**（不必新裁）：compaction「摘要必须作为会话事件落盘（可审计、可从日志重建进模输入）」+「agent/request 前置不可绕过」；llm 调用链「熔断→限流→预算→重试→路由」配置钉死、错序拒启——与 tepeu 红线 §6-6 / 总线五道闸同构。

## 2. 吸收清单（8 条，各指认归属切片）

### 2.1 → llm transport 切片：重试协议

> 「重试仅限可重试错误（TIMEOUT/RATE_LIMIT/5xx）；4xx 业务错误必须直抛」「重试必须指数退避+抖动，且受 max-attempts 硬限（禁无限重试）」「重试请求必须携带同一 requestId（幂等，流式从零重放）」「熔断打开时禁止重试（直接抛，避免放大故障）」（specs/model/v3-spec-model-retry.md §5）

tepeu 落法：可重试分类做**封闭枚举**（对齐 Policy 封闭 union 品味）；requestId 幂等 + 流式从零重放进 transport 契约；熔断卫兵（作用域参数化，第四轮 B7）开路时重试即拒绝。

### 2.2 → 并入「总线自动落事件」挂账：tool/call 先落日志再执行

> 「✅ `tool/call` 必须先入日志再执行工具」+ 时间戳断言测试（specs/session/v3-spec-session-store.md §5 V3）

tepeu 意义：崩溃中的工具留下**孤儿 TOOL_CALL**，replay 可识别为 interrupted——与 §9「崩溃恢复补合成闭合」（turn 级）互补，此为 call 级。随 ③ Loop 落事件归属同裁。

### 2.3 → registers / ③ Loop：终态单调

> 「❌ 晚到事件不得把 `RESUMABLE`/`COMPLETED` 盖回 `IN_PROGRESS`（checkpoint 状态回归禁止）」（specs/session/v3-spec-session-projection.md §5）

tepeu 落法：「恢复=点查非重放」（第五轮）的补丁规则——registers 终态值只许单调跃迁，晚到/重放旧值拒绝写入。

### 2.4 → Tool 契约切片：TIMED_OUT 一等结果

> 「✅ 超时后必须中止执行并返回 TIMED_OUT 标记结果，不得悬挂」「❌ 不得因超时静默返回『成功』」「❌ 超时不得自动重试」（specs/tools/v3-spec-tool-timeout 类 §5）

tepeu 落法：工具超时产出**成对 TOOL_RESULT**（错误标记型），tool_call↔result 配对不破（红线 §6-7 取消合成闭合的同族规则）。

### 2.5 → 并入 OpenCode DoomLoop 挂账：guard 具体形状

> 指纹归一化（「剥离时间戳/随机参数」）+ 阈值（5 次/120s）+ NUDGE 模型可见：「❌ 提醒不得被静默吞掉（模型必须可见 reminder 载荷）」（specs/tools/v3-spec-tool-loop 检测 §5）

tepeu 落法：NUDGE 是模型可见事实 → 走 entries；若需新事件类型走第六轮 manifest 钉数流程（词汇表现 7 类，勿开垃圾抽屉——SYSTEM_NOTE 教训）。

### 2.6 → 并入 ledger barrier 挂账：用量不变式与写失败语义

> 「✅ 每次调用必须经 tokenMeter 记账（usage 只增，禁负/禁篡改）」「❌ 记账失败不得阻断主调用（旁路降级放行 + 告警）」（specs/model/v3-spec-model-gateway.md §5）

**半吸收（严苛节 C4）**：「usage 只增、禁负、禁篡改」进 ledger 不变式；「写失败降级放行」**不照抄**——tepeu 预算门吃 ledger 供数（第七轮正典），丢账=预算失效，倾向 fail-closed（与 barrier 挂账同倾向）；gnex3 的放行+告警记为备选形态。

### 2.7 → ③/RegisterStore 挂账同裁：版本化快照锁存

> 「✅ preset 变更必须以新版本快照装配（运行中会话不受影响）」（specs/agent/v3-spec-agent-preset.md §5）

tepeu 落法：工具集/模型配置增量 = 新版本快照；in-flight turn 锁存旧快照——与第四轮 B2「注册表规范序」互补（序管同集稳定，快照管变更隔离；CC §3.2 beta header 会话锁存同款）。

### 2.8 → Compaction 切片：修剪证物保护 + spill 容量纪律

> 「✅ 交付证据保护：Write/HTTP 2xx 证物（P0-a）不得被修剪丢失」「✅ 修剪必须留痕：原文可恢复（spill 引用或日志），禁止静默丢信息」「❌ 禁止无 TTL / 无上限写入」（spill 30d/1MB；specs/context/v3-spec-context-pruner.md、spill.md §5）

tepeu 落法：§9 spill 条目补三属性——TTL、单条上限、证物不可修剪类标记。

## 3. 明确不吸收（用户指令：只吸取有利）

1. **cordis-jvm 自研插件内核**——Java 已有静态模块系统 + DI；Spring + cordis-jvm + 「双向桥」三套生命周期共存是自造缝。tepeu 已裁「不引 Cordis；先包级乐高 + 依赖规则」（ADR-016）。
2. **67 插件 × F7 三角色接口森林**——第八轮蒸馏刀点名的 EJB 死法；tepeu 内核必需端口收敛 4 个的路线不变。
3. **冷装插件仪式**——重启生效的「插件」= 改名模块；tepeu 能力 = 注册进总线的 syscall handler，无部署独立性的仪式不引入。
4. **开放事件词汇表**——几十个 `域/动作` 名无封闭集；tepeu 7 类钉死 + manifest 钉数是 `derive(log)` 可全定义的前提。
5. **审批无单次消费**——队列 + TTL + 回放只靠审计兜底；tepeu 严格单次 consume（第九轮 C1）更强，不退。
6. **日志背压可 DROP**——对「日志即事实源」致命；tepeu append-only 不丢线守住（见严苛节 C2 矛盾分析）。
7. **Spring AI 黑盒 model 层**——单协议族、wire 零可观测、无 cache_control；tepeu §6-6 逐字节断言 + 第十轮自研双协议族选型不退。

## 4. 严苛节（对 gnex3 与对 tepeu 自身）

**C1 完成度剧场**。8337 行规范对 0 行实现，单日 81/81 ✅——✅ 指「文档写完」非「设计验证过」。§8 deferred 段无绑定落码切片、无逾期处置，挂账无牙齿。tepeu 的 C3 纪律（轮=落码切片轮、连续两轮未落码标悬置）正是防此病，勿松。

**C2 must 清单必须交联自检**。gnex3 两处当面矛盾（§1.3）皆因各 SDD 独立填充、无人对全集做一致性裁决。tepeu 每次红线/底板变更过 §8.5 对账 + conformance 钉数的纪律保持。

**C3 收割防越界**。8 条吸收均为**规则**（不变式/语义/纪律），非架构。若收割过程中发现自己在讨论「要不要插件化 / 要不要 waterfall 事件总线 / 要不要 GraalJS 桥」，即越界，回到本节。gnex3 最清醒的一条恰恰是它自己也裁了的：「风险分类不得由 Agent 自述（平台侧规则，禁模型自报低风险豁免）」——收割同理：**吸收决策不由被吸收方的完备度自证，由 tepeu 的裁决纪律自证**。

**C4 参数不吸收**。maxTurns 64 / token 阈值 48000 / keep-head 4096 / lease 15s / TTL 24h 均无来源推导。tepeu 参数随切片落码从自身量级推（对齐 Netty 参照 C1 量级判据原则）。

**证据链**：单路探查报告，spec 引文带文件与 §节号、未复核行号；gnex3 为规划语料无代码可验，规则层引用自洽、风险低。

## 5. 对切片的影响

- **§8.5 新增 4 行挂账**（retry 协议 / 终态单调 / TIMED_OUT / 证物保护+spill 容量），**并入 3 行既有挂账备注**（先落日志再执行 / DoomLoop 形状 / ledger 写失败语义——倾向与 gnex3 相反，登记为备选），**快照锁存随 RegisterStore 同裁**。
- **ADR-016 第十轮** = 本参照 + LlmProvider 选型裁决（自研双协议族）+ 断言形态升格（派生式）；落码切片 = `llm.*` 断言切片（os/llm 新模块），随下刀兑现。
- 零运行时影响：gnex3 不进依赖、不进代码，仅规则入账。
