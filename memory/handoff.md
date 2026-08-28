# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md` + `memory/decisions-log.md`（ADR-016）。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-28（Persist 访问口；组件不关库）

## 当前阶段

- develop：**本机 Agent OS 骨架可演示** + **⑤ CLI 宿主**（仓库根 `host/`，非 os 组件）
- 仍不是企业 Agent OS / 完整 OS；**无完整 UI**（有 ProjectionBus v1 + CLI）、**无向量记忆**（有 KnowledgeSource 端口）
- 下一刀按痛点：⑤ UI/SSE、记忆平面、多副本 fencing

## 口径

- **组件（10）**：session / policy / persist / observation / bus / llm / loop / orchestration / execution / compose
- **不是组件**：identity、syscall（词汇）；conformance（harness）
- **⑤ 应用**：`host/`（Spring Boot 4 CLI；依赖 compose；**不在 `os/` 内**）
- Loop **不**依赖 orchestration。CompactionWork 在 loop，经总线 llm.*。
- execution 隔离 = **partial**。spawn：Windows `CREATE_SUSPENDED` 入 Job 再跑；Linux bwrap + ro-bind-try。无 jail 时失败可见。
- 压缩后 `log.surfaceEpoch` 跳过上笔 llm digest 复核。审批绑 `argsDigest`。`/approve` 校验会话。
- compose **不**读 API 密钥；host **可以**读。live 测试有 key 才烧。
- 完成：证据在 ① entries，宣判在 ③ `CompletionGate`。内核有 Inbox/claim，**没有调度器**。不改成「按 CC 模式」。不为了更像 OS 开调度切片。
- persist：访问口 = `Persist`（jdbc/tx/script）；引擎口 = `PersistEngine`。session/policy/compose 不建连接、不关库。host `DataSourceBuilder` 后 `Persist.jdbc`。换方言仍改适配器 SQL。`os/` 不启 Boot，不上 ORM。

## 已完成

