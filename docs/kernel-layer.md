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

---

## 内核内部架构（2026-08-16，✓已落码 / ⏳已裁待码 / ✗缺失）

```text
                        ③ 编排（Loop/Team，待建）
                          │ 一切调用显式携带 TurnContext
                          │ (principal · namespace · session · delegation · cancel)
                          ▼
┌──────────────────────── ① 内核（唯一门 + 账本）──────────────────────────────┐
│                                                                              │
│  ┌────────────────────── 能力总线 CapabilityBus ──────────────────────┐     │
│  │  invoke(ctx, syscall) 的五道闸（fail-closed 全路径 ✓）               │     │
│  │                                                                     │     │
│  │   ①ctx 已取消？──────────── 是 ──▶ BusGuardException ✗              │     │
│  │      │                                                              │     │
│  │   ②卫兵链 before×N（顺序）                                           │     │
│  │      │  卫兵异常 ──▶ ✗（规范化为拒绝，不穿透）                        │     │
│  │      │  ⏳ 卫兵无 verdict 载体 → deny>ask>allow 组合代数无处落        │     │
│  │      │                                                              │     │
│  │   ③PolicyHook.evaluate → 封闭 union {ALLOW, DENY, NEED_APPROVAL}    │     │
│  │      │  ALLOW ──▶ 继续                                              │     │
│  │      │  DENY ──────────▶ PolicyDeniedException ✗                   │     │
│  │      │  NEED_APPROVAL ─▶ ✗（现状与 DENY 同命运：ask 无挂起端口 C1）   │     │
│  │      │  异常/null ────▶ 规范化为 DENY ✗（词汇表外=fail-closed）      │     │
│  │      │  ⏳ 未装配 Policy 时默认 ALLOW（C2 待裁，现与身份陈述相抵）      │     │
│  │      │                                                              │     │
│  │   ④syscall 注册表（按名分发）                                         │     │
│  │      │  命中 ──▶ SyscallHandler.handle ──▶ SyscallResult            │     │
│  │      │            └ handler 异常/null ──▶ failure 结果（不抛穿）     │     │
│  │      │  未注册 ──▶ failure("NOT_FOUND")（失败可见，不伪装）           │     │
│  │      │  ⏳ 注册表无确定性规范序（缓存键不变量已裁未码）                 │     │
│  │      │                                                              │     │
│  │   ⑤卫兵链 after×N（观察结果）                                         │     │
│  │  ⏳ SyscallResult 无 usage/latency 计量槽（挂账 llm.* 刀）            │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  ┌────────────────────── 会话设施 Session（三 store）─────────────────┐     │
│  │                                                                    │     │
│  │  entries  对话事实日志（append-only）≈70%                           │     │
│  │    SessionLog.append → seq 连续分配 ✓ | readAll ✓ | get(seq) ✓      │     │
│  │    LogReplacePort.replaceRange(from,to) → surface 替换 ✓            │     │
│  │      ├ 底层审计日志只增不删 ✓（checkpoint 也只是追加）                │     │
│  │      └ surface 投影：checkpoint 插区间位（模型读面）✓                 │     │
│  │    词汇表 7 类 manifest 钉死 ✓ ⏳ per-type 版本化结构未落              │     │
│  │    ⏳ fork/end-seed：parentId 字段在、语义与种子区校验无              │     │
│  │                                                                    │     │
│  │  registers  覆写可变状态（恢复=点查）≈40%                            │     │
│  │    Inbox：enqueue → FIFO → claimNext ✓                              │     │
│  │            租约 TTL 300s + 死租约惰性回收 ✓                          │     │
│  │            → ack ✓ / nack 归还 ✓                                    │     │
│  │    ⏳ priority now/next/later 未落；✗ 分支 leaf/模型配置寄存器未建模    │     │
│  │                                                                    │     │
│  │  ledger  用量记账（append-only）0%                                   │     │
│  │    ✗ 完全没有——内核必需端口 Metering 同缺（kernel 端口演化刀）         │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  identity：Principal(个人/Agent) × Namespace(workspace→将来租户) ✓           │
│  conformance：Session 12 + Bus 13 用例，随包发布、runner 无关 ✓               │
│  （AuditSink 不在内核——人手审计真相在 ②；内核只立双真相的边界）                 │
└──────────────────────────────────────────────────────────────────────────────┘
      ▲ 注册（llm.* / execution.* / 工具…）          │ SyscallResult
      │                                              ▼
┌──────────────────── ② 适配（门另一侧，可换实现）──────────────────┐
│  handlers：LlmProvider 双协议族 ⏳ | Execution+Sandbox ⏳ | 工具 ⏳  │
│  内核必需端口实现：InMemorySession/Bus ✓（过 conformance）           │
│                   Policy 完整实现 ⏳ | Metering ⏳ | SQLite 后端 ⏳   │
└─────────────────────────────────────────────────────────────────────┘
```

### 待验点（画图时暴露、尚未裁决——详见底板 §8.5）

1. **总线不自动写会话日志**：TOOL_CALL/TOOL_RESULT 事件由 ③ 编排落 entries，总线只管门。备选：总线自动追加工具事件（journal 更强，但总线与事件类型耦合）。
2. NEED_APPROVAL 与 DENY 同命运（= C1 审批端口挂账）。
3. 未装配 Policy 默认 ALLOW（= C2 挂账，与身份陈述相抵）。
4. **registers 无通用端口**：Inbox 租约表是事实寄存器；分支 leaf/模型配置是建统一 `RegisterStore` 端口还是各设施自管——未裁。
