---
name: weaver-e10-shijingran
description: 泛微E10事井然项目管理助手，覆盖项目和任务的创建、查询、修改、删除操作，以及项目/任务类型、紧急程度、状态等字典查询。当用户需要创建/管理项目或任务时触发使用。
display_name: 泛微E10事井然项目管理
display_name_en: Weaver E10 Shijingran Project Management
description_zh: 泛微E10事井然项目管理助手，封装项目和任务的创建、查询、修改、删除操作，并提供项目/任务类型、紧急程度、状态等字典查询能力。适用于在对话中通过 Agent 创建、查询、修改、删除项目或任务等场景。
description_en: Weaver E10 Shijingran project management assistant. Covers CRUD operations for projects and tasks, and provides dictionary queries for project/task types, priorities, and statuses. Used when an Agent needs to create, query, modify, or delete projects or tasks in conversation.
version: 1.0.8
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
dependencies:
  - weaver-e10-shared
cliHelp: "weaver-work-cli project --help"
---

# 泛微E10事井然项目管理

## 适用场景（何时使用本技能）

用户要创建、查询、修改或删除事井然项目与任务，或查项目/任务的类型、状态、紧急程度等字典时使用本技能。

用户处理的是流程审批、组织人员或发票时，转对应业务技能，不在本技能范围。

## CRITICAL：共享规则

先读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

### 登录与请求头凭证契约

本 Skill 是 CLI 型技能，不自行发起 HTTP 请求：认证与会话**统一由 `weaver-e10-shared` 共享规则和 `weaver-work-cli auth ...` 命令体系托管**，禁止自建或绕过登录流程。

所有 E10 请求由 `weaver-work-cli` 统一注入以下三项请求头，缺一不可（唯一权威声明见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md) 的「请求头契约」章节，本技能只做指针引用，不复制其代码块）：

| 请求头 | 取值 | 约束 |
| --- | --- | --- |
| `Cookie` | `weaver-work-cli` 登录后返回的完整原始 Cookie 串 | 必须原样携带，禁止裁剪 / 去重 / 改写 |
| `eteamsid` | 登录会话对应的 ETEAMSID | 必须原样携带 |
| `User-Agent` | `AgentType=<agentType>,IsAgent=true` | 必须携带，不得省略 |

以上三项均由 CLI 注入，不要手工拼装请求头，也不得把字段写进业务入参。

认证和登录态只能按共享规则通过 `weaver-work-cli auth ...` 命令判断；禁止直接读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。

Agent 不得自行拼接、改写或向用户索取上述凭证（禁止要求用户贴 Cookie / ETEAMSID / Token），也不得直接 `curl` / `fetch` E10 接口，或把接口原始路径、Cookie、ETEAMSID、Token 当作"用户可执行步骤"写进回复。

所有命令通过 `weaver-work-cli --json project run <operation>` 执行：简单 JSON 优先传 `--input-json '<json>'`，复杂或多行 JSON 先写入 UTF-8 文件再传 `--input <path>`，只有确认当前 shell 能稳定传管道时才使用 `--input -`。调用前先按需读取 references 下对应的文件查参数结构，不要猜字段；**references 是第一信息源**，`weaver-work-cli project schema` 是 operation、字段和风险等级的合约来源。

命令示例、提示词和临时说明必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、Python 启动器或文件比对时，必须同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。

## 路由

命中以下意图时按表路由到对应 operation 和 reference；不要为了普通查询执行 `project schema`、`project.version.check`、`project.form.fields` 或读取非必要 reference。

