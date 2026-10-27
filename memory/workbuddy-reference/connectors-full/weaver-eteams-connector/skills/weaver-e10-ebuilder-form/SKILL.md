---
name: weaver-e10-ebuilder-form
display_name: 泛微E10 eBuilder表单
display_name_en: Weaver E10 eBuilder Forms
description: "泛微E10 eBuilder 表单助手：定位菜单，查询 List/NList 列表，读取字段和浏览数据，并通过 CLI 安全执行表单 OpenAPI/自定义接口读写。"
description_zh: "面向泛微 E10 eBuilder 表单：通过 weaver-work-cli ebuilder-form 定位菜单与页面上下文，查询列表和表单数据，解析字段、选项和浏览字段，并用 prepare/apply 确认链新增、修改或删除数据。"
description_en: "Query eBuilder menus, List/NList pages, fields, browser values, and form data through the weaver-work-cli ebuilder-form command with prepare/apply confirmation for writes."
version: 1.0.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
dependencies:
  - weaver-e10-shared
cliHelp: "weaver-work-cli ebuilder-form --help"
---

# 泛微E10 eBuilder表单

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

本技能覆盖 eBuilder 表单菜单定位、List/NList 页面查询、字段/选项/浏览字段解析，以及 OpenAPI 和已发布自定义接口的表单数据读写。所有业务调用必须通过 `weaver-work-cli ebuilder-form`，禁止绕过 CLI 直接拼 E10 接口、Cookie、ETEAMSID 或 `/api/bs/` 路径。

认证由 `weaver-e10-shared` 共享规则和 `weaver-work-cli auth ...` 命令体系托管。底层请求头三件套由 CLI 统一注入，Agent 不得在对话或命令输入中索取、粘贴或输出这些凭证；唯一权威声明见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md) 的「请求头契约」章节。

## 命令入口

```text
weaver-work-cli ebuilder-form --help
weaver-work-cli ebuilder-form schema
weaver-work-cli --json ebuilder-form run <operation> --input-json '<json>'
```

`schema` 是可用 operation 与字段的事实来源。未出现在 schema 中的能力，不要写成可执行步骤。

## 高优先级语义

- “这个表单在哪 / 找某个 eBuilder 菜单” → `ebuilder-form.menu.search` 取候选菜单，再 `ebuilder-form.context.resolve`
- “页面 URL 解析 / 这个列表页对应哪个 objId” → `ebuilder-form.context.resolve`（`url` 传整串 URL）
- “这个表单有哪些视图 / 切换列表视图” → `ebuilder-form.views.list`
- “查列表 / 表格数据 / 分页 / 总数” → 先 resolve，再按 `listKind` 走 `ebuilder-form.list.query` 或 `ebuilder-form.nlist.query`
- “看列表字段、搜索项、排序、统计、固定条件” → `ebuilder-form.list.config` 或 `ebuilder-form.nlist.config`
- “表单字段、选项、主明细映射、人员/部门/关联字段按名称解析” → `fields.get`、`options.get`、`mapping.get`、`browser.resolve`
- “按 objId 查详情/分页/总数” → `openapi.detail`、`openapi.query`、`openapi.count`
- “新增/修改/删除表单数据” → 对应 `*.prepare` → 用户确认 → 对应 `*.apply`
- “已发布自定义接口 interfacePk 查询/写入” → `custom.*`

菜单多命中时必须列出候选让用户选择，不得自动取第一条。

## Reference 路由表

| 触发条件 | Reference |
| --- | --- |
| 菜单搜索、URL 解析、apiPrefix、appId/listId/objId 上下文、页面地址、视图枚举 | [`references/context-routing.md`](references/context-routing.md) |
| List/NList 配置、分页、总数、搜索条件、NList customConfig 编码 | [`references/list-nlist.md`](references/list-nlist.md) |
| 字段定义、选项、主明细映射、人员/部门/审批/文档等浏览字段 | [`references/fields-browser.md`](references/fields-browser.md) |
| OpenAPI 详情、分页、总数的只读查询 | [`references/openapi-read.md`](references/openapi-read.md) |
| OpenAPI 新增、修改、删除的 prepare/apply 确认链 | [`references/openapi-write.md`](references/openapi-write.md) |
| 已发布自定义接口的查询、新增、修改、删除 | [`references/custom-api.md`](references/custom-api.md) |
| 源资料中存在但当前 CLI 未自动化或需要人工补充的能力边界 | [`references/disabled-capabilities.md`](references/disabled-capabilities.md) |

## 处理链

