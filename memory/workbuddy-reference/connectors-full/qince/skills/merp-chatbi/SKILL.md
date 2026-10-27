---
name: merpbi-chatbi
display_name: 智能问数
description: Use ChatBI HTTP APIs to read chat history, recognize query intent, clarify ambiguous questions, and query business data.
description_zh: 通过 MBU 智能问数 HTTP 接口读取历史上下文、识别问数意图，并在澄清后查询经营数据。
description_en: Query business data through ChatBI HTTP APIs with history and intent recognition.
category: bi
version: 1.0.0
author: songminjian
agent_created: true
---

# 智能问数技能

本技能配合 `qince` MCP 连接器使用。 
为减少明确问题的等待时间，本技能支持快速路径：新建 `chatId` 的首轮问题，如果没有上下文指代且时间、指标、维度和筛选条件已经明确，可以直接调用 `merpbi_chatbi_question`。
只要问题依赖历史上下文、复用了已有会话、刚完成用户澄清，或无法判断是否为新会话，就使用完整流程。

## 前置条件

1. 企业需在勤策注册开户（官方网站 https://www.qince.com ），并根据连接器引导绑定用户账号。
2. MCP支持接口可查看接口定义信息。


## 可用工具

| 工具  | 用途 |
|---|---|
| `merpbi_chatbi_get_chat_record` |  获取当前登录用户在指定 `chatId` 下的历史对话记录，用来理解上下文和上一轮澄清内容。 |
| `merpbi_chatbi_intent_recognition` | 对用户问题做意图识别，返回可供问数继续使用的 `intentData` 和 Token 消耗。 |
| `merpbi_chatbi_question` |在问题和必要 `intentData` 已明确后执行智能问数，返回 `agentData`、查询结果 `result`  |

## 参数规范

### merpbi_chatbi_get_chat_record

请求参数：

```json
{
  "chatId": 10001,
  "agentId": 1,
  "limit": 3,
  "includeResult": false
}
```

- `chatId`：必填，数字。用于标识同一段问数会话。
- `agentId`：可选，数字。不传时查询该会话下全部智能体记录。
- `limit`：可选，数字，范围1到10。默认传3，只读取最近少量上下文。
- `includeResult`：可选，布尔值。默认传`false`，只用于理解问题和上一轮澄清，不返回完整`sqlResult`。

### merpbi_chatbi_intent_recognition

请求参数：

```json
{
  "question": "查询本月销售额",
  "chatId": "10001",
  "agentId": "1"
}
```

- `question`：必填，用户当前自然语言问题。
- `chatId`：必填，数字字符串。与历史记录接口保持同一个值。
- `agentId`：可选，数字字符串。不传时默认使用 `1`。

### merpbi_chatbi_question

请求参数：

```json
{
  "question": "查询本月销售额",
  "chatId": "10001",
  "agentId": "1",
  "intentData": {}
}
```

- `question`：必填，用户最终确认后的问题。若用户做过澄清，应合并原问题和澄清内容。
- `chatId`：必填，数字字符串。必须与前两步保持一致。
- `agentId`：可选，数字字符串。不传时默认使用 `1`。
- `intentData`：可选，优先传入 `merpbi_chatbi_intent_recognition` 返回的 `intentData`。

## 推荐流程

1. 收到用户问题后，先确定本轮会话的 `chatId`。同一轮连续追问必须复用同一个 `chatId`。
2. 如果是新建 `chatId` 的首轮问题，且没有“上一个、那按客户、上个月、继续”等上下文指代，时间、指标、维度和筛选条件也已明确，可以直接调用 `merpbi_chatbi_question`。
3. 如果是上下文追问、复用已有 `chatId`、用户刚完成澄清，或无法确定是否为新会话，先调用 `merpbi_chatbi_get_chat_record`，默认传 `limit=3`、`includeResult=false`，结合最近对话理解上下文。
4. 完整流程下，再调用 `merpbi_chatbi_intent_recognition` 识别本次问题意图。
5. 如果意图识别结果中存在不清楚、缺失或冲突的条件，先把需要澄清的点抛给用户，不要直接调用问数。
6. 收到用户澄清后，合并历史上下文、原始问题和澄清结果，再调用 `merpbi_chatbi_question`，并尽量携带上一轮 `intentData`。
7. 返回数据时优先解释 `result` 中的查询结果；如需要说明口径，可结合 `agentData` 中的语义解析信息。

