# Task History

| Date | Session | Task | Key Decisions |
|------|---------|------|---------------|
| 2026-08-23 | os-history-hygiene | **修好 compaction hook 并收口乱码档案** | UTF-8；按 session 去重；真裁最近 25 条；archive 不再重复追加 |
| 2026-08-23 | os-review-fix | **审查修补（第二十三轮）** | surfaceEpoch 跳过压缩后 digest；CREATE_SUSPENDED 入 Job；argsDigest；jail NOFOLLOW；/approve 校验会话 |
| 2026-08-23 | os-skeleton-demo | **五层骨架可演示** | live opt-in；overflow 压缩；PLAN/FILE 完成门；Job Object/bwrap 仍 partial；/approve + policy.rules |
| 2026-08-23 | os-seams | **Compaction + DefaultRuleMatrix + execution 囚笼** | maintain 挂窗；write/proc ASK；isolation=partial；spawn 失败可见 |
| 2026-08-23 | os-kernel-issuance | **本机单写者内核可发行** | SQLite WAL schema v1；SqliteAssembly；MemoryAssembly 仅测试；ledger fail-closed |
| 2026-08-23 | os-kernel-contract | **内核契约收口** | fork+END_SEED；卫兵 deny>ask>allow；AuditSink；recover INTERRUPTED；sha256 CAS |
| 2026-08-23 | os-orchestration | **PromptAssembly + CommandDispatcher** | orchestration 第 7 组件；静/动分离；Slash 不经模型；Loop 不依赖本模块 |
| 2026-08-23 | os-loop-maintenance | **maintenance 窗 + DoomLoop** | 独占窗/上限/NOW 让位/latch；连续 3 次熔断 + NUDGE 入 TOOL_RESULT |
| 2026-08-23 | os-budget-gate | **预算硬门进往返** | LedgerMetering token 硬顶；Loop claim 前 STOPPED；compose 默认 unlimited |
| 2026-08-23 | os-llm-openai-http | **OpenAI HTTP 薄壳** | Bearer + `/v1/chat/completions`；body=prepare；prompt_tokens 拆缓存 |
| 2026-08-22 | os-llm-http | **Anthropic HTTP 薄壳** | JDK HttpClient；body=prepare；max_tokens 入 attrs；离线 stub |
| 2026-08-22 | os-loop-tools | **Loop 工具循环** | 先落 TOOL_CALL 再 invoke；合成 RESULT；不 import Tool 类 |
| 2026-08-22 | os-loop | **Loop 答复主路** | 阻塞式 + 显式门；CompletionGate；SessionRegisters；③ 写 entries |
| 2026-08-22 | os-llm | **llm 契约 + fake 传输** | derive(log)；normalize；digest 复核；禁 messages 入参 |
| 2026-08-22 | os-components | **os/ 按组件拆模块** | 洋葱是依赖方向；默认实现跟组件走 |
| 2026-08-18 | v1-brain-off-path | **启动路径改 develop 正文（只动文档）** | project-memory 改为 os/ 记忆，v1 全文进 archive/v1；CLAUDE 技术栈代码块改为 os/；Product-Spec 标题与七层/技术栈节标 v1 档案 |
| 2026-08-18 | v1-trap-banner | **启动路径钉 v1 标本（只动文档）** | project-memory 顶栏 + Architecture/Chat 标 v1；CLAUDE 技术栈标明 Forge 填空、ChatModel 不进 llm.* |
| 2026-08-18 | docs-cut | **文档蒸馏（只动文档）** | 手册只留独有章；删除 kernel-layer；对照+essay+superpowers+phase2-plan 进 docs/archive/ |
| 2026-08-18 | agent-os-gap | **距 Agent OS 差距落文档** | `docs/agent-os-gap.md`；kernel≠OS；成色债清单；禁止口径 |
| 2026-08-18 | gnex3-round10 | **ADR-016 第十轮 gnex3 对账** | LlmProvider 裁自研双协议族；messages 恒 derive(log)；digest+版本号落 ledger attrs |
| 2026-08-18 | work-docs-abs | **`E:\work\docs` 吸收清单** | `docs/work-docs-absorption.md`；闸门与诚实八条；CONTEXT/project-memory 同步 |
| 2026-08-07 | phase18-v1 | **DEV-PLAN Phase 18 / v1.0.0** | RELEASE_NOTES；版本 1.0.0；Docker 说明；tag 未推送 |
| 2026-08-07 | phase17-runtime | **DEV-PLAN Phase 17 技能脚本沙箱** | ADR-015 GraalJS 24.2.1；run_skill_script；超时强制中断；wasmtime 从缓 |
| 2026-08-07 | phase16-market | **DEV-PLAN Phase 16 应用市场** | 本地目录+启动扫描+可选远程；MarketplaceView；hello-assistant |
| 2026-08-05 | phase14-slash | **DEV-PLAN Phase 14 Slash 命令** | Registry+5 命令；GET/POST /api/slash；ChatInput 可选；不走 LLM |
