---
name: tedianren
display_name: 特点人 · 人才测评
display_name_en: Tediaren Talent Assessment
description: 查询并解读「特点人」企业人才测评数据。当用户需要按姓名/昵称查找人才、按性格/能力/价值观等条件筛选人才、查看某人或某部门的测评报告（MBTI 风格/角色类型/价值观/动机/4Q 职业素质/36 才干/评价指标/岗位匹配）、判断人才适合什么岗位或能否带团队、做部门团队盘点与搭配建议时使用。触发词：特点人、人才测评、测评报告、找人才、筛选人才、团队盘点、部门分析、MBTI、才干、价值观、动机、4Q、岗位匹配。
description_zh: 通过「特点人」MCP 连接器查询企业人才测评数据并给出带业务含义的解读。覆盖人才查找与筛选、单份/批量报告读取、部门级聚合分析，以及 MBTI/角色类型/价值观/动机/4Q/36 才干等维度的分数解读与用人建议。
description_en: "Query Tediaren enterprise talent-assessment data and interpret it with business meaning. Covers talent lookup and filtering, single/batch report retrieval, department-level aggregation, and score interpretation across MBTI, role types, values, motives, 4Q, and 36 talents."
version: 1.0.1
author: 特点天赋数据科技重庆有限公司
---

# 特点人 · 人才测评企业分析系统

用于查询并解读「特点人」平台中的企业人才测评数据。鉴权走连接器注入的 `Authorization: Bearer <API Key>`，密钥绑定企业，系统自动识别企业、**无需也不能**在请求中传入企业标识。

> 💡 提示：如已安装技能「特点人 · 人才测评套件」（`tedianren-talent`），可结合使用——该技能内置完整测评知识库（MBTI/角色类型/价值观/动机/4Q/36 才干词条释义与解读纪律），并提供简历筛选 × 人岗匹配能力，能给出更专业、更结构化的解读与用人建议。

## 推荐调用路径

- **以人为中心**（了解某个/某几个具体的人）：`talent.find`（按姓名/昵称找人）→ 拿 `report_id` → `report.get`（单份完整）或 `report.batch_get`（批量瘦身）。
- **按条件筛人**（按性格/能力/价值观找一批人）：`talent.search`（多维度筛选，支持 filter DSL 复杂条件）→ 拿 `talent_id`/`report_id` → `report.get` / `report.batch_get`；不确定 filter 写法时先调 `tool.help('talent.search')` 查询参数、字段枚举与示例。
- **以组织为中心**（看部门/团队整体）：`company.get`（组织架构）→ `depart.get`（部门全员与 report_ids）→ `report.batch_get`；人数较多时直接用 `report.analyze_by_depart_id` 取部门级统计聚合，无需逐份报告。

## 工具清单（共 8 个）

| 工具 | 用途 | 入参 | 关键产出 |
|---|---|---|---|
| `talent.find` | 按姓名 / 昵称模糊查找人才 | `name`（关键词）、`limit`（可选） | 候选人列表 + 每人的「有效解锁报告列表」（含 report_id） |
| `talent.search` | 按性格/能力/价值观等**多维度筛选**人才 | 扁平参数（mbti/role/ability_min 等）或 `filter`（JSON DSL：and/or/not 嵌套 + 比较 + in_top/in_bottom 个人内排名） | 候选人基本信息（姓名/MBTI/部门/性别/学历/年龄）+ 有效解锁报告列表 |
| `company.get` | 查看本企业组织架构 | 无 | 组织架构树：部门 id、上下级、层级、人数、报告数、覆盖率 |
| `depart.get` | 查部门信息与全员报告 | `depart_id` 或 `keyword`（二选一） | 部门信息 + 成员列表 + 部门内全部有效报告 id |
| `report.get` | 取**单份完整**报告 | `report_id` | 完整解读报告（风格/角色/价值观/动机/才干/素质能力/岗位匹配/评价指标/管理理念） |
| `report.batch_get` | 取**批量瘦身**报告 | `report_ids`（逗号分隔，最多 100 个） | 才干 36 项 / 能力 24 项分数（优势弱势超集，约 2.8KB/份） |
| `report.analyze_by_depart_id` | 取**部门级统计聚合** | `depart_id` | 团队强弱项 Top10、角色分布、均值/中位数/分箱等，无个人原始数据 |
| `tool.help` | 查工具详细使用说明 | `tool_name`（工具名）/ `topic`（专题，如 filter_dsl） | 参数/字段枚举/运算符/示例/返回结构 + 实时价格（0 Token） |

## ID 传参约定（重要）

- 工具返回的所有 id 都是**带字母前缀的字符串**，请原样传回：人才 `T-`、部门 `D-`、报告 `R-`、企业 `C-`。
- 例：`"depart_id": "D-vLMYnFzmdC"`、`"report_id": "R-bos"`；把返回的 id 原样作为字符串传回即可（系统自动识别前缀），纯数字 id 也兼容。
- ⚠️ **大小写敏感**：`R-bos` ≠ `R-BOS`，不要当数字运算或改动任何一位。

## 解读口径

- 报告包含 MBTI（风格）、角色类型、评价指标、价值观、动机、4Q 职业素质能力、36 才干、报告可读性、个人认知等维度；解读时把分数翻译为有业务含义的判断，**不要只罗列原始数值**。
- **高分点优势、低分点短板一并说**；**跨维度不可直接相加**（量纲不同，只在同维度内比较）。
- 报告可读性低或人格一致性 < 0.7 时，先提示该报告数据可信度存疑。
- 结论附上 `report_id`、姓名、部门名便于核验。

## 计费与错误处理

- 每次调用按工具单价扣减 Token 余额；**失败不扣费、空结果不扣费**；`tool.help` 为 0 Token 不计费。批量取报告优先 `report.batch_get`，部门分析优先 `report.analyze_by_depart_id`。
- 返回鉴权失败（401/未校验）时，提示用户到特点人管理端（tdhr.tedianren.com）→ MCP 开放平台 → API Key 管理 创建或轮换密钥，并在连接器配置中重新填写。
- 参数错误时，核对 ID 前缀与大小写后再重试；权限不足/部门不存在时如实告知，不要编造数据。
