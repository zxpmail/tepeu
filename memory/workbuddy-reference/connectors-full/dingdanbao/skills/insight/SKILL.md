---
name: dingdanbao-insight
description: "订单豹工作台、业绩、客户人数、待办条数、经营简报与自由查数。"
description_zh: "今日工作台、我的业绩、客户人数、待办完成/逾期、经营简报与销售数据查询。"
description_en: "Today's workbench, my performance, customer counts, completed/overdue todos, business briefs, and sales data queries."
version: "1.2.1"
author: "订单豹"
---

# 订单豹 · 洞察

今日工作台、我的业绩、客户人数、待办条数、经营简报、自然语言查数。

## 认证说明

登录失败时请用户重新连接连接器完成授权。不要在对话里要密码。

## 对用户说话

只说姓名、订单号、金额、人数、条数。不要说内部编号、账号 uid 或工具名。

## 模型路由（不要向用户复述）

「我是谁」、工作台、业绩、新增/跟进/全部/拉黑客户人数、待办完成数/待办逾期数、查数用本技能。本技能不下单、不出收款码。不要猜 salesman_id。

## 可用工具

### ddb_auth_whoami

当前登录账号。无参数。返回 uid/username/realname（uid 不对用户展示）。

### ddb_get_workbench_brief

今日工作台简报。无参数。

### ddb_get_my_performance

业绩看板。参数：`period`(默认 month)、`salesman_id`(查他人时用，须用户指定)。

### ddb_get_performance_targets

目标进度。参数：`period`、`salesman_id`(查他人时用，须用户指定；默认当前登录账号)。

### ddb_get_performance_tab_stats

订单/出库/退货汇总。参数：`tab`(order/deliver/refund)、`period`、`salesman_id`。

### ddb_get_performance_orders

业绩明细。参数：`tab`、`period`、`page`、`page_size`、`list_type`(order/goods)、`salesman_id`。

### ddb_get_my_top_customers

我的 TOP 客户。参数：`limit`、`period`。

### ddb_get_my_customer_kpi

当前登录账号的客户人数。参数：`period`(默认 month：today/month/last_month/year)、`metric`(默认 all：all/new/follow/total/blacklist)。

对应：新增客户、跟进客户、全部客户、拉黑客户。`period` 只影响新增和跟进；全部、拉黑是存量。不要向用户展示 source。

### ddb_get_my_todo_kpi

当前登录账号的待办条数。参数：`period`(默认 month：today/month/last_month/year)、`metric`(默认 all：all/done/overdue)。

对应：待办完成数、待办逾期数。按条计，不是客户人数，也不是跟进客户。口径同 PC 业绩统计：完成看完成时间，逾期看到期日且未完成。不要和工作台「今日待办」混用。不要向用户展示 source。

### ddb_get_brief

日月年经营简报。参数：`period`、`start_time`、`end_time`、`detail`。

### ddb_free_query

自然语言查数。参数：`text`(必填)。适合「这个月酱油卖了多少」。结果再按展示规范输出。

### ddb_sales_summary

销售汇总。参数：`period`、`keyword`。

### ddb_performance_rank

业务员业绩排行。参数：`period`、`limit`。走业务员提成列表，不是客户消费 TOP。客户排行用 `ddb_customer_rank`。

### ddb_goods_sales

商品销量。参数：`period`、`keyword`、`sort`(money 等)。

### ddb_customer_rank

客户消费排行。参数：`period`、`limit`。

### ddb_delivery_summary / ddb_refund_summary

出库汇总 / 退货汇总。参数：`period`。

## 使用示例

- 「今天待办 / 工作台」→ `ddb_get_workbench_brief`。
- 「本月我的业绩」→ `ddb_get_my_performance`。
- 「这个月我新增了几个客户」→ `ddb_get_my_customer_kpi`（`metric=new`）。
- 「我名下全部客户多少 / 拉黑了几个」→ `ddb_get_my_customer_kpi`（`metric=total` 或 `blacklist`）。
- 「这个月待办完成了几条 / 逾期几条」→ `ddb_get_my_todo_kpi`。
- 「这个月酱油卖了多少」→ `ddb_free_query` 或 `ddb_goods_sales`。
- 「我是谁」→ `ddb_auth_whoami`。

## 错误场景

| 现象 | 处理 |
|------|------|
| 登录失败 | 请用户重新连接连接器完成授权 |
| 自由查数答不上 | 改用对应的 summary/rank 工具 |
| 无权限看他人业绩 | 不要猜 salesman_id，改查本人 |
| 当前账号没有客户业绩数据 / 没有待办统计数据 | 请用户重新连接连接器，换有权限的账号授权 |
| 业绩目标接口需要业务员 ID | 当前号可能不是业务员，请用户指定业务员或换账号；不要重试空参 |
