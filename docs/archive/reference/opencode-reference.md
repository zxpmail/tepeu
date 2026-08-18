# OpenCode 对 Tepeu OS 的启示（对账与吸收清单）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../memory/decisions-log.md) + [os-baseplate.md](./os-baseplate.md) 为准。
> **来源**：`E:\work\opencode-dev`（OpenCode v1.18.18，开源 AI coding agent，TypeScript/Bun，40+ 包 monorepo，Effect v4 基座）。2026-08-16 五路源码探查（事件总线 / 会话与撤销 / 权限与命令 / LLM 层 / 服务架构）。非 git 仓库，版本号弱钉。
> **五参照定位**：CC 巨石 · Pi 极简 · TriniOS 真 OS · AIOS 学术同名 · **OpenCode 工程同代**——与 tepeu 技术形态最接近（本地 server + 多客户端、事件溯源内核、正在从 v1 向 v2 重写），且**它的 EventV2 内核与 tepeu ① 已冻决定逐条同构**，等于 tepeu 方向的生产级先例。

---

## 1. 总判断

1. **OpenCode 正在重写**（v1 AI SDK 路径 → v2 Effect 原生 + 自研 LLM SDK），三代架构并存（双事件体系、三代 SDK、双运行时桥）——**这是 tepeu 重写路线的前车之鉴样本**：它能跑，但代际税极高（认知成本、双份 bug 面、bridge 硬撑）。tepeu「legacy 冻结只读 + os/ 从零」比它的「原地渐进迁移」干净。
2. **EventV2 与 tepeu ① 同构到逐条**：seq 连续分配（immediate 事务内 `latest+1`）、未知 durable 事件 `InvalidDurableEventError` die（= tepeu required-fail）、重放三重校验（同 id 同内容幂等 / 分歧 die / owner fencing）、durable 事务内投影。tepeu 的内核设计第一次拿到生产级同构印证。
3. **对 tepeu 接下来三刀全部有直接输入**：② conformance（事件 manifest + 数量钉死测试）、`llm.*` 断言（`compile()/prepare()` 纯函数边界 + 录制回放）、③/权限（`always[]` 模式、deny 回灌、doom_loop 熔断）。
4. OpenCode 自己的痛处反证 tepeu 裁决：v1 `transform.ts` 1858 行散落 normalize + 800 行 provider 特判 + 按模型名/发布日期字符串嗅探（其 DESIGN.md 自骂 "inefficient dogshit"）——正是「normalize 版本化纯函数」要杀的东西。

## 2. 对账表：tepeu 冻结的规矩 × OpenCode 实现

