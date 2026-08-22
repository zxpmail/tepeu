# os/ — Tepeu 重写骨架（develop）

> **身份陈述（目标态——实现进度见下表，未实现者不以现状宣称）**：不变量进内核概念，能力全是组件。组件 ≠ 插件。洋葱是依赖方向，不是两个大 jar。

实施底板：[docs/os-baseplate.md](../docs/os-baseplate.md)  
规范：[memory/decisions-log.md](../memory/decisions-log.md) ADR-016

## 规则

- **组件** = 有人能单独拥有、最好能单独测的能力。一个类型不够成组件。
- 默认实现跟组件走。compose 只接线。空 README 可留，空 jar 不预开。
- 新代码只进 `os/` 某模块；禁止在 `legacy/` 加功能。

## 现网

**组件（6）**

| 模块 | 一件事 | 独立测 |
|------|--------|--------|
| `session/` | 会话三 store + Metering 端口 | `mvn -f os/pom.xml -pl session test` |
| `policy/` | Policy + 审批 | — |
| `bus/` | 总线分发 + 卫兵 | `mvn -f os/pom.xml -pl bus test` |
| `llm/` | llm.* 派生式断言 + fake 传输 | `mvn -f os/pom.xml -pl llm test` |
| `loop/` | claim → 有界 turn（含工具）→ 完成证据门 | `mvn -f os/pom.xml -pl loop test` |
| `compose/` | 开机接线 | `mvn -f os/pom.xml -pl compose test` |

**不是组件**

| 模块 | 角色 |
|------|------|
| `identity/` | 词汇：谁 / 在哪 / 哪次会话 / TurnContext |
| `syscall/` | 词汇：调用信封 + Usage |
| `conformance/` | 测试 harness |

尚未落码：maintenance / PromptAssembly / Command。`orchestration/` 仍是环索引。真 HTTP 随后一刀。

```bash
mvn -f os/pom.xml test
mvn -f os/pom.xml -pl loop test
```
