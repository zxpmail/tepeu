---
name: youshu-data-prod
display_name: 音为有数 生产数据查询
display_name_en: 音为有数 Data Query
description: Use the Production environment to answer authorized music catalog, contract, finance, performance, singer, and catalog-event questions through the youshu MCP tools.
description_zh: 生产环境：通过 youshu 查询有权限访问的歌曲、合同、收支、业绩、歌手指标和曲库事件。
description_en: Query authorized music catalog, contract, finance, performance, singer, and catalog-event data with youshu.
version: 1.2.0
author: youshu
---

# youshu 数据查询

当用户询问曲库歌曲、采买或宣推合同、收入与支出、付款流水、部门与人员业绩、歌手运营指标、认领或下架事件时，使用本连接器。它提供 19 个只读 MCP 工具。完整的工具选择表见 @references/tool-routing.md；自然语言问题到请求参数的示例见 @references/query-playbook.md。工具列表和实时 Schema 是调用时的最终依据，不自行拼接 OpenAPI 路径或调用未列出的接口。

平铺 JSON 示例见 @references/schema-examples.md。

## WorkBuddy 调用方式

直接调用 MCP 当前列出的工具；参考 Agent 的 `skill_load`、`search_apis`、`invoke_api`、`ui_renderer` 和 COS 卡片流程不属于本连接器调用步骤。参数是平铺 JSON 对象，不包在 `request` 或 `req` 下。只传业务参数，账号、租户、权限、thread/run、trace 字段由接入平台处理，用户或模型不得填写。

使用实际运行时间按北京时间解析相对时间，不照抄示例月份。“我部门”通常省略 `depId`，无需先查部门树；用户点名其他部门时才解析部门。只给人员姓名时先查 `conditionList(statDimension=PERSON)`，使用唯一匹配项的 `conditionId`。

工具 Schema 的分页上限是服务端能力；日常明细从 `pageSize=10` 开始。完整查询最多取 1000 条，超过时说明限制并请用户缩小范围，不把部分结果作为完整统计或全量导出。服务端歌曲月收支 Top N 可使用 `limit=N`（最多 1000），不可把它改成客户端先取 10 行再排序。服务端非分页业绩排行保留原始名次。

## 每次查询的步骤

1. **确定业务对象和粒度。** 先分清歌曲基础资料、歌曲月度收支、部门月度收支、付款流水、合同、业绩、歌手运营指标或曲库事件。尤其分清“部门自身业绩”和“下级部门排行”，“歌曲采买合同号”和“完整合同”，“歌手运营”和“歌曲运营”。
2. **补齐必要条件。** 从用户话语提取名称或 ID、时间范围、部门、歌曲范围、排序和结果数量。仅在缺少的条件会改变业务含义或工具要求必填时询问；能由明确的“上月”“最近七天”等表达确定时直接换算。姓名不能当作人员 ID，必要时先查 `conditionList`；部门不明确但需要指定 `depId` 时可用 `getDepartmentInfoTree`。
3. **按 Schema 构造最小请求。** 使用工具实际暴露的字段名、枚举和类型。不要同时传互斥条件；传排序方向时必须同时传排序字段；月度收支只传排序字段时方向默认 DESC。无需填与问题无关的条件。一次查询只选足够回答问题的工具，确需跨工具补充时分别说明各工具的业务口径。
4. **核对响应，再作答。** 检查结果是否分页、是否截断、是否含预估或不可见指标，保留服务端排序和排名。说明实际查询范围、时间、单位和结果条数；只有完整取回后才称“全部”或基于全量计算统计。空结果、无权限、接口失败和超时要分别表述。

## 可直接复用的常见问题

- “查询部门业绩排行”：当前部门，`songRange=ALL`、`aiSongType=ALL`、`viewLevel=ALL_CHILD_DEP`，调用部门排行。
- “查询人员业绩排行”：同样三个分类参数，调用人员排行；包含当前部门自身人员。
- “近7天支出记录”：用付款时间预设，查询总数及最近 20 条（两页、每页 10），明确仅展示最近 20 条。
- “近7天认领入库／被认领／下架”：使用各自固定状态工具及事件时间预设，三种业务方向不可互换。
- “本月支出最多100首歌曲”：当前部门、本月，`sortField=AMOUNT`、`sortDirection=DESC`、`limit=100`；单月行数与歌曲数对应。
- “查询我的部门收入”未给时间时，只问需要查询哪个月份或范围。参考 Agent 的默认月份由专用预设计划决定，本连接包未取得该计划，不猜测时间。

