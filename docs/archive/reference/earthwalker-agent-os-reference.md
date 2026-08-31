# earthwalker Agent OS × Tepeu

> **地位**：存档备查。⑤ 本机编码 harness（FastAPI + React + SQLite），**不是** Java 内核。不进底板。  
> **来源**：https://github.com/earthwalker17/agent-os。2026-08-31 读 README / ARCHITECTURE。未 clone、未跑其 820 测。  
> **别和** 本仓 `docs/agent-os-gap.md`、也别和 Builder Methods 那套 skill 框架混。

口号 `LLM + Harness = Agent`。Main Agent 管对话和记忆，不改 `repo/`、不跑 shell；Coding Agent 在沙箱里用 6 个工具干活，不改记忆。完成要过真 build / Playwright；失败走 typed Recovery Matrix（预算 ≤2）。交付（push / Vercel / Stripe）全是 preview→confirm。

**能吸收的：没有。** 「模型不能自己批作业」你们已经冻了：证据在 entries，宣判在 `CompletionGate`。他们用 npm build 当门，是产品验证，不是新不变量。

| 它 | Tepeu | 吸不吸 |
|----|--------|--------|
| `completed` 要过验证 | 完成权在门 | 已有。不把「绿构建」写进内核 |
| Recovery Matrix + 子跑修复 | 重试资格入账本（挂账） | 不另开修复环 |
| 一条沙箱咽喉 | execution + Policy | 已有 |
| 脑/手拆开、摘要通信 | Inbox ≠ 调度器 | 不 |
| 并行 team ≤3 + wave scheduler | 内核无调度器 | 不 |
| 验证失败不卡 `running`（best-effort tail） | fail-closed 纪律 | 不抄「尾巴永远成功」 |

反面：用工作台流水线当 OS；并行调度进内核。

不新开 ADR / jar / §8.5。
