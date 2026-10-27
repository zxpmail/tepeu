---
name: weaver-e10-meeting
display_name: 泛微E10会议管理
display_name_en: Weaver E10 Meeting Management
description: "泛微 E10 会议管理，帮您处理会议日常事务：[会议]-查询今天/本周/我的会议与会议详情，预约建会（自动做会议室与参会人冲突检测），会议改期/换地点/改参会人、取消、删除；[会议室]-查询会议室与占用/空闲情况；[参会与回执]-查参会人名单、填写/更新会议回执；[签到]-查签到与签退情况、补签、补签退、清除签到记录；[会议资料]-会议议题（查看/更新/删除）、议题类型、会议纪要查询、会议类型与应用设置、会议决议发布与决议任务跟踪。认证与会话由 weaver-e10-shared 提供。"
description_zh: "泛微 E10 会议管理，帮您处理会议日常事务：[会议]-查询今天/本周/我的会议与会议详情，预约建会（自动做会议室与参会人冲突检测），会议改期/换地点/改参会人、取消、删除；[会议室]-查询会议室与占用/空闲情况；[参会与回执]-查参会人名单、填写/更新会议回执；[签到]-查签到与签退情况、补签、补签退、清除签到记录；[会议资料]-会议议题（查看/更新/删除）、议题类型、会议纪要查询、会议类型与应用设置、会议决议发布与决议任务跟踪。认证与会话由 weaver-e10-shared 提供。"
description_en: "Weaver E10 meeting management for daily meeting tasks: [Meetings] query today's / this week's / my meetings and meeting details, book meetings with automatic room & attendee conflict checks, reschedule / change location / change attendees, cancel, delete; [Rooms] query meeting rooms and occupancy / availability; [Attendees & receipts] view attendee lists, fill in / update meeting receipts; [Sign-in] view sign-in / sign-out status, manual sign-in, manual sign-out, clear sign-in records; [Meeting content] meeting agenda (view / update / delete), agenda types, minutes query, meeting types, application settings, meeting decision publishing and decision task tracking. Auth and session are provided by weaver-e10-shared."
version: 2.13.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
dependencies:
  - weaver-e10-shared
cliHelp: "weaver-work-cli meeting --help"
---

# 泛微 E10 会议管理 Skill（双模式：标准版 + EB 版）

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

凭证边界：所有 E10 调用凭证一律由 `weaver-work-cli` 的登录托管凭据（`auth` 命令/profile 体系）提供，禁止硬编码任何密钥，禁止向用户索取 Cookie、ETEAMSID、Token 或密码。

本 Skill 通过 `weaver-work-cli meeting` 命令调用真实 operation，不绕过 CLI 直接拼接口。

## 触发场景（用户这样说时使用本 Skill）

- 会议查询：「今天的会议」「我的会议」「下周有什么会」「这场会谁参加」「会议详情」
- 预约与变更：「帮我约个会」「订个会议室」「周会改到周三」「换个地点」「取消这个会」「删掉这条会议」
- 冲突与空闲：「这个时间会议室空着吗」「谁有空」「有冲突吗」
- 签到：「谁签到了」「签到率多少」「张三签到了吗」「帮他补签」「补签退」「清掉误签」
- 回执：「帮我回执不参加」「谁还没回执」「回执情况」
- 会议室/类型查询：「有哪些会议室」「会议室占用情况」「有哪些会议类型」
- EB 版独有（仅 mode=eb）：「会议议题」「议题类型」「会议纪要」「上会申请」「应用设置」「会议决议」「决议任务」
- **会议决议 / 决议任务**：「把这次会议的决议落到系统里」「生成会议任务」「这次会议定了哪些事要谁做」「我负责的会议任务完成了吗」→ 见「命令表（EB 版）」的决议区块（**用 `meeting.eb.decision.publish` 一步完成**）
- **不触发**（转对应 Skill）：日程/日历类 → `weaver-e10-calendar`；审批流程发起 → `weaver-e10-workflow`；人员/部门查询 → `weaver-e10-hrm`。

## 认证与请求头契约

登录与会话由 `weaver-e10-shared` 共享规则和 `weaver-work-cli auth ...` 命令体系统一托管。业务 Skill 不自建登录流程，不索取、打印或转存任何凭证。

