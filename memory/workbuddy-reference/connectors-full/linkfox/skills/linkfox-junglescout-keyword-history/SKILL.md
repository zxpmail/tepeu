---
name: linkfox-junglescout-keyword-history
description: Jungle Scout关键词历史搜索量查询，按7天周期返回亚马逊关键词的精确搜索量趋势，覆盖美国、英国、德国、日本等10个站点。当用户提到关键词搜索量趋势、历史搜索量、搜索热度变化、关键词季节性、搜索量波动、Jungle Scout搜索量、keyword search volume history, keyword trend, search volume over time, seasonal search volume, keyword popularity trend时触发此技能。即使用户未明确提及"Jungle Scout"，只要其需求涉及查看某个亚马逊关键词在一段时间内的搜索量变化趋势，也应触发此技能。
---

# 查询关键词历史精确搜索量

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "tool-jungle-scout/keywords/historical-search-volume"`，不加前导 `/`，保留大小写。
- **所属分组**：Jungle Scout · 亚马逊关键词与销量
- **功能说明**：查询关键词历史精确搜索量：GET historical_search_volume，按7天周期返回区间内各周估算搜索量


## 何时使用

当用户意图与“查询关键词历史精确搜索量”匹配，或需要以下能力时使用本工具：查询关键词历史精确搜索量：GET historical_search_volume，按7天周期返回区间内各周估算搜索量

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `endDate` | `string` | 是 | 最长 1000；示例：`2025-02-01` | 结束日期(YYYY-MM-DD)；与开始日期间隔最大366天 |
| `keyword` | `string` | 是 | 最长 1000；示例：`sushi` | 要查询的关键词 |
| `startDate` | `string` | 是 | 最长 1000；示例：`2025-01-05` | 开始日期(YYYY-MM-DD) |
| `marketplace` | `string` | 是 | 最长 1000；示例：`us`, `uk`, `de`, `in`, `ca`, `fr`, `it`, `es` | 目标市场代码 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "tool-jungle-scout/keywords/historical-search-volume",
  "arguments": {
    "marketplace": "us",
    "keyword": "sushi",
    "startDate": "2025-01-05",
    "endDate": "2025-02-01"
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`tool-jungle-scout/keywords/historical-search-volume` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `costToken` | `integer` | 否 |  | 消耗token |
| `historicalSearchVolumeList` | `array<object>` | 否 |  | 历史搜索量周期列表 |

### 嵌套输出结构：`historicalSearchVolumeList`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `id` | `string` | 否 |  | 数据周期标识(市场/关键词/日期范围) |
| `type` | `string` | 否 |  | 响应资源类型(固定 historical_keyword_search_volume) |
| `estimateEndDate` | `string` | 否 |  | 周期结束日期(YYYY-MM-DD，7天统计周期终点) |
| `estimateStartDate` | `string` | 否 |  | 周期开始日期(YYYY-MM-DD，7天统计周期起点) |
| `estimatedExactSearchVolume` | `integer` | 否 |  | 该周期内精确匹配搜索量(次/周) |

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