| # | tepeu 概念 | OpenCode 对应 | 判定 |
|---|-----------|---------------|------|
| 1 | `seq = log.length` 强制连续（§9） | `event_sequence` 表 + immediate 事务内 `seq=latest+1`；重放校验 `seq !== latest+1` 即 die | ✅ 同构；OpenCode 靠单写者串行事务保证，tepeu 靠显式不变量——后者可检查 |
| 2 | 未知事件 required-fail（§9） | 不在 Durable manifest 的 type → `InvalidDurableEventError` die | ✅ 完全同构 |
| 3 | 重放/幂等（§9 崩溃恢复） | 同 id+同内容=幂等 no-op；同 seq 不同内容="Replay diverged" die；`owner_id` fencing + steal 端点 | ✅ 同构 + 跨设备 fencing 是 tepeu 未到的远方 |
| 4 | 三 store（entries/registers/ledger） | event 表 + 投影表（session_message）+ 投影在事件插入**同一事务**内执行——投影与日志永不分离 | ✅ 印证；「事务内投影」是 registers 与 entries 一致性的实现术 |
| 5 | surface 替换压缩（不删事件） | compaction = 日志内一行（durable 事件）+ 读取按 `seq >= compaction.seq` 下限；v1 用 tail 指针 + 视图重排 | ✅ 原则相同（日志全留、seq 定界、读取层换面） |
| 6 | Policy 封闭 union | `{allow,deny,ask}` 三值；**未匹配默认 ask**（tepeu 默认 deny） | ⚔️ 双刃：ask-默认依赖每个工具记得发问（检查在工具 execute 内部不在调度层），漏发问即无门禁；tepeu 总线入口结构性更稳 |
| 7 | 审批单次许可（第四轮 A2 拒绝记住型规则） | `once/always/reject`；always 是 **instance 内存、跨 session、重启失效** | ⚠️ 反面印证：OpenCode 的 always 跨 session 泄漏（A 会话批准 B 会话生效）正是 tepeu 拒绝的理由的活例证 |
| 8 | CommandDispatcher 两型 | allow=local 直通、deny=local 立即失败、ask=等待-应答协议（事件+deferred） | ✅ 三动作恰落在两型两端；ask 的「pending map + 级联清理 + 关停 finalizer」是现成并发安全设计 |
| 9 | 事件词汇表演进 | **per-type 版本化**（持久化键 `session.next.step.ended.2`，旧版本留在 Durable map 供 decode）+ manifest 数量钉死测试 | 💡 比 tepeu 现规（ignorable 标记）细粒度——候选裁决（见 §5） |
| 10 | `llm.*` 断言 normalize 版本化 | v1 无保证（transform 散落）；native SDK `compile()` 纯函数 + `prepare()` 不发请求拿到完整 body + 录制回放 golden 测试 | 💡 断言实现的现成落点（见 §3.2） |
| 11 | Metering/ledger | usage **inclusive 总量 + 非重叠 breakdown 双轨不变式**（`nonCached+cacheRead+cacheWrite=inputTokens`，「消费者永不做减法」）；Decimal 精算 | ✅ 语义比 pi-ai 还干净，ledger 字段设计直接抄 |
| 12 | ⑤ 应用投影 | 「UI 永远是 client」：TUI/web/desktop/CLI 全是同一 HTTP 契约的客户端（TUI 走注入 fetch 的进程内传输） | ✅ 同构 tepeu「⑤ 不直接链接内核」 |
| 13 | compose 开机接线 | 声明式 DI 图（LayerNode deps → 拓扑排序）+ 组合根集中清单 + per-instance 接线顺序 | ✅ 与 Spring DI 同构，方向互相印证 |

## 3. 值得借鉴（按落点）

### 3.1 → ① 事件内核 / ② conformance（直接对当前任务）

1. **事件 manifest + 数量钉死测试**：词汇表是编译期聚合清单（分组 foundation/feature、暴露子集 ServerDefinitions、`Latest` 按类型取最高版本），重复定义启动即 throw，测试断言定义总数（85/88）防漂移。tepeu 落法：`SessionEventType` 旁立 manifest 测试——数量与成员钉死，加事件必须显式改测试。
2. **per-type 版本化事件**：schema 变了 bump 该类型的 `durable.version`，持久化键 `type.version`，旧版本定义留在 Durable map 专门 decode 历史，`Latest` 服务当下。比全局格式版本便宜得多，与 required-fail 并存（未知 version 同样 die）。
3. **durable 事务内投影**：投影器在事件插入的同一 SQLite 事务里跑——投影可作为缓存随时重建而永不漂移。tepeu registers↔entries 一致性的实现术。
4. **live-only delta vs durable 全值边界**：流式片段显式标记不可重放（只有 Ended/全值事件 durable），避免为省事件引入半状态。tepeu 流事件词汇的纪律条款。
5. **重放三重校验 + owner fencing**：多设备同步若做，直接抄（幂等/分歧 die/steal）。
6. **慢消费者两种背压样板**：`allBounded(capacity)` 只杀溢出订阅者不阻塞别人；durable 流「落后就重读 DB」。tepeu ProjectionBus 的设计输入。

### 3.2 → `llm.*` 断言切片（下一刀的实现术）

