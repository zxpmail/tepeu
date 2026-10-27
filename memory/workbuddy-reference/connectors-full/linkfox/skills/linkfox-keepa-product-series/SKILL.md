---
name: linkfox-keepa-product-series
description: 查询亚马逊商品的历史时序数据，包括价格走势、BSR（畅销排名）趋势、评分变化、卖家数量和月销量，支持多个亚马逊站点的任意ASIN。当用户提到价格历史、价格追踪、BSR历史、BSR趋势、历史定价、价格波动、Keepa数据、排名历史、降价提醒、秒杀历史价格、Buy Box价格趋势、优惠券价格、FBA/FBM价格对比、卖家数量变化、评分趋势、销量历史、price history, BSR trends, Keepa historical data, price tracking, sales history, rating changes, seller count changes, price fluctuation时触发此技能。即使用户未明确提及"Keepa"或"时序数据"，只要其需求涉及亚马逊历史商品级数据（如价格、排名或销量随时间的变化趋势），也应触发此技能。
---

# Keepa-亚马逊价格历史

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "keepa/productSeries"`，不加前导 `/`，保留大小写。
- **所属分组**：Keepa · 亚马逊商品与价格历史
- **功能说明**：按亚马逊 amazon asin查询该 asin 的 价格 变化 历史、BSR历史、历史数据趋势
- **关键词**：Keepa, 价格历史, BSR历史, 历史数据趋势, 价格追踪


## 何时使用

当用户意图与“Keepa-亚马逊价格历史”匹配，或需要以下能力时使用本工具：按亚马逊 amazon asin查询该 asin 的 价格 变化 历史、BSR历史、历史数据趋势

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `asin` | `string` | 是 | 最长 1000 | 亚马逊标准识别号(ASIN)，只支持单个ASIN |
| `days` | `integer` | 否 | 默认 `90`；最大 1096；示例：`30`, `90` | 限制历史数据天数，默认90天 |
| `domain` | `string` | 是 | 格式 `1\|2\|3\|4\|5\|6\|8\|9\|10\|11\|12`；示例：`1`, `2`, `3`, `4`, `5`, `6`, `8`, `9` | 亚马逊域名ID |
| `showPrice` | `integer` | 否 | 示例：`1` | 是否返回市场最低新品价曲线 |
| `showBsrMain` | `integer` | 否 | 示例：`1` | 是否返回大类BSR曲线 |
| `showPriceFba` | `integer` | 否 | 示例：`1` | 是否返回第三方FBA新品价曲线 |
| `showPriceFbm` | `integer` | 否 | 示例：`1` | 是否返回第三方FBM新品价曲线 |
| `showPriceDeal` | `integer` | 否 | 示例：`1` | 是否返回闪促价格曲线 |
| `showPriceList` | `integer` | 否 | 示例：`1` | 是否返回划线价/标价曲线 |
| `showPricePrime` | `integer` | 否 | 示例：`1` | 是否返回Prime专属新品价曲线 |
| `showPriceCoupon` | `integer` | 否 | 示例：`1` | 是否返回优惠券后买盒价曲线 |
| `showSellerCount` | `integer` | 否 | 示例：`1` | 是否返回卖家数曲线 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "keepa/productSeries",
  "arguments": {
    "asin": "B0EXAMPLE01",
    "domain": "1"
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`keepa/productSeries` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `asin` | `string` | 否 |  | ASIN |
| `price` | `array<object>` | 否 |  | 价格,(time=时间,value=价格) |
| `bsrSub` | `array<object>` | 否 |  | 小类BSR |
| `rating` | `array<object>` | 否 |  | 评分(time=时间,value=评分) |
| `bsrMain` | `array<object>` | 否 |  | 大类BSR |
| `priceFba` | `array<object>` | 否 |  | FBA价格,(time=时间,value=FBA价格) |
| `priceFbm` | `array<object>` | 否 |  | FBM价格,(time=时间,value=FBM价格) |
| `costToken` | `integer` | 否 |  | 消耗token |
| `priceDeal` | `array<object>` | 否 |  | Deal价格,(time=时间,value=Deal价格) |
| `priceList` | `array<object>` | 否 |  | 划线价,(time=时间,value=划线价格) |
| `pricePrime` | `array<object>` | 否 |  | Prime价格,(time=时间,value=Prime价格) |
| `buyboxPrice` | `array<object>` | 否 |  | Buybox价格,(time=时间,value=Buybox价格) |
| `monthlySold` | `array<object>` | 否 |  | 子体销量(time=时间,value=销量) |
| `priceCoupon` | `array<object>` | 否 |  | coupon价格(time=时间,value=coupon价格) |
| `ratingCount` | `array<object>` | 否 |  | 评分数(time=时间,value=评分数) |
| `sellerCount` | `array<object>` | 否 |  | 卖家数(time=时间,value=卖家数) |

### 嵌套输出结构：`bsrSub`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `points` | `array<object>` | 否 |  | time=时间,value=排名 |
| `categoryName` | `string` | 否 |  | 类目名称 |

### 嵌套输出结构：`bsrMain`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `points` | `array<object>` | 否 |  | time=时间,value=排名 |
| `categoryName` | `string` | 否 |  | 类目名称 |

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
