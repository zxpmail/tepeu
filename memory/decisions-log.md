# Decisions Log

## ADR-000: Tech Stack Selection (2026-07-10)
- **Decision**: Spring Boot 4.0+ + Spring AI 2.0.0 GA + Java 21 + SQLite + React 18 + Vite 6
- **Rationale**: Spring AI 2.0 GA (Jun 2026) requires Spring Boot 4.0. Java 21 virtual threads for concurrency. SQLite for zero-config single-machine deployment.
- **Alternatives considered**: Spring Boot 3.4 + Spring AI 1.x (user explicitly chose Boot 4.0)

## ADR-001: Single JAR Deployment (2026-07-10)
- **Decision**: Frontend SPA is built to backend/src/main/resources/static/ and served from Spring Boot
- **Rationale**: Dev mode uses Vite proxy to backend for HMR; production single JAR simplifies distribution

## ADR-002: SQLite with WAL Mode (2026-07-10)
- **Decision**: SQLite with WAL mode for concurrent read performance
- **Rationale**: v1.0 is single-machine only. WAL allows >10 concurrent agents (mostly reads) without contention

## ADR-003: EventLoop State Management (2026-07-10) — RETIRED 2026-07-11
- **Decision (原)**: React 状态管理用自定义 EventLoop 模式（dispatch→scheduler→state）
- **Rationale (原)**: Netty 风格单线程事件循环，避免分散 setState
- **Retirement (2026-07-11, code-review C3)**: `store/eventLoop.ts` 从未被任何组件引用（零 importer），实际状态管理全用 `useState`。属"声称合规但实际不存在"的 drift。**已删除 eventLoop.ts**。Phase 1 状态管理正式 = useState（hooks 局部）+ props；统一调度器推迟到确有需要时再引入。Spec §5.4.6 / DEV-PLAN / project-memory 相应描述已更正。

## ADR-004: Database Layer (2026-07-10)
- **Decision**: Spring JdbcTemplate instead of JPA for SQLite
- **Rationale**: JPA + SQLite dialect support is finicky. JdbcTemplate provides direct control without ORM complexity

## ADR-005: No Authentication in Phase 1 (2026-07-10)
- **Decision**: Phase 1 local mode has no authentication
- **Rationale**: Single-machine deployment. Auth (OAuth2/OIDC) deferred to Phase 2 enterprise mode

## ADR-006: API Key At-Rest Encryption (2026-07-11)
- **Decision**: AES-256-GCM；主密钥首启自动生成（32 字节随机），base64 写入 `<user.home>/.tepeu/master.key`（POSIX owner-only 权限；Windows 继承 home 目录 ACL）。可经 `tepeu.security.master-key-file` 覆盖。
- **Rationale**: §7.4 要求加密存储。单机本地 + Phase1 无认证 → 零配置优先（自动密钥文件 > 环境变量 > OS DPAPI）。GCM 提供认证加密。DB 单独被拷走无法解密。
- **Spec 偏差声明 (code-review M1)**: Spec §7.3/§7.4 原文写「SQLite 加密扩展（SEE）」；本实现改用**应用层 AES-256-GCM**（仅加密 `llm_provider.api_key` 列，非整库）。理由：xerial `sqlite-jdbc` 不带 SEE 构建/授权；应用层 GCM 提供认证加密且 DB 单独被拷走不可解。**代价**：memory/workspace 行在磁盘仍为明文（Phase 1 单机可接受；Phase 2 若需整库加密再评估 SEE/SQLCipher）。
- **Stored format**: `"enc:v1:" + base64(iv(12B) ‖ ciphertext ‖ tag(16B))`。非 `enc:` 前缀值视为遗留明文（decrypt passthrough，重存即加密）。
- **GET 行为**: 永不回显明文/密文；脱敏 `first(3)+••••+last(4)`。服务层解密后返回明文给内部调用（Phase 2 agent 用真 key）。
- **实现**: `CryptoService`（加解密+主密钥）；`LlmProviderService` 写入加密/读出解密；`ProviderController` 脱敏回显。
- **Alternatives**: 环境变量（需运维管理）、Windows DPAPI（Java 调用复杂需 JNI/JNA）、passphrase 派生（与无认证冲突）。
- **Caveat**: 主密钥文件**需备份**，丢失则已存 API key 不可恢复。

## ADR-007: testConnection 实现位置 + streamWithTools 工具注册 (2026-07-11)
- **Decision (testConnection)**: `LlmProviderService.testConnection` 占位（恒 `return true`）移除，真实实现放 `ChatService.testConnection(providerId)`——build `ChatModel`（经 `ChatModelFactory`，复用校验+解密）+ `model.call(new Prompt(new UserMessage("ping")))` + 任何 `RuntimeException` → `false`。`ProviderController` 注入 `ChatService` 调用之。
- **Rationale**: `ChatModelFactory` 依赖 `LlmProviderService`（单向）；若 `testConnection` 留在 `LlmProviderService` 并注入 `ChatModelFactory` 会构成构造器循环依赖。`ChatService` 已持有 `ChatModelFactory` 且语义上是"发一次 chat call"的归属层，无循环。真实 round-trip（非仅校验配置）才能验"连接测试成功"（Phase 2 验收标准 1）——占位 `true` 使该标准形同虚设。
- **Cost**: Test 按钮触发一次真实（云厂商计费、极小）调用；Ollama 本地免费。可接受（手动触发）。
- **Decision (streamWithTools M1)**: 原 `ChatService.streamWithTools` 三重注册工具（`defaultToolCallbacks(wrapped)` + `.tools(fileTools)` + `.toolCallbacks(wrapped)`）→ 改为单次 per-request `.toolCallbacks(wrapped)`。`wrapped` 已装饰 `ToolCallbacks.from(fileTools)`，`.tools()` 冗余；模型原本会收到重复 tool schema。
- **Rationale**: 装饰器模式（`ToolEventEmittingCallback` 包 `ToolCallback` 做工具事件可视化）要求注册预构建 `ToolCallback[]`，而 Spring AI 2.0 中接受 `ToolCallback...` 的两个 API（`defaultToolCallbacks` + `toolCallbacks`）**均已 @Deprecated**；非 deprecated 的 `.tools(Object...)` 只接受裸 `@Tool` bean（内部 `ToolCallbacks.from`），无法注入 wrapped 回调。故装饰器路径暂不可避免地走 deprecated API（原代码即如此，本改动把 deprecated 调用从 2 处减到 1 处）。
- **Carry-over**: 真实 LLM e2e 未验（机器离线）——工具循环是否实际触发/不重复执行，待用户提供 key 后验证。若 deprecated API 未来移除，需改用 `ToolCallingManager` 自定义或 Advisor 观察工具执行（更大重构）。
