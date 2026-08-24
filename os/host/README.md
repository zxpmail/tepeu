# host — CLI 宿主（⑤ 应用面 v0）

Spring Boot 4 **无 Web** 壳，接 `SqliteAssembly` 发行接线。UI / SSE 后续加在本模块或 sibling。

## 运行

```bash
# 交互 REPL（无 API key 时用 fake LLM 并打 WARN）
mvn -f os/pom.xml -pl host -am package -DskipTests
java -jar os/host/target/tepeu-os-host-0.0.1-SNAPSHOT.jar

# 单次对话
java -jar os/host/target/tepeu-os-host-0.0.1-SNAPSHOT.jar chat hello

# 开发态（须先 install 依赖模块）
mvn -f os/pom.xml -pl host -am install -DskipTests
mvn -f os/host/pom.xml spring-boot:run -Dspring-boot.run.arguments="chat hello"
```

## 配置（`application.properties` / 环境）

| 键 | 默认 | 说明 |
|----|------|------|
| `tepeu.data-dir` | `~/.tepeu` | kernel.sqlite + workspace |
| `tepeu.model` | `claude-sonnet-4-20250514` | LoopConfig.model |
| `tepeu.principal-id` | `cli-user` | 会话 owner |
| `tepeu.workspace-id` | `default` | workspace |
| `tepeu.fake-llm` | `false` | true 强制 fake；false 时读 `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` |

宿主**可以**读 LLM 环境变量（与 compose 不同；compose 由调用方注入）。

## REPL

- 普通行 → Inbox → `SessionLoop.run`
- `/help`、`/approve …` → `CommandDispatcher`（不经模型）
- `:quit` / `:exit` 退出
- `:session` 打印当前 session id

完成证据仍由 Loop `CompletionGate` 判定；CLI 只打印 ASSISTANT 等新事件摘要。
