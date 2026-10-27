---
name: weaver-e10-esb
display_name: 泛微E10 ESB动作流执行
display_name_en: Weaver E10 ESB ActionFlow Execution
description: "泛微E10 ESB 中心自定义触发动作流的取模板与执行能力：先用 esb.input-format 取入参模板，确认后用 esb.trigger.prepare / esb.trigger.apply 触发动作流，通过 weaver-work-cli esb 命令执行。"
description_zh: "泛微E10 ESB 中心自定义触发动作流：取入参模板，再用 prepare → apply 确认链触发动作流，通过 weaver-work-cli esb 命令执行。"
description_en: "Query custom ESB action flow input templates and trigger action flows through the weaver-work-cli esb command with a prepare/apply confirmation chain."
version: 1.0.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
cliHelp: "weaver-work-cli esb --help"
---

# 泛微E10 ESB动作流执行

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

本技能覆盖 ESB 中心的**自定义触发类型动作流**：取入参模板（只读）与执行动作流（写操作，需确认）。按动作流名称解析 ID、唯一值对应的 `primary_key` SQL 查询都不在本技能范围内，见「不在范围」。

## 命令入口

```text
weaver-work-cli esb --help
weaver-work-cli esb schema
weaver-work-cli --json esb run <operation> --input-json '<json>'
```

`schema` 是可用能力的唯一事实来源。禁止把本技能或源接口文档中的接口路径当成可直接调用的地址，也禁止把未出现在 `schema` 中的能力当作可用 operation。

## 高优先级语义

- 「执行/触发/跑一下动作流」「按动作流 ID 触发」→ `esb.trigger.prepare` → 用户确认 → `esb.trigger.apply`
- 「这条动作流要传哪些参数」「给我入参格式」「触发前先看参数模板」→ `esb.input-format`
- 「用唯一值触发」→ `uniqueIndent`（值为该动作流在 `esb_application.primary_key` 中查得的唯一值），由调用方查好后传入
- 触发标识二选一：`esbFlowId`（普通触发）与 `uniqueIndent`（唯一值触发）不能同时传、也不能都缺

## Reference 路由表

命中任一条件时，执行下一步前读取对应 reference。

| 触发条件 | Reference |
| --- | --- |
| 取入参模板、确认主表/明细表字段、看哪些字段是可选 | [`references/input-format.md`](references/input-format.md) |
| 触发/执行动作流、写操作确认链、结果判读与失败处置 | [`references/trigger.md`](references/trigger.md) |

## 处理链（触发动作流必须按此顺序）

1. **确认目标动作流**：用户给的是动作流 ID。只给名称时向用户索取 ID（或 `primary_key`），不要猜测。
2. **取模板（推荐）**：`esb.input-format` 取回 `customParams` 模板，按模板组装业务字段；模板中的 `#可选` 占位替换为真实值或删除。
3. **prepare**：`esb.trigger.prepare` 做只读回查与请求体组装，返回 `requestPreview` 与 `continuation`（有效期 10 分钟）。此步不触发动作流。
4. **向用户确认**：把目标动作流、触发人员、请求体摘要和副作用风险展示给用户，取得明确确认。
5. **apply**：`esb.trigger.apply` 传 `continuation` 与 `confirm: true` 执行。`confirm` 必须显式为 `true`。
6. **结果判读**：`resultCode=200` 为成功，按 `followUp` 与 `actionData` 处理页面动作、提醒与返回数据。

## 写操作失败决策树

| 错误 subtype | 含义 | 处置 |
| --- | --- | --- |
| `confirm_required` | 未显式确认 | 先向用户确认，再传 `confirm: true` |
| `continuation_invalid` / `continuation_expired` / `continuation_mismatch` | continuation 无效或过期 | 重新执行 `esb.trigger.prepare` |
| `continuation_actor_changed` | 登录身份与签发时不一致 | 重新 prepare，不要复用旧 continuation |
| `parameter_invalid` | 参数错误（resultCode 100） | 对照 `customParams` 模板核对必填字段 |
| `flow_not_bound` / `flow_not_found` | 触发标识未绑定或动作流不存在 | 核对 ID，不重试 |
| `flow_disabled` / `flow_in_recycle_bin` | 动作流未启用或在回收站 | 先在 ESB 中心处理，不重试 |
| `permission_denied` / `tenant_permission_denied` / `tenant_missing` | 权限或租户问题 | 确认登录态与租户权限，不重试 |
| `license_required` | 需要对应 license | 联系管理员开通，不重试 |
| `throttled` | 触发频繁被限流（507/508/511） | 稍后重试，禁止高频轮询 |
| `flow_running` / `execute_timeout` | 正在执行或执行超时 | 稍后重试或查运行日志 |
| `write_uncertain` | 请求已发出但结果未知 | **立即停止**：不重试、不判定失败，先在 ESB 中心查看动作流运行日志 |

## 安全边界

- **🔴 写操作必须走确认链**：`esb.trigger.apply` 需要 `confirm: true` 且 continuation 有效期 10 分钟，禁止跳过 prepare 直接触发。
- **🔴 `write_uncertain` 不重试**：触发请求发出后网络中断时结果不可判定，自动重试可能造成重复执行。
- **唯一值触发有歧义**：同一租户下存在相同唯一值的多条动作流时，系统只执行 `create_time` 最新的一条；需要精确控制时改用 `esbFlowId`。
- **认证由 CLI 托管**：禁止自行拼接 Cookie / ETEAMSID / User-Agent，禁止绕过 `weaver-work-cli` 直接调用 E10 接口。

## 不在范围

- **按动作流名称解析 ID**：源资料未提供 ESB 中心动作流查询接口的路径与字段契约，`esb.actionflow.resolve` 未实现，请向用户索取动作流 ID。
- **唯一值 `primary_key` 的查询**：该步骤需要直接执行 `SELECT primary_key FROM esb_application WHERE id = ?`（`esb_setting` 库），CLI 没有数据库通道，`esb.unique-indent.resolve` 未实现；请由具备库权限的一方查好后通过 `uniqueIndent` 传入。
- 附件上传、OCR、文件解析类能力：本模块不涉及。

## 平台兼容

命令示例同时兼容 Windows 与 macOS/Linux。

Windows PowerShell：

```powershell
weaver-work-cli --json esb run esb.input-format --input-json '{"applicationId":"900000000000000001"}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli --json esb run esb.input-format --input-json '{"applicationId":"900000000000000001"}'
```

复杂 JSON（例如带明细表的 `customParams`）先写入 UTF-8 文件再传 `--input <file>`。
