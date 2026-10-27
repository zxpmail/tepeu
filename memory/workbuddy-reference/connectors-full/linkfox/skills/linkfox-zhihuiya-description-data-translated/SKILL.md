---
name: linkfox-zhihuiya-description-data-translated
description: 从智慧芽获取翻译后的专利说明书（描述）文本。当用户要求专利说明书翻译、其他语言的专利全文、翻译后的专利全文，或想查看中文、英文、日文版的专利说明书、patent specification translation, patent description translation, PatSnap, patent translation时触发此技能。当用户提供专利ID或公开号并要求获取其他语言的说明书/描述内容，或提到"专利说明书翻译"、"描述翻译"、"翻译全文"等类似意图时也应触发。
---

# 智慧芽-说明书翻译

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "zhihuiya/descriptionDataTranslated"`，不加前导 `/`，保留大小写。
- **所属分组**：智慧芽 · 专利数据
- **功能说明**：工具中文名：智慧芽-说明书翻译
描述：可以通过输入专利ID或专利公开号查询具体某条专利的说明书的翻译文本（支持中文、英文、日文任意一种）
关键词：智慧芽,说明书,翻译
- **关键词**：智慧芽,说明书,翻译


## 何时使用

当用户意图与“智慧芽-说明书翻译”匹配，或需要以下能力时使用本工具：工具中文名：智慧芽-说明书翻译
描述：可以通过输入专利ID或专利公开号查询具体某条专利的说明书的翻译文本（支持中文、英文、日文任意一种）
关键词：智慧芽,说明书,翻译

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `lang` | `string` | 否 | 默认 `"en"`；最长 1000；示例：`en`, `cn`, `jp` | 翻译语言，支持cn、en、jp |
| `patentId` | `string` | 否 | 最长 60000 | 专利ID（专利id和公开号两个参数必须要有一个，如果两个都存在，会优先使用专利id），多个用英文逗号隔开，上限100条 |
| `patentNumber` | `string` | 否 | 最长 60000 | 公开(公告)号专利id和公开号两个参数必须要有一个，如果两个都存在，会优先使用专利id），多个用英文逗号隔开，上限100条 |
| `replaceByRelated` | `integer` | 否 | 默认 `0`；示例：`1`, `0` | 说明书无法获取时是否用同族专利说明书替代：1是 0否 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "zhihuiya/descriptionDataTranslated",
  "arguments": {}
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`zhihuiya/descriptionDataTranslated` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `data` | `array<object>` | 否 |  | 专利列表 |
| `type` | `string` | 否 |  | 渲染的样式 |
| `total` | `integer` | 否 |  | 记录数 |
| `columns` | `array<object>` | 否 |  | 渲染的列 |
| `costToken` | `integer` | 否 |  | 消耗token |

### 嵌套输出结构：`data`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `pn` | `string` | 否 |  | 公开(公告)号 |
| `patentId` | `string` | 否 |  | 专利Id |
| `pnRelated` | `string` | 否 |  | 替代专利的公开号（仅当使用同族专利替代时提供） |
| `description` | `string` | 否 |  | 说明书翻译 |

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
