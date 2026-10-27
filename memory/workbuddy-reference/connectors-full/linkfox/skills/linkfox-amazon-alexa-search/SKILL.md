---
name: linkfox-amazon-alexa-search
description: 通过亚马逊前台的 Alexa 购物助手发起自然语言问答，获取与问题相关的导购回答、推荐商品分组、ASIN 列表，以及可继续追问的问题。每次调用仅支持 1 条 prompt，如需追问须由 agent 总结上下文后拼接新问题发起新请求。可用 url 补充亚马逊页面上下文。当用户提到亚马逊 Alexa、Alexa 购物助手、亚马逊智能助手、AI 导购、对话式选品、自然语言购物、亚马逊聊天问答、Amazon Alexa shopping, conversational shopping, AI shopping assistant, follow-up questions、产品推荐对话、上下文追问等场景时触发此技能。即使用户未明确提及"Alexa"，只要其需求是"在亚马逊前台用自然语言问出商品推荐"，也应触发此技能。
---

# 亚马逊-Alexa助手

## 基本信息

- **MCP 服务**：`LinkFox`
- **MCP 工具**：`proxy_call`
- **业务路由**：`toolName = "amazon/alexaSearch"`，不加前导 `/`，保留大小写。
- **所属分组**：Amazon · 搜索、评论与商业洞察
- **功能说明**：通过自然语言向 Amazon Alexa 发起问答，获取与问题相关的回答、推荐商品、商品信息和可继续追问的问题。仅支持单轮对话问答。支持传入 URL 参数，为本次问答补充指定亚马逊页面上下文。
- **关键词**：Amazon Alexa, Alexa助手, 亚马逊Alexa,  对话式查询, 商品推荐, 商品信息, follow-up questions


## 何时使用

当用户意图与“亚马逊-Alexa助手”匹配，或需要以下能力时使用本工具：通过自然语言向 Amazon Alexa 发起问答，获取与问题相关的回答、推荐商品、商品信息和可继续追问的问题。仅支持单轮对话问答。支持传入 URL 参数，为本次问答补充指定亚马逊页面上下文。

调用前应先确认所有必填参数。涉及站点、国家、日期、语言、ASIN、SKU、图片或 URL 时，应使用用户提供的信息，不要擅自虚构真实业务标识。

## 输入参数

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `url` | `string` | 否 | 最长 1000；示例：`https://www.amazon.com/` | 联动页面 URL.用于补充 Alexa 当前答复的页面上下文 |
| `format` | `string` | 否 | 默认 `"markdown"`；最长 1000；示例：`markdown`, `json` | 响应格式.可选 markdown 或 json；默认 markdown。 |
| `prompts` | `array<string>` | 是 | 必须且只能包含 1 个字符串；示例：`["best wireless earbuds for running"]` | 每次调用仅发送 `prompts[0]` 这一个问题。必须传 JSON 数组，不要传字符串或序列化后的数组字符串。每次调用是独立的新会话，不保留跨次调用的历史上下文；如需追问，agent 须总结上一轮回答中的推荐商品、ASIN 和关键结论，拼接新问题后作为下一次调用唯一的 `prompts[0]`。 |


## MCP 调用示例

调用已安装的 **LinkFox MCP** 所暴露的 **`proxy_call` 工具**，传入以下工具参数：

```json
{
  "toolName": "amazon/alexaSearch",
  "arguments": {
    "prompts": ["best wireless earbuds for running"],
    "format": "json"
  }
}
```

> 示例值仅用于展示请求结构。实际调用时必须替换为用户的真实查询条件。

`proxy_call` 是 MCP 工具名；`amazon/alexaSearch` 是其 `toolName` 参数中的业务路由。WorkBuddy 自动注入认证信息，无需在工具参数中传入凭证。若 LinkFox MCP 不可用，按下方安装指引提示用户，不改用直接 HTTP 请求。

## 输出字段

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `msg` | `string` | 否 |  | 返回消息.成功为 ok |
| `code` | `string` | 否 |  | 返回码.成功为 "200" |
| `data` | `array<object>` | 否 |  | Alexa 查询结果列表.仅当 format=json 时返回结构化结果 |
| `type` | `string` | 否 |  | 渲染类型.markdown 为 stdoutWorkbenches，json 为 json |
| `stdout` | `string` | 否 |  | Alexa 查询结果.Markdown 格式，包含用户问题、Alexa回答、推荐商品和可继续追问的问题 |
| `taskId` | `string` | 否 |  | 任务ID.用于排查和追踪本次查询 |
| `costTime` | `integer` | 否 |  | 接口耗时.单位毫秒 |
| `costToken` | `integer` | 否 |  | 消耗 Token 数.按上游成功对话轮次计费 |
| `resultsNum` | `integer` | 否 |  | 对话结果数量.工具统计字段 |

### 嵌套输出结构：`data`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `prompt` | `string` | 否 |  | 用户提示词.对应的问题或者追问 |
| `content` | `string` | 否 |  | Alexa回答内容 |
| `products` | `array<object>` | 否 |  | 推荐商品分组列表.商品列表 |
| `screenshot` | `string` | 否 |  | 本轮对话截图链接 |
| `followUpQuestions` | `array<any>` | 否 |  | 可继续追问的问题列表 |

### 嵌套输出结构：`data.products`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `items` | `array<object>` | 否 |  | 推荐商品列表 |
| `title` | `string` | 否 |  | 推荐商品分组标题 |

### 嵌套输出结构：`data.products.items`

| 字段 | 类型 | 必填 | 约束/默认值 | 说明 |
|---|---|:---:|---|---|
| `url` | `string` | 否 |  | 商品详情页 URL |
| `asin` | `string` | 否 |  | 商品 ASIN |
| `cover` | `string` | 否 |  | 商品封面图 URL |
| `price` | `string` | 否 |  | 现价 |
| `score` | `string` | 否 |  | 评分 |
| `title` | `string` | 否 |  | 商品标题 |
| `describe` | `string` | 否 |  | 商品简介 |
| `ratingsCount` | `string` | 否 |  | 评价数量 |
| `originalPrice` | `string` | 否 |  | 原价或划线价 |

## 响应处理建议

1. 检查 MCP 工具调用是否报错（如 `isError`）；报错时保留错误信息。
2. 读取返回的 `content` 或结构化结果；若 `content` 中包含 JSON 文本，解析后检查业务 `code` 是否为 `"200"`。
3. `format=json` 时读取 `data`、`products` 和 `items`；`format=markdown` 时读取 `stdout`。
4. 保留 `taskId`、`resultsNum` 和 `costToken`，用于追踪查询结果。
5. 向用户输出分析结论；如需追问，总结本次结果后另发一次调用，仍只传一个问题。

## 原始 Schema

Input Schema 和 Output Schema 参考 [references/schema.md](references/schema.md)。其中 `prompts` 为字符串数组，`minItems` 和 `maxItems` 均为 `1`。

## 解决认证和积分问题

## [!IMPORTANT]
本 Skill 必须依赖 MCP “LinkFox”。如未安装该MCP，请明确通知用户：
> 请先安装 LinkFox 连接器：
> - 在 WorkBuddy 左侧找到 “专家·技能·连接器” → “连接器”；
> - 搜索关键词 “LinkFox”，并安装连接器 “LinkFox”（注意：是 linkfox，不是 linkfox-product-selection），按指引完成登录授权；
> - 返回 WorkBuddy 重试。
