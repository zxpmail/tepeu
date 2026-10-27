---
name: weibo-skill
title: 微博技能库
description: 通过 weibo-cli 命令行或微博 MCP 连接器查询和发布微博、管理评论与文章、分析用户和粉丝数据、检索热搜与影视指数。
version: "1.2.4"
author: "Weibo Open Platform"
---

# 微博技能库

本 Skill 指导 AI Agent 通过 `weibo-cli` 或微博 MCP 连接器访问微博开放平台。下文列出 10 类、75 项平台能力及其参数，覆盖微博内容、评论、文章、互动、用户、粉丝、搜索、短链、标签与影视指数。每项能力的标题是 MCP 工具名，其下的“CLI 对应命令”是命令行调用方式；根据当前可用的接入方式选择其一。

## CLI 使用

```bash
npm install -g @weibo-ai/weibo-cli
weibo-cli auth login
weibo-cli auth whoami
weibo-cli commands list --available
weibo-cli commands show search hot_word/biz
weibo-cli search hot_word/biz --agent codex --output json
```

- 调用格式：`weibo-cli <group> <action> [--参数名 值] --agent <调用方名称> --output json`。`--agent` 也可由 `WEIBO_CLI_AGENT` 环境变量提供；使用实际 Agent 名称，不要机械复制示例中的 `codex`。
- 先用 `commands list --available` 核实当前账号可用命令，再用 `commands show <group> <action>` 或 `<group> <action> --help` 核实参数。本文是能力参考；命令、权限和参数以当前 CLI 返回的信息为准。某些 action 含 `/`，须原样传入。
- Agent 读取结果时显式使用 `--output json`；需要人工查看时可选 `table`、`yaml` 或 `raw`。不要将 `--output` 用于 `auth login`。
- 若尚未登录，`auth login` 在交互终端打开浏览器，在无 TTY 环境使用设备码；可显式运行 `auth login --device`。设备码仍需用户在浏览器完成授权。登录后用 `auth whoami` 或 `doctor` 检查状态。
- 自动化环境可通过 `WEIBO_CLI_TOKEN` 或 `WEIBO_CLI_REFRESH_TOKEN` 注入凭据。不要在命令参数、日志或回复中写出 Token；`auth token --export` 会显示完整 Token，仅在用户明确需要导出时使用。
- 对发布、删除、回复等会改变微博状态的操作，按用户授权范围执行；对文章正式发布，先预览并让用户核对最终内容。

## MCP 使用

- 连接端点：`https://cli.weibo.com/mcp`。连接器使用 OAuth 2.1 + PKCE；在 MCP 客户端完成微博网页授权，令牌的保存和刷新由客户端负责。
- 从客户端获取当前 `tools/list` 与工具的 `inputSchema`，再调用相应工具。下文标题如 `search_hot_word_biz` 为工具名，对应 CLI 命令 `weibo search hot_word/biz`。工具是否可用以当前连接返回的清单和账号权限为准。
- MCP 工具入参使用结构化字段，不传 CLI 的 `--agent`、`--output` 等命令行选项。调用结果包含工具返回的微博数据；失败时按文末的 MCP 故障排查处理。
- `statuses_upload`、`statuses_upload_pic` 的 `pic` 不能传调用方本地文件路径；先读取图片，传 `data:image/<格式>;base64,<内容>`、纯 Base64 或服务端可访问的 HTTP/HTTPS URL。
- 对发布、删除、回复等会改变微博状态的操作，按用户授权范围执行；对文章正式发布，先预览并让用户核对最终内容。

---