- 本 Skill 的实际调用由 `weaver-work-cli` 托管：凭据与本地化相关请求头（含 `langType` / `timeZoneOffset`）均由 CLI 请求运行时统一携带。Agent **不得**自行拼装请求头、不得读取 `~/.e10-cli` 等凭证文件、不得向用户索取 Cookie / ETEAMSID / Token / 密码。
- 依赖缺失处理：必需依赖 `weaver-e10-shared`（共享规则：安装、E10 认证、JSON 输出与高风险写入规范）缺失时，明确告知用户缺失的 Skill 名，**就绪前不执行任何功能**；人员/部门名称 ↔ id 转换另需 `weaver-e10-hrm`（未安装时，相关字段请让用户直接提供 id）。

## 路由优先级

1. 先判断用户意图是否属于会议域：会议、会议室、预约/建会、会议冲突、签到、回执、会议类型、占用视图。
2. **属于本模块 → 第一步强制跑 `meeting.env.detect` 确定环境模式**（默认按 baseUrl 读缓存；用户说"重新探测/刷新模式"时传 `refresh:true`）。**注意：只要结论要用于回答用户"当前走的是标准版还是 EB 版"，就必须传 `refresh:true` 实探测，不能拿缓存结论作答**——缓存可能是旧环境的结论，且 EB 冒烟失败时的回退原因（`ebSmokeFailed`/`ebSmokeError`）只在实探测结果里出现：
   - `mode=standard` → 使用「命令表（标准版）」的 operation；
   - `mode=eb` → **改用「命令表（EB 版）」的 `meeting.eb.*` operations**；禁止在 EB 环境下调用标准版 operations（两套接口/数据互不相通，标准接口查 EB 数据只会得到空结果），也禁止拿标准接口结果冒充 EB 数据；
   - `env_unavailable` → 提示环境会议应用未安装或接口未配置完整，停止。
3. 不确定字段先跑 `weaver-work-cli meeting schema`。
4. 不属于本模块（日程、审批流程、组织查询）→ 转对应 Skill（calendar / workflow / hrm）。

## 高优先级语义

- 「今天的会议 / 下一个会」→ `meeting.list`（`filterDate` 客户端过滤；接口 body 过滤实测不生效，不要现场试 body）。
- 「会议室占用 / 空闲 / 推荐」→ `meeting.room.list`（必须成对传 `beginDate`/`endDate`；`disabled=true` 已占用，不得推荐）。
- 「预约会议 / 建会」→ 先 `meeting.create.prepare`（内部强制冲突检测），用户确认后 `meeting.create.apply`。
- 「改期 / 换会议室 / 改参会人 / 变更会议」→ `meeting.change.prepare` → 确认后 `apply`。CLI 按会议状态**自动分流**：草稿走 `UPDATE`、正常会议走 `CHANGE`；权限由服务端判定并返回原文提示（如"您没有变更权限！"），必须原样转达用户。
- 「取消会议 / 不开了」→ `meeting.cancel.prepare` → 确认后 `apply`。
- 「删除会议记录」→ `meeting.delete.prepare` → 确认后 `apply`。
- 「提前结束会议」→ **CLI 不支持**（服务端 `overMeeting` 为空实现，调用会假成功但不生效），如实告知用户并建议在系统页面操作。
- 「回执为否 / 请假」→ `meeting.receipt.update.prepare`（自动回查回执记录 id）→ 确认后 `apply`。
- 「签到情况」→ `meeting.sign.list`（输出已签/未签统计；其 `signs[].id` 是**签到记录 id**，补签/清除要用它，不是会议 id）。
- 「补签 / 帮他补上签到」→ 先 `meeting.sign.list` 定位记录 → `meeting.sign.mark.prepare` → 确认后 `apply`；「补签退」用 `meeting.sign.back.prepare`。二者会改写签到事实，必须用户明确确认。
- 「清除签到记录 / 撤销签到」→ `meeting.sign.clear.prepare` → 确认后 `apply`。
- 「我要签到 / 扫码签到」→ **CLI 不支持**（依赖现场二维码票据 qrticket 与设备 IP），引导用户现场扫码或走 H5 签到；**绝不代签**。
- **EB 模式（mode=eb）下，同一意图改用 EB operations**（见「命令表（EB 版）」）：今天的会议 → `meeting.eb.meeting.list`（先 count 再 list）；建会 → 先读 `meeting.eb.baseSet.get` 判断是否需走流程，再 `meeting.eb.meeting.create.prepare`（**prepare 内部自动执行会议室/参会人冲突检测**：命中禁止提交 → `conflict_rejected` 直接停止；仅提醒 → 只写入 `warnings`，如实告知 + 建议换时间/换地点，是否继续由用户决定（不主动建议忽略冲突））；变更/取消 → `meeting.eb.meeting.change.prepare`（取消用 `operate_type=CANCEL`、`mt_status=4`）；签到 → `meeting.eb.sign.list`；回执 → `meeting.eb.receipt.list`；纪要 → `meeting.eb.minute.list`；议题 → `meeting.eb.topic.list`；**补签 → `meeting.eb.sign.mark`（走更新，禁止用 `sign.create`）**；**会议决议 → `meeting.eb.decision.publish`（写决议 + 自动把会议标为已决议，一步到位，勿拆成两步）**；决议任务 → `meeting.eb.decisionTask.list` / `.update`。