| 意图 | operation | reference | 备注 |
| --- | --- | --- | --- |
| 查询任务/项目列表 | `project.task.list` / `project.project.list` | [operations.md](references/operations.md) | `@me` 直接传 `filters.manager` / `filters.participant(s)` |
| 查询任务/项目详情 | `project.task.detail` / `project.project.detail` | [operations.md](references/operations.md) | |
| 查询任务/项目类型、状态、优先级 | `project.dictionary.*` | [operations.md](references/operations.md) | |
| 应用/对象 ID、字段定义、字段选项 | `project.app.ids`、`project.object.id`、`project.form.fields`、`project.form.options` | [operations.md](references/operations.md) | 仅自定义字段或异常排查 |
| 关联表单按名称查记录 | `project.form.data.page` | [field-resolution.md](references/field-resolution.md) | |
| 按姓名解析员工/部门/分部 | `project.employee/department/subcompany.resolve` | [field-resolution.md](references/field-resolution.md) | 多个匹配必须让用户选择 |
| 版本异常诊断 | `project.version.check` | [operations.md](references/operations.md) | 仅接口异常或用户主动询问 |
| 创建/修改/删除任务或项目 | `project.task/*`、`project.project/*` 的 prepare/apply | [write-operations.md](references/write-operations.md) | 严格 prepare → apply |
| 附件上传、字段自动解析、系统状态改写等未暴露能力 | 无可用命令 | [disabled-capabilities.md](references/disabled-capabilities.md) | 如实告知并给替代路径，不要猜 operation |

## 高优先级语义

- 事井然、项目管理、项目、任务、待办任务、任务列表、项目列表、任务详情、项目详情。
- 我负责的任务、我参与的任务、我负责的项目、我参与的项目、所属项目、父任务、父项目。
- 创建/新建项目或任务、修改/编辑项目或任务、删除项目或任务。
- 任务类型、任务状态、任务紧急程度/优先级、项目类型、项目状态、项目优先级。
- 负责人、参与人、测试负责人、客户经理、验收负责人、所属部门、所属分部、关联表单字段。

## 处理链

1. 判断用户目标对象和最小必要条件，按路由读取最小必要 reference；已知出厂字段直接使用 `operations.md` 的字段 quick reference。
2. 名称条件先解析为 ID；0 个匹配要告知，多个匹配必须让用户选择，不得猜。
3. 查询默认第 1 页 10 条；返回条数等于 pageSize 时提示可能还有更多，等待用户要求再翻页。
4. 列表和详情中不要单独输出 `detail_url` 列；将 `task_name` / `proj_name` 渲染为 Markdown 链接（方括号内为记录名称，紧跟的圆括号内为 `detail_url` 的取值）。链接不可用时名称保持纯文本并如实说明，不得编造 URL。
5. 写操作先 `prepare`，向用户摘要目标、差异、风险、默认值和不可逆性；用户明确确认后才 `apply`。
6. `partial/write_uncertain`、登录失效、回查失败或网络中断时停止写流程，不自动重试。

## 输出与展示

- 列表和详情默认只给摘要：记录数 + 下方固定展示列，不粘贴完整 JSON，不罗列全部原始字段；完整响应过长时先落本地 UTF-8 文件再给摘要与路径。
- 默认展示列以 [`operations.md`](references/operations.md) 的「输出处理」为准：任务列表 `task_name / task_type / plan_start_time / plan_finish_time / manager / task_status / progress / create_time`；项目列表把 `task_*` 换成 `proj_*`；详情在此基础上补 `project`/`priority` 与描述、创建时间。用户明确指定字段时以用户为准。
- 记录名称列（`task_name` / `proj_name`）渲染为 Markdown 链接 `[名称](detail_url)`，**不单独输出 `detail_url` 列**；`detail_url_warning` 时名称保持纯文本并如实说明，不得编造 URL。
- 关联字段（`manager`、`participant(s)`、`task_type`、`proj_status` 等）只展示 `name`，不展示内部 ID；字典 ID、对象 ID、`groupId` 只用于入参。
- 未指定状态时先说明默认口径「只查未开始 + 进行中」（默认系统状态口径见 [`operations.md`](references/operations.md)）；返回条数等于 `pageSize` 时提示可能还有下一页。

## 执行原则

