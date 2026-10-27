---
name: linkfox-junglescout-keyword-by-asin
description: Jungle Scout根据ASIN反查关键词，输入最多10个ASIN获取其在亚马逊搜索结果中出现的所有关键词及搜索量、竞争度、PPC竞价等数据，覆盖10个站点。当用户提到ASIN反查关键词、反查词、ASIN关键词挖掘、竞品关键词、ASIN流量词、反向ASIN查询、ASIN搜索词、关键词拓展、ASIN词库、竞品流量分析、reverse ASIN lookup, keyword by ASIN, ASIN keyword mining, competitor keywords, ASIN traffic keywords, reverse keyword lookup, ASIN search terms, keyword expansion时触发此技能。即使用户未明确提及"Jungle Scout"或"反查"，只要其需求涉及通过ASIN查找相关关键词或分析竞品关键词，也应触发此技能。
---

# 根据ASIN列表扩展关键词信息

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "tool-jungle-scout/keywords/by-asin"`，不加前导 `/`，保留大小写。
- **所属分组**：Jungle Scout · 亚马逊关键词与销量
- **功能说明**：根据ASIN列表扩展关键词信息：返回所查ASIN在亚马逊搜索结果中出现的关键词；单次最多10个ASIN


## 何时使用

当用户意图与“根据ASIN列表扩展关键词信息”匹配，或需要以下能力时使用本工具：根据ASIN列表扩展关键词信息：返回所查ASIN在亚马逊搜索结果中出现的关键词；单次最多10个ASIN

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `sort` | `string` | 否 | 最长 1000；示例：`name`, `-name`, `dominant_category`, `-dominant_category`, `monthly_trend`, `-monthly_trend`, `quarterly_trend`, `-quarterly_trend` | 排序字段。可选值: name, -name, dominant_category, -dominant_category, monthly_trend, -monthly_trend, quarterly_trend, -quarterly_trend, monthly_search_volume_exact, -monthly_search_volume_exact, monthly_search_volume_broad, -monthly_search_volume_broad, recommended_promotions, -recommended_promotions, sp_brand_ad_bid, -sp_brand_ad_bid, ppc_bid_broad, -ppc_bid_broad, ppc_bid_exact, -ppc_bid_exact, ease_of_ranking_score, -ease_of_ranking_score, relevancy_score, -relevancy_score, organic_product_count, -organic_product_count。默认: -monthly_search_volume_exact |
| `asins` | `string` | 是 | 最长 1000 | 要分析的ASIN列表(1-10个有效ASIN), 多个asin使用逗号分隔 |
| `needCount` | `integer` | 否 |  | 需要返回的总条数(系统内部自动分页拉取) |
| `marketplace` | `string` | 是 | 最长 1000；示例：`us`, `uk`, `de`, `in`, `ca`, `fr`, `it`, `es` | 目标市场代码 |
| `maxWordCount` | `integer` | 否 |  | 关键词最多词数(1-99999) |
| `minWordCount` | `integer` | 否 |  | 关键词最少词数(1-99999) |
| `includeVariants` | `boolean` | 否 | 示例：`false`, `true` | 是否包含变体产品关键词 |
| `maxOrganicProductCount` | `integer` | 否 |  | 最大自然搜索结果数(1-99999) |
| `minOrganicProductCount` | `integer` | 否 |  | 最小自然搜索结果数(1-99999) |
| `maxMonthlySearchVolumeBroad` | `integer` | 否 |  | 最大广泛月搜索量(1-999999) |
| `maxMonthlySearchVolumeExact` | `integer` | 否 |  | 最大精确月搜索量(1-999999) |
| `minMonthlySearchVolumeBroad` | `integer` | 否 |  | 最小广泛月搜索量(1-999999) |
| `minMonthlySearchVolumeExact` | `integer` | 否 |  | 最小精确月搜索量(1-999999) |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "tool-jungle-scout/keywords/by-asin",
  "arguments": {
    "marketplace": "us",
    "asins": "B0EXAMPLE01"
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`tool-jungle-scout/keywords/by-asin` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `costToken` | `integer` | 否 |  | 消耗token |
| `keywordInfoList` | `array<object>` | 否 |  | 按ASIN扩展的关键词信息列表 |

### 嵌套输出结构：`keywordInfoList`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `name` | `string` | 否 |  | 关键词名称 |
| `country` | `string` | 否 |  | 国家市场代码 |
| `updatedAt` | `string` | 否 |  | 数据更新时间(UTC) |
| `organicRank` | `integer` | 否 |  | 自然排名位置 |
| `overallRank` | `integer` | 否 |  | 综合排名 |
| `ppcBidBroad` | `number` | 否 |  | 广泛匹配PPC竞价(USD) |
| `ppcBidExact` | `number` | 否 |  | 精确匹配PPC竞价(USD) |
| `primaryAsin` | `string` | 否 |  | 主要ASIN |
| `monthlyTrend` | `number` | 否 |  | 月度搜索趋势(%) |
| `spBrandAdBid` | `number` | 否 |  | 品牌广告建议竞价(USD) |
| `sponsoredRank` | `integer` | 否 |  | 赞助排名位置 |
| `quarterlyTrend` | `number` | 否 |  | 季度搜索趋势(%) |
| `relevancyScore` | `integer` | 否 |  | 相关性评分(0-100) |
| `dominantCategory` | `string` | 否 |  | 主导产品类别 |
| `easeOfRankingScore` | `integer` | 否 |  | 排名难度评分(0-100) |
| `organicProductCount` | `integer` | 否 |  | 自然搜索结果数 |
| `competitorOrganicRank` | `array<object>` | 否 |  | 竞品自然排名列表 |
| `recommendedPromotions` | `integer` | 否 |  | 推荐推广次数 |
| `sponsoredProductCount` | `integer` | 否 |  | 赞助产品数量 |
| `competitorSponsoredRank` | `array<object>` | 否 |  | 竞品赞助排名列表 |
| `relativeOrganicPosition` | `integer` | 否 |  | 相对自然位次 |
| `avgCompetitorOrganicRank` | `number` | 否 |  | 平均竞品自然排名 |
| `monthlySearchVolumeBroad` | `integer` | 否 |  | 广泛匹配月搜索量 |
| `monthlySearchVolumeExact` | `integer` | 否 |  | 精确匹配月搜索量 |
| `organicRankingAsinsCount` | `integer` | 否 |  | 自然排名ASIN数 |
| `relativeSponsoredPosition` | `integer` | 否 |  | 相对赞助位次 |
| `avgCompetitorSponsoredRank` | `number` | 否 |  | 平均竞品赞助排名 |
| `sponsoredRankingAsinsCount` | `integer` | 否 |  | 赞助排名ASIN数 |
| `variationLowestOrganicRank` | `integer` | 否 |  | 变体最低自然排名 |
| `variationLowestSponsoredRank` | `integer` | 否 |  | 变体最低赞助排名 |

### 嵌套输出结构：`keywordInfoList.competitorOrganicRank`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `asin` | `string` | 否 |  | ASIN |
| `organicRank` | `integer` | 否 |  | 自然排名 |

### 嵌套输出结构：`keywordInfoList.competitorSponsoredRank`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `asin` | `string` | 否 |  | ASIN |
| `sponsoredRank` | `integer` | 否 |  | 赞助排名 |

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
