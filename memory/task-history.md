# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
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
