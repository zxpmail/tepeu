---
name: weaver-e10-blog
display_name: 泛微E10工作日报
display_name_en: Weaver E10 Daily Report
description: "泛微 E10 工作日报的查询、发布、编辑、评论、点赞、提醒与 Excel 导出能力，通过 weaver-work-cli blog 命令执行。"
description_zh: "泛微 E10 工作日报全流程：群体日报查询（下属/关注）、单人日报查询、HRM 姓名解析、评论查询、默认共享查询、日报导出 xlsx，以及发布、编辑、评论、点赞、提醒的 prepare -> apply 写入确认链。"
description_en: "Full lifecycle of Weaver E10 daily reports: group and personal queries, HRM name resolution, comments, default sharing, Excel export, and prepare -> apply confirmation chains for publish, edit, comment, like, and remind operations."
version: 1.0.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
cliHelp: "weaver-work-cli blog --help"
---

# weaver-work-cli 日报助手

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

认证和登录态只能按共享规则通过 `weaver-work-cli auth ...` 命令判断；禁止直接读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
所有命令通过 `weaver-work-cli --json blog run <operation>` 执行：简单 JSON 优先传 `--input-json '<json>'`，复杂或多行 JSON 先写入 UTF-8 文件再传 `--input <path>`，只有确认当前 shell 能稳定传管道时才使用 `--input -`。调用前先按需读取 references 下对应文件；**references 是第一信息源**，`weaver-work-cli blog schema` 是 operation、字段和风险等级的合约来源，不要靠反复尝试不同 JSON 格式来猜字段。
命令示例、提示词和临时说明必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、Python 启动器、文件删除、目录查看或文件比对时，同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。

## 适用场景与触发条件

触发条件：用户提到「日报」「工作日报」「写日报」「查日报」「日报补交」「未提交日报」「催交日报」「提醒交日报」「下属日报」「我关注的人的日报」「日报评论」「点赞日报」「导出日报」「日报 Excel」等意图，或需要把某人姓名解析为 E10 人员后再查日报。

典型适用场景：
- 查询下属/关注人群体的日报提交与补交情况（blog.query）
- 按日期区间逐日查询单人日报与提交状态（blog.query_user）
- 把姓名、工号模糊信息解析成 E10 userId（blog.resolve_users）
- 查询某篇日报的评论（blog.comments）、导出日报 Excel（blog.export）
- 发布/编辑/评论/点赞/催交日报（prepare -> apply 确认链，写入类高风险操作）

不适用：人事档案、考勤、薪酬、流程审批等非日报意图。

## 路由优先级

1. 用户意图涉及工作日报（日报、日志、补交、催交、提醒交日报）→ 本模块。
2. 先读 `weaver-work-cli blog schema` 确认 operation 合约，再按命令表选 operation。
3. 人事档案、考勤、薪酬等非日报意图 → 不属于本模块。

## 高优先级语义

- 业务对象：工作日报（blog）。别名：日报、工作日报、日志、补交、催交。
- **查指定个人**只能用 `blog.query_user`（按天扫描，区间上限 62 天）；不要尝试按 userId 列表批量查个人——服务端会忽略该过滤并静默返回当前登录人自己的日报。
- **查群体**（直接下属 / 间接下属 / 全部下属 / 我关注的人）用 `blog.query` 的 `user_type`。
- 补交状态由 CLI 按「提交时间 vs 日报日期是否同日」判定，输出为 `正常` / `补交` / `未提交`，不要使用其它口径。
- 提醒默认文案固定为「请填写 YYYY年 M月 D日 的日报！」，日期与输入 `date` 一致。
- HRM 解析人员取 `user_id`；姓名同名多命中时 CLI 会报错并列出候选，改用 `user_id` 指定。

## 命令表

