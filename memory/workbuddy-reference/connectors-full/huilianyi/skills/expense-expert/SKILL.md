---
name: expense-expert
display_name: 费用报销专家
description: Handle travel/expense request creation, expense claim submission and approval status queries via Huilianyi MCP.
description_zh: 指导 AI 调用汇联易 MCP 完成费用申请、费用报销创建及审批查询与审批进度查询。
description_en: Guide the AI to create expense requests and claims, and query approval status via Huilianyi MCP.
category: office
version: 1.0.0
author: 汇联易
---

# 费用报销专家

## 何时使用

用户提到：申请差旅/费用、创建或提交报销单、查审批列表、查审批进度/当前审批环节时，调用本连接器。仅闲聊费用制度不涉及单据数据时不调用。

## 技能与工具

| 技能 | 工具名 | 用途 | 关键参数 | 写操作 |
| --- | --- | --- | --- | --- |
| 费用申请 | `expense-request-create` | 创建费用/差旅申请单 | 申请人、事由、金额、费用归属、起止日期 | 是（高风险） |
| 费用报销 | `expense-claim-create` | 创建报销单 | 单据类型、费用明细、金额、币种、发票关联 | 是（高风险） |
| 审批查询 | `approval-list-query` | 查询审批单列表 | 申请人、时间范围、单据状态、分页 | 否 |
| 审批进度查询 | `approval-progress-query` | 查询单据当前审批环节与流转记录 | 单据 ID 或单号 | 否 |

> 参数名以 MCP Server 实际 JSON Schema 为准，上表为业务语义。

## 调用约定

- **日期**：入参 `YYYY-MM-DD`；"上周""本月"先换算为绝对区间并在回复中说明。
- **金额**：注意币种与精度，跨币种不自行换算，按接口返回原样展示。
- **单据标识**：优先用接口返回的稳定 ID，不要用用户口述单号猜测。
- **分页**：列表工具默认只取首页，用户要"全部"时明确说明翻页范围。

## 高风险确认规则

`expense-request-create` 和 `expense-claim-create` 执行前必须：

1. 补齐必填字段，缺失字段逐项向用户确认，不得编造；
2. 回显完整信息（单据类型、费用明细、金额合计、币种、费用归属、日期）；
3. 明确问句确认（"是否确认提交？"），用户未确认不得执行；
4. 执行后返回单据 ID/单号与当前状态，提示下一步。

## 错误与恢复

| 现象 | 处理 |
| --- | --- |
| 401 / 授权失效 | 引导用户在连接器页重新「连接」，不要重试原请求 |
| 参数校验失败 | 读取错误中的字段名，向用户确认后重试，不盲试 |
| 超时（>30s） | 告知稍后重试，不静默重试多次 |
| 无权限 | 说明当前账号无该单据权限，建议联系费用管理员 |
| 空结果 | 如实告知并复述查询条件 |

## 认证前置

使用 MCP 自带 OAuth（WorkBuddy 内置 OAuth 管理器，PKCE + S256）。首次使用需在连接器页点击「连接」完成汇联易登录。