### 直接问数失败后的降级

如果快速路径直接调用 `merpbi_chatbi_question` 失败，不要立即重复调用同一个问题。
先调用 `merpbi_chatbi_intent_recognition`；根据识别结果向用户提出一个最关键的澄清问题，或携带明确的 `intentData` 再调用一次问数。

## 澄清规则

- 用户没有明确时间范围时，优先澄清时间，例如“你想查今天、本月，还是指定日期范围？”
- 用户没有明确指标口径时，优先澄清口径，例如销售额、回款额、订单金额、出库金额不要混用。
- 用户没有明确维度时，可以先按总览查询；但如果问题带有“按它拆一下”“那上个月呢”这类上下文指代，必须先读取历史记录。
- 意图识别返回空数据、多个候选口径、缺少关键过滤条件或表达冲突时，只向用户提一个最关键的澄清问题。
- 用户澄清后，再把澄清内容写入 `question`，并尽量携带上一轮 `intentData` 调用问数。

## 响应处理

- 外层响应以 MBU `ApiResponse` 为准；成功时读取 `data`。
- `merpbi_chatbi_get_chat_record` 成功后，`data` 是历史对话记录列表。
- `merpbi_chatbi_intent_recognition` 成功后，`data.intentData` 是意图识别结果，注意：`data.tokenUsage` 是 Token 消耗,不需要向用户展示。
- `merpbi_chatbi_question` 成功后，`data.result` 是查询结果，`data.agentData` 是智能体解析信息，`data.tokenUsage` 是 Token 消耗,也不需要向用户展示。
- 接口失败时，向用户转述可理解的失败原因；不要编造查询结果。

## 调试用例

### 直接问数

1. 新建一个 `chatId`，用户问：“查询2026年9月华东区域按客户汇总的销售金额”。
2. 问题没有上下文指代，时间、指标、维度和筛选条件明确。
3. 直接调用 `merpbi_chatbi_question`。
4. 如果接口失败，再按“直接问数失败后的降级”处理。

### 上下文追问

1. 用户上一轮问过：“查询本月销售额”。
2. 用户继续问：“按客户拆一下”。
3. 必须先调用 `merpbi_chatbi_get_chat_record`，请求 `limit=3`、`includeResult=false`，确认“按客户拆”的对象是上一轮销售额。
4. 再做意图识别和问数。

### 需要澄清

1. 用户问：“查一下销量”。
2. 调用历史记录和意图识别。
3. 如果无法确定时间或统计口径，先问用户：“你想查哪个时间范围的销量？”
4. 用户补充后再调用问数。


## 使用原则

- 优先通过 MBU 应用接口调用，保留登录态、租户、用户和数据权限上下文。
- 只对新会话中条件明确的首轮问题跳过历史记录；上下文相关问题不得跳过历史记录。
- 不确定字段、时间范围、统计口径或筛选条件时，必须先向用户澄清。
- `includeResult=false` 只压缩历史上下文响应，不影响最终 `merpbi_chatbi_question` 返回查询结果。
- `merpbi_chatbi_question` 会执行智能问数返回的安全查询并记录历史对话；重复调用前应确认用户确实需要重新查询。

## 错误处理

| 现象 | 处理 |
|---|---|
| HTTP 4xx | 核对 `api_path` 与文档是否一致（常见：版本号 v1/v2 写错） |
| `return_code` 非 0 | 如实转述 `return_msg`；签名类错误提示用户核对 App Key 与服务器时间 |
| 超时 | 缩小 `rows`、加过滤条件后重试读操作；写操作超时不要盲目重试，先查询确认是否已写入 |