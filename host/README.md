# host — CLI 宿主（⑤ 应用面 v0）

> **不在 `os/` 内**。内核组件在 `os/`；本模块是可跑进程，依赖 `tepeu-os-compose`。

Spring Boot 4 **无 Web** 壳，接 `SqliteAssembly`。UI / SSE 后续可加在本模块。

## 运行

```bash
# 先安装 os 组件到本地仓库
mvn -f os/pom.xml install -DskipTests

# 打包
mvn -f host/pom.xml package -DskipTests

# 交互 REPL（无 API key 时加 fake）
java "-Dtepeu.fake-llm=true" -jar host/target/tepeu-host-0.0.1-SNAPSHOT.jar

# 单次对话
java "-Dtepeu.fake-llm=true" -jar host/target/tepeu-host-0.0.1-SNAPSHOT.jar chat hello

# 测试（须先 install os）
mvn -f host/pom.xml test
```

## 配置

| 键 | 默认 | 说明 |
|----|------|------|
| `tepeu.data-dir` | `~/.tepeu` | kernel.sqlite + workspace |
| `tepeu.model` | `claude-sonnet-4-20250514` | LoopConfig.model |
| `tepeu.principal-id` | `cli-user` | 会话 owner |
| `tepeu.workspace-id` | `default` | workspace |
| `tepeu.fake-llm` | `false` | true 强制 fake；否则读 `ANTHROPIC_API_KEY` / `OPENAI_API_KEY` |
| `tepeu.cli.enabled` | `true` | 测试可关，避免 REPL 阻塞 |

宿主**可以**读 LLM 环境变量（compose **不**读）。

## REPL

- 普通行 → Inbox → `SessionLoop.run`
- `/help`、`/approve …` → `CommandDispatcher`（不经模型）
- `:quit` / `:exit` 退出
- `:session` 打印当前 session id
