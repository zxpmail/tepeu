# ① 内核

冻住三件：主体+命名空间、能力总线、会话（日志+Inbox+主/子+日志替换端口）。

## 包结构（端口，无 Spring）

| 包 | 内容 |
|----|------|
| `identity` | Principal / Namespace / AgentKind |
| `context` | TurnContext（显式传递，禁单例 bind） |
| `session` | Session / Log / Inbox / LogReplacePort / Registry |
| `bus` | CapabilityBus / Syscall / PolicyHook / GuardHook |

总线入口只钩 Policy+卫兵；不实现 Tool/LLM/UI。实现见 `os/adaptors`。