1. **`compile()/prepare()` 纯函数边界**：normalize 之后、transport 之前，可以**不发请求**拿到 schema 校验过的 provider-native body——`derive(log) ∘ normalize == sent` 断言的落点就是把「sent」做成可观测一等公民（preparedRequest），而不是事后抓包。
2. **录制回放 golden 测试**：HTTP cassette 录制真实请求，`prepare()` 输出与录制对比——这就是 LlmProvider 的 conformance 套件形状。
3. **双 runtime 收敛到同一事件层**：OpenCode 迁移期两条执行路径收敛到 `LLMEvent` 流，每 provider 逐个过 gate、降级带 reason。tepeu 若将来换 LLM 实现，先立事件层 canonical。
4. **usage 双轨不变式**：`nonCached+cacheRead+cacheWrite=inputTokens`，消费方永不做减法——消灭下溢 bug 类，tepeu ledger 字段照抄（三家里最干净：pi 扣减式、CC 挖掘式、OpenCode 不变式）。
5. **cache 断点策略**：auto = 最后一个 tool 定义 + 最后一个 system part + 最新 user message 三断点 + 4-breakpoint 预算计数 + TTL 桶。

### 3.3 → Policy / 审批 / Command

1. **`always[]` 由工具在 ask 时声明**：「记住什么」的语义决定权交给最懂该工具的代码（bash 记 arity 前缀 `git checkout *`，read 记 `*`），审批端零策略只确认——若 tepeu 将来解禁「记住」，必须是这个方向（工具声明、会话内、不跨 session）。
2. **bash arity 前缀归一**：「记住 `git checkout`」而非整条命令或裸 `git`——CC prefix 规则与裸工具名之间的最佳平衡；once 用全文、always 用前缀，一次询问两级粒度。
3. **deny 结果携带规则文本回灌模型** + reject 可带用户反馈（模型能自我纠正）+ **doom_loop 熔断**（同工具同输入连续 3 次 → ask）——tepeu 卫兵清单可加 DoomLoop 熔断类型。
4. **reject/always 级联**：拒绝一个即拒绝全部挂起（用户意图明确）；always 后自动清空已被覆盖的挂起。
5. **逻辑能力名与工具名解耦**（edit 覆盖三个写工具、external_directory 独立）+ **子代理权限派生只继承 deny 与 external_directory**（「放行」不传递、「禁止」必须传递——最小权限的继承规则）。
6. env 文件默认 ask（`*.env.example` 除外）这类**精细化默认值**。

### 3.4 → 架构 / compose

1. **契约/实现分离 + 单源生成客户端**：protocol（纯契约，中间件以 key 注入）→ OpenAPI → 生成 TS SDK + generation-equivalence 测试防漂移。tepeu 对应：springdoc OpenAPI → 生成前端 client；一份契约喂 Web/桌面/CLI。
2. **per-request 实例加载**（`x-opencode-directory` + InstanceStore 惰性 boot + Deferred 并发去重）——tepeu 多 workspace 参考。
3. server 内嵌/反代 web UI：升级 UI 不必升级二进制。
4. 插件 mutate-the-bag 签名 + 顺序确定性 + 按需 npm 安装 + 版本兼容门；`chat.params/tool.definition` 这类「改请求」挂点有表达力。
5. 接线顺序的坑写成注释（Observability 必须最后 provide）——tepeu compose 层同款纪律。

## 4. 反面教材

| OpenCode 之坑 | tepeu 防线 |
|---|---|
| 三代并存（双事件体系+bridge、三代 SDK、双运行时桥），CONTEXT.md 里一张「Avoid: X」术语表是概念漂移伤疤 | legacy 冻结只读 + os/ 从零；不引入第二套作用域机制 |
| v1 normalize 散落 1858 行 + 800 行 provider 特判 + 模型名/发布日期字符串嗅探 | normalize 版本化纯函数（已裁） |
| 权限检查在工具 execute 内部，漏发问即无门禁；未匹配默认 ask | 总线入口结构性关卡 + 默认 deny（已裁） |
| always 记忆跨 session 泄漏、不持久化（与用户预期相悖） | 会话中授予严格单次（已裁，OpenCode 是理由的活例证） |
| committed-revert 删投影行 + 手动反向扣 cost/tokens（投影可变的连带账） | 不删事件 + surface；凡投影可变，引用完整性杂务就跟出来 |
| v1 prune 原地 mutate 历史 part | 追加标记事件（已裁） |
| GlobalBus 是 `any` 型裸 EventEmitter，词汇表有旁路 | 词汇表 manifest + 钉死测试（候选裁决） |
| 同一产品两套 SSE 背压策略（unbounded vs bounded） | ProjectionBus 单一策略 |
| `identity` 包 = 品牌 logo（命名反直觉） | 包按领域语义命名 |
| Basic 单密码 + mDNS 自动发布 | 本机令牌 + localhost 锁（v1 已有先例） |