## 命令表（标准版，仅 mode=standard 使用）

| 用户意图 | CLI operation | Reference |
| --- | --- | --- |
| 探测环境模式（EB/标准） | `meeting.env.detect` | [meeting-queries.md](references/meeting-queries.md) |
| 建会前置判断（onlyFlowCreate/工作流） | `meeting.calendar.base` | [meeting-queries.md](references/meeting-queries.md) |
| 会议列表 / 今天的会议 | `meeting.list`（便捷命令 `weaver-work-cli meeting today`） | [meeting-queries.md](references/meeting-queries.md) |
| 会议详情表单结构 / 提醒 options | `meeting.detail.field` | [meeting-queries.md](references/meeting-queries.md) |
| 会议室列表与占用 | `meeting.room.list` | [meeting-queries.md](references/meeting-queries.md) |
| 会议室占用视图 | `meeting.room.usage` | [meeting-queries.md](references/meeting-queries.md) |
| 会议类型列表 | `meeting.type.list` | [meeting-queries.md](references/meeting-queries.md) |
| 会议室/人员/属性冲突检测 | `meeting.conflict.room` / `member` / `roomAttribute` | [meeting-create.md](references/meeting-create.md) |
| 预约会议 | `meeting.create.prepare` → `meeting.create.apply` | [meeting-create.md](references/meeting-create.md) |
| 会议变更（改期 / 改地点 / 改参会人） | `meeting.change.prepare` → `meeting.change.apply` | [meeting-change.md](references/meeting-change.md) |
| 取消会议 | `meeting.cancel.prepare` → `meeting.cancel.apply` | [meeting-lifecycle.md](references/meeting-lifecycle.md) |
| 删除会议记录 | `meeting.delete.prepare` → `meeting.delete.apply` | [meeting-lifecycle.md](references/meeting-lifecycle.md) |
| 回执列表 | `meeting.receipt.list` | [meeting-receipts-signs.md](references/meeting-receipts-signs.md) |
| 更新回执 | `meeting.receipt.update.prepare` → `apply` | [meeting-receipts-signs.md](references/meeting-receipts-signs.md) |
| 签到记录与统计 | `meeting.sign.list` | [meeting-receipts-signs.md](references/meeting-receipts-signs.md) |
| 补签 / 补签退 | `meeting.sign.mark.prepare` → `apply` / `meeting.sign.back.prepare` → `apply` | [meeting-receipts-signs.md](references/meeting-receipts-signs.md) |
| 清除签到记录 | `meeting.sign.clear.prepare` → `apply` | [meeting-receipts-signs.md](references/meeting-receipts-signs.md) |
| 生成对象详情页链接（会议/会议室/签到…） | `meeting.link`（**禁止手工拼 URL**） | [meeting-eb.md](references/meeting-eb.md) |

## 命令表（EB 版，仅 mode=eb 使用）

> 全部 EB operation 的实现契约（interfacePk、URL query 参数、写操作 body 结构）见 [meeting-eb.md](references/meeting-eb.md)。

