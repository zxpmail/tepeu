# 分层图示（短投影）

> 完整实施底板见 [`os-baseplate.md`](./os-baseplate.md)。规范以 **ADR-016** 为准。

```text
⑤ 应用        UI · Skill/记忆资产 · 面板
③ 编排        兜底Agent · Loop · Team/Subagent/LongTask
              PromptAssembly · ReasoningPresenter · Command(Slash)
              路由三决策(Thread·Flow·Model，默认透传)   ←④已并入③(第八轮)
② 适配        内核必需端口实现(Store/Claim/Policy·Approval/Metering)
              + syscall 命名族处理器(execution.*/llm.*/fs/…)
              支撑服务：Audit · Identity/Secret · Knowledge · ProjectionBus
① 内核        主体+命名空间 · 能力总线〔入口钩Policy+卫兵(超时/取消/不变量/配额限流)〕
              会话三 store(entries/registers/ledger；surface替换端口)
```

**三路**：对话主路 → 会话日志；人手旁路 → AuditSink；Slash → Command→(可选总线)。  
**底板 = 内核三件。** 压缩属 Compaction 缝，经总线 `llm.*`。
