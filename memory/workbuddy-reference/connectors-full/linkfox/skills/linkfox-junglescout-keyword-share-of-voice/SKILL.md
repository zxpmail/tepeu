---
name: linkfox-junglescout-keyword-share-of-voice
description: Jungle Scout关键词市场份额（Share of Voice）分析，返回亚马逊搜索结果前3页的品牌声量占比（自然/广告/综合）、30天精确搜索量、PPC竞价中位数及TOP3 ASIN点击转化数据，覆盖10个站点。当用户提到品牌市场份额、品牌声量占比、搜索结果品牌分布、Share of Voice、SOV分析、品牌竞争格局、广告位占比、自然排名品牌占比、PPC竞价分析、品牌垄断分析、keyword share of voice, brand visibility, organic vs sponsored share, brand dominance, PPC bid analysis, search result brand distribution, competitive landscape, weighted SOV, top ASIN clicks conversions时触发此技能。即使用户未明确提及"Share of Voice"或"SOV"，只要其需求涉及分析某个亚马逊关键词搜索结果中各品牌的市场占有率或竞争格局，也应触发此技能。
---

# 关键词市场份额(Share of Voice)

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "tool-jungle-scout/keywords/share-of-voice"`，不加前导 `/`，保留大小写。
- **所属分组**：Jungle Scout · 亚马逊关键词与销量
- **功能说明**：关键词市场份额(Share of Voice)：GET share_of_voice，返回前三页各品牌自然/赞助/综合声量份额、精确匹配30天搜索量、PPC建议竞价中位数及TOP3 ASIN近一周转化


## 何时使用

当用户意图与“关键词市场份额(Share of Voice)”匹配，或需要以下能力时使用本工具：关键词市场份额(Share of Voice)：GET share_of_voice，返回前三页各品牌自然/赞助/综合声量份额、精确匹配30天搜索量、PPC建议竞价中位数及TOP3 ASIN近一周转化

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `keyword` | `string` | 是 | 最长 1000；示例：`golf` | 要分析的亚马逊搜索关键词 |
| `marketplace` | `string` | 是 | 最长 1000；示例：`us`, `uk`, `de`, `in`, `ca`, `fr`, `it`, `es` | 目标市场代码 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "tool-jungle-scout/keywords/share-of-voice",
  "arguments": {
    "marketplace": "us",
    "keyword": "golf"
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`tool-jungle-scout/keywords/share-of-voice` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `costToken` | `integer` | 否 |  | 消耗token |
| `shareOfVoice` | `object` | 否 |  |  |

### 嵌套输出结构：`shareOfVoice`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `id` | `string` | 否 |  | 数据标识(市场/关键词) |
| `type` | `string` | 否 |  | 响应资源类型(固定 share_of_voice) |
| `brands` | `array<object>` | 否 |  | 各品牌在前三页的声量与排名、价格等指标 |
| `topAsins` | `array<object>` | 否 |  | TOP3 ASIN的点击、购买与转化率；可为空 |
| `updatedAt` | `string` | 否 |  | 数据刷新时间(ISO 8601) |
| `productCount` | `integer` | 否 |  | 返回数据中包含的ASIN数量(搜索结果产品总数) |
| `topAsinsModelEndDate` | `string` | 否 |  | TOP3 ASIN点击与转化统计区间终点(YYYY-MM-DD)；可为空 |
| `topAsinsModelStartDate` | `string` | 否 |  | TOP3 ASIN点击与转化统计区间起点(YYYY-MM-DD)；可为空 |
| `exactSuggestedBidMedian` | `number` | 否 |  | 所选市场货币下，赢得竞价的中位估算成本(PPC) |
| `estimated30DaySearchVolume` | `integer` | 否 |  | 该关键词精确匹配30天搜索量估算(次) |

### 嵌套输出结构：`shareOfVoice.brands`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `brand` | `string` | 否 |  | 品牌名称 |
| `organicBasicSov` | `number` | 否 |  | 自然基础声量份额；可为空 |
| `organicProducts` | `integer` | 否 |  | 自然搜索结果中该品牌产品数；可为空 |
| `combinedBasicSov` | `number` | 否 |  | 综合基础声量份额(出现次数/总结果) |
| `combinedProducts` | `integer` | 否 |  | 综合(自然+赞助)前三页内该品牌产品数 |
| `sponsoredBasicSov` | `number` | 否 |  | 赞助基础声量份额；可为空 |
| `sponsoredProducts` | `integer` | 否 |  | 赞助位中该品牌产品数；可为空 |
| `organicWeightedSov` | `number` | 否 |  | 自然加权声量份额；可为空 |
| `combinedWeightedSov` | `number` | 否 |  | 综合加权声量份额(0–1，含Amazon Choice徽标与位次权重) |
| `organicAveragePrice` | `number` | 否 |  | 自然结果平均价格；可为空 |
| `combinedAveragePrice` | `number` | 否 |  | 综合平均价格(市场货币) |
| `sponsoredWeightedSov` | `number` | 否 |  | 赞助加权声量份额；可为空 |
| `sponsoredAveragePrice` | `number` | 否 |  | 赞助结果平均价格；可为空 |
| `organicAveragePosition` | `number` | 否 |  | 自然结果平均排名；可为空 |
| `combinedAveragePosition` | `number` | 否 |  | 综合平均排名位置 |
| `sponsoredAveragePosition` | `number` | 否 |  | 赞助结果平均排名；可为空 |

### 嵌套输出结构：`shareOfVoice.topAsins`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `asin` | `string` | 否 |  | ASIN |
| `name` | `string` | 否 |  | 商品标题/描述；可为空 |
| `brand` | `string` | 否 |  | 品牌；可为空 |
| `clicks` | `integer` | 否 |  | 统计区间内点击次数 |
| `conversions` | `integer` | 否 |  | 统计区间内购买次数 |
| `conversionRate` | `number` | 否 |  | 转化率(购买/点击)；可为空 |

## 响应处理建议

1. 检查 MCP 工具调用是否报错（如 `isError`）；报错时保留错误信息。
2. 读取返回的 `content` 或结构化结果；若 `content` 中包含 JSON 文本，解析为业务响应，并按该工具返回的状态码和错误信息判断是否成功。
3. 成功后按输出字段读取 `data`、`products`、`items` 或其他业务结果。
4. 保留分页字段、总数、失败明细及 `costToken`。
5. 向用户输出分析结论，不要直接倾倒大段原始 JSON。

## 原始 Schema

原始的Input Schema 和 Output Schema 参考 references/schema.md

## 解决认证和积分问题

## [!IMPORTANT]
本 Skill 必须依赖 MCP “LinkFox”。如未安装该MCP，请明确通知用户：
> 请先安装 LinkFox 连接器：
> - 在 WorkBuddy 左侧找到 “专家·技能·连接器” → “连接器”；
> - 搜索关键词 “LinkFox”，并安装连接器 “LinkFox”（注意：是 linkfox，不是 linkfox-product-selection），按指引完成登录授权；
> - 返回 WorkBuddy 重试。
