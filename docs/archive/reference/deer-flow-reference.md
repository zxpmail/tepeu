# DeerFlow × Tepeu

> **地位**：存档备查。⑤ 超级 Agent 工作台（ByteDance / LangGraph），**不是** Java OS。不进底板。  
> **来源**：`C:\test\deer-flow-main`（[bytedance/deer-flow](https://github.com/bytedance/deer-flow) 2.0 重写）。2026-08-31 抽检 README、backend Lead Agent / ThreadState / Session Goals。未跑其测试。

Lead Agent + 9 条 middleware + 子代理 + 沙箱 + 记忆 + Skills/MCP。状态在 LangGraph checkpoint（`ThreadState`：messages / goal / delegations / summary_text…）。完成可以挂一条自然语言 `/goal`，跑完后用评估模型判满不满，不满就隐式续跑（默认最多 8 次）。压缩是摘要 + 最近消息，聊天全文仍给 UI 看。

**能吸收的：没有。** 这是产品 harness，循环在图和中间件里。内核无调度器、完成权在 `CompletionGate`，两边反着。

| DeerFlow | Tepeu | 吸不吸 |
|----------|--------|--------|
| `/goal` + LLM 评估器 + 隐式续跑 | 证据在 entries，宣判在门 | 不。完成不是模型自报、也不是旁路评估环 |
| `summary_text` / `/compact` | `replaceRange` + checkpoint | 已有。不抄「摘要当模型面、全文给 UI」当内核 |
| `merge_delegations` 终态不降级 | gnex 已对过终态单调 | 旧账，不重挂 |
| 子代理 + batch SQL + 调度容量 | Inbox ≠ 调度器 | 不 |
| 多 worker 租约取消 / RunStore | ⑤ / 未落多副本 | 不抄进 `os/` |
| 沙箱 / Skills / MCP / IM | execution / ⑤ | 产品，不 |

反面：用自然语言目标 + 评估模型当完成门；用 Graph/middleware 当内核循环。

不新开 ADR / jar / §8.5。
