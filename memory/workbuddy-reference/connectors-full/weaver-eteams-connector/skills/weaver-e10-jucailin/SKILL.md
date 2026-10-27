---
name: weaver-e10-jucailin
display_name: 泛微聚才林人事云
display_name_en: Weaver Jucailin HR Cloud
description: 泛微聚才林（人事档案、出勤管理、薪酬管理）的 E10 查询与出勤写操作能力，通过 weaver-work-cli ehr 命令执行。
description_zh: 泛微聚才林（人事档案、出勤管理、薪酬管理）的 E10 查询与出勤写操作能力，通过 weaver-work-cli ehr 命令执行。
description_en: Weaver Jucailin HR capabilities for E10, covering HR profile, attendance and salary queries plus attendance write operations, delivered through the weaver-work-cli ehr command.
version: 1.0.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
cliHelp: "weaver-work-cli ehr --help"
---

# 泛微聚才林人事云

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

本技能覆盖聚才林业务域：**人事档案（hr）**、**出勤管理（attend）**、**薪酬管理（salary）**、**招聘（recruit，读 + 写）**。招聘写操作走 prepare→apply 确认协议；撤销类写操作不实现，原因见 [`references/safety-boundaries.md`](references/safety-boundaries.md)。

## 命令入口

```text
weaver-work-cli ehr --help
weaver-work-cli ehr schema
weaver-work-cli --json ehr run <operation> --input -
```

`schema` 是可用能力的唯一事实来源。禁止把本技能或源文档里的接口路径当成可直接调用的地址，也禁止把未出现在 `schema` 中的能力当作可用 operation。

## 认证

所有请求复用 `weaver-work-cli` 托管的 E10 会话，业务输入只描述业务对象和意图。禁止读取、列出、打印或解析 `.e10-cli` 等认证目录和文件；认证诊断只能通过 `weaver-work-cli auth` 系列命令、`weaver-work-cli doctor --e10` 和业务命令返回的 JSON 错误完成。

## Reference 路由表

命中任一条件时，执行下一步前读取对应 reference。

| 触发条件 | Reference |
| --- | --- |
| 工资单、福利、调薪、社保台账、员工薪资明细 | [`references/salary.md`](references/salary.md) |
| 出勤统计、异常考勤、打卡记录、外勤轨迹、假期统计、申诉查询 | [`references/attend-read.md`](references/attend-read.md) |
| PC 端打卡、创建申诉、处理申诉、报表重算 | [`references/attend-write.md`](references/attend-write.md) |
| 通讯录、字段列表、浏览器选择器、生日查询、人员状态变更 | [`references/hr-contact.md`](references/hr-contact.md) |
| HR 数据源：历史时点名册、离职名册、历史曾在职、编制、流动分析、人员情况合计 | [`references/hr-datasource.md`](references/hr-datasource.md) |
| 部门/岗位/职务/职类人数、流失离职、占编人员、人员流动明细、属性合计 | [`references/hr-report.md`](references/hr-report.md) |
| 人才库搜索、招聘需求/职位/入职/录用列表、人才详情卡片 | [`references/recruit-read.md`](references/recruit-read.md) |
| 新建人才、安排面试、发送 Offer、办理入职、面试反馈编辑/催评（写，prepare→apply） | [`references/recruit-write.md`](references/recruit-write.md) |
| 招聘撤销类写操作、移动端打卡、能力边界与禁止项 | [`references/safety-boundaries.md`](references/safety-boundaries.md) |

## 跨子系统差异（必须先知道）

四个子系统（人事档案 / 出勤 / 薪酬 / 招聘）的响应契约和分页结构不统一，已在 CLI 内归一化，但语义差异仍会影响结果解读：

- **响应成功判定**：出勤是 `success` 布尔，人事档案是 `code=200`，薪酬个人报表是 `status` 布尔。
- **分页**：出勤统计接口不返回 page 对象。其余表格接口的 `pageSize` **由服务端固定，传参无效**：通讯录为 100，出勤与报表类为 20。全量数据必须递增 `current` 翻页（已实测：通讯录 `current=1` 返回 100 条、`current=2` 返回 45 条，合计 145）。
- **数据行位置不统一（三种命名，已实测）**：

  | 接口 | 数据行字段 |
  |---|---|
  | 通讯录 `hr.contact.search` | `data` |
  | 出勤表格类（`abnormal`/`signrecord`/`orbit`/`worktime`/`leave`/`appeal`）+ hr 报表（`report.*`/`props.table`） | **`displayData`**（此时 `data` 是空数组） |
  | 出勤报表 `attend.report.get` | **`datas`**（注意复数） |

  取不到行数据时，按上表依次检查 `data` → `displayData` → `datas`。CLI 原样透传整个响应对象，不做重命名。
- **每页条数由服务端按接口独立配置，传参无效**（实测）：通讯录 100，出勤表格类（`abnormal`/`signrecord`/`orbit`/`appeal`）20，出勤 `worktime`/`leave.report` 10，hr 报表 `report.*`/`props.table` 20。全量数据一律递增 `current` 翻页。
- **默认人员范围**：通讯录默认只查**在职**（`personnelStatus=["normal"]`）；人员状态变更台账默认**包含离职人员**。这是最容易出错的一处差异。
- **日期格式**：出勤与人事档案用 `YYYY-MM-DD`；创建申诉的 `attendDay` 用 `yyyyMMdd`；薪酬月份用 `YYYY-MM`。

## 安全边界

- 姓名、工号等条件都是**模糊匹配**，多人命中时必须列出候选让用户选择，不得自动取第一条。浏览器选择器返回多条时，响应会带 `requiresDisambiguation=true`，此时必须让用户确认选哪一个。
- `est.emp.table` / `est.dc.emp.table` 的 `cfgId`、`id` 必须先从对应数据源记录取得，禁止编造或使用默认值。
- 社保台账查询（`salary.siaccount.query`）不分页且返回高度敏感数据，CLI 会强制要求提供月份范围或人员/组织 ID 之一。
- 涉及附件、图片、本地文件或文件内容解析时，必须先向用户说明文件内容可能进入大模型上下文，也可能被发送到业务系统或解析服务，获得**明确确认**后才继续。本技能当前不涉及附件接口，若后续新增同类能力必须沿用该确认要求。
- 招聘撤销类写操作（取消改期入职）与移动端考勤打卡属于禁用边界，见 [`references/safety-boundaries.md`](references/safety-boundaries.md)。招聘其余写操作必须走 prepare→apply 确认协议。

## 失败处理

- 遇到 `partial`、`write_uncertain`、登录失效、回查失败或网络中断，立即停止当前写流程，不自动重试写入，先做一次只读回查再决定。
- `code=302` 或认证类错误：引导用户执行 `weaver-work-cli auth login`，不要用其他方式排查。
- 业务失败看 stderr JSON 的 `error.type` / `error.subtype` / `error.message`，不要用退出码 `0` 判断成功。

## 平台兼容

命令示例必须同时兼容 Windows 与 macOS/Linux。涉及 JSON stdin 时：

Windows PowerShell：

```powershell
weaver-work-cli --json ehr run ehr.attend.summary.get --input-json '{"beginDate":"2026-03-01","endDate":"2026-03-31"}'
```

macOS/Linux（bash/zsh）：

```bash
printf '%s
' '{"beginDate":"2026-03-01","endDate":"2026-03-31"}' | weaver-work-cli --json ehr run ehr.attend.summary.get --input -
```

复杂 JSON 建议保存为 UTF-8 文件后按平台传给 `--input <file>`。
