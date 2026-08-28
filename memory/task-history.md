# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-08-28 | persist-access | **Persist=统一访问口** | 组件只 jdbc/tx/script；关库在 host/测试；不复活 PersistRow |
| 2026-08-28 | persist-engine-api | **persist/api=PersistEngine** | 连接仍是 DataSource；引擎口 ServiceLoader；host 不 import sqlite |
| 2026-08-28 | persist-spring-ds | **可换口=Spring DataSource** | 删 HostDataSources；host DataSourceBuilder+Hikari |
| 2026-08-28 | persist-jdbc-url | **开库认 JDBC URL** | 删 open(Path) 引擎口；tepeu.datasource.url |
| 2026-08-28 | persist-spring-jdbc | **最小 Spring JDBC** | 删 PersistStore/Tx/Row；os 不启 Boot、不上 ORM |
| 2026-08-27 | persist-engine-invert | **Persist 不认识 Session** | 适配器回 session/policy；换库改 SQL |
| 2026-08-27 | os-analogy-honesty | **组织原则与 OS 类比** | 证据在①、宣判在③；不为像 OS 开调度切片 |
| 2026-08-27 | completion-authority | **完成权唯一、控制循环不唯一** | completed 只许账本+门 |
| 2026-08-26 | persist-nested-pom | **persist POM 嵌套** | os 只列 persist/；api+sqlite 子模块；插头不抬一级 |
| 2026-08-26 | persist-split-jar | **Persist 与 sqlite 分 jar** | 契约 persist；插头 persist-sqlite；compose 依赖插头 |
| 2026-08-26 | persist-engine | **persist 引擎插头** | compose 选 Persist；sqlite 一套 JDBC 分两库；不是再写一份 SessionStore |
| 2026-08-26 | observation-entry | **模型可见管道成组件** | 入口 Observation.view；llm 只投影+传输；不是第四 store；PromptAssembly 仍独立 |
| 2026-08-26 | persist-component | **库是组件** | JDBC 只在 persist；session/policy 只暴露端口；compose 接线；换库不改领域 |
| 2026-08-26 | projection-log-locate | **投影诊断带组件/类** | 每条日志 `component=session class=LocalProjectionBus` |
| 2026-08-26 | projection-bus-log | **投影诊断可见** | drop/消费者失败记 JDK System.Logger；不进 entries；不加 slf4j；异常不阻断其他订阅 |
| 2026-08-26 | projection-port-not-mq | **ProjectionBus 形态入文档** | 契约=接口；Local 只是本机插头；其他组件可不实现；MQ 升版，本骨架不引入 broker |
| 2026-08-26 | local-projection-bus | **LocalProjectionBus** | 发行投影实现不再叫 InMemory；InMemory* 仅测试夹具 |
| 2026-08-25 | drop-main-session-memory | **删除发行路径 session.memory 包** | 投影总线迁 session 根包；memory 仅测试源 |
| 2026-08-25 | inmemory-out-of-jars | **会话/审批内存夹具迁 test** | InMemorySessionStore 等不进发行 jar；InMemoryAssembly 仅测试源；test-jar 供下游 |
| 2026-08-25 | conform-out-of-jars | **全组件 conformance 迁 test** | 套件不进发行 jar；bus 出 test-jar 供 compose；execution 去掉无用 conformance 依赖 |
| 2026-08-25 | session-conform-testsrc | **conformance 迁 test + ClaimLease.DEFAULT_TTL** | 套件不进发行 jar；TTL 生产常量；conformance dep=test |
| 2026-08-25 | session-adaptor-docs | **session memory/sqlite/conformance 注释+日志口径** | 单写者插头；真相=entries/Audit；无 slf4j；AttrsJson 手写理由 |
| 2026-08-25 | session-port-javadoc | **session 组件 package-info + 端口边界** | 三 store/Audit≠entries/Inbox≠调度器；多副本仍挂账 |
| 2026-08-25 | syscall-vocab-javadoc | **syscall 词汇边界写入 package-info** | 非组件；信封≠总线；与 identity 对称 |
| 2026-08-25 | identity-vocab-javadoc | **identity 词汇边界注释** | package-info + 各类型钉「不是什么」/预留/§6-4；无 slf4j |
| 2026-08-25 | host-config-comments | **host base-url 可配 + 中文注释** | tepeu.base-url/api-key；Javadoc 中文 |
| 2026-08-25 | host-fix | **host family/exit/PromptAssembly** | 传输族对齐；ExitCodeGenerator；base 段经 PromptAssembly |
| 2026-08-25 | host-out-of-os | **host 挪出 os/** | 根目录 host/；artifact tepeu-host；非内核组件 |
| 2026-08-24 | os-host-cli | **Spring Boot 4 CLI 宿主** | os/host 接 SqliteAssembly；REPL + chat 子命令；无 Web |
| 2026-08-24 | os-projection-memory | **ProjectionBus + KnowledgeSource v1** | UI 投影 since 游标 + catchUp；memory_hits 须有 sourceId；compose Wired 接线 |