1. 先运行 `weaver-work-cli ebuilder-form schema` 确认 operation 和字段。
2. 有菜单名或页面 URL 时先执行 `ebuilder-form.context.resolve`，保留返回的 `context`。同一任务后续 operation 优先传 `context`，避免重复搜索菜单或猜 ID。
3. 页面列表查询必须按 `context.listKind` 分流：`List` 用 `list.*`，`NList` 用 `nlist.*`。不要把两套接口互相试跑。
4. 表单级数据查询或写入必须先确认接口族：有 `objId` 走 OpenAPI；有 `interfacePk` 且用户明确是已发布自定义接口时走 `custom.*`；两者同时出现且用户没有指定时先消歧。
5. 字段显示名（`text`）不能直接当写入键：通常用字段的 `name`；自定义字段若 `name == id`，或目标接口明确要求组件数据键时，改用 `config.dataKey`。写入前用 `fields.get` 核对真实字段。选择类字段写入选项 `id`，不写选项名称。
6. 新增、修改、删除必须先 prepare。prepare 返回 `requestPreview` 和 10 分钟有效的 `continuation`；向用户摘要目标、字段、数量和风险，得到明确确认后 apply。

## 写操作失败决策树

| 错误 subtype | 含义 | 处置 |
| --- | --- | --- |
| `required` / `confirm_required` | 未显式确认 | 先向用户确认，再传 `confirm: true` |
| `continuation_invalid` / `continuation_expired` / `continuation_mismatch` | continuation 无效、过期或不属于当前 operation | 重新执行 prepare |
| `continuation_actor_changed` | 当前 E10 登录环境或用户与 prepare 时不一致 | 重新 prepare，不复用旧 continuation |
| `business_error` / `http_error` | 服务端业务失败或 HTTP 失败 | 保留业务消息，停止后续写入 |
| `session_expired` | 登录态失效或权限不足 | 按 shared 规则恢复 E10 会话后从只读步骤重新开始 |
| `too_many_pages` | 查询全部页超过 100 页预算 | 请用户增加筛选条件，不得拆成多次单页查询规避预算 |
| `write_uncertain` | 写请求结果未知 | 立即停止，不自动重放；先做只读核验 |

## 安全边界

- 认证、profile、Cookie、ETEAMSID 和小 E 统一登录由 `weaver-work-cli` 托管；不要索取或输出凭证。
- 禁止调用 `/api/bs/`，禁止默认域名，禁止从 `menuId`、`appId`、`listId` 或 `objId` 互相猜测。
- 文件、附件、OCR、导入、远程 URL 解析不属于当前模块；如后续新增，必须先按 shared 规则取得用户对文件内容处理的明确确认。
- 查询列表时单页最大 200；查询全部页预计 100 页或更多时停止。
- 删除不可逆。用户当前请求已明确删除目标时仍必须走 CLI 的 continuation 校验；目标不明确时先询问。

## 失败处理与自检

- 业务失败看 stderr JSON 的 `error.type` / `error.subtype` / `error.message`，不要用退出码 `0` 判断成功。
- 不确定某个能力是否可用时，先跑 `weaver-work-cli ebuilder-form schema` 核对；`schema` 是唯一事实来源，未出现在其中的能力不要写成可执行步骤。
- 命令不可用（`command not found` 或退出码 127）时，按 shared 规则的安装章节处理，不要自行拼装接口。
- **诊断出口**：任何步骤拿不到预期结果时，优先回到 `ebuilder-form schema` 与对应 reference 核对参数，不要换接口硬试、不要绕过 CLI 直连 E10。
- 认证类错误（`authentication` / `session_expired`）：按 shared 规则恢复 E10 会话后从**只读步骤**重新开始；写操作的旧 continuation 作废，必须重新 prepare。

## 平台兼容示例

Windows PowerShell：

```powershell
weaver-work-cli ebuilder-form schema
weaver-work-cli --json ebuilder-form run ebuilder-form.menu.search --input-json '{"menuKeywords":["客户档案"]}'
weaver-work-cli --json ebuilder-form run ebuilder-form.context.resolve --input-json '{"menuKeywords":["客户档案"]}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli ebuilder-form schema
weaver-work-cli --json ebuilder-form run ebuilder-form.menu.search --input-json '{"menuKeywords":["客户档案"]}'
weaver-work-cli --json ebuilder-form run ebuilder-form.context.resolve --input-json '{"menuKeywords":["客户档案"]}'
```

复杂 JSON 使用 UTF-8 文件和 `--input <path>`；只在确认当前 shell 管道稳定时使用 `--input -`。