| 用户意图 | operation | reference |
| --- | --- | --- |
| 查群体日报（下属 / 我关注的人） | `blog.query` | [blog-query.md](references/blog-query.md) |
| 查某人某天 / 某段日报 | `blog.query_user` | [blog-query-user.md](references/blog-query-user.md) |
| 按姓名解析 userId | `blog.resolve_users` | [blog-resolve-users.md](references/blog-resolve-users.md) |
| 查某条日报的评论 | `blog.comments` | [blog-comments.md](references/blog-comments.md) |
| 查某日期默认共享对象 | `blog.default_permission` | [blog-default-permission.md](references/blog-default-permission.md) |
| 导出日报为 xlsx | `blog.export` | [blog-export.md](references/blog-export.md) |
| 发布日报 | `blog.publish.prepare` → `blog.publish.apply` | [blog-publish.md](references/blog-publish.md) |
| 编辑指定日期日报 | `blog.edit.prepare` → `blog.edit.apply` | [blog-edit.md](references/blog-edit.md) |
| 评论指定日报 | `blog.comment.prepare` → `blog.comment.apply` | [blog-comment.md](references/blog-comment.md) |
| 点赞 / 取消点赞 | `blog.like.prepare` → `blog.like.apply` | [blog-like.md](references/blog-like.md) |
| 提醒提交日报 | `blog.remind.prepare` → `blog.remind.apply` | [blog-remind.md](references/blog-remind.md) |
| 暂未支持的能力 | — | [blog-disabled-capabilities.md](references/blog-disabled-capabilities.md) |

统一 JSON 入口：

Windows PowerShell：

```powershell
weaver-work-cli --json blog run blog.query --input-json '{"user_type":"myFollow","start_date":"2026-09-14","end_date":"2026-09-16"}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli --json blog run blog.query --input-json '{"user_type":"myFollow","start_date":"2026-09-14","end_date":"2026-09-16"}'
```

## 处理链

- **日报提交情况报表**：`blog.query`（user_type 按意图选择）→ 汇总 `submit_status` → 向用户输出按人统计表。未提交按人天取 `not_submitted[]`；`not_submitted_people[]` 是整窗完全没交的人（用于催交）。
- **发布**：`blog.publish.prepare`（自动查当日是否已有日报、回填默认共享）→ 向用户复述日期与内容 → 用户确认 → `blog.publish.apply --yes`（CLI 回查验证）。
- **编辑**：`blog.edit.prepare`（自动取原内容与差异）→ 复述原内容与新内容 → 用户确认 → `blog.edit.apply --yes`。
- **评论 / 点赞**：`prepare` 定位目标日报（无日报即报错，不询问）→ 用户确认 → `apply --yes`。
- **提醒**：`blog.remind.prepare`（解析被提醒人 + 默认文案）→ 复述人员与文案 → 用户确认 → `blog.remind.apply --yes`（逐人发送）。

## 执行原则

- 最小必要查询：先 `schema`，再按需读 reference；列表结果只保留任务所需字段，回复时给摘要与统计，不粘贴超长 JSON。
- 业务输入只描述业务对象和意图；认证、Cookie、ETEAMSID、Token 全部由 CLI 托管，禁止出现在输入中。
- 错误以 JSON envelope 的 `error.type/subtype` 驱动：`name_ambiguous` 列出候选后改用 user_id；`range_too_large` 缩小区间；`output_exists` 换输出路径。
- 导出属于本地文件写入：`output` 指向已存在文件时 CLI 拒绝覆盖。

## 写操作失败决策树

1. `confirmation.required` → 向用户复述 prepare 返回的目标、差异与文案，取得明确确认后带 `confirm=true` 重调 apply；不要伪造 continuation。
2. `target_changed` / `publish_already_exists` → 重新 prepare，再向用户确认。
3. `partial/write_uncertain` → 停止，不做任何写重试；用 `blog.query_user` 只读回查后向用户报告。
4. `authentication/session_expired` → 按 shared 规则先向用户确认 E10 登录域名，再引导登录。

## 不在范围

- 禁止绕过 CLI 直接 `curl`/`fetch` E10 日报接口，禁止读取或拼装 Cookie、ETEAMSID。
- 按 userId 列表批量查个人日报未暴露（服务端忽略该过滤，详见 disabled-capabilities）。
- EB 日报应用接口族（ebuilder / secondev）未暴露；当前租户未启用 EB 日报应用。
- 删除日报、撤销已发送提醒未提供。
