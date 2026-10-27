---
name: invoice-expert
display_name: 发票管理专家
description: Query invoice info, recognize non-mainland-China receipts and verify invoices via Huilianyi MCP.
description_zh: 指导 AI 调用汇联易 MCP 查询开票信息、识别国际小票、查验发票真伪。
description_en: Query invoice info, recognize international receipts and verify invoices via Huilianyi MCP.
category: office
version: 1.0.0
author: 汇联易
---

# 发票管理专家

## 何时使用

用户提到：开发票/查开票信息、国外或港澳台小票识别、发票查验/验真时，调用本连接器。

## 技能与工具

| 技能 | 工具名 | 用途 | 关键参数 | 写操作 |
| --- | --- | --- | --- | --- |
| 开票信息 | `invoice-info-query` | 查询公司开票信息（抬头、税号等） | 公司主体、抬头类型 | 否 |
| 国际小票识别 | `non-mainland-china-receipt-recognition` | 识别非中国大陆票据/小票内容 | 票据图片、币种提示 | 否 |
| 发票查验 | `invoice-verification` | 发票真伪查验 | 发票代码/号码、开票日期、金额（不含税/价税合计） | 否 |

> 参数名以 MCP Server 实际 JSON Schema 为准。

## 调用约定

- **开票信息**：返回的税号等关键字段要完整原样输出，用户常直接复制使用，不要省略或改写格式。
- **国际小票识别**：需要用户提供清晰票据图片；识别结果按字段结构化展示（商户、日期、金额、币种），对模糊字段标注"识别不确定"，不要补全猜测值；涉及汇率换算时说明使用的汇率与日期。
- **发票查验**：查验要素必须与用户逐项核对（发票代码、号码、日期、金额），任一要素错误都会导致验真失败；查验结果明确给出"查验通过/未通过/无法查验"及原因。

## 错误与恢复

| 现象 | 处理 |
| --- | --- |
| 401 / 授权失效 | 引导用户在连接器页重新「连接」 |
| 查验要素缺失 | 列出缺少的要素请用户补充，不猜测 |
| 图片无法识别 | 请用户重拍：光线充足、票据完整、避免反光 |
| 超时（>30s） | 告知稍后重试，不静默重试 |

## 认证前置

使用 MCP 自带 OAuth。首次使用需在连接器页点击「连接」完成汇联易登录。
