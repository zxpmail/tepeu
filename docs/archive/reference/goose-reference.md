# Goose × Tepeu

> **地位**：存档备查。⑤ 本机 Agent（Rust Desktop/CLI/API + MCP），**不是** Java OS。不进底板。  
> **来源**：`C:\test\goose-main`（[aaif-goose/goose](https://github.com/aaif-goose/goose)）。2026-08-31 抽检 `crates/goose-agent`、`goose-context-management`、权限枚举。未跑其测试。

`goose-agent` 把循环拆成步骤表：每步看**持久化对话**，要么不处理要么写 effect；机器每轮 `load` 会话，**不缓存**。行为是对话的函数，不是内存环状态。压缩是把一段 history 收成一条 summary。权限有 AlwaysAllow / AllowOnce / DenyOnce / AlwaysDeny。

**能吸收的：没有。** 「状态 = 投影(日志)」你们已经冻了。他们用 conversation 当账本，你们用 entries。同形，不是新不变量。

| Goose | Tepeu | 吸不吸 |
|-------|--------|--------|
| 每 pass `load` 会话再派生 | `Observation.view(surface)` | 已有 |
| 步骤记忆写在 message meta | 事实进 entries | 已有 |
| 压缩 → 单条 summary | `replaceRange` + checkpoint | 已有。不抄 Provider 外包 compact |
| `ends_turn` / stop-hook 挡结束 | `CompletionGate` | 不让 hook 当完成权 |
| AlwaysAllow 会话记住 | 审批单次 consume | 不退 |
| MCP 扩展 / Recipe / 桌面 / roam P2P | ⑤ / 未落 Team | 不 |
| `crates/goose/.../scheduler.rs` | 内核无调度器 | 不 |

不新开 ADR / jar / §8.5。