原 Agent 的 21 类预设含歌曲运营与歌曲宣推查询；本 MCP 只开放 19 个工具，不能照搬全部预设能力。

## 常见选择

| 用户表达 | 选用工具 | 关键理由 |
| --- | --- | --- |
| “查这首歌的发行信息、作者、供应商” | `batchQueryTrackBasicInfo` | 查录音库歌曲资料，不是收入或运营数据。 |
| “这首歌有哪些采买合同号” | `pageQueryTrackPurchaseContractInfo` | 返回歌曲关联的词、曲、表演者、录音录像合同号。 |
| “看采买合同授权、预付款、分成” | `pageQueryPurchaseContractInfo` | 返回完整采买合同与作品清单。 |
| “看宣推合同费用” | `pageQueryPromotionContractInfo` | 仅为合同费用，不能解释为歌曲宣推实际成本。 |
| “上月我部门收入/支出” | `queryDepartmentIncome` / `queryDepartmentExpense` | 单部门逐月汇总。 |
| “本月支出最多的 100 首歌” | `querySongExpense` | 按歌曲-月份行在服务端排序后截取。 |
| “最近七天付款流水” | `pageQueryExpenseFlow` | 付款记录，按付款时间倒序。 |
| “张三的业绩” | `conditionList` → `queryPersonPerformance` | 先把姓名解析为绩效人员 ID。 |
| “下级部门谁排名靠前” | `queryDepartmentPerformanceRanking` | 下级部门排行不包含查询部门自身。 |
| “歌手近七天的运营指标” | `pageQuerySingerOperationData` | 歌手级周期指标，非歌曲逐日运营。 |
| “最近认领入库/被别人认领/已下架的歌” | 对应的三个曲库事件工具 | 事件状态由专用工具固定。 |

## 参数和结果口径

- **ID：** `trackId` 是 SaaS 曲库歌曲 ID；歌曲月度收支中的 `songId` 是中央曲库歌曲 ID；歌手运营的 `singerId` 是中央曲库歌手 ID。不能只因数字相同就跨 ID 体系套用。外部平台歌曲 ID 要按工具 Schema 提供平台类型及 ID 列表。
- **时间：** 部门/歌曲月度收支的 `startMonth`、`endMonth` 为 `yyyy-MM`，查询单月时两者相同。歌曲资料的发行、上线、创建日期筛选为 `yyyy-MM-dd`。付款流水和曲库事件的明确日期范围使用成对的 13 位毫秒时间戳，按北京时间理解自然日；“近七天”优先使用各工具的 `RECENT_7_NATURAL_DAYS` 预设，勿同时传起止时间。
- **分页与截断：** 分页接口从 `pageNo=1` 开始，按 Schema 的 `pageSize` 上限请求。歌曲月度收支的 `limit` 限制**歌曲-月份行**，不是去重后的歌曲数。分页总数以 `totalCount` 为准；只读取部分页时不得宣称总结果已全部展示。
- **金额与指标：** 合同、收支和流水金额按返回契约以元解释，不把空字符串当零。月度收入/支出可能包含预估值；应提示估算口径。业绩付费份额为空字符串表示不可见，不是 0%；歌手结算指标还要看可见性标记，标记为 `false` 时默认数值 0 不代表真实业务值。
- **统计范围：** 部门/人员业绩工具返回固定的年度、最近完整月、昨日、本月累计或近 30 天指标，不接收任意自定义日期。歌手 `YESTERDAY` 是单日值，其余周期是对应周期日均值；返回的 `date` 是数据更新日期，不是统计周期起止日。

## 能力边界与回答

- **歌曲宣推状态/实际成本**和**歌曲逐日运营数据**的两个原子接口未注册为 MCP 工具。不能把宣推合同、歌手运营或歌曲基础资料冒充这些数据，也不要组合其他工具重建受保护的歌曲级数据。遇到此类请求，直接说明当前连接器不提供。
- 工具只返回当前可访问范围内的数据。若请求对象不在结果中，不据此断言全局不存在；如有 `totalCount`，区分“本页无数据”和“整个查询为空”。
- 默认用简洁文字或表格给出结论、关键数值、筛选条件和必要限制。不要暴露内部工具调用、认证字段或错误堆栈。用户明确要求文件时，仅能基于已取得、获授权且完整的数据使用 WorkBuddy 的文件能力；未生成文件时不要宣称已导出。
