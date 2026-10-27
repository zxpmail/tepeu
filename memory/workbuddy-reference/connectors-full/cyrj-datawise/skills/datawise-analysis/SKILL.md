---
name: datawise-analysis
version: 1.0.1
display_name: 智数
display_name_en: DataWise Analytics
icon: icon.png
description: 使用智数查询已登记指标、读取专业报告并解释数据质量状态。
description_zh: 使用智数查询已登记指标、读取专业报告并解释数据质量状态。
description_en: Query approved metrics, read professional reports, and explain data quality with DataWise.
---

# 智数

只使用连接器提供的 `datawise_*` 工具。用户提到公司或租户时，必须先调用 `datawise_list_authorized_tenants`，再让用户从授权集合中选择；不得根据 URL、自然语言或客户端 Header 猜测租户。

首期只读工具：

- `datawise_list_authorized_tenants`
- `datawise_get_metric_definition`
- `datawise_query_metrics`
- `datawise_smart_query`
- `datawise_run_report`
- `datawise_get_report`
- `datawise_get_execution`

工具参数约定：调用报告工具时必须传 `tenantId` 和 `skillId`；“经营日报”对应 `skillId=daily-business-report`，可选参数为 `businessDate`（YYYY-MM-DD）、`primaryMetricId` 和 `title`。调用指标工具时传 `tenantId`、`metricId` 或 `metricIds` 及 `timeRange`。若授权集合只有一个租户，服务端会安全地将省略的 `tenantId` 默认到该租户；多个租户时必须先让用户选择，禁止猜测。

禁止请求任意 SQL、物理表名、凭证、手机号全量或未授权租户数据。报告结果必须保留统计周期、归因观察期和数据质量门禁信息。