- 业务调用统一使用 `weaver-work-cli --json project run <operation>`，优先 `--input-json`；复杂多行 JSON 用 UTF-8 文件和 `--input <path>`。
- 列表展示只保留用户所需字段；普通列表也用 `fields` 对齐展示列，不要拉回全部原始字段；关联类字段取 `name`，不展示内部 ID。
- 同一会话已确认 CLI 可用、登录态有效且字段语义无变化时，不要重复执行版本、认证或 schema 诊断；“我负责/我参与”直接使用 `@me`；同一会话已解析的名称→ID、字典 ID、对象 ID 直接复用。
- 大结果需要检查时先用 operation 内置过滤/裁剪参数，必要时再落盘为 UTF-8 JSON 并用 Node 解析；不要依赖 `grep`、`head`、`wc` 等 shell 工具。仅做摘要、统计、筛选或名称转 ID 时，优先 `withDetailUrl:false` 和 `fields` 裁剪输出；`fields` 不含 `detail_url` 时可少一次链接解析请求。
- 不要把接口原始路径、Cookie、ETEAMSID或 Token 作为用户可执行步骤。
- 接口异常后才调用版本诊断；版本低于 `20.6.11` 时提示升级，不重试原接口。

## 写操作失败决策树

1. `apply` 缺少 `confirm: true` 或 continuation：停止，重新执行 `prepare` 并取得用户确认。
2. `target_changed`：目标已变化，重新回查并重新 `prepare`。
3. `partial/write_uncertain`：停止，不重试写入；先执行只读详情/列表回查。
4. 登录失效：按共享规则处理登录，不得使用旧 continuation 直接重试。
5. 回查失败：向用户说明结果不确定，等待用户决策。

## 常用命令示例

以下示例只示意参数形态；执行前必须先按 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md) 判定是否补 `--is-connector true`，命中时本任务内每条命令都要带。

Windows PowerShell：

```powershell
weaver-work-cli --json project run project.task.list --input-json '{"filters":{"manager":"@me"},"pageNo":1,"pageSize":10,"withDetailUrl":false,"fields":["task_name","task_type","task_status","manager","plan_finish_time","progress"]}'
weaver-work-cli --json project run project.project.list --input-json '{"pageNo":1,"pageSize":10}'
weaver-work-cli --json project run project.task.detail --input-json '{"id":"<taskId>"}'
weaver-work-cli --json project run project.dictionary.task-status --input-json '{"pageNo":1,"pageSize":100}'
weaver-work-cli --json project run project.task.create.prepare --input-json '{"records":[{"task_name":"需求评审"}]}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli --json project run project.task.list --input-json '{"filters":{"manager":"@me"},"pageNo":1,"pageSize":10,"withDetailUrl":false,"fields":["task_name","task_type","task_status","manager","plan_finish_time","progress"]}'
weaver-work-cli --json project run project.project.list --input-json '{"pageNo":1,"pageSize":10}'
weaver-work-cli --json project run project.task.detail --input-json '{"id":"<taskId>"}'
weaver-work-cli --json project run project.dictionary.task-status --input-json '{"pageNo":1,"pageSize":100}'
weaver-work-cli --json project run project.task.create.prepare --input-json '{"records":[{"task_name":"需求评审"}]}'
```

写操作的 `prepare` 返回 continuation 后，必须先向用户摘要再执行 apply：

```json
{"continuation":"PREPARE_CONTINUATION","confirm":true}
```

## 不在范围

- 禁止绕过 `weaver-work-cli` 直接调用 E10 原始接口；禁止 `curl` / `fetch` / 浏览器自动化访问 `/api/ebuilder/**`、`/api/ebproject/**` 等路径。
- 禁止读取或复制 CLI runtime 内部的 Token、Cookie、ETEAMSID；禁止读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
- 不提供附件上传；`attachment` 只接受已上传文件 ID，且处理文件前必须取得用户敏感数据确认。未暴露能力清单见 [`disabled-capabilities.md`](references/disabled-capabilities.md)。
- 不自动替用户选择同名人员、组织、项目、任务或字典选项。
- 不自动翻页、自动扩大 pageSize 或自动聚合全量数据。
- 不在正常路径预调用版本诊断。
- 非事井然的 E10 能力（流程审批、组织人员、发票、文书定档案等）不由本 Skill 承载，转对应业务技能。