## 目录
1. [微博内容 (Statuses) (19 个工具)](#statuses)
2. [微博评论 (Comments) (14 个工具)](#comments)
3. [专栏与文章 (Article) (8 个工具)](#article)
4. [表态与点赞 (Attitudes) (3 个工具)](#attitudes)
5. [用户信息与统计 (Users) (7 个工具)](#users)
6. [社交关系与粉丝画像 (Friendships) (11 个工具)](#friendships)
7. [搜索与热搜 (Search) (4 个工具)](#search)
8. [短链接服务 (Short URL) (2 个工具)](#short_url)
9. [用户标签 (Tags) (2 个工具)](#tags)
10. [影视综艺榜单 (Weibo Index) (5 个工具)](#wbindex)

---

## <a id="statuses"></a>微博内容 (Statuses)

支持发布微博（纯文字、单图、多图）、转发、删除微博，查询个人/他人微博时间线、@提及、批量获取微博详情与转评赞统计。

### `statuses_count_biz` - 批量获取指定微博的转发数评论数喜欢数

- **CLI 对应命令**：`weibo statuses count/biz`
- **接口路径**：`statuses/count/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `ids` | `string` | ✅ | - |
| `exclude_comment_like` | `int` | - | - |

---

### `statuses_count_sp_biz` - 批量获取指定授权微博的转发数、评论数、赞数、阅读数

- **CLI 对应命令**：`weibo statuses count_sp/biz`
- **接口路径**：`statuses/count_sp/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `ids` | `string` | ✅ | 微博ID，英文半角逗号分隔，最大100。 |
| `exclude_comment_like` | `string` | - | 是否排除评论赞计数，0:表示不排除，1:表示排除，默认为0。 |

---

### `statuses_destroy` - 删除微博信息

- **CLI 对应命令**：`weibo statuses destroy`
- **接口路径**：`statuses/destroy.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要删除的微博ID。 |

---

### `statuses_mentions_biz` - 获取@当前登录用户的微博列表

- **CLI 对应命令**：`weibo statuses mentions/biz`
- **接口路径**：`statuses/mentions/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `filter_by_type` | `int` | - | 原创筛选类型，0：全部微博、1：原创的微博，默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博、2：来自微群，默认为0。 |

---

### `statuses_mentions_other` - 获取@某人的微博

- **CLI 对应命令**：`weibo statuses mentions/other`
- **接口路径**：`statuses/mentions/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `filter_by_type` | `int` | - | 原创筛选类型，0：全部微博、1：原创的微博，默认为0 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博、2：来自微群，默认为0。 |

---

### `statuses_queryid_biz` - 通过微博（评论、私信）MID获取其ID

- **CLI 对应命令**：`weibo statuses queryid/biz`
- **接口路径**：`statuses/queryid/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `mid` | `string` | ✅ | 需要查询的微博（评论、私信）MID，批量模式下，用半角逗号分隔，最多不超过20个。 |
| `type` | `int` | ✅ | 获取类型，1：微博、2：评论、3：私信 |
| `inbox` | `int` | - | 仅对私信有效，当MID类型为私信时用此参数，0：发件箱、1：收件箱，默认为0 。 |
| `isBase62` | `int` | - | MID是否是base62编码，0：否、1：是，默认为0。 |
| `is_batch` | `int` | - | 是否使用批量模式，0：否、1：是，默认为0。 |

---

### `statuses_querymid_biz` - 通过微博（评论、私信）ID获取其MID

- **CLI 对应命令**：`weibo statuses querymid/biz`
- **接口路径**：`statuses/querymid/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `string` | ✅ | 需要查询的微博（评论、私信）ID，批量模式下，用半角逗号分隔，最多不超过20个。 |
| `type` | `int` | ✅ | 获取类型，1：微博、2：评论、3：私信 |
| `is_batch` | `int` | - | 是否使用批量模式，0：否、1：是，默认为0。 |

---

### `statuses_repost` - 转发一条微博信息

- **CLI 对应命令**：`weibo statuses repost`
- **接口路径**：`statuses/repost.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 要转发的微博ID。 |
| `status` | `string` | ✅ | 要发布的微博文本内容。 |
| `list_id` | `string` | - | 微博的保护投递指定分组ID，只有当visible参数为3时生效且必选。 |
| `visible` | `int` | - | 微博的可见性，0：所有人能看，1：仅自己可见，2：密友可见，3：指定分组可见，默认为0。 |
| `is_comment` | `int` | - | 是否在转发的同时发表评论，0：否、1：评论给当前微博、2：评论给原微博、3：都评论，默认为0 。 |
| `annotations` | `string` | - | 元数据，主要是为了方便第三方应用记录一些适合于自己使用的信息，每条微博可以包含一个或者多个元数据，必须以json字串的形式提交，字串长度不超过512个字符，具体内容可以自定。 |

---

### `statuses_repost_timeline_all` - 返回一条微博的全部转发微博列表

- **CLI 对应命令**：`weibo statuses repost_timeline/all`
- **接口路径**：`statuses/repost_timeline/all.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要查询的微博ID。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过50，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |

---

### `statuses_repost_timeline_biz` - 获取当前登录用户发布的一条原创微博的最新转发微博

- **CLI 对应命令**：`weibo statuses repost_timeline/biz`
- **接口路径**：`statuses/repost_timeline/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要查询的微博ID。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |

---

### `statuses_show_batch_biz` - 根据微博ID批量获取微博信息

- **CLI 对应命令**：`weibo statuses show_batch/biz`
- **接口路径**：`statuses/show_batch/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `ids` | `string` | ✅ | 需要查询的微博ID，用半角逗号分隔，最多不超过50个。 |
| `trim_user` | `int` | - | 返回值中user字段开关，0：返回完整user字段、1：user字段仅返回uid，默认为0。 |
| `isGetLongText` | `int` | - | 针对字数大于140字微博，是否返回完整内容的开关，0：只返回140以内的微博内容、1：返回值中增加longText字段返回完整微博内容，默认为0。 |

---

### `statuses_statistic_biz` - 返回近90天内发布的授权微博每天的被转发数、被评论数、被喜欢数、被点击数（区分短链、图片、视频点击）

- **CLI 对应命令**：`weibo statuses statistic/biz`
- **接口路径**：`statuses/statistic/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要查询的微博ID。 |
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

### `statuses_update` - 发布一条微博信息

- **CLI 对应命令**：`weibo statuses update`
- **接口路径**：`statuses/update.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `lat` | `number` | - | 纬度，有效范围：-90.0到+90.0，+表示北纬，默认为0.0。 |
| `long` | `number` | - | 经度，有效范围：-180.0到+180.0，+表示东经，默认为0.0。 |
| `status` | `string` | ✅ | 要发布的微博文本内容。 |
| `list_id` | `string` | - | 微博的保护投递指定分组ID，只有当visible参数为3时生效且必选。 |
| `visible` | `int` | - | 微博的可见性，0：所有人能看，1：仅自己可见，2：密友可见，3：指定分组可见，默认为0。 |
| `annotations` | `string` | - | 元数据，主要是为了方便第三方应用记录一些适合于自己使用的信息，每条微博可以包含一个或者多个元数据，必须以json字串的形式提交，字串长度不超过512个字符，具体内容可以自定。 |
| `is_longtext` | `int` | - | 是否发送超过140字微博，0：不超过140字，1：超过140字但在2000字以内，默认为0。操作用户需满足发送超过140字微博资格。 |
| `mblog_statement` | `int` | - | 微博内容声明，0:无；1:AI生成；2:虚构演绎；3:内容为转载；4:内容为自主创作。根据相关要求，若内容为AI生成，需 mblog_statement=1，没有按要求打标者责任由开发者或作者自己承担。 |

---

### `statuses_upload` - 上传图片并发布一条微博

- **CLI 对应命令**：`weibo statuses upload`
- **接口路径**：`statuses/upload.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `lat` | `number` | - | 纬度，有效范围：-90.0到+90.0，+表示北纬，默认为0.0。 |
| `pic` | `string` | ✅ | 要上传的图片，仅支持 JPEG、GIF、PNG 格式且小于 10M。CLI 可传本地文件路径；MCP 须传 data URL、纯 Base64 或服务端可访问的 HTTP/HTTPS URL，不可传调用方本地路径。 |
| `long` | `number` | - | 经度，有效范围：-180.0到+180.0，+表示东经，默认为0.0。 |
| `status` | `string` | ✅ | 要发布的微博文本内容，内容不超过140个汉字。 |
| `list_id` | `string` | - | 微博的保护投递指定分组ID，只有当visible参数为3时生效且必选。 |
| `visible` | `int` | - | 微博的可见性，0：所有人能看，1：仅自己可见，2：密友可见，3：指定分组可见，默认为0。 |
| `annotations` | `string` | - | 元数据，主要是为了方便第三方应用记录一些适合于自己使用的信息，每条微博可以包含一个或者多个元数据，必须以json字串的形式提交，字串长度不超过512个字符，具体内容可以自定。 |

---

### `statuses_upload_pic` - 上传图片，返回图片id

- **CLI 对应命令**：`weibo statuses upload_pic`
- **接口路径**：`statuses/upload_pic.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pic` | `string` | ✅ | 要上传的图片，仅支持 JPEG、GIF、PNG 格式且小于 10M。CLI 可传本地文件路径；MCP 须传 data URL、纯 Base64 或服务端可访问的 HTTP/HTTPS URL，不可传调用方本地路径。 |

---

### `statuses_upload_url_text` - 发布一条带单/多图的微博（需要组合上传图片接口）

- **CLI 对应命令**：`weibo statuses upload_url_text`
- **接口路径**：`statuses/upload_url_text.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `lat` | `number` | - | 纬度，有效范围：-90.0到+90.0，+表示北纬，默认为0.0。 |
| `long` | `number` | - | 经度，有效范围：-180.0到+180.0，+表示东经，默认为0.0。 |
| `pic_id` | `string` | ✅ | 已经上传的图片pid，多个时使用英文半角逗号符分隔，最多不超过9个。 |
| `status` | `string` | ✅ | 要发布的微博文本内容。 |
| `list_id` | `string` | - | 微博的保护投递指定分组ID，只有当visible参数为3时生效且必选。 |
| `visible` | `int` | - | 微博的可见性，0：所有人能看，1：仅自己可见，2：密友可见，3：指定分组可见，默认为0。 |
| `annotations` | `string` | - | 元数据，主要是为了方便第三方应用记录一些适合于自己使用的信息，每条微博可以包含一个或者多个元数据，必须以json字串的形式提交，字串长度不超过512个字符，具体内容可以自定。 |
| `is_longtext` | `int` | - | 是否发送超过140字微博，0：不超过140字，1：超过140字但在2000字以内，默认为0。操作用户需满足发送超过140字微博资格。 |
| `mblog_statement` | `int` | - | 微博内容声明，0:无；1:AI生成；2:虚构演绎；3:内容为转载；4:内容为自主创作。根据相关要求，若内容为AI生成，需 mblog_statement=1，没有按要求打标者责任由开发者或作者自己承担。 |

---

### `statuses_user_timeline_biz` - 获取当前登录用户发布的微博

- **CLI 对应命令**：`weibo statuses user_timeline/biz`
- **接口路径**：`statuses/user_timeline/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `flag` | `int` | - | 用于筛选推广微博。0：返回推广微博和普通微博；1：返回推广微博；2：返回普通微博；默认为0。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过25，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `feature` | `int` | - | 过滤类型ID，0：全部、1：原创、2：图片、3：视频、4：音乐、7：纯文本、8：转发，默认为0。 |
| `base_app` | `int` | - | 是否只获取当前应用的数据。0为否（所有数据），1为是（仅当前应用），默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `trim_user` | `int` | - | 返回值中user字段开关，0：返回完整user字段、1：user字段仅返回uid，默认为0。 |
| `exclude_comment_like` | `int` | - | 是否排除评论赞计数，0:表示不排除，1:表示排除，默认为0。 |

---

### `statuses_user_timeline_other` - 获取其他用户发布的微博

- **CLI 对应命令**：`weibo statuses user_timeline/other`
- **接口路径**：`statuses/user_timeline/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `flag` | `int` | - | 用于筛选推广微博。0：返回推广微博和普通微博；1：返回推广微博；2：返回普通微博；默认为0。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过100，默认为20。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的微博，默认为0。 |
| `feature` | `int` | - | 过滤类型ID，0：全部、1：原创、2：图片、3：视频、4：音乐，默认为0。 |
| `base_app` | `int` | - | 是否只获取当前应用的数据。0为否（所有数据），1为是（仅当前应用），默认为0。 |
| `end_time` | `int` | - | unix时间戳，若指定此参数，则返回ID比end_time早的微博。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的微博（即比since_id时间晚的微博），默认为0。 |
| `start_time` | `int` | - | unix时间戳，若指定此参数，则返回ID比start_time晚的微博。 |

---

### `statuses_user_timeline_batch` - 批量获取用户个人微博列表

- **CLI 对应命令**：`weibo statuses user_timeline_batch`
- **接口路径**：`statuses/user_timeline_batch.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `flag` | `int` | - | 用于筛选推广微博。0：返回推广微博和普通微博；1：返回推广微博；2：返回普通微博；默认为0。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `uids` | `string` | - | 需要查询的用户ID，用半角逗号分隔，一次最多20个。 |
| `count` | `int` | - | 单页返回的记录条数，默认为20。 |
| `feature` | `int` | - | 过滤类型ID，0：全部、1：原创、2：图片、3：视频、4：音乐，默认为0。 |
| `base_app` | `int` | - | 是否只获取当前应用的数据。0为否（所有数据），1为是（仅当前应用），默认为0。 |

---

## <a id="comments"></a>微博评论 (Comments)

支持发表评论、回复评论、删除评论，获取单条微博评论、自己发出的/收到的评论、时间线评论以及@提及评论。

### `comments_by_me_biz` - 获取当前登录用户发出的评论列表

- **CLI 对应命令**：`weibo comments by_me/biz`
- **接口路径**：`comments/by_me/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

### `comments_by_me_other` - 获取某个用户发出的评论列表

- **CLI 对应命令**：`weibo comments by_me/other`
- **接口路径**：`comments/by_me/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

### `comments_create` - 评论一条微博

- **CLI 对应命令**：`weibo comments create`
- **接口路径**：`comments/create.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要评论的微博ID。 |
| `comment` | `string` | ✅ | 评论内容，内容不超过140个汉字。 |
| `pic_ids` | `string` | - | 需要评论的图片ID，多个之间用逗号分隔。 |
| `is_repost` | `int` | - | 评论的同时转发到我的微博, 0:不转发。1：转发。默认0. |
| `comment_ori` | `int` | - | 当评论转发微博时，是否评论给原微博，0：否、1：是，默认为0。 |

---

### `comments_destroy` - 删除一条评论

- **CLI 对应命令**：`weibo comments destroy`
- **接口路径**：`comments/destroy.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `cid` | `int` | ✅ | 要删除的评论ID，只能删除登录用户自己发布的评论。 |

---

### `comments_mentions_biz` - 获取@当前登录用户的评论

- **CLI 对应命令**：`weibo comments mentions/biz`
- **接口路径**：`comments/mentions/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

### `comments_mentions_other` - 获取@某人的评论

- **CLI 对应命令**：`weibo comments mentions/other`
- **接口路径**：`comments/mentions/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

### `comments_reply` - 回复一条评论

- **CLI 对应命令**：`weibo comments reply`
- **接口路径**：`comments/reply.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要评论的微博ID。 |
| `cid` | `int` | ✅ | 需要回复的评论ID。 |
| `comment` | `string` | ✅ | 回复评论内容，内容不超过140个汉字。 |
| `pic_ids` | `string` | - | 需要评论的图片ID，多个之间用逗号分隔。 |
| `is_repost` | `int` | - | 评论的同时转发到我的微博, 0:不转发。1：转发。默认0. |
| `comment_ori` | `int` | - | 当评论转发微博时，是否评论给原微博，0：否、1：是，默认为0。 |
| `without_mention` | `int` | - | 回复中是否自动加入“回复@用户名”，0：是、1：否，默认为0。 |

---

### `comments_show_all` - 返回一条微博的全部评论列表

- **CLI 对应命令**：`weibo comments show/all`
- **接口路径**：`comments/show/all.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要查询的微博ID。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |

---

### `comments_show_biz` - 获取当前登录用户发布的微博下的评论列表

- **CLI 对应命令**：`weibo comments show/biz`
- **接口路径**：`comments/show/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 需要查询的微博ID。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |

---

### `comments_show_batch_biz` - 批量获取评论内容

- **CLI 对应命令**：`weibo comments show_batch/biz`
- **接口路径**：`comments/show_batch/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `cids` | `int` | ✅ | 需要查询的批量评论ID，用半角逗号分隔，最大50。 |

---

### `comments_timeline_biz` - 获取当前登录用户发出及收到的评论列表

- **CLI 对应命令**：`weibo comments timeline/biz`
- **接口路径**：`comments/timeline/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `trim_user` | `int` | - | 返回值中user字段开关，0：返回完整user字段、1：user字段仅返回uid，默认为0。 |

---

### `comments_timeline_other` - 获取某个用户发出和收到的评论列表

- **CLI 对应命令**：`weibo comments timeline/other`
- **接口路径**：`comments/timeline/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `trim_user` | `int` | - | 返回值中user字段开关，0：返回完整user字段、1：user字段仅返回uid，默认为0。 |

---

### `comments_to_me_biz` - 获取当前登录用户收到的评论列表

- **CLI 对应命令**：`weibo comments to_me/biz`
- **接口路径**：`comments/to_me/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

### `comments_to_me_other` - 获取某个用户收到的评论列表

- **CLI 对应命令**：`weibo comments to_me/other`
- **接口路径**：`comments/to_me/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。（必填） |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的评论，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则返回ID比since_id大的评论（即比since_id时间晚的评论），默认为0。 |
| `filter_by_author` | `int` | - | 作者筛选类型，0：全部、1：我关注的人、2：陌生人，默认为0。 |
| `filter_by_source` | `int` | - | 来源筛选类型，0：全部、1：来自微博的评论、2：来自微群的评论，默认为0。 |

---

## <a id="article"></a>专栏与文章 (Article)

支持发布头条长文章、文章预览、发文配置查询，以及 V+ 付费专栏的创建、查询、更新和删除。

### `article_collections_create` - 创建付费专栏

创建付费专栏。需先开通V+付费专栏权限，如未开通请访问 https://videoalbum.e.weibo.com/signup/standard 申请。定价档位（单位分）：990/1900/2900/4900/6900/9900/12800，对应9.9/19/29/49/69/99/128元。60秒内同一用户最多5次请求。video_nums需3-100范围。创建成功返回collections_id。

- **CLI 对应命令**：`weibo article collections/create`
- **接口路径**：`article/collections/create.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `type` | `int` | - | 固定传0 |
| `money` | `int` | ✅ | 专栏定价（分），只能是990/1900/2900/4900/6900/9900/12800之一 |
| `title` | `string` | ✅ | 专栏标题 |
| `images` | `string` | ✅ | 图片JSON字符串，包含albumCover（封面图对象，含pic_id和url字段，只有一张）和albumDesc（介绍图数组，每个元素含pic_id和url字段，没有则传空数组[]） |
| `content` | `string` | ✅ | 专栏介绍 |
| `sale_money` | `int` | - | 优惠价格（分） |
| `video_nums` | `int` | ✅ | 预计更新视频数（3-100） |
| `category_id` | `int` | ✅ | 分类id：97财商知识/98教育课程/99健康科普/100兴趣生活 |
| `is_recommend` | `int` | - | 0聚合页推荐/1不推荐 |
| `sale_end_time` | `string` | - | 优惠价结束时间（如2026-09-12 03:22:59） |

---

### `article_collections_delete` - 删除专栏

删除指定专栏。已被购买过的专栏无法删除。需先开通V+付费专栏权限，如未开通请访问 https://videoalbum.e.weibo.com/signup/standard 申请。

- **CLI 对应命令**：`weibo article collections/delete`
- **接口路径**：`article/collections/delete.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 要删除的专栏id（或审核流水号） |

---

### `article_collections_info` - 查询专栏详情

查询指定专栏的详细信息，包括标题、价格、状态、封面图、分类等。需先开通V+付费专栏权限，如未开通请访问 https://videoalbum.e.weibo.com/signup/standard 申请。

- **CLI 对应命令**：`weibo article collections/info`
- **接口路径**：`article/collections/info.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | - | 专栏id（或审核流水号） |

---

### `article_collections_list` - 查询专栏列表

查询当前用户的专栏列表，包含已发布和审核中的专栏。支持分页。需先开通V+付费专栏权限，如未开通请访问 https://videoalbum.e.weibo.com/signup/standard 申请。

- **CLI 对应命令**：`weibo article collections/list`
- **接口路径**：`article/collections/list.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | ✅ | 页码 |
| `limit` | `int` | ✅ | 每页条数 |
| `short_play` | `int` | ✅ | 是否短剧：1是/0否，默认传0 |

---

### `article_collections_update` - 更新专栏

更新专栏信息。注意：title/money/images/content/video_nums必须每次完整传，不支持部分更新。60秒内同一用户最多5次请求。money必须是约定价位之一。video_nums不能比当前已有章节数小。id参数传列表接口中的collections_id。需先开通V+付费专栏权限，如未开通请访问 https://videoalbum.e.weibo.com/signup/standard 申请。

- **CLI 对应命令**：`weibo article collections/update`
- **接口路径**：`article/collections/update.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 要更新的专栏id（即列表接口中的collections_id） |
| `money` | `int` | ✅ | 专栏定价（分），只能是990/1900/2900/4900/6900/9900/12800之一 |
| `title` | `string` | ✅ | 专栏标题 |
| `images` | `string` | ✅ | 图片JSON字符串，包含albumCover（封面图对象，含pic_id和url字段，只有一张）和albumDesc（介绍图数组，每个元素含pic_id和url字段，没有则传空数组[]） |
| `content` | `string` | ✅ | 专栏介绍 |
| `change_img` | `int` | - | 是否改了封面/简介图（1表示改了，需重新审核） |
| `sale_money` | `int` | - | 优惠价格（分） |
| `video_nums` | `int` | ✅ | 预计更新视频数，不能比当前已有章节数小 |
| `category_id` | `int` | ✅ | 分类id：97财商知识/98教育课程/99健康科普/100兴趣生活 |
| `is_recommend` | `int` | - | 0聚合页推荐/1不推荐 |
| `sale_end_time` | `string` | - | 优惠价结束时间 |

---

### `article_config` - 查询发文配置

查询当前用户可用的付费类型（V+会员专栏、单篇付费、V+单篇付费等）和价格区间。发文前调用此接口确认用户能发什么类型的文章及价格范围。无需传入任何参数。

- **CLI 对应命令**：`weibo article config`
- **接口路径**：`article/config.json` (HTTP GET)

*无需输入参数。*

---

### `article_preview` - 生成文章预览链接

提交文章内容生成预览链接，用户可在浏览器中打开预览链接查看发布后的效果。预览链接30分钟内有效，需用户登录微博后访问。同一用户重复提交会覆盖之前的预览数据。注意：预览接口标题长度限制为32字，与发文接口的64字不同。正文内容使用标准Markdown格式，支持标题/粗体/斜体/删除线/引用/有序无序列表/分割线/行内代码/代码块/链接/图片/表格/表情/转义字符/数学公式，不支持HTML标签/任务列表/脚注/TOC目录等。

- **CLI 对应命令**：`weibo article preview`
- **接口路径**：`article/preview.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `cover` | `string` | - | 封面图URL（须为*.sinaimg.cn域名） |
| `title` | `string` | ✅ | 文章标题（1-32字） |
| `content` | `string` | ✅ | 正文内容（Markdown格式） |
| `summary` | `string` | - | 文章摘要 |
| `pay_type` | `string` | ✅ | 付费类型 |
| `free_content` | `string` | ✅ | 免费试读内容 |

---

### `article_publish` - 发布文章

发布头条文章并同步发布短微博。一次调用同时发布文章和短微博，两者都发。支持免费文章、V+会员专栏、V+单篇付费、单篇付费四种类型。发布后不可撤销。5秒内同uid同内容重复提交会被去重。注意：follow_to_read、collection_id、非free付费三者最多出现1种。关联专栏发文时pay_type传free。正文内容使用标准Markdown格式，支持标题/粗体/斜体/删除线/引用/有序无序列表/分割线/行内代码/代码块/链接/图片/表格/表情/转义字符/数学公式，不支持HTML标签/任务列表/脚注/TOC目录等。

- **CLI 对应命令**：`weibo article publish`
- **接口路径**：`article/publish.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `text` | `string` | ✅ | 短微博文案（≤1980字） |
| `cover` | `string` | - | 封面图URL（须为*.sinaimg.cn域名） |
| `money` | `string` | - | 单篇付费金额（pay_type=single_pay_new时必填，单位元） |
| `title` | `string` | ✅ | 文章标题（1-64字） |
| `writer` | `string` | - | 第二作者（1-48字） |
| `content` | `string` | ✅ | 正文内容（Markdown格式，1-300000字） |
| `is_word` | `int` | - | 导入word（0/1，默认0） |
| `summary` | `string` | - | 文章简介 |
| `pay_type` | `string` | ✅ | 付费类型 |
| `freecontent` | `string` | - | 免费试读内容（pay_type非free时必填） |
| `collection_id` | `string` | - | 专栏ID |
| `follow_to_read` | `int` | - | 加关注看全文（0/1，默认0） |
| `vclub_single_pay_money` | `string` | - | V+单篇付费金额（pay_type=vclub_single_pay时必填，单位元） |

---

## <a id="attitudes"></a>表态与点赞 (Attitudes)

支持对指定微博发布表态（点赞）、取消表态（取消赞）以及获取微博点赞列表。

### `attitudes_create` - 发布一条表态

- **CLI 对应命令**：`weibo attitudes create`
- **接口路径**：`attitudes/create.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 微博ID |
| `attitude` | `string` | - | 表态内容，默认为heart。heart(赞)；happy(喜)；sad(悲)；surprise(惊)；angry(怒)；hug(抱一抱) |
| `attitude_type` | `string` | - | 表态类型，默认为1。1(赞)；2(喜)；3(惊)；4(悲)；5(怒)；8(抱一抱) |

---

### `attitudes_destroy` - 删除一条表态

- **CLI 对应命令**：`weibo attitudes destroy`
- **接口路径**：`attitudes/destroy.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 微博ID |
| `attid` | `int` | - | 表态ID |

---

### `attitudes_show_biz` - 根据微博ID返回某条微博的赞列表

- **CLI 对应命令**：`weibo attitudes show/biz`
- **接口路径**：`attitudes/show/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `id` | `int` | ✅ | 微博ID。 |
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `max_id` | `int` | - | 若指定此参数，则返回ID小于或等于max_id的赞信息，默认为0。 |
| `since_id` | `int` | - | 若指定此参数，则只返回ID比since_id大的赞信息（即比since_id发表时间晚的赞信息），默认为0。 |

---

## <a id="users"></a>用户信息与统计 (Users)

支持获取当前登录用户资料、批量查询其他用户信息、粉丝/关注/微博数统计、行为趋势分析与阅读点击统计。

### `users_behavior_trend` - 获取授权用户行为数据每日变化趋势

- **CLI 对应命令**：`weibo users behavior_trend`
- **接口路径**：`users/behavior_trend.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

### `users_behavior_trend_biz` - 获取他人用户行为数据每日变化趋势

- **CLI 对应命令**：`weibo users behavior_trend/biz`
- **接口路径**：`users/behavior_trend/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。 |
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

### `users_counts_batch_other` - 批量获取用户的粉丝数、关注数、微博数

- **CLI 对应命令**：`weibo users counts_batch/other`
- **接口路径**：`users/counts_batch/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uids` | `string` | ✅ | 需要获取数据的用户UID，多个之间用逗号分隔，最多不超过100个。 |

---

### `users_receive_like_count` - 获取某用户近90天每天的被喜欢数

- **CLI 对应命令**：`weibo users receive_like_count`
- **接口路径**：`users/receive_like_count.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | - | 需要查询的用户ID。 |
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

### `users_show_biz` - 获取当前登录用户信息

- **CLI 对应命令**：`weibo users show/biz`
- **接口路径**：`users/show/biz.json` (HTTP GET)

*无需输入参数。*

---

### `users_show_batch_other` - 批量获取其他用户的基本信息

- **CLI 对应命令**：`weibo users show_batch/other`
- **接口路径**：`users/show_batch/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uids` | `string` | - | 需要查询的用户ID，用半角逗号分隔，一次最多50个。 |
| `screen_name` | `string` | - | 需要查询的用户昵称，用半角逗号分隔，一次最多50个。 |

---

### `users_statistic_biz` - 获取当前登录用户近90天每天的被点击数（区分短链、图片点击）、被阅读数、用户去重的被阅读数

- **CLI 对应命令**：`weibo users statistic/biz`
- **接口路径**：`users/statistic/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

## <a id="friendships"></a>社交关系与粉丝画像 (Friendships)

支持获取关注列表、粉丝列表，以及深度粉丝画像分析（活跃粉丝、年龄性别分布、地域分布、兴趣星座、在线时段、每日增减趋势等）。

### `friendships_followers_active_list` - 获取授权用户活跃或互动粉丝TOP20列表

- **CLI 对应命令**：`weibo friendships followers/active_list`
- **接口路径**：`friendships/followers/active_list.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `flag` | `int` | ✅ | 列表类型：0=活跃粉丝，1=互动粉丝 |

---

### `friendships_followers_age_group_count` - 获取某用户粉丝的年龄段、性别细分数据

- **CLI 对应命令**：`weibo friendships followers/age_group_count`
- **接口路径**：`friendships/followers/age_group_count.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | - |

---

### `friendships_followers_biz` - 获取当前登录用户的粉丝列表

- **CLI 对应命令**：`weibo friendships followers/biz`
- **接口路径**：`friendships/followers/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `age` | `string` | - | 指定接收用户的年龄范围，取值内容为：6-12、13-18、19-22、23-25、26-29、30-39、40-59、60-80、其他。 |
| `city` | `int` | - | 指定接收用户的城市范围，城市ID。 |
| `flag` | `int` | - | 指定粉丝的属性条件。0：所有粉丝；1：活跃粉丝；2：互动粉丝；支持任意数字叠加值，如，3：活跃粉丝+互动粉丝，默认为0。 |
| `type` | `int` | - | 指定粉丝的认证类型。0：所有用户；1：普通用户；2：黄V；4：蓝v（包括政府，企业，媒体，网站，校园，应用，机构/团体，公益组织）；8：达人；支持任意数字叠加值，如，6：黄V+蓝V，默认为所有用户。 |
| `bucket` | `int` | - | 筛选粉丝的个数范围，默认为50，最大不超过200。 |
| `gender` | `string` | - | 指定接收用户的性别范围，m：男、f：女。 |
| `max_time` | `string` | - | 返回结果的时间戳游标，若指定此参数，则返回关注时间小于或等于max_time的粉丝，默认从当前时间开始算。返回结果中会得到next_cursor字段，表示下一页的max_time。next_cursor为0表示已经到记录末尾。 |
| `province` | `int` | - | 指定接收用户的省份范围，省份ID。 |
| `cursor_uid` | `int` | - | 排重ID，传上次获取时最后一个用户ID。 |

---

### `friendships_followers_demographics_day` - 获取用户粉丝的星座、兴趣标签和粉丝量级的细分数据

- **CLI 对应命令**：`weibo friendships followers/demographics_day`
- **接口路径**：`friendships/followers/demographics_day.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `string` | ✅ | 要查询的用户 UID |
| `property` | `string` | ✅ | 查询条件：tag=兴趣标签，astro=星座，fan=粉丝量级 |

---

### `friendships_followers_influence_top20` - 获取近7天转发或互动最多的粉丝TOP20（仅蓝V）

- **CLI 对应命令**：`weibo friendships followers/influence_top20`
- **接口路径**：`friendships/followers/influence_top20.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。 |
| `flag` | `int` | ✅ | 查询条件。1：转发最多粉丝；2：互动最多粉丝。 |
| `time_span` | `int` | ✅ | 查询时间范围。1：近7天；2：近30天；3：近90天。 |

---

### `friendships_followers_location_count` - 获取用户粉丝的地区细分数据

- **CLI 对应命令**：`weibo friendships followers/location_count`
- **接口路径**：`friendships/followers/location_count.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `string` | - | 需要查询用户的uid。 |

---

### `friendships_followers_online_time_biz` - 获取当前登录用户粉丝最近一周在线时段比例分布

- **CLI 对应命令**：`weibo friendships followers/online_time/biz`
- **接口路径**：`friendships/followers/online_time/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `type` | `string` | ✅ | 分布类型：hour-小时分布，day-天分布 |

---

### `friendships_followers_source` - 获取某用户粉丝的关注来源

- **CLI 对应命令**：`weibo friendships followers/source`
- **接口路径**：`friendships/followers/source.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | - | 主用户ID。默认为当前登陆用户。 |

---

### `friendships_followers_trend_other_biz` - 获取当前登录用户的粉丝每天增加和减少数量

- **CLI 对应命令**：`weibo friendships followers/trend_other/biz`
- **接口路径**：`friendships/followers/trend_other/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `endtime` | `string` | ✅ | 结束时间，格式为XXXX-XX-XX。 |
| `starttime` | `string` | ✅ | 开始时间，格式为XXXX-XX-XX。 |

---

### `friendships_followers_unfollower_count` - 获取最长近90天内每天取消关注该账户的用户数

- **CLI 对应命令**：`weibo friendships followers/unfollower_count`
- **接口路径**：`friendships/followers/unfollower_count.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uid` | `int` | ✅ | 需要查询的用户ID。 |
| `endtime` | `int` | ✅ | 搜索范围结束时间，格式为xxxx-xx-xx。 |
| `starttime` | `int` | ✅ | 搜索范围起始时间，格式为xxxx-xx-xx。 |

---

### `friendships_friends_biz` - 获取当前登录用户的关注列表

- **CLI 对应命令**：`weibo friendships friends/biz`
- **接口路径**：`friendships/friends/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果页码。默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过200，默认为50。 |
| `trim_status` | `int` | - | user中的status信息开关， 1：user中的status字段仅返回status_id， 0：返回完整status信息。默认trim_status为1。 |

---

## <a id="search"></a>搜索与热搜 (Search)

提供微博实时热搜主榜、分类榜单、关键词微博检索以及用户搜索建议。

### `search_hot_word_biz` - 微博热搜主榜

- **CLI 对应命令**：`weibo search hot_word/biz`
- **接口路径**：`search/hot_word/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `count` | `int` | - | - |

---

### `search_hot_word_cate_biz` - 搜索分类榜单

- **CLI 对应命令**：`weibo search hot_word_cate/biz`
- **接口路径**：`search/hot_word_cate/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `count` | `int` | - | - |
| `category` | `string` | ✅ | 榜单类型，必填，单选：1=文娱榜、2=社会榜、3=科技榜、4=acg榜、5=生活榜、6=体育榜。 |

---

### `search_statuses_limited` - 搜索含某关键词的微博

- **CLI 对应命令**：`weibo search statuses/limited`
- **接口路径**：`search/statuses/limited.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `q` | `string` | ✅ | 搜索的关键字( 不能包含{、}、“ 等特殊字符)。 |
| `dup` | `int` | - | 是否排重（不显示相似数据），0：否、1：是，默认为1。 |
| `ids` | `string` | - | 搜索指定批量用户的微博，传用户UID，多个用~分隔，最多不超过50个。 |
| `city` | `int` | - | 搜索的城市ID，参考省份城市编码表。 |
| `hasv` | `int` | - | 是否为v用户发言，0：否、1：是，默认为空（全部）。 |
| `page` | `int` | - | 页码，默认为1。 |
| `sort` | `string` | - | 排序方式，time：时间倒序、hot：热门度【设置此参数只会返回精选微博】、fwnum：按转发数倒序、cmtnum：评论数倒序，默认为time。 |
| `type` | `int` | ✅ | 获取类型，1：微博、2：评论、3：私信，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，最大不超过50，最小为10，默认为10。 |
| `hasat` | `int` | - | 是否包含@，0：不包含@、1：只包含@，默认为空（全部）。 |
| `istag` | `int` | - | 是否严格为搜##内的话题，0：否、1：##内模糊匹配、2：##内精确匹配，默认为0。 |
| `hasori` | `int` | - | 是否包含原创，0：不包含原创、1：只包含原创，默认为空（全部）。 |
| `haspic` | `int` | - | 是否包含图片，0：不包含图片、1：只包含图片，默认为空（全部）。 |
| `hasret` | `int` | - | 是否包含转发，0：不包含转发、1：只包含转发，默认为空（全部）。 |
| `endtime` | `int` | - | 搜索范围结束时间，取值为时间戳，单位为秒。 |
| `haslink` | `int` | - | 是否包含链接，0：不包含链接、1：只包含链接，默认为空（全部）。 |
| `hastext` | `int` | - | 是否包含纯文本，0：不包含纯文本、1：只包含纯文本，默认为空（全部）。 |
| `antispam` | `int` | - | 是否反垃圾（不显示低质量数据），0：否、1：是，默认为1。 |
| `base_app` | `int` | - | 是否只获取当前应用的数据。0为否（所有数据），1为是（仅当前应用），默认为0。 |
| `hasmusic` | `int` | - | 是否包含音乐，0：不包含音乐、1：只包含音乐，默认为空（全部）。 |
| `hasvideo` | `int` | - | 是否包含视频，0：不包含视频、1：只包含视频，默认为空（全部）。 |
| `is_batch` | `int` | - | 是否使用批量模式，0：否、1：是，默认为0。 |
| `starttime` | `int` | - | 搜索范围起始时间，取值为时间戳，单位为秒。 |

---

### `search_suggestions_users_biz` - 搜用户搜索建议

- **CLI 对应命令**：`weibo search suggestions/users/biz`
- **接口路径**：`search/suggestions/users/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `q` | `string` | ✅ | 搜索的关键字。 |
| `count` | `int` | - | 单页返回的记录条数，默认为10。 |

---

## <a id="short_url"></a>短链接服务 (Short URL)

支持长链接生成 t.cn 微博短链接，以及短链接还原为原始长链接。

### `short_url_expand` - 将一个或多个短链接还原成原始的长链接

- **CLI 对应命令**：`weibo short_url expand`
- **接口路径**：`short_url/expand.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `url_short` | `string` | ✅ | 需要还原的短链接 |

---

### `short_url_shorten` - 将一个或多个长链接转换成短链接

- **CLI 对应命令**：`weibo short_url shorten`
- **接口路径**：`short_url/shorten.json` (HTTP POST)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `url_long` | `string` | ✅ | 需要转换的长链接 |

---

## <a id="tags"></a>用户标签 (Tags)

获取当前登录用户的兴趣个性标签，或批量获取其他用户的标签。

### `tags_biz` - 获取当前登录用户的标签

- **CLI 对应命令**：`weibo tags biz`
- **接口路径**：`tags/biz.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `page` | `int` | - | 返回结果的页码，默认为1。 |
| `count` | `int` | - | 单页返回的记录条数，默认为20。 |

---

### `tags_tags_batch_other` - 批量获取用户标签

- **CLI 对应命令**：`weibo tags tags_batch/other`
- **接口路径**：`tags/tags_batch/other.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `uids` | `string` | ✅ | 要获取标签的用户ID。最大20，逗号分隔。 |

---

## <a id="wbindex"></a>影视综艺榜单 (Weibo Index)

查询微博官方权威的剧集热播榜/待播榜/热议角色榜，以及综艺热播榜/待播榜。

### `wbindex_ranking_showComing` - 综艺-待播榜

- **CLI 对应命令**：`weibo wbindex ranking/showComing`
- **接口路径**：`wbindex/ranking/showComing.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pageNum` | `number` | - | 页码，默认 1。 |
| `pageSize` | `number` | - | 分页大小，默认 20。 |
| `timeType` | `string` | - | 时间类型，默认 hour：min 实时榜、hour 小时榜、day 日榜、week 周榜、month 月榜。 |
| `timeRange` | `string` | - | 榜单时间，默认当前小时。实时榜传空字符串；小时榜示例：2025-05-21 09:00:00；日榜/周榜示例：2025-05-21 00:00:00；月榜示例：2025-05-01 00:00:00。与 timeType 配合使用。 |
| `portraitType` | `string` | - | 画像维度筛选，默认空：portraitAge 年龄、portraitGender 性别、portraitProv 省份、portraitCity 城市。与 portraitValue 配合使用。 |
| `portraitValue` | `string` | - | 画像具体值，默认空，与 portraitType 配合使用。 |

---

### `wbindex_ranking_showHot` - 综艺-热播榜

- **CLI 对应命令**：`weibo wbindex ranking/showHot`
- **接口路径**：`wbindex/ranking/showHot.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pageNum` | `number` | - | 页码，默认 1。 |
| `pageSize` | `number` | - | 分页大小，默认 20。 |
| `timeType` | `string` | - | 时间类型，默认 hour：min 实时榜、hour 小时榜、day 日榜、week 周榜、month 月榜。 |
| `timeRange` | `string` | - | 榜单时间，默认当前小时。实时榜传空字符串；小时榜示例：2025-05-21 09:00:00；日榜/周榜示例：2025-05-21 00:00:00；月榜示例：2025-05-01 00:00:00。与 timeType 配合使用。 |
| `portraitType` | `string` | - | 画像维度筛选，默认空：portraitAge 年龄、portraitGender 性别、portraitProv 省份、portraitCity 城市。与 portraitValue 配合使用。 |
| `portraitValue` | `string` | - | 画像具体值，默认空，与 portraitType 配合使用。 |

---

### `wbindex_ranking_tvComing` - 剧集-待播榜

- **CLI 对应命令**：`weibo wbindex ranking/tvComing`
- **接口路径**：`wbindex/ranking/tvComing.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pageNum` | `number` | - | 页码，默认 1。 |
| `pageSize` | `number` | - | 分页大小，默认 20。 |
| `timeType` | `string` | - | 时间类型，默认 hour：min 实时榜、hour 小时榜、day 日榜、week 周榜、month 月榜。 |
| `timeRange` | `string` | - | 榜单时间，默认当前小时。实时榜传空字符串；小时榜示例：2025-05-21 09:00:00；日榜/周榜示例：2025-05-21 00:00:00；月榜示例：2025-05-01 00:00:00。与 timeType 配合使用。 |
| `portraitType` | `string` | - | 画像维度筛选，默认空：portraitAge 年龄、portraitGender 性别、portraitProv 省份、portraitCity 城市。与 portraitValue 配合使用。 |
| `portraitValue` | `string` | - | 画像具体值，默认空，与 portraitType 配合使用。 |

---

### `wbindex_ranking_tvHot` - 剧集-热播榜

- **CLI 对应命令**：`weibo wbindex ranking/tvHot`
- **接口路径**：`wbindex/ranking/tvHot.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pageNum` | `number` | - | 页码，默认 1。 |
| `pageSize` | `number` | - | 分页大小，默认 20。 |
| `timeType` | `string` | - | 时间类型，默认 hour：min 实时榜、hour 小时榜、day 日榜、week 周榜、month 月榜。 |
| `timeRange` | `string` | - | 榜单时间，默认当前小时。实时榜传空字符串；小时榜示例：2025-05-21 09:00:00；日榜/周榜示例：2025-05-21 00:00:00；月榜示例：2025-05-01 00:00:00。与 timeType 配合使用。 |
| `portraitType` | `string` | - | 画像维度筛选，默认空：portraitAge 年龄、portraitGender 性别、portraitProv 省份、portraitCity 城市。与 portraitValue 配合使用。 |
| `portraitValue` | `string` | - | 画像具体值，默认空，与 portraitType 配合使用。 |

---

### `wbindex_ranking_tvRole` - 剧集-热议角色榜

- **CLI 对应命令**：`weibo wbindex ranking/tvRole`
- **接口路径**：`wbindex/ranking/tvRole.json` (HTTP GET)

| 参数名 | 类型 | 必填 | 说明 |
|---|:---:|:---:|---|
| `pageNum` | `number` | - | 页码，默认 1。 |
| `pageSize` | `number` | - | 分页大小，默认 20。 |
| `timeType` | `string` | - | 时间类型，默认 hour：min 实时榜、hour 小时榜、day 日榜、week 周榜、month 月榜。 |
| `timeRange` | `string` | - | 榜单时间，默认当前小时。实时榜传空字符串；小时榜示例：2025-05-21 09:00:00；日榜/周榜示例：2025-05-21 00:00:00；月榜示例：2025-05-01 00:00:00。与 timeType 配合使用。 |
| `portraitType` | `string` | - | 画像维度筛选，默认空：portraitAge 年龄、portraitGender 性别、portraitProv 省份、portraitCity 城市。与 portraitValue 配合使用。 |
| `portraitValue` | `string` | - | 画像具体值，默认空，与 portraitType 配合使用。 |

---

## 认证与授权规范

### CLI

CLI 使用浏览器 OAuth 授权或设备码登录。运行 `weibo-cli auth login` 后由用户在浏览器完成确认；刷新凭据优先保存在系统钥匙串，钥匙串不可用时使用本地加密存储。无人工交互的环境使用环境变量注入凭据。认证失效时运行 `weibo-cli auth login` 重新登录；用 `weibo-cli doctor` 查看登录、账号与服务状态。不要读取或输出凭据文件。

### MCP

MCP 连接器使用 OAuth 2.1 + PKCE。用户在所用 MCP 客户端中连接微博并完成网页授权；访问令牌的保存与刷新取决于客户端实现。授权失效时在客户端重新连接或授权，不要在对话中粘贴令牌。

---

## 常见组合应用场景

以下示例采用 CLI 命令；使用 MCP 时，调用对应能力标题所示的工具名，并按工具 `inputSchema` 传参。

### 场景 1：微博实时舆情与热点跟踪
1. 运行 `weibo-cli search hot_word/biz` 获取当前微博实时热搜主榜。
2. 针对特定热搜词，运行 `weibo-cli search statuses/limited --q <热搜词>` 获取相关讨论博文列表。
3. 运行 `weibo-cli comments show/all --id <微博ID>` 或 `weibo-cli comments show/biz --id <微博ID>` 读取评论。

### 场景 2：个人微博发布与转评互动
1. 运行 `weibo-cli statuses user_timeline/biz` 获取当前用户最近的微博及 `id`。
2. 运行 `weibo-cli statuses count/biz --ids <微博ID>` 获取转发、评论和点赞统计。
3. 按用户授权运行 `weibo-cli comments create` 或 `weibo-cli comments reply` 回复评论。
4. 按用户授权运行 `weibo-cli statuses update` 发布纯文字微博；单图使用 `statuses upload`，多图先用 `statuses upload_pic` 再用 `statuses upload_url_text`。

### 场景 3：运营粉丝深度分析与画像洞察
1. 运行 `weibo-cli friendships followers/trend_other/biz` 获取粉丝每天增减与近 30 天趋势。
2. 运行 `weibo-cli friendships followers/online_time/biz` 查看粉丝在线时段分布。
3. 运行 `weibo-cli friendships followers/age_group_count` 与 `weibo-cli friendships followers/location_count` 分析受众年龄和地域。

---

## 错误处理与故障排查建议

### CLI

命令失败时先看错误码和消息。平台调用加 `--output json` 后，CLI 会把服务端错误体写到 stderr；若含 `trace_id`，保留它供排查。不要只凭 HTTP 状态码判断原因，也不要对发布、评论、删除等写操作盲目重试。

| 错误码 / 现象 | 实际含义 | 处理建议 |
|---|---|---|
| 未登录提示；`401 UNAUTHORIZED` | 本地无凭据，或访问令牌失效、会话被撤销。收到 401 时 CLI 会清除缓存的访问令牌。 | 运行 `weibo-cli auth whoami`、`weibo-cli doctor`；需要时运行 `weibo-cli auth login`，无 TTY 可用 `auth login --device`。不要反复提交同一写操作。 |
| `400 INVALID_PARAMS`、`INVALID_REQUEST`；本地 `Unknown option` | 缺少必填项、枚举值或参数格式不对；也可能是命令要求用户授权。 | 运行 `weibo-cli commands show <group> <action>` 核对必填项与取值；检查 `id`、`ids` 等参数和当前登录状态。`INVALID_CLIENT` 是 OAuth 客户端配置错误，不是普通命令缺参。 |
| `404 NOT_FOUND` | 当前命令不存在或未启用。 | 用 `weibo-cli commands list --available` 查当前可用命令，核对 group/action 的拼写及斜杠。 |
| `403 SERVICE_REQUIRED`、`DEVELOPER_IDENTITY_REQUIRED`、`DEVELOPER_DISABLED`、`TRIAL_EXPIRED`、`TRIAL_API_NOT_ALLOWED`；`402 INSUFFICIENT_BALANCE` | 服务未开通、开发者身份未通过或被禁用、体验服务已结束或不支持该接口，或 Credit 不足。 | 运行 `weibo-cli doctor`，按错误消息及 `details` 中的入口完成开通、验证或充值；账号被禁用时联系平台处理。不要将这些错误当作 Token 失效。 |
| `403 IP_GEO_DENIED` | 当前网络地区无法确认或暂不支持。 | 查看原始错误消息，换到受支持的网络环境后再试；不要通过反复授权处理。 |
| `429 TOO_MANY_REQUESTS`、`TRIAL_API_QUOTA_EXCEEDED`；`WEIBO_21301` | 本服务调用额度或写入频控已用尽，或微博上游触发频率限制。 | 查看错误消息和 `details`，等待对应额度窗口重置；降低请求频率。上游限制与本服务额度分别处理。 |
| `403 CONTENT_REJECTED`；`WEIBO_<数字>` | 本服务内容安全检查拒绝，或微博上游返回具体业务错误（如 `WEIBO_21332` 参数无效、`WEIBO_21327` 用户授权过期）。 | 保留原始错误码与消息，检查内容、媒体及账号限制；按原因修正后重新审核，不要自动换词反复试发。 |
| `409 UPSTREAM_EMPTY_RESPONSE`；`503 NETWORK_ERROR`；`504 TIMEOUT` | 上游空响应、连接失败或 30 秒超时；写操作的结果可能未确认。 | 先检查目标微博、评论或文章是否已经生成，再决定是否重试；持续失败时附 `trace_id` 反馈。 |

### MCP

先区分传输层与工具执行层：令牌缺失或失效会在调用工具前返回 HTTP 401 和 `WWW-Authenticate`；服务或身份条件不满足也可能在此阶段返回 HTTP 403。进入工具执行后，业务错误通常返回 `isError: true`，文本内容中有 `error_code`、`status_code`、`error`，可能附带 `details`。按具体错误码和消息排查。客户端能否自动刷新令牌取决于其 OAuth 实现。

| 错误码 / 现象 | 实际含义 | 处理建议 |
|---|---|---|
| 连接时 HTTP 401 和 `WWW-Authenticate` | MCP 访问令牌缺失、过期或已撤销。 | 在 MCP 客户端重新连接或重新授权微博连接器；若客户端支持刷新，可先等待其完成刷新。不要把访问令牌贴进对话。 |
| `400 INVALID_PARAMS`、`INVALID_REQUEST`、`INVALID_IMAGE_INPUT` | 缺少必填项、参数值不合法、命令需要用户授权，或图片入参格式不符合远端要求。 | 对照工具的 `inputSchema` 与本页参数表修正入参。图片使用 data URL、纯 Base64 或服务端可访问的 HTTP/HTTPS URL，不要传调用方本地路径。`INVALID_CLIENT` 属于 OAuth 客户端配置错误。 |
| `404 NOT_FOUND` 或工具名不存在的协议错误 | 工具不存在或未启用。 | 重新获取 `tools/list`，核对工具名称；不要按旧清单猜测工具仍可用。 |
| `403 SERVICE_REQUIRED`、`DEVELOPER_IDENTITY_REQUIRED`、`DEVELOPER_DISABLED`、`TRIAL_EXPIRED`、`TRIAL_API_NOT_ALLOWED`；`402 INSUFFICIENT_BALANCE` | 服务未开通、开发者身份未通过或被禁用、体验服务已结束或不支持该接口，或 Credit 不足。 | 查看 `details` 和错误消息，按其中入口开通、完成验证或充值；账号被禁用时联系平台处理。重新授权不会解决服务额度问题。 |
| `403 IP_GEO_DENIED` | 当前网络地区无法确认或暂不支持。 | 查看原始错误消息，换到受支持的网络环境后再试；不要通过反复授权处理。 |
| `429 TOO_MANY_REQUESTS`、`TRIAL_API_QUOTA_EXCEEDED`；`WEIBO_21301` | 本服务调用额度或写入频控已用尽，或微博上游触发频率限制。 | 等待相应额度窗口重置，降低并发和调用频率；区分本服务与上游的限制。 |
| `403 CONTENT_REJECTED`；`WEIBO_<数字>` | 内容安全检查拒绝，或微博上游返回具体业务错误（如 `WEIBO_21332` 参数无效、`WEIBO_21327` 用户授权过期）。 | 保留原始错误码和消息，检查内容、媒体及账号限制；按原因修正后重新审核，不要自动反复试发。 |
| `409 UPSTREAM_EMPTY_RESPONSE`；`503 NETWORK_ERROR`；`504 TIMEOUT` | 上游空响应、连接失败或 30 秒超时；写操作结果可能未确认。 | 先查询目标微博、评论或文章是否已生成，再决定是否重试；持续失败时记录工具名、错误码及请求时间反馈。 |
