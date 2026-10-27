---
name: drug-search
display_name: 药品 ATC 与医保查询
display_name_en: Drug ATC and Reimbursement Search
description: 查询已上市药品的通用名、批准文号、ATC 分类和医保信息。
description_zh: 根据通用名、商品名或批准文号查询药品基础信息、ATC 与医保状态。
description_en: Search marketed-drug information, ATC classification, and reimbursement status.
allowed-tools: search_drug
version: 1.0.1
author: 丁香园
user-invocable: false
---

# 药品 ATC 与医保查询

当用户需要核对已上市药品的通用名、商品名、剂型、规格、批准文号、ATC 分类、生产企业或医保状态时，使用 `search_drug`。典型问题包括“某药的 ATC 编码”“某批准文号对应什么药”“某药是否医保”“某商品名的通用名是什么”。

## 连接与认证

使用已连接的“丁香园医学检索”MCP 连接器。首次使用由 WorkBuddy 引导用户在浏览器登录临床决策账号并授权。未连接或授权失效时，引导用户连接或重新授权；不要索取、输出或手工拼接 Token，不通过 shell 或 HTTP 请求替代 MCP 工具。

## 调用规则

1. 根据用户明确的检索意图使用本工具，无需选择场景胶囊。
2. 使用用户原始的自然语言问题作为 `query`，保留通用名、商品名、批准文号、剂型规格和生产企业等可区分实体的信息。
3. 药品名称存在歧义时，优先要求用户补充通用名、批准文号、剂型规格或生产企业；若用户希望先看候选，可调用一次并清楚呈现候选差异。
4. 一次用户请求只调用一次本工具。不得为了补充药物相互作用、适应症、用药剂量或指南推荐而调用其他 Tool。

## 调用示例

调用 `search_drug`，参数如下（仅为检索请求，不代表真实结果）：

```json
{
  "query": "查询阿莫西林的 ATC 分类和医保信息"
}
```

`query` 为必填字符串，无默认值；结果是列表，没有总数或分页字段。

## 结果处理

- 输入仅含必填字符串 `query`，不能为空白；不要添加分页、数量、筛选或客户端 IP 参数。
- 成功结果位于 MCP `content` 的 text 中，内容是 JSON 数组；先检查 `isError`，错误文本不能当作结果解析。
- 仅呈现 MCP 药品检索返回的字段，不得自行推断成分、ATC、批准文号、医保状态或生产企业。
- 默认按 MCP 返回顺序展示前 5 条；用户要求时可展示更多，最高不超过 MCP 本次返回的候选数。
- 每条结果展示：`genericName`、`dosageForm`、`strength`、`approvalNumber`、`atc`、`manufacturer`、`nationalReimbursementStatusValue`、`reimbursementNotes`。
- `sourceType` 固定为 `marketedDrugs`。`atc` 是上游原始字符串，原样展示，不拆分为编码和中文名，不推断成分映射。医保状态使用 `nationalReimbursementStatusValue`，不自行解释数字码。
- 字段为 null、空串或空白时省略，不补全。当前不返回商品名或来源链接，不自行生成。
- 医保状态和备注具有地区、目录版本与时间敏感性。仅说明“当前返回数据中的医保状态”，不可承诺实际报销资格或比例。

## 输出格式

```markdown
### 药品结果

1. **{genericName}**
   - 剂型/规格：{dosageForm}；{strength}
   - 批准文号：{approvalNumber}
   - ATC：{atc}
   - 生产企业：{manufacturer}
   - 医保状态：{nationalReimbursementStatusValue}
   - 医保备注：{reimbursementNotes}
```

## 边界与异常

- 本工具只提供药品基础信息，不提供用药剂量、适应症判断、药物相互作用、处方或个体化治疗建议。
- 无结果时，建议用户检查药品名称，或提供通用名、批准文号、剂型规格或生产企业。
- 授权、额度、超时或服务错误时直接反馈，不重试，不猜测药品信息，也不自动改用其他 Tool。

## 错误恢复

- 未连接、登录或授权失效：引导用户通过 WorkBuddy 重新连接；令牌刷新由客户端处理，不自行刷新或循环重试。
- `query 不能为空`：补齐非空检索需求，由用户确认后发起新的查询。
- 工具执行失败或超时：说明本次查询失败，用户明确要求再试时再发起一次查询。错误信息不可当作检索结果。
- 不请求其他工具补齐结果。
