---
name: linkfox-ruiguan-copyright-detection
description: 图片版权侵权检测与风险分析。当用户提到版权检测、版权核查、图片侵权检查、图片版权风险、版权相似度搜索、TRO风险分析、权利人查询、版权合规验证、copyright detection, image infringement, copyright risk, TRO risk, copyright lookup, infringement analysis, Ruiguan时触发此技能。即使用户未明确提及"版权"，只要其需求涉及检查图片是否可能侵犯已登记的版权作品，也应触发此技能。
---

# 睿观-版权检测

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "ruiguan/copyrightDetection"`，不加前导 `/`，保留大小写。
- **所属分组**：睿观 · 合规检测
- **功能说明**：支持根据图片URL进行版权检测


## 何时使用

当用户意图与“睿观-版权检测”匹配，或需要以下能力时使用本工具：支持根据图片URL进行版权检测

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `imageUrl` | `string` | 是 | 最长 1000 | 检测的版权图片URL |
| `topNumber` | `integer` | 是 | 默认 `100`；最小 10；最大 100 | 召回数量（默认100，最大200） |
| `enableRadar` | `boolean` | 是 | 默认 `true` | 是否开启雷达检测 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "ruiguan/copyrightDetection",
  "arguments": {
    "imageUrl": "https://example.com/item",
    "topNumber": 100,
    "enableRadar": true
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`ruiguan/copyrightDetection` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `data` | `array<object>` | 否 |  | 检测结果列表 |
| `type` | `string` | 否 |  | 渲染的样式 |
| `total` | `integer` | 否 |  | 记录数 |
| `columns` | `array<object>` | 否 |  | 渲染的列 |
| `detectId` | `string` | 否 |  | 检测id |
| `costToken` | `integer` | 否 |  | 消耗token |

### 嵌套输出结构：`data`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `link` | `string` | 否 |  | 版权官网链接 |
| `path` | `string` | 否 |  | 版权画图片路径 |
| `troCase` | `boolean` | 否 |  | 是否有TRO维权史 |
| `pathThumb` | `string` | 否 |  | 版权画缩略图路径 |
| `troHolder` | `boolean` | 否 |  | 是否是TRO权利人的版权 |
| `similarity` | `string` | 否 |  | 相似度 |
| `rightsOwner` | `string` | 否 |  | 权利人 |
| `copyrightUrl` | `string` | 否 |  | 来源 |
| `copyrightCode` | `string` | 否 |  | 版权标识码 |
| `subRadarResult` | `integer` | 否 |  | 1-侵权 0-不侵权 ,null 没有进行雷达检测 |

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
