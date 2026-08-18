# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md`（**develop**）+ `memory/decisions-log.md`（**ADR-016**）。  
> **不要**从 Product-Spec 正文、README 旧完成栏、`docs/archive/v1/project-memory-v1.md` 或本文件 2026-08-07 旧稿推断 OS 已成形 / 按 ChatModel 写 `os/`。  
> `Product-Spec.md` / `DEV-PLAN.md` / `RELEASE_NOTES-v1.0.0.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-18

## 当前阶段

- develop：按 ADR-016 重建内核
- **当前 = kernel 切片，不是 Agent OS**（[`docs/agent-os-gap.md`](../docs/agent-os-gap.md)）
- 下一刀：`llm.*` 断言切片**落码**（第十轮裁决已备：`os/llm` + kernel 四处小改）
- v1.0 在 `main` / `legacy/`；禁止在 `legacy/` 加功能

## 文档口径（2026-08-18 统一）

- 规范冲突：**ADR-016 > os-baseplate > handbook 独有章 > v1 规格**
- Product-Spec = v1 工作台 / Harness；七层、四智能体、Spring AI、WASM+V8、白盒记忆 P0 不得当 develop 已具备
- 对账参照在 `docs/archive/`；禁止再开对账轮、再写第三份投影
- `project-memory.md` = develop 记忆；v1 工作台记忆在 `docs/archive/v1/project-memory-v1.md`
- CLAUDE 技术栈代码块 = `os/`；Spring Boot/AI/React 标为 v1
- `Product-Spec.md` 根目录不能搬（Forge 门）；标题与 §4.1/§5.1 已标 v1 档案

## 已完成（develop，摘要）

- ② conformance + 第九轮端口演化（审批 ask、fail-closed、失败双通道、ledger/Metering 端口、Inbox 优先级）
- 第十轮纯文档：LlmProvider 自研双协议族；派生式断言；gnex3 只吸取有利
- 吸收八条 + agent-os-gap

## 待办

- `llm.*` 断言切片落码（下一刀）
- 其后 ③ Loop（含完成证据门）
- 五层未钉死：禁止对外「骨架可演示 / OS 成形」

## Blocker

- 无。对账冻结已解除（② conformance 已合入）。

## 关键 ADR

- **ADR-016**（规范真相，十轮）
- ADR-015 GraalJS（v1 技能脚本；develop 脚本隔离仍以此为已裁选型，直至另裁）

## v1 档案

tag `v1.0.0`：https://github.com/zxpmail/tepeu/releases/tag/v1.0.0  
切片正文见冻结的 `DEV-PLAN.md`；已知限制（Docker 未实测等）仍以当时发布说明为准。