- 第九–二十一轮：四端口、Loop、llm HTTP、SQLite 发行、Compaction 窗、DefaultRuleMatrix、execution 囚笼
- 第二十二轮：live 测试（opt-in）、turn 内 overflow 压缩、PLAN/FILE 完成门、OS jail、`/approve` + `policy.rules`
- 第二十三轮：surfaceEpoch、Job CREATE_SUSPENDED、env 白名单、stdout 封顶、argsDigest、jail NOFOLLOW、文档口径对齐
- 2026-08-24：外部「Agent 运行时安全」系列开篇+工具 2–4 篇中肯对账落入 `docs/archive/reference/agent-runtime-security-series.md`（九宫格≠九 jar；gate/response 空 jar 不预开）
- 同日补：EnvHarness 机制同构（Gate=边界脚本；观测可改/verifier 不可改；Observation 组件候选）写入安全系列 §6 + 底板 §3.3 / §8.5 挂账；未开 jar、未裁 ADR
- 同日补：腾讯「Harness Engineering」文 → `tencent-harness-engineering.md` 降级为**勿当 os 参照**（与 `os/` 不同线，仅防混谈）；不列入吸收
- 同日补：DEV.to 评测可观测管线 → `ai-eval-observability-pipeline.md`（运维姿态可借脱敏/诚实边界；非内核；勿开 observability jar）
- 同日补：Terax ADE → `terax-ai.md`（⑤ 工作台；与 `os/` 不同线；勿当内核参照）
- 2026-08-24：`ProjectionBus` + `SessionProjections`（UI 投影 v1）；`KnowledgeSource` + `PromptAssembly.memoryHits`
- 2026-08-24→25：`host/` CLI（Spring Boot 4）；**已从 `os/host` 挪到仓库根**（非组件）
- 2026-08-25：`identity/` 词汇 Javadoc + `package-info`（不是组件、Id≠实体、AgentKind 预留、TurnContext §6-4）；无运行时 logger
- 2026-08-25：`syscall/` 词汇 `package-info` + 类型边界（信封≠总线；Usage/ArgDigest 职责钉死）
- 2026-08-25：`session/` 组件 `package-info` + 端口边界（三 store / Audit≠entries / Inbox≠调度器 / Projection≠真相）
- 2026-08-25：`session.memory`/`sqlite`/`conformance` package-info；日志口径=entries/ledger/AuditSink（无 slf4j）；AttrsJson 手写理由；单写者≠多副本
- 2026-08-25：session conformance 套件迁 `src/test`；`ClaimLease.DEFAULT_TTL`；`tepeu-os-conformance` 仅 test scope
- 2026-08-25：bus/policy/llm/loop/orchestration 套件同样迁 `src/test`；compose 经 bus test-jar 跑 BusConformance；execution 去掉无用 conformance 依赖
- 2026-08-25：删发行路径 `session.memory` 包；投影总线升到 `com.tepeu.os.session`；`memory` 只留测试源夹具
- 2026-08-26：发行投影实现改名 `LocalProjectionBus`；`InMemory*` 只用于测试夹具
- 2026-08-26：钉死 ProjectionBus 形态——契约是接口；本机也只依赖接口；其他组件可实现可不实现；MQ/Redis 升版再落，本骨架不引入 broker
- 2026-08-26：`LocalProjectionBus` drop / 消费者失败走 JDK `System.Logger`（不进 entries、不加 slf4j）；消费者异常不阻断其他订阅
- 2026-08-26：抽出 `persist` 库组件；session/policy 只留端口；JDBC 只在 persist.sqlite
- 2026-08-26：抽出 `observation` 组件；入口 `Observation.view`（derive ∘ normalize ∘ shape）；llm 只投影+传输；PromptAssembly 仍独立
- 2026-08-26：persist 引擎接口 `Persist`；host 选 `SqlitePersist.file`；compose 只接线；会话/审批分库、一套 JDBC
- 2026-08-26：persist.sqlite 开库/关库/JDBC 回滚走 JDK `System.Logger`（`component=persist class=...`）；schema 表有注释；不加 slf4j，不进 entries
- 2026-08-27：`Persist` 无 `open`；调用方只认接口 CRUD；sqlite 是 JDBC 实现（无 ORM）；host 注入
- 2026-08-27：host 把 `Persist` 挂成 Spring bean 再交给 compose；`os/` 仍零 Spring
- 2026-08-27：完成权唯一、控制循环不唯一（ADR-016 + 红线 §6-8）。`completed` 只由 entries + `CompletionGate`。重试资格统一入账本仍挂账
- 2026-08-27：组织原则与 OS 类比——证据在①、宣判在③；Inbox≠调度器；不按 CC 整机；不为更像 OS 开调度切片
- 2026-08-27：Persist 收成引擎（`store(name)`）；session/policy 适配器落领域；persist/api 不再依赖 session/policy
- 2026-08-28：删 `Persist.store(name)`；host `tepeu.sqlite.*` 配路径
- 2026-08-28：最小引入 spring-jdbc 7.0.8；删 PersistStore/Tx/Row；`SqliteDataSources` + JdbcTemplate

## 待办

- live 往返未在 CI 烧（无 key skip）
- 处置链 / 可配置 deny-sequence 规则面 — 见安全系列；未裁决前不空开 `gate/`·`response/`
- ⑤ UI / 完整 SSE / 向量记忆
- 多副本 fencing / timer 轮 / 哈希链 — 远期
- 重试资格统一入账本（哪步成败、还能不能再试）— 未统一前禁止另开成功/再试旁路

## 调试

```bash
mvn -f os/pom.xml test
mvn -f os/pom.xml install -DskipTests
mvn -f host/pom.xml test
# live（可选）：ANTHROPIC_API_KEY=... mvn -f os/pom.xml -pl llm -am test
```
