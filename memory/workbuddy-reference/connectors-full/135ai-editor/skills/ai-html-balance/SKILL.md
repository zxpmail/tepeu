---
name: ai-html-balance
description: 查询 135 编辑器账户的 AI 排版剩余点数，触发词：AI排版余额、排版点数、135编辑器点数
description_zh: 查询 135 编辑器账户的 AI 排版剩余点数。当用户询问「还剩多少 AI 排版点数」「我的排版余额」时使用。
description_en: Check the remaining AI HTML formatting points of a 135 Editor account. Use when the user asks about their AI formatting balance or remaining points.
version: 1.0.1
author: 135editor
---

# 135 编辑器 AI 排版余额查询

## 前置条件

本连接器使用 **MCP 原生 OAuth** 认证，无需填写 API Key。首次访问受保护资源时，
由 WorkBuddy 打开浏览器完成 135 编辑器账号授权；之后 WorkBuddy 自动携带
`Authorization: Bearer` 访问令牌调用，令牌过期时自动使用 refresh token 续期，
全程对用户透明。

- `connector-meta.json` 不设置 `auth_mode`、`mcp.json` 不写静态 `headers` 是有意设计，
  请勿在对话中向用户索取 API Key、Token 或验证码。
- 仅在连接器显示「已连接」后调用工具。
- 服务地址固定为 `https://www.135editor.com/ai_html_mcp/mcp`。

如果调用返回 401 或错误码 -32001，说明授权已失效或已被撤销，应提示用户在连接器
设置中断开后重新连接（重新走一次浏览器授权）；不要建议用户填写 API Key。

## 工具

- `get_ai_html_balance`（显示名「查询AI排版点数」）：查询当前账户 AI 排版剩余点数。**不接受任何参数。**

## 调用方式

直接以空参数对象调用 `get_ai_html_balance`：

```json
{"name": "get_ai_html_balance", "arguments": {}}
```

## 返回格式

工具结果 `structuredContent`（同时以 JSON 文本出现在 content[0].text 中）：

```json
{"total_num": 372}
```

- `total_num`：剩余可用点数（套餐剩余次数 + 额外购买次数），整数，可能为 0。

## 回答示例

- 用户：「我还剩多少 AI 排版点数？」
- 回答：「你的 135 编辑器账户还剩 372 次 AI 排版点数。」

`total_num` 为 0 时，提醒用户点数已用完，需要购买后再试。
