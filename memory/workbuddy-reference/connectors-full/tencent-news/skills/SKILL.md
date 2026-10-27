---
name: tencent-news-skill
description: 腾讯新闻 Connector 技能 - 查询新闻、查询用户在腾讯新闻的资产（收藏/点赞/用户信息）、获取用户 API Key
description_zh: 腾讯新闻 Connector 技能 - 查询新闻、查询用户在腾讯新闻的资产（收藏/点赞/用户信息）、获取用户 API Key
description_en: Tencent News connector skill - search news, read the user's saved and liked items, and get the user's API Key for CLI authorization.
version: "1.1.0"
author: "Tencent News"
---

# 腾讯新闻 Skill

通过腾讯新闻 Connector 调用 MCP 工具。认证采用**标准 MCP OAuth**：首次请求未携带 Token 时服务端返回 401，WorkBuddy 会自动完成元数据发现、动态注册并**打开浏览器**请你完成腾讯新闻账号授权，之后自动携带 Bearer Token 调用；Token 过期由 refresh_token 自动续期。**用户不需要填写任何 Key。**

## 工具清单以接口返回为准

本 Connector 的能力**在持续扩充**，因此：

- **先调用 `tools/list` 取得当前可用工具**，再按每个工具的 `description` 与 `inputSchema` 选择并调用；
- 本文档按**能力分类**描述，不锁定具体工具名；`tools/list` 里没有的能力即当前未开放；
- 若 `tools/list` 返回了本文未提及的工具，按它自身携带的说明使用。

## 能力范围

| 能力 | 内容 | 当前状态 |
| --- | --- | --- |
| **查询新闻** | 搜索新闻、热点新闻、新闻详情等新闻内容查询 | 陆续开放 |
| **查询用户在腾讯新闻的资产** | 收藏列表、点赞列表、用户信息（查询 / 修改） | 陆续开放 |
| **获取用户凭证** | 取得当前用户自己的 API Key，供腾讯新闻 skill / CLI 本地授权 | ✅ 已开放（工具 `GetAPIKeyBySUID`） |

> 上表是编写时的快照，仅用于说明能力边界；**实际可用性以 `tools/list` 返回为准**。

## 工具选择指引

### 查询新闻

- 典型诉求：「搜一下某某新闻」「今天有什么热点」「这条新闻详情」
- 参数形态：关键词、条数上限、新闻 ID / 链接等

### 查询用户在腾讯新闻的资产

- 典型诉求：「我收藏了哪些新闻」「我点赞过什么」「我的腾讯新闻资料」
- ⚠️ **修改用户信息属写操作**：执行前必须向用户确认修改内容，不要擅自提交。

### 获取用户凭证

- 典型诉求：「配置一下腾讯新闻」「取一下我的 API Key」
- 当前已开放的工具为 **`GetAPIKeyBySUID`**，**不需要参数**——用户身份由本次 OAuth 授权决定，不需要也不要尝试手写凭证；
- 其余工具名与参数一律以 `tools/list` 为准；
- 具体约束见下方「凭证安全约束」。

## 与腾讯新闻 skill / CLI 的配合

```
① 本 Connector            取得 API Key
② 腾讯新闻 skill / CLI     把 API Key 写入本地凭证
③ 腾讯新闻 skill / CLI     搜索新闻、查询热点新闻、查询天气等
```

- 用户要「配置腾讯新闻 / 设置 CLI / 取凭证」→ 走 ①②
- 用户直接问新闻、热点、收藏、点赞 → 优先用本 Connector 的对应工具（若 `tools/list` 中有）
- 本 Connector 未提供的能力（如天气）→ 由腾讯新闻 skill / CLI 承担
- 腾讯新闻 skill / CLI **不在本 Connector 包内**，由用户自行从技能市场 / 官方渠道获取；本 Connector 只负责取得 API Key
- 若用户本地未安装该 skill / CLI：**只提示安装位置与手工配置方法，不要自行下载、安装或执行任何命令行工具**，也不要臆造不存在的命令

## 凭证安全约束

- 本 Connector 自身的鉴权由 OAuth 完成，**AI 不需要也不应该手写任何凭证**；
- `GetAPIKeyBySUID` 返回的 API Key 是**给腾讯新闻 skill / CLI 用**的：不要在对话里回显原文，也不要写入日志、聊天记录或临时文件；
- 只通过腾讯新闻 skill / CLI 的凭证设置流程写入本地（由该 CLI 负责安全存储）；
- 若用户直接把 Key 贴进对话，提示其改用本流程获取，并建议轮换该 Key；
- 返回多条 `api_keys` 且无法判断时，只用名称 / ID 与用户确认，不要列举凭证内容。

## 认证说明

- MCP 端点：`https://openapi.inews.qq.com/tencent-news-mcp`（streamableHttp）。
- 鉴权：标准 MCP OAuth 2.1 + PKCE，由 WorkBuddy 内置的 OAuth 管理器自动完成，**用户不需要填写任何 Key**。
- 首次连接会打开浏览器完成「腾讯新闻账号授权」；**若 12 秒内未完成，客户端会提示连接超时，请重新点击「连接」并尽快在浏览器中确认授权**。
- access_token 过期时客户端自动用 refresh_token 续期；续期失败会重新打开浏览器授权。
- 端点路径写错会命中网关的另一套校验（返回 `text/plain` 的 `token nil`），此时错误与授权无关，先核对 URL。

## 错误处理

| 现象 | 处理建议 |
| --- | --- |
| 授权被取消 / 未在浏览器完成 | 提示用户重新点击「连接」，并在浏览器中尽快完成授权（注意 12 秒超时窗口） |
| 授权失效且无法自动续期 | 引导用户在 Connector 面板断开后重新连接 |
| `text/plain` 且 body 为 `{"code":10001,"msg":"token nil"}` | 端点路径错误（落在 `/mcp/...` 网关上），不是授权问题，检查 mcp.json 的 url |
| JSON-RPC `-32602` `unknown tool name` | 先调 `tools/list` 取当前工具清单，再用其中的工具名重试 |
| JSON-RPC `-32602` `Invalid params` | 按该工具的 `inputSchema` 补齐或修正参数 |
| JSON-RPC `-32003`「缺少 xx 权限」 | 提示用户该操作需要更高权限 |
| HTTP 503 + `retryable: true` | 上游服务暂时不可用，稍后重试，不要引导用户重新授权 |

## 注意事项

- 工具是否真正成功需看响应体：业务错误走 JSON-RPC `error`，**HTTP 状态仍是 200**。
- 查询类结果通常较大，回答用户具体问题时只摘取相关字段，不要整段贴出。
- 写操作（如修改用户信息）执行前必须与用户确认。
