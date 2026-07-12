# 开发导航地图（dev-map）

> 谁动代码谁改地图。改结构时必须同步更新。
> 安装来源：`core/templates/dev-map-template.md` → 用户项目 `.forge/dev-map.md`（`pnpm forge-install` 写入）

---

## 技术栈

> **必填**。流水线所有 Skill 依赖此节判断构建、测试、lint 命令和代码规范。

- Language: Java 21 (backend) + TypeScript 5.x (frontend)
- Build:    `cd frontend && npm run build && cd ../backend && mvn -q package` (全量构建：前端打包到 static + 后端 JAR)
- Test:     `cd backend && mvn -q test` (后端测试，当前 0 用例); `cd frontend && npm run typecheck` (前端类型检查)
- Lint:     后端未配置 checkstyle（pom 无插件）；前端仅 `npm run typecheck`（package.json 无独立 lint 脚本）
- Source:   `backend/src/main/java/` (后端); `frontend/src/` (前端)


## 模块索引

| 模块 | 关键文件 | 说明 | 改动影响链 |
|------|---------|------|-----------|
| 加密 (§7.4) | `service/CryptoService.java` | AES-256-GCM；主密钥 `~/.tepeu/master.key`；`enc:v1:` 格式；`mask()` 脱敏 | 改算法/格式 → 所有已存 key 不可解；丢主密钥=不可恢复 |
| LLM Provider | `service/LlmProviderService`、`controller/ProviderController`、`repository/LlmProviderRepository`、`model/LlmProvider` | 凭证 CRUD：写入加密 / 读出解密 / GET 脱敏。**testConnection 已移出**（见 Chat 行） | 改加密点 → 遗留明文行迁移 |
| Chat (SSE) | `service/chat/ChatService`、`service/chat/ChatModelFactory`、`controller/ChatController`、`agent/AgentOrchestrator`、`agent/tool/{FileTools,ToolEventEmittingCallback,ToolEventEmitter}` | 流式对话 + 工具循环（Spring AI 2.0 `ChatClient`+`@Tool`）。`ChatService.testConnection`（build model + `model.call("ping")`）；`streamWithTools` 单次 `.toolCallbacks(wrapped)` | `ChatModelFactory`→`LlmProviderService` 单向依赖；改 testConnection 勿回 LlmProviderService（循环）。Spring AI 2.0 `ToolCallback...` API 均 @Deprecated（装饰器路径，carry-over） |
| Memory | `repository/MemoryRepository`、`controller/MemoryController`、`service/MemoryService` | 记忆 CRUD + LIKE 搜索；tags 以 JSON 存 | RowMapper 依赖注入的 ObjectMapper（Jackson 3） |
| Workspace | `controller/WorkspaceController`、`service/WorkspaceService`、`repository/WorkspaceRepository` | Workspace CRUD | `owner_id` 硬编码 `"local"`（无认证） |

## 已有模式

| 模式 | 位置 | 说明 |
|------|------|------|
| _记录项目中的标准写法、惯用模式_ | | |

## 注意事项

- _记录踩过的坑、不可碰的红线_
- **Spring AI 2.0 GA starter 命名为 `spring-ai-starter-model-*`**（如 `spring-ai-starter-model-openai`），不是旧的 `spring-ai-*-spring-boot-starter`（2.0 已废弃）。BOM：`org.springframework.ai:spring-ai-bom:2.0.0`。无版本号靠 BOM 管理。
- **构建工具是 Maven（pom.xml）**，不是 Gradle。`backend/gradle/` 是空目录残留。后端命令用 `mvn`，无 gradlew。
- Phase 1 后端原 pom 用了旧 starter 名 + MemoryRepository 空_final 捕获 + ProviderController 泛型推断三处编译错误，已于本会话修复（2026-07-11）。
- **Boot 4 默认 Jackson 3**（`tools.jackson.*`），注入 ObjectMapper 用 `tools.jackson.databind.ObjectMapper`；旧 `com.fasterxml.jackson.databind.ObjectMapper` 无自动 bean。
- **SQLite URL 禁用 `?mode=wal`**（Windows `?` 非法文件名字符）；WAL 由 `DatabaseConfig` PRAGMA 设。
- 本地 Maven repo = `D:\maven\repo`（非 ~/.m2）。

---

*dev-map · 开发 Agent 维护，PM 不负责改地图。先搞清这里已经长成了什么样，再下手。*
