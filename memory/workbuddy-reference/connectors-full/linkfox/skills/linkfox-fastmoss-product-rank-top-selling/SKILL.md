---
name: linkfox-fastmoss-product-rank-top-selling
description: 通过FastMoss数据查询TikTok全球电商市场的热销商品排行榜，支持按日/周/月维度和类目维度分析。当用户提到TikTok热销榜、TikTok爆品排行、TikTok销量排行、TikTok GMV排名、TikTok类目热销、TikTok选品周报、TikTok top-selling rankings, TikTok bestseller charts, TikTok GMV ranking, TikTok category hot sellers, TikTok weekly product report, FastMoss时触发此技能。即使用户未明确提及"FastMoss"，只要其需求涉及查看TikTok平台的热销排行榜或按时间维度的销售排名，也应触发此技能。
---

# FastMoss-TikTok热销榜单

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "fastmoss/productRankTopSelling"`，不加前导 `/`，保留大小写。
- **所属分组**：FastMoss · TikTok 选品
- **功能说明**：通过时间轴（日/周/月）与类目交叉维度，精准调取 TikTok 全球电商市场的爆款排行榜。本工具专注于“趋势发现”与“榜单复盘”，核心功能包括：
- **关键词**：FastMoss, TikTok 榜单, 销量排行榜, GMV 排名, 爆款发现, 增长率分析, 类目调研, 选品周报


## 何时使用

当用户意图与“FastMoss-TikTok热销榜单”匹配，或需要以下能力时使用本工具：通过时间轴（日/周/月）与类目交叉维度，精准调取 TikTok 全球电商市场的爆款排行榜。本工具专注于“趋势发现”与“榜单复盘”，核心功能包括：

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `page` | `integer` | 否 | 示例：`1` | 页码，默认1 |
| `region` | `string` | 是 | 最长 1000；示例：`US`, `GB`, `MX`, `ES`, `ID`, `VN`, `MY`, `TH` | 国家/地区代码，与商品搜索相同白名单：['US','GB','MX','ES','DE','IT','FR','ID','VN','MY','TH','PH','BR','JP','SG'] |
| `orderby` | `object` | 否 |  |  |
| `category` | `string` | 否 | 最长 1000；示例：`Phone Cases` | 类目名称（文本，用于匹配 TikTok 英文类目并解析为一级类目 ID）。TikTok 类目为英文，服务端按英文做 BM25 匹配；若用户输入非英语，请先在对话侧译为英语再传入本参数。 |
| `dateInfo` | `object` | 是 |  |  |
| `pageSize` | `integer` | 否 | 最大 10；示例：`10` | 每页条数，每页最多10条，默认10 |

### 嵌套输入结构：`orderby`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `field` | `string` | 是 | 最长 1000；示例：`units_sold`, `gmv`, `total_units_sold`, `total_gmv`, `growth_rate` | 排序字段名 |
| `order` | `string` | 否 | 最长 1000；示例：`desc`, `asc` | 排序方向：desc-降序, asc-升序，默认 desc |

### 嵌套输入结构：`dateInfo`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `type` | `string` | 是 | 最长 1000；示例：`day`, `week`, `month` | 日期类型：day-按天, week-按周, month-按月 |
| `value` | `string` | 是 | 最长 1000；示例：`2025-02-01`, `2025-18`, `2025-02` | 日期值，格式取决于type：day→'2025-02-01', week→'2025-18', month→'2025-02' |

## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "fastmoss/productRankTopSelling",
  "arguments": {
    "region": "US",
    "dateInfo": {
      "type": "day",
      "value": "2025-02-01"
    }
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`fastmoss/productRankTopSelling` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `page` | `integer` | 否 |  | 当前页码 |
| `type` | `string` | 否 |  | 响应类型 |
| `total` | `integer` | 否 |  | 结果总数 |
| `columns` | `array<object>` | 否 |  | 列定义 |
| `costTime` | `integer` | 否 |  | 接口耗时毫秒 |
| `pageSize` | `integer` | 否 |  | 每页条数 |
| `products` | `array<object>` | 否 |  | 商品列表 |
| `costToken` | `integer` | 否 |  | 消耗Token数量 |
| `matchedCategoryIdPath` | `string` | 否 |  | 匹配类目ID路径 |
| `matchedCategoryNamePath` | `string` | 否 |  | 匹配类目名称路径 |

### 嵌套输出结构：`products`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `price` | `number` | 否 |  | 商品价格.数值类型，含价格范围时取最低值，货币单位见currency字段 |
| `title` | `string` | 否 |  | 商品名称 |
| `region` | `string` | 否 |  | 区域代码.如US、GB、ID等 |
| `coverUrl` | `array<any>` | 否 |  | 商品封面图URL列表 |
| `currency` | `string` | 否 |  | 货币符号 |
| `imageUrl` | `string` | 否 |  | 商品图片URL |
| `maxPrice` | `number` | 否 |  | 最高价格.仅当原始价格为范围时有值，货币单位见currency字段 |
| `minPrice` | `number` | 否 |  | 最低价格.仅当原始价格为范围时有值，货币单位见currency字段 |
| `shopName` | `string` | 否 |  | 店铺名称 |
| `productId` | `string` | 否 |  | TikTok产品ID.如1730696681877443081 |
| `growthRate` | `number` | 否 |  | 销量增长率(单位%) |
| `shopAvatar` | `string` | 否 |  | 店铺头像URL |
| `sourceTool` | `string` | 否 |  | 来源工具 |
| `sourceType` | `string` | 否 |  | 商品来源 |
| `categoryIds` | `array<any>` | 否 |  | 商品品类ID列表.一级到三级，如["24","914824","819984"] |
| `categoryName` | `string` | 否 |  | 商品品类名称路径.如Food & Beverages -> Drinks -> Meal Replacement & Protein Drinks |
| `shopSellerId` | `string` | 否 |  | 店铺ID |
| `totalSaleCnt` | `integer` | 否 |  | 总销量.累计历史总销量 |
| `offShelvesText` | `string` | 否 |  | 是否下架.是=已下架，否=在售 |
| `totalSale1dCnt` | `integer` | 否 |  | 1天内销量 |
| `totalSale7dCnt` | `integer` | 否 |  | 7天内销量.仅dateType=week时有值 |
| `totalSale30dCnt` | `integer` | 否 |  | 30天内销量.仅dateType=month时有值，与筛选周期一致的区间销量 |
| `totalSaleGmvAmt` | `number` | 否 |  | 总销售额.累计历史总销售额，货币单位见currency字段 |
| `totalSaleGmv1dAmt` | `number` | 否 |  | 1天内销售额.仅dateType=day时有值，货币单位见currency字段 |
| `totalSaleGmv7dAmt` | `number` | 否 |  | 7天内销售额.仅dateType=week时有值，货币单位见currency字段 |
| `shopTotalUnitsSold` | `integer` | 否 |  | 店铺总销量 |
| `totalSaleGmv30dAmt` | `number` | 否 |  | 30天内销售额.仅dateType=month时有值，货币单位见currency字段 |
| `productCommissionRate` | `integer` | 否 |  | 商品佣金比例.基点制整数，1000表示10%，除以100得百分比值 |

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
