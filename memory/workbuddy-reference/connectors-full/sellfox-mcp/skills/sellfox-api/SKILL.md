---
name: sellfox-api
description: 指导 AI 正确调用 Sellfox 开放接口 MCP 工具。当用户需要查询店铺、订单、利润报表、多平台结算数据或管理亚马逊 SP 广告时使用。
version: 1.0.0
---

# Sellfox 开放接口使用说明

本连接器通过 MCP 工具访问 Sellfox 跨境电商 ERP。所有工具均返回 JSON 数据。

## 工具分组

### 基础数据
- `get_shop_page_list`：店铺列表（分页）
- `get_order_page_list`：订单列表（分页，支持时间范围筛选）
- `get_order_detail`：订单详情
- `get_online_product_page_list`：在线产品列表
- `get_custom_report_page_list`：自定义报表数据

### 亚马逊 SP 广告
- 查询：`get_sp_campaign_list` / `get_sp_ad_group_list` / `get_sp_ad_product_list` / `get_sp_keyword_list` / `get_sp_product_targeting_list` / `get_sp_negative_keyword_list` / `get_sp_negative_product_targeting_list`
- 编辑：`edit_sp_campaign`（预算/状态）/ `edit_sp_ad_group` / `edit_sp_ad_product` / `edit_sp_targeting`
- 新建定向：`create_sp_keyword_targeting` / `create_sp_negative_keyword_targeting` / `create_sp_product_targeting` / `create_sp_negative_product_targeting`
- 其他：`close_sp_negative_targeting`（关闭否定定向）、`create_ad_download_task` / `get_ad_download_task_page_list`（广告数据下载任务）

### 利润与结算报表
- 利润：`get_profit_report_by_shop` / `get_profit_report_by_msku` / `get_profit_report_by_sku` / `get_profit_report_by_order`
- 回款：`get_receipt_order_page_list`、`get_amazon_reimbursement_report`
- 多平台结算：Temu 全托管/半托管系列工具、Shein 结算系列工具、`get_mercadolibre_settlement_page_list`

## 调用要求

1. 时间参数一般使用 `startTime` / `endTime`（毫秒时间戳或 YYYY-MM-DD，以具体工具定义为准），分页参数为 `pageNum` / `pageSize`。
2. 涉及广告出价、预算修改（edit_* / create_* 类工具）属于高风险写操作，执行前必须向用户确认店铺、对象 ID 和目标值。
3. 返回错误时，将错误信息原样反馈给用户，不要编造数据；凭证失效时引导用户重新连接。