| 用户意图 | CLI operation | Reference |
| --- | --- | --- |
| EB 会议列表 / 详情 | `meeting.eb.meeting.list` / `meeting.eb.meeting.detail` | [meeting-eb.md](references/meeting-eb.md) |
| EB 建会 / 变更 / 删除 | `meeting.eb.meeting.create.prepare`→`apply` / `meeting.eb.meeting.change.prepare`→`apply` / `meeting.eb.meeting.delete.prepare`→`apply` | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议室列表 / 详情 | `meeting.eb.room.list` / `meeting.eb.room.detail` | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议室新建 / 更新 / 删除 | `meeting.eb.room.create` / `.update` / `.delete`（各 prepare→apply） | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议室占用列表 / 详情 | `meeting.eb.roomUsage.list` / `.detail` | [meeting-eb.md](references/meeting-eb.md) |
| EB 占用新建 / 删除 | `meeting.eb.roomUsage.create` / `.delete`（prepare→apply） | [meeting-eb.md](references/meeting-eb.md) |
| EB 签到列表 / 详情 | `meeting.eb.sign.list` / `.detail` | [meeting-eb.md](references/meeting-eb.md) |
| EB 签到新建 / 更新 | `meeting.eb.sign.create` / `.update`（prepare→apply） | [meeting-eb.md](references/meeting-eb.md) |
| EB 回执列表 / 详情 | `meeting.eb.receipt.list` / `.detail` | [meeting-eb.md](references/meeting-eb.md) |
| EB 回执新建 / 更新 | `meeting.eb.receipt.create` / `.update`（prepare→apply） | [meeting-eb.md](references/meeting-eb.md) |
| EB 纪要列表（纪要创建未实现） | `meeting.eb.minute.list` | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议类型列表 / 新建 / 删除 | `meeting.eb.type.list` / `.create` / `.delete` | [meeting-eb.md](references/meeting-eb.md) |
| EB 议题类型列表 / 详情 / 更新 / 删除 | `meeting.eb.topicType.list` / `.detail` / `.update` / `.delete` | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议议题列表 / 详情 / 更新 / 删除 | `meeting.eb.topic.list` / `.detail` / `.update` / `.delete` | [meeting-eb.md](references/meeting-eb.md) |
| EB 应用设置（判断审批/流程建会） | `meeting.eb.baseSet.get` | [meeting-eb.md](references/meeting-eb.md) |
| **EB 发布会议决议**（写决议 + 自动把会议标为已决议） | **`meeting.eb.decision.publish.prepare` → `apply`** | [meeting-eb.md](references/meeting-eb.md) |
| EB 决议列表 / 详情 / 删除 | `meeting.eb.decision.list` / `.detail` / `.delete.prepare` → `apply` | [meeting-eb.md](references/meeting-eb.md) |
| EB 决议任务列表（按会议 / 执行人 / 状态） | `meeting.eb.decisionTask.list` | [meeting-eb.md](references/meeting-eb.md) |
| EB 更新决议任务完成状态 | `meeting.eb.decisionTask.update.prepare` → `apply` | [meeting-eb.md](references/meeting-eb.md) |
| EB 补签 / 补签退（自动定位签到记录，走更新） | `meeting.eb.sign.mark.prepare` → `apply` | [meeting-eb.md](references/meeting-eb.md) |
| EB 会议室 / 参会人冲突检测（只查不建） | `meeting.eb.conflict.room` / `.member` | [meeting-eb.md](references/meeting-eb.md) |
| EB 详情页链接（会议/会议室/占用/签到/回执/纪要/议题/设置） | `meeting.link`（按 `type` 解析 objid 并缓存） | [meeting-eb.md](references/meeting-eb.md) |
| EB 传参口径（人员传 **HRM 员工 id**，非 E10 userId；日期 `yyyy-MM-dd HH:mm`） | — | [meeting-eb.md](references/meeting-eb.md) |

> **给验证方（其他智能体）**：需要"验证所有会议功能"时，直接交付 [meeting-verification.md](references/meeting-verification.md)
> —— 内含全部 **99 个 operation**（标准版 28 + EB 版 70 + 跨模式 1）的逐条判据、写链路模板、14 个负向用例、
> 8 项不在范围的功能清单，以及可直接粘贴的验证提示词与回执模板。

## 处理链：预约会议（高风险写，强制确认链）

