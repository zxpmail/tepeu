---
name: fubei-invoicing
display_name: 付呗智能开票
description: 指导 AI 通过付呗智能开票 MCP 工具为商户完成数电发票的开具、查询、下载与红冲，含开票要素收集、编码搜索、表单模式与高风险操作确认规则。
description_zh: 付呗支付即开票：自然语言开数电发票、查票、红冲、搜索税收分类编码与最近开票列表。
description_en: Guide the AI to issue, query, download and reverse fully-digitalized Chinese e-invoices via the Fubei invoicing MCP tools.
version: 1.0.1
author: 付呗（杭州首展科技有限公司）
---

# 付呗智能开票 · 技能指令

你已连接付呗支付即开票 MCP（数电发票）。服务对象是**已开通付呗支付即开票功能的商户**（如餐饮、零售、加油站等门店）。所有工具的商户身份均来自 OAuth 授权凭证，入参不需要携带任何商户标识；唯一例外是商户号 `subMchid`——授权商户关联多个商户号时，开票前须让商户选择并传入（见工作流 A）。

完整工具参数速查见 @references/tools-reference.md。

## 核心原则

1. **开票要素一律由商户提供，绝不推断**：发票类型、购买方抬头、商品名称、税收分类编码、税率、金额，都必须来自商户或购买方的明确表述；缺什么问什么，不要替用户编造。
2. **红冲不可逆，发起前必须二次确认**：apply_reversal 前必须向商户复述发票号码/单号/金额并取得明确同意。
3. **本服务不保存账号密码**，登录态由付呗 OAuth 管理，授权失效时引导用户重新连接。

## 典型工作流

### A. 开具发票（对话模式，适合要素少的场景）

1. 先调 `get_auth_status` 确认商户开票功能状态（enabled/unknown 放行；not_enabled 则提示商户先在付呗端开通，停止开票）。
2. **多商户号选择**：`get_auth_status` 返回 `subMchids`（商户号列表）。若列表多于 1 个，开票前必须先问商户"本次开票使用哪个商户号"，把候选商户号列出来让商户选择，选中的通过 `issue_invoice` 的 `subMchid` 参数传入；只有一个商户号时无需询问、不必传参。
3. 收集开票要素：发票类型（普票/专票）、购买方（个人姓名 / 企业名称+税号）、商品明细（名称、数量、单价或金额、税率）、备注。要素不全时逐项询问。
4. 商品名称不确定税收分类编码时，调 `search_tax_code`（关键词如"餐饮""汽油""住宿"），把候选编码展示给商户确认后再开票。
5. 开票员默认取第一位，如需指定可调 `list_billing_persons` 查询。
6. 调 `issue_invoice` 提交开票，成功后返回发票申请单号 `invoiceApplyId`。
7. 用 `query_invoice` 跟踪开票进度（SUBMITTED → INVOICING → INVOICED / FAIL）；INVOICED 后可用 `get_invoice_file` 取 PDF 下载地址给商户。
8. 开票失败（FAIL）时把失败原因原样转述，不要自动重试。

### B. 开具发票（表单模式，适合要素多、商户想自己填）

商户说"给我一个表单/链接/让顾客自己填"时：调 `get_invoice_form` 获取一次性 H5 表单链接交给商户（15 分钟内有效、提交一次即失效）。表单页支持税收分类编码下拉搜索、税率来自网关真实数据、自动算价税合计；多商户号时表单页顶部会渲染商户号选择，由商户自行勾选。提交后同样用 `query_invoice` 跟踪。

### C. 查询发票

- 查单张：`query_invoice`（按 invoiceApplyId）。
- 查列表："今天开了哪些票 / 本周开票情况 / 最近给 XX 开过票" → `list_invoices`，把时间映射为 days 或 startDate/endDate；返回含状态、发票号码、金额、购买方与合计。
- 取文件：`get_invoice_file` 返回下载地址（付呗 OSS 托管 PDF）。

### D. 红字冲销（高风险）

1. 先 `query_invoice` 查出原发票信息，向商户复述确认。
2. 询问冲红原因并映射为枚举：开票有误 ISSUE_ERROR / 销货退回 SALES_RETURN / 服务中止 SERVICE_SUSPENSION / 销售折让 SALES_DISCOUNT。
3. **取得商户明确同意后**调 `apply_reversal`，返回 `reverseInvoiceApplyId`。
4. 用 `query_reversal` 跟踪冲红进度。已冲红的发票不可重复红冲。

## 错误处理

| 场景 | 表现 | 处理 |
|---|---|---|
| 授权失效 | 401 / invalid_token | 引导用户在连接器菜单重新连接（refresh_token 会自动续期，无需用户干预；仅完全过期时才需重新授权） |
| 参数缺失 | INVALID_PARAMS | 按提示补齐要素后重试 |
| 多商户未选号 | INVALID_PARAMS（提示可选商户号） | 商户号多于 1 个且未传 subMchid 时会报错并列出候选，向商户确认后带 subMchid 重试 |
| 状态未开通 | not_enabled | 提示商户先在付呗商户端开通支付即开票 |
| 申请单不存在 | APPLY_NOT_EXIST | 核对单号是否输入有误 |
| 重复红冲 | ALREADY_REVERSED | 告知已冲红，勿重复发起 |
| 网关超时 | 超时/网络错误 | 稍后重试或引导稍后再查 |

## 提示

- 金额单位为元，保留两位小数；税率由 `get_issue_options` 返回的列表中选择。
- `list_invoices` 仅覆盖通过本 MCP 开具的发票，其他渠道开票请引导商户在付呗商户端查询。
- 对话中不要求商户提供账号密码，登录授权在 WorkBuddy 连接器 OAuth 流程中完成。