## 5. 严苛节（对 tepeu 自身）

> **裁决状态（同日）**：C2 已裁入 ADR-016 第六轮（事件词汇表三件：per-type 版本化 + manifest + 数量钉死测试；落码切片=② conformance）；C3 已立备注（不改 A2 裁决）。C1/C4 为观察项不裁。

**C1 重写方式的对照。** OpenCode 原地渐进迁移，代价是三代并存税：本报告引用的机制一半属于正在退役的 v1。tepeu「legacy 只读 + os/ 重写」是更干净的路线——**但 OpenCode 同时证明渐进迁移能让产品在重写期间继续出货**。tepeu 的对应风险不是代际税而是「重写期间无产品」：v1 已发布冻结在 main，develop 重写期间没有可用增量——这是有意的取舍（已裁），不是疏漏，但别忘了它的存在。

**C2 事件词汇表机制是唯一够格的新裁决候选。** tepeu §9 现规只有「未知事件 required-fail + ignorable 标记」；OpenCode 补上三件：**per-type 版本化**（schema 变更不破历史 decode）、**manifest 显式清单 + 数量钉死测试**（防漂移）、**Latest/Durable 双 map**（当下发布与历史解码分离）。落码切片现成：② conformance 任务里加 manifest 测试。若裁决，归 ADR-016 第六轮一行即可——待你点头。

**C3 「always[] 不能翻案 A2，但给出了将来解禁的形状」。** ADR-016 第四轮拒绝「会话中授予的记住型规则」，OpenCode 的实例级 always 恰好演示了拒绝理由（跨 session 泄漏）。但「工具声明 + 会话内 + 不跨 session」的 always[] 若加上作用域约束，是未来若 UX 逼宫时的唯一可接受形态——记入 A2 条目的备注即可，不改裁决。

**C4 单写者假设 vs 显式不变量。** OpenCode 的 seq 连续性靠「单写者 + 串行 immediate 事务」隐式保证（多进程靠 owner fencing 只管重放不管并发写）；tepeu 的 `seq=log.length` + append 点校验是显式可检查的。单机阶段两者等价，tepeu 不需要改；但若做多设备同步，OpenCode 的 fencing/steal 是必经之路——挂账即可。

**证据链**：五路报告行号未逐一复核；关键论断（seq 分配、die 语义、prepare()、双轨 usage）在多路报告中交叉出现或自带源码引用；v1.18.18 是过渡态快照，引用机制前须确认代际（v1/native/v2），报告已逐处标注。

## 6. 不照搬清单

- 三代并存的迁移路径本身（tepeu 直接一步到位）
- Effect v4 全 Layer 化（tepeu 用 Spring DI，同构无需换形）
- Vercel AI SDK 黑盒 + provider 特判表（正是 tepeu normalize 要杀的）
- Basic 单密码 + mDNS 广播的安全模型
- 投影可删的 revert 模型（tepeu surface 不删事件）
- 组合根 god file（50 项清单 + 220 行 import）——Spring 的 JavaConfig 分组可避免

## 7. 对下一刀的直接影响

1. **② conformance 套件**（进行中任务）新增落地件：**事件 manifest 测试**（SessionEventType 成员与数量钉死）——若 C2 裁决通过则一并落码；此即「新裁决指认落码切片」的示范。
2. **`llm.*` 断言切片**的实现术定型：preparedRequest 可观测 + 录制回放 golden 测试 + usage 双轨不变式（§3.2）。
3. **③ Loop/卫兵**：DoomLoop 熔断（同工具同输入 N 次→ask）加入卫兵类型候选，与第四轮 CircuitBreaker 并列。
4. C3 备注可顺手补进 ADR-016 第四轮 A2 条目（不改裁决，只加「将来唯一可接受形态」备注）——与 C2 一起待你点头。