1. **环境模式确定（强制第一步）**：跑 `meeting.env.detect`（默认读缓存，`refresh:true` 强制刷新）；`mode=eb` → 本处理链改用 EB operations：先 `meeting.eb.baseSet.get` 读 `only_flow_create`/`open_mt_approve` 判断是否需走流程（需走 → 提示走 workflow，CLI 不直接建会），再 `meeting.eb.meeting.create.prepare`；`env_unavailable` → 提示并停止。
2. 解析意图：名称 / 时间（`yyyy-MM-dd HH:mm`）/ 会议室 / 参会人 / 类型；缺失字段先向用户确认，**禁止字段未确认直接建会**。
3. `meeting.room.list`（带时间范围）推荐空闲会议室、`meeting.type.list` 推荐类型，交用户点选。
3. `meeting.create.prepare`：自动执行建会路径检查（`onlyFlowCreate=1` 或周期工作流 → 拒绝并提示走流程）与**强制冲突检测**（接口失败视为冲突；`cansub=false` 直接拒绝；`cansub=true` 有冲突描述 → warnings 如实告知并建议换时间/地点，**但不阻止提交**，是否继续由用户决定；CLI 不主动建议忽略冲突）。
4. 向用户摘要 preview（含 caller/contacter 默认当前用户、周期 `repeatDesc` 自动拼接）。
5. 用户明确确认 → `meeting.create.apply`（`confirm: true` + continuation，**10 分钟内有效，过期重新 prepare**）。
   **apply 的入参口径（2026-10-08 修正）**：只传 `confirm` + `continuation` 即可 —— 业务字段以 prepare 快照为准（快照就是用户刚确认过的内容）；若同时传了业务字段，则必须与快照一致，否则报 `target_changed`（提示重新 prepare），**不会**绕过确认闸门。
6. 结果回读用 `meeting.list`；会议链接一律用 `meeting.link` 生成（**禁止手工拼 URL**：EB 版 objid 需动态解析，标准版会议室/会议类型没有单页），再按 `[名称](url)` **内联到名称单元格**输出。

## 处理链：变更 / 取消 / 删除会议（高风险写，强制确认链）

1. 先用 `meeting.list`（或用户给出的会议 id）定位目标会议，必要时用 `meeting.link` 生成 `[名称](url)` 给用户核对（EB 环境会议也在 `meeting.eb.meeting.list`）。
2. `meeting.change.prepare`（变更）/ `meeting.cancel.prepare` / `meeting.delete.prepare`（入参 `id`）：CLI 先**回查会议存在性**；`change` 还会按会议状态自动决定 `operateType`（草稿=UPDATE、正常=CHANGE）。**权限判定交给服务端**，CLI 不做权限预判。成功返回 `preview` + `continuation`（10 分钟有效）。
3. 向用户摘要动作与后果（变更=覆盖所提交字段、当前状态与分流类型；取消=会议进入取消状态、占用/回执/签到失效；删除=**移入回收站**、不再出现在正常列表），**取得明确确认**。变更只提交 preview 中列出的字段。
4. 用户确认 → `meeting.*.apply`（`confirm: true` + continuation；**只传这两个参数即可**，业务字段取 prepare 快照；改动内容会报 `target_changed`）。
5. 结果回读：`meeting.list` 确认状态（取消/删除后该会议不应再出现在正常列表）。
6. **禁止**跳过 prepare 直接 apply；**禁止**在未确认目标存在时执行写操作。

## 执行原则

- 最小必要查询；列表结果默认摘要输出，不粘贴超长 JSON。
- 长 id（`address`/`mtId`/`hrmMembers` 等）一律字符串传递（超 2^53 数字会失真）。
- **可点击链接一律内联到名称单元格**（`[会议名称](url)`），由 `meeting.link` 生成：**禁止**手工拼 URL，也**禁止**单列「详情链接」一行。
- **服务端业务/权限提示必须原文转达用户**：如"您没有变更权限！""您没有取消权限！""您没有删除权限！""您没有读取权限！"——不得改写成模糊说法，也不得建议用户绕过 CLI 自行拼接口。
- 错误驱动：`error.type/subtype` 决定下一步（`session_expired` → 登录流程；`conflict_rejected` → 停止；`confirmation.required` → 补 confirm；`meeting_not_operable` → 核对会议 id 与可见性）。

## 写操作失败决策树

- 写请求发出后网络中断 / 超时 → CLI 返回 `partial/write_uncertain`：**立即停止，禁止自动重试**，先 `meeting.list` / `meeting.receipt.list` 只读回查确认是否已生效，确未生效才可重新 prepare。
- `apply` 返回 `target_changed` / `continuation_expired` → 重新执行 prepare，向用户重新确认。
- 冲突 `conflict_rejected` → 直接停止，不提供任何绕过选项。
- `session_expired` → 先完成 E10 登录，再从失败步骤续跑，已成功的步骤不重做。

## 不在范围

