# os/ — Tepeu 重写骨架（develop）

> **身份陈述（目标态——实现进度见下表，未实现者不以现状宣称）**：不变量进内核概念，能力全是组件。组件 ≠ 插件。洋葱是依赖方向，不是两个大 jar。

实施底板：[docs/os-baseplate.md](../docs/os-baseplate.md)  
规范：[memory/decisions-log.md](../memory/decisions-log.md) ADR-016

## 规则

- **组件** = 有人能单独拥有、最好能单独测的能力。一个类型不够成组件。
- 领域组件只暴露端口。访问口是 persist/api {@code Persist}；引擎口是 {@code PersistEngine}。compose 只接线。
- 新代码只进 `os/` 某模块；禁止在 `legacy/` 加功能。

## 现网

**组件（10）**

| 模块 | 一件事 | 独立测 |
|------|--------|--------|
| `session/` | 会话三 store + 适配器 Spring JDBC | `mvn -f os/pom.xml -pl session -am test` |
| `policy/` | Policy + 审批适配器 Spring JDBC | `mvn -f os/pom.xml -pl policy -am test` |
| `persist/` | Persist 访问口 + PersistEngine + sqlite | `mvn -f os/persist/pom.xml test` |
| `observation/` | 模型可见管道：derive ∘ normalize ∘ shape；入口 `Observation.view` | `mvn -f os/pom.xml -pl observation -am test` |
| `bus/` | 总线分发 + 卫兵 | `mvn -f os/pom.xml -pl bus -am test` |
| `llm/` | llm.* 派生式断言 + fake + Anthropic/OpenAI HTTP 薄壳 + live opt-in | `mvn -f os/pom.xml -pl llm -am test` |
| `loop/` | claim → 有界 turn → 完成门；overflow 压缩；maintenance 窗 | `mvn -f os/pom.xml -pl loop -am test` |
| `orchestration/` | PromptAssembly + CommandDispatcher（local/prompt） | `mvn -f os/pom.xml -pl orchestration -am test` |
| `execution/` | 工作区囚笼 + Job Object/bwrap（隔离 partial） | `mvn -f os/pom.xml -pl execution -am test` |
| `compose/` | 开机接线 | `mvn -f os/pom.xml -pl compose -am test` |

**不是组件**

| 模块 | 角色 |
|------|------|
| `identity/` | 词汇：谁 / 在哪 / 哪次会话 / TurnContext |
| `syscall/` | 词汇：调用信封 + Usage |
| `conformance/` | 测试 harness |

**⑤ 应用（仓库根，不在本树）**：[`../host/`](../host/) — Spring Boot 4 CLI 宿主，依赖 compose；UI/SSE 后续。

尚未落码：Team / Subagent / LongTask / 路由三决策。PromptAssembly / Command 已在 `orchestration/`；Loop 不依赖该模块。

```bash
mvn -f os/pom.xml test
mvn -f os/pom.xml -pl compose -am test
```
