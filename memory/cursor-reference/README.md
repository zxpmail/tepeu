# Cursor Reference

Cursor（Anysphere，v3.27.22，VS Code fork）逆向档案。主 agent 系统提示词在服务端（`agent.v1.SystemPromptSpec` 下发）；本档案收录**客户端可见的协议形状、内嵌提示词、安全/运行时架构与内置 skill 全量语料**。

- 深读与结论 → [ANALYSIS.md](ANALYSIS.md)
- `prompts/` — 客户端内嵌提示词原文：
  - `system-prompt-ide-*.md`（13 份）— **IDE 各模型的完整系统提示词**（state.vscdb 捞回，明文）：gpt-5.2/5.4/5.4-nano/5.1、codex-5.3、gpt-5.3-codex-low、qwen3.5-plus、composer-pair、composer、grok-4.5/4.6/4.7、cursor-generic-old。20–24K 全量版含 persistence/进度更新预算/citing_code 双方法契约/MCP 文件系统工具等完整规范
  - `system-prompt-composer-current.md` / `-old-sept.md` — CLI/Auto 流提示词两版演进（chats/*/store.db 捞回）
  - `side-chat.raw.txt` — 子对话边界隔离协议
  - `memory-profiling.raw.txt` — 行为学习两段式（转录→画像→index.md 原子写）
  - `phone-agent.raw.txt` — 电话语音 agent 完整契约
  - `planner-subagent.raw.txt` — planner 子代理（只准委派不准实现）
  - `privacy-disclosure.raw.txt` — 数据流披露表（surface→why→RPCs）
  - `builtin-skills/` — `~/.cursor/skills-cursor/` 25 个内置 skill 全量拷贝（autopilot/goal/loop/review-bugbot 等）

来源：`C:\Users\zxpma\AppData\Local\Programs\cursor\resources\app\`（无 asar，VS Code fork 解包目录；AI 层在 `out/vs/workbench/workbench.glass.main.js` 49.7MB）+ `~/.cursor/`（skills/worktrees/plans）。沙箱 helper：`resources/app/resources/helpers/cursorsandbox.exe`（Rust）。抽取脚本思路见 ANALYSIS.md §十。内容版权归 Anysphere，仅限内部研究，勿公开分发。

关联：[[reference_trae_prompts]]（同为 VS Code 系 fork，抓包方法在彼条）、[[reference_workbuddy]]（沙箱 broker 对照）、[[reference_qingclaw]]（薄壳+服务端引擎同款拓扑）。