- 禁止绕过 CLI 直接 `curl`/`fetch` E10 接口；禁止读取认证目录与 Cookie。
- **历史变更**：旧 `meeting.update.*` 已下线（它固定发送 `operateType=UPDATE`，与业务语义不符），改由 `meeting.change.*` 按会议状态自动分流；迁移说明见 [meeting-update.md](references/meeting-update.md)。
- **提前结束会议（meeting.over）不提供**：服务端接口为空实现（源码中 service 调用被注释、直接返回成功），调用只会得到"假成功"；请引导用户在系统页面操作，禁止绕过 CLI 自行拼接口。
- **EB 版已实现**（`meeting.eb.*`，覆盖会议/会议室/占用/签到/回执/纪要/会议类型/议题类型/会议议题/应用设置/**会议决议与决议任务**的全部 interfacePk）：`mode=eb` 时必须使用它们，**不要**再回答"EB 版未实现"。注意**标准版会议本身没有议题/纪要能力**（EB 版才有）——若当前是 standard 模式而用户问议题/纪要，如实说明属 EB 版能力（EB 环境下才可查）。
- **⚠️ EB 返回值解析（真机踩坑，必看）**：EB 接口里**人员 / 关联 / 状态 / 选择类字段返回的是「对象数组」**（如 `[{"name":"崔智友","id":"1305319807700025376"}]`），**不是字符串** —— 取 id 读 `[0].id`、显示名读 `[0].name`；**禁止**把整个数组当字符串拼接（会显示成 `System.Object[]`，看起来像"字段为空"）。已实测这类形态：`executors` / `rummager` / `mt_id` / `relate_decision` / `decision_status` / `detail1[].executor`；而 `finish_status`（"未完成"/"已完成"）、日期、`id` 是字符串。另注意 **`sign.list` 返回的 `mt_id` 是「会议室/会议名称」而筛选参数 `mt_id` 才是会议 id**。
- **⚠️ EB 写操作失败语义（真机踩坑，必看）**：**EB 写接口"报错"≠"没有写入"** —— 实测 `create_mt_decision` 因字段校验失败返回错误时，主表与明细其实**已经落库**，重试产生了重复数据。因此写操作失败后**必须先回查**（list/detail）再决定重试或删除；CLI 的语义化写操作（`decision.publish`、`sign.mark`）在拿不到明确成功证据时会抛 **`partial`** 并提示"先回查、勿盲目重试"，请如实转达用户。
- EB 版纪要**创建**未实现（该功能走 OpenAPI、无自定义 interfacePk），仅支持 `meeting.eb.minute.list` 查询。
- 周期会议走工作流（`repeatWorkFlowBaseIds` 非空）与 `onlyFlowCreate=1` 的流程建会需要工作流集成，CLI 会主动拒绝。
- **冲突处置严格按应用设置，CLI 不额外加码**：`=1`「禁止提交」→ 怎么都不允许提交（抛 `conflict_rejected`，不询问、不新建）；`=0`「仅提醒」→ 如实告知冲突并建议换时间/换地点，**但不阻止提交** —— 用户了解冲突后仍要求新建时，可继续 `apply` 完成建会。**检测失败 → `conflict_check_failed` 拒绝**（策略无法判定时保守处理）。
- **签到**：查询统计（`meeting.sign.list`）与写操作（补签 `meeting.sign.mark`、补签退 `meeting.sign.back`、清除签到 `meeting.sign.clear`）**已实现**；**本人扫码签到（`signMeeting`）不提供**——它依赖现场二维码票据（qrticket）与设备 IP，由 AI 代签会破坏签到真实性，请引导用户现场扫码或走 H5 签到；签到二维码与大屏签到未实现。
- **删除是软删**：`meeting.delete` 走 `update mt_detail set delete_type = 2`，会议**进入回收站、可以还原**；CLI 暂无还原能力，需要恢复时请引导用户到**会议回收站页面**操作。**不要**再对用户说"彻底删除、不可逆"。
- **标准版**会议室管理（`addMeetingRoom`/`updateMeetingRoom`/`deleteMeetingRoom`/`lockMeetingRoom`）与会议类型管理接口未实现；EB 版请用 `meeting.eb.room.*` / `meeting.eb.type.*`。
- **会议变更 / 取消 / 删除已实现**（`meeting.change.*` / `meeting.cancel.*` / `meeting.delete.*`，见「命令表」与「处理链」），不要因为本节的旧描述而把用户推去页面手改。当前唯一"未实现"的会议生命周期动作是**提前结束会议**（`meeting.over`，服务端空实现，调用只会假成功）。
