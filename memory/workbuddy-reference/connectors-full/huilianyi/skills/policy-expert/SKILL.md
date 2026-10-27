---
name: policy-expert
display_name: 政策问答专家
description: Answer company expense policy, personal expense standards and quota questions via Huilianyi MCP.
description_zh: 指导 AI 调用汇联易 MCP 查询企业费用政策、本人费用标准与费用额度。
description_en: Query company policy, personal expense standards and quotas via Huilianyi MCP.
category: office
version: 1.0.0
author: 汇联易
---

# 政策问答专家

## 何时使用

用户问：公司差旅/费用政策、交通住宿标准、自己的报销标准、剩余可用额度时，调用本连接器。

## 技能与工具

| 技能 | 工具名 | 用途 | 关键参数 | 写操作 |
| --- | --- | --- | --- | --- |
| 企业政策 | `company-policy-query` | 查询公司级费用/差旅政策条文 | 政策类别、关键词、适用人群 | 否 |
| 我的费用标准 | `my-expense-standard-query` | 查询当前用户的费用标准（城市/职级维度） | 费用类别、城市、日期 | 否 |
| 我的费用额度 | `my-expense-quota-query` | 查询当前用户的额度与已用情况 | 费用类别、时间范围 | 否 |

> 参数名以 MCP Server 实际 JSON Schema 为准。

## 调用约定

- 三者都是只读查询，无需用户确认，但**回答时要注明数据口径与生效日期**（如"按 2026 版差旅制度"），政策可能分版本。
- 涉及"能不能报""超不超标"类问题：先查政策/标准，再结合用户的具体消费金额做对照，给出结论与依据条文。
- 用户问的类别不明确时（如"住宿标准"），先确认城市与职级，这类标准通常按两者区分。
- 政策条文若返回长文本，摘要关键数字（限额、比例、审批要求）而不是全文粘贴。

## 错误与恢复

| 现象 | 处理 |
| --- | --- |
| 401 / 授权失效 | 引导用户在连接器页重新「连接」 |
| 查询无匹配政策 | 如实告知未找到，建议用户确认政策类别或咨询财务 |
| 超时（>30s） | 告知稍后重试 |

## 认证前置

使用 MCP 自带 OAuth。首次使用需在连接器页点击「连接」完成汇联易登录。
