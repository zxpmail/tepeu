---
name: datayes-data-skill
description: 使用通联数据 MCP 查询 A股与港股、基金、债券、指数、期货期权、因子、行情、宏观、公告、分析师预测和政策法规。
version: "1.1.0"
author: "通联数据 DataYes"
---

# 通联数据 MCP

先在 WorkBuddy 连接器中连接通联数据，按浏览器提示登录 DataYes 账号并授权。
WorkBuddy 管理 OAuth 凭证并自动续期；不要向用户索要 Token、密码或验证码。
本连接器提供数据查询，不执行交易。可用数据受账号权限和余额限制。

## 选择业务域

以当前连接返回的工具列表和参数 schema 为准。统一入口包含以下业务域：

| 工具 | 用途 |
|---|---|
| `astock_search_api` | A股资料、财务、盘后行情、股东、公司事件；申万行业板块日行情与资金流 |
| `hkstock_search_api` | 港股资料与盘后行情；沪深港通、北向/南向资金与持仓 |
| `fund_search_api` | 基金资料、净值业绩、持仓、财务、风险与归因 |
| `bond_search_api` | 债券档案、估值、信用、可转债条款与事件 |
| `index_search_api` | 指数资料、成分、盘后行情、估值与收益率 |
| `futopt_search_api` | 国内期货期权合约、盘后行情、持仓与波动率 |
| `factor_search_api` | 股票量化因子 |
| `quotes_search_api` | 沪深京证券日内实时快照与分钟行情 |
| `macro_search_api` | 宏观行业指标检索；先搜指标代码，再用 `get_macro_data` 取数值 |
| `announcement_search_api` | A股、基金、债券公告列表与原文 |
| `analystfcst_search_api` | 分析师一致预期、盈利预测与评级 |
| `policy_search_api` | 政策法规结构化数据与原文 |

实时/分钟行情选 `quotes_search_api`；盘后历史行情按资产选域。
沪深港通选 `hkstock_search_api`；行业板块行情选 `astock_search_api`。
研报原文与新闻全文不在本统一入口中，不承诺通过分析师预测工具取得原文。

## 普通数据查询（两步）

1. **域搜索**：调用对应域搜索工具，`query` 填业务关键词，如「净利润 利润表」，不必重复域名；
   `user_question` 填用户本轮原始问题，同一问题拆成多次检索时保持原文一致。
   返回 Top 10 候选接口，每个候选自带完整 `parameters` 定义（名称、类型、是否必填、说明）。
   搜索参数：`query`（string，必填）、`limit`（integer，选填，1～10，默认 10）、
   `user_question`（string，选填）；没有合适候选时细化或更换关键词后重试。
2. **直接取数**：从候选中选定接口，按其 `parameters` 定义调用
   `api_call(api_name=..., api_parameters={...}, user_question=...)`，无需再查接口详情。
   `api_parameters` 是对象，日期格式以该接口的参数定义为准。不要凭记忆猜接口名或参数。

仅当返回候选缺少 `parameters` 定义时，才用 `get_api_info(api_name=...)` 补查：`api_name` 为必填
string，需要多个接口时可用英文逗号分隔，最多 5 个。`api_call` 的 `api_name`（string）与
`api_parameters`（object）必填，`user_question`（string）选填。只调用确实需要数据的接口，
近义候选择一，避免重复取数产生费用。

用户只给中文名称时，先通过对应域搜索证券/基金/指数代码查询接口，再查目标数据。
例如「贵州茅台去年的净利润」：`astock_search_api(query="净利润 利润表")` → 从返回的参数定义中
选定接口 → `api_call`，按定义填写证券代码及报告期。

## 宏观查询

宏观取数为两跳：先搜索指标代码，再按代码取时序数值。

1. `macro_search_api(query="CPI")`：返回指标搜索接口 `search_macro_indicators` 及其参数定义。
2. `api_call(api_name="search_macro_indicators", api_parameters={"keyword": "CPI"})`：
   只返回指标元信息与指标代码（indicator_code），不含任何数值；只搜到指标名不等于回答了问题，
   必须继续第 3 步。从结果中选择口径吻合用户问题的指标。
3. `api_call(api_name="get_macro_data", api_parameters={"indicID": "<指标代码>"})`：
   返回时序数值。`indicID` 多个时可传字符串数组；可选 `beginDate`/`endDate` 日期过滤、
   `limit`（最大 50）、`summary_only` 等，参数定义可查 `get_api_info(api_name="get_macro_data")`。

如需经济日历，查 `get_macro_calendar` 的定义后调用。不要把 PMI 数值误表述为同比增速。
若后续服务升级为指标直搜（域搜索直接返回 `data[].indicator_code`），可跳过第 2 步，
直接用返回的指标代码调用 `get_macro_data`。

## 返回数据与错误处理

成功返回通常包含 `retCode: 1` 和 `data`，宏观结果等以实际返回结构为准。
分页按接口定义执行；只有 schema 支持时才传 `pagenum`、`pagesize` 等参数。
若返回截断提示，应缩小代码/日期范围或选择所需字段后重查，不能把展示片段当作完整结果。
回答应注明数据日期、单位、报告期和口径；空数据不能推断为零，不编造缺失值。

| 情况 | 处理方式 |
|---|---|
| `retCode: -1` 或空数据 | 核对代码、日期与口径，必要时调整范围；避免重复相同查询 |
| 参数错误或缺少参数 | 重新查看接口定义并修正 |
| 超时/系统繁忙 | 缩小查询范围，必要时稍后重试一次 |
| HTTP 429 / `CONCURRENCY_LIMITED` | 稍后串行重试，避免并发重发 |
| 积分不足 | 提示用户在通联数据平台核对余额/充值；不推断是否已扣费 |
| 未开通权限 | 提示用户核对该账号的数据订阅，不通过换接口绕过权限 |
| 授权过期/HTTP 401 | 先由 WorkBuddy 自动刷新；若仍失败，提示重新连接并完成浏览器授权 |

费用与权限以通联数据平台及上游返回为准；不要声称所有查询免费或固定按返回行数计费。
