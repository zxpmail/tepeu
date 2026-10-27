---
name: linkfox-ruiguan-text-trademark-detection
description: 面向电商产品Listing的文字商标检测与侵权风险分析。当用户提到商标检测、商标风险检查、品牌侵权筛查、产品标题商标扫描、文字商标查询、Listing合规检查、知识产权风险评估、text trademark detection, trademark infringement, brand infringement screening, listing compliance, intellectual property risk, Ruiguan时触发此技能。即使用户未明确说"商标"，只要其需求涉及检查产品文本（标题、描述、五点描述）中是否包含可能侵权的商标，也应触发此技能。
---

# 睿观-文本商标检测

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "ruiguan/textTrademarkDetection"`，不加前导 `/`，保留大小写。
- **所属分组**：睿观 · 合规检测
- **功能说明**：工具中文名：睿观-文本商标检测
工具说明：支持按产品标题、产品的其他文本信息、国家来进行商标检测


## 何时使用

当用户意图与“睿观-文本商标检测”匹配，或需要以下能力时使用本工具：工具中文名：睿观-文本商标检测
工具说明：支持按产品标题、产品的其他文本信息、国家来进行商标检测

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `limit` | `integer` | 是 | 默认 `100`；最大 500 | 返回结果数量限制 |
| `regions` | `string` | 否 | 最长 1000；示例：`US`, `EM`, `GB`, `DE`, `FR`, `IT`, `ES`, `AU` | 国家/地区代码，多个用逗号分隔，支持 AU,BX,CA,DE,EM,ES,FR,GB,IT,JP,MX,TR,US,WO,CN |
| `productText` | `string` | 否 | 最长 1000 | 产品的其他文本信息 |
| `productTitle` | `string` | 是 | 最长 1000 | 产品标题 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "ruiguan/textTrademarkDetection",
  "arguments": {
    "productTitle": "example",
    "limit": 100
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`ruiguan/textTrademarkDetection` 是其 `toolName` 参数中的业务路由。`arguments` 为业务参数对象，字段类型按输入参数表及 Schema 传入；数组和对象不要序列化为字符串。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `data` | `array<object>` | 否 |  | 商标列表（扁平化） |
| `type` | `string` | 否 |  | 渲染的样式 |
| `total` | `integer` | 否 |  | 记录数 |
| `columns` | `array<object>` | 否 |  | 渲染的列 |
| `detectId` | `string` | 否 |  | 接口调用 id |
| `costToken` | `integer` | 否 |  | 消耗token |
| `textTrademarkRadar` | `string` | 否 |  | 产品风险等级：0低风险, 1待人工核查, 2高风险 |
| `blacklistTrademarks` | `array<object>` | 否 |  | 黑名单 |
| `whitelistTrademarks` | `array<object>` | 否 |  | 白名单 |

### 嵌套输出结构：`data`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `score` | `integer` | 否 |  | 风险分数 |
| `holder` | `string` | 否 |  | 权利人 |
| `region` | `string` | 否 |  | 国家/地区代码 |
| `isFamous` | `boolean` | 否 |  | 是否著名商标 |
| `niceClass` | `array<object>` | 否 |  | 尼斯分类 |
| `regionStatus` | `string` | 否 |  | 状态 |
| `isAmazonBrand` | `boolean` | 否 |  | 是否亚马逊热搜品牌 |
| `isCommonSense` | `boolean` | 否 |  | 是否常用词 |
| `trademarkName` | `string` | 否 |  | 商标词 |
| `isActiveHolder` | `boolean` | 否 |  | 是否活跃维权人 |
| `isCompatibility` | `boolean` | 否 |  | 是否兼容性 |
| `highestModeScore` | `integer` | 否 |  | 最高风险分数（范围0-5） |
| `trademarksStatus` | `string` | 否 |  | 最高分商标词状态 |
| `applicationNumber` | `string` | 否 |  | 申请号 |
| `registrationNumber` | `string` | 否 |  | 注册号 |
| `originalTextMatches` | `array<any>` | 否 |  | 原词 |

### 嵌套输出结构：`blacklistTrademarks`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `note` | `string` | 否 |  | 备注 |
| `region` | `string` | 否 |  | 国家 |
| `trademark` | `string` | 否 |  | 商标 |

### 嵌套输出结构：`whitelistTrademarks`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `note` | `string` | 否 |  | 备注 |
| `region` | `string` | 否 |  | 国家 |
| `trademark` | `string` | 否 |  | 商标 |

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
