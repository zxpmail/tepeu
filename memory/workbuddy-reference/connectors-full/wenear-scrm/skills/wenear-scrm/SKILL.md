---
name: wenear-scrm
description: 鹄得 SCRM 连接器。查询或管理企业微信中的客户信息、客户标签、客户群（含群标签、群继承、退群记录、群欢迎语）、素材库（查询与公众号文章同步）、活码（渠道码/群活码/一键拉群）、群发（好友群发与企微群群发）、朋友圈、加好友任务、删人提醒、聊天关键词规则、会话标签规则（自动打标）、话术库等业务能力。素材中除文章可由同步产生外，图片/链接/视频/文件/小程序均需在鹄得后台创建。客户名单导出与结构分析报表由「鹄得 SCRM 客户分析报表」技能配合完成。当用户提到 SCRM、企微客户、客户标签、客户群、群标签、群欢迎语、素材库、公众号文章、渠道码、群活码、一键拉群、群发、朋友圈、加好友任务、删人提醒、退群记录、关键词规则、自动打标、会话标签、话术库、成员 userId 时使用。
---

# 鹄得 SCRM 使用说明

通过 MCP 工具查询或管理企业微信中的客户信息、客户标签、客户群、素材库、活码、
群发、朋友圈、加好友任务、删人提醒、聊天关键词规则、会话标签规则（自动打标）、
话术库等业务能力。

## 可用工具（共 56 个，按业务域分组）

### 客户与成员

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `list_members` | 查询在职成员、按昵称解析 userId | 否 |
| `count_follow_customers` | 按条件统计跟进客户数量 | 否 |
| `filter_customers` | 按条件筛选客户明细，单次最多 100 条，返回 `relationId` | 否 |

### 企微群管理

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `count_my_customer_groups` | 统计**我作为群成员所在**的客户群数量 | 否 |
| `list_my_customer_groups` | 查询**我所在**的客户群列表（仅条数限制，最多 100，不筛选不分页） | 否 |
| `list_work_wx_groups` | **租户内全部客户群**列表（可筛选 + 分页，含客户数/今日进出/群主/标签/管理员） | 否 |
| `get_work_wx_group` | 单个群详情（总人数、客户数、今日入群/退群、群主、群标签、管理员 + 群成员分页） | 否 |
| `export_work_wx_groups` | 按条件导出企微群（xlsx base64），**全量导出用这个** | 否 |
| `list_group_exit_logs` | 查询退群记录（分页） | 否 |
| `export_group_exit_logs` | 按条件导出退群记录（xlsx base64） | 否 |
| `search_work_wx_group_tags` | 搜索群标签（默认最多 50 个，`unlimited: true` 不限） | 否 |
| `add_work_wx_group_tags` | 给企微群添加群标签（已解散的群跳过，已存在的标签自动跳过） | **是** |
| `remove_work_wx_group_tags` | 删除企微群上的群标签 | **是** |
| `sync_work_wx_groups` | 触发企微群同步（异步，结果稍后在群数据中可见） | **是** |
| `transfer_work_wx_groups` | 企微群在职分配（换群主） | **是** |
| `update_group_exit_notice` | 退群通知开关（分别通知群主 / 群管理员） | **是** |
| `create_group_welcome_template` | 创建企微群欢迎语（文本/图片/图文/文件/视频/小程序） | **是** |
| `create_group_mass_message` | 创建**企微群**群发（按群 `chatIds` 或群主 `ownerUserIds`） | **是** |
| `create_one_bond_pull_group` | 创建一键拉群 | **是** |
| `list_one_bond_pull_groups` | 查询一键拉群列表（分页） | 否 |
| `delete_one_bond_pull_group` | 删除一键拉群（逻辑删除） | **是** |

### 素材库

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `list_media_categories` | 查询素材分类列表（含分类下素材数量） | 否 |
| `list_media` | 查询素材列表（分页，按分类/名称/类型筛选） | 否 |
| `sync_essay_media` | **文章素材的唯一来源**：同步已授权公众号文章到指定分类 | **是** |
| `get_media_sidebar_config` | 查询素材库企微侧边栏开启状态 | 否 |
| `set_media_sidebar_config` | 开关素材库企微侧边栏 | **是** |

> ⚠️ **素材不能通过本连接器创建**：服务端未提供 `upload_media` /
> `create_media` / `create_media_category`。**图片 / 链接 / 视频 / 文件 /
> 小程序**素材均需前往**鹄得 SCRM 后台**创建；**文章**素材只能由
> `sync_essay_media` 从已授权公众号同步产生。详见规则 7。

### 群发与朋友圈

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `create_mass_message` | 创建**好友**群发（发给客户个人） | **是** |
| `send_moments` | 创建并发送企业微信朋友圈（发到客户朋友圈） | **是** |
| `list_moments_records_with_details` | 查询朋友圈发表记录（含发表成员与可见客户明细，重查询） | 否 |

### 活码

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `create_gather_code` | 创建渠道码 | **是** |
| `create_group_code` | 创建群活码（`codeType=1` 企微群活码，需 `groupIds`） | **是** |

### 加好友任务与提醒

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `get_friend_task_import_template` | 获取加好友任务导入模板（列名约束 + xlsx base64） | 否 |
| `import_friend_task` | 导入加好友任务（Excel base64，按手机号分配添加人） | **是** |
| `update_unfriend_attention_notice` | 开关删人提醒（成员删客户 / 客户删成员） | **是** |

### 聊天关键词规则（会话内容监控）

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `list_chat_keyword_rule_categories` | 查询关键词分类列表（分页，按名称模糊筛选） | 否 |
| `create_chat_keyword_rule_category` | 创建关键词分类（名称 1-20 字） | **是** |
| `update_chat_keyword_rule_category` | 修改关键词分类名称 | **是** |
| `delete_chat_keyword_rule_category` | 删除关键词分类（**分类下还有规则时会失败**） | **是** |
| `list_chat_keyword_rules` | 查询关键词规则列表（分页，按名称/分类筛选） | 否 |
| `create_chat_keyword_rule` | 创建关键词规则（`categoryId` + `name` 必填） | **是** |
| `update_chat_keyword_rule` | 修改关键词规则（`id` + `categoryId` + `name` 必填） | **是** |
| `delete_chat_keyword_rule` | 删除关键词规则 | **是** |
| `list_chat_keyword_rule_whitelists` | 查询关键词白名单成员（分页） | 否 |
| `add_chat_keyword_rule_whitelist` | 新增白名单成员（已在白名单自动跳过） | **是** |
| `remove_chat_keyword_rule_whitelist` | 移除白名单成员 | **是** |

### 会话标签规则（自动打标）

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `list_all_tags` | 查询全部企微标签（按分组返回，可按名称/状态筛选） | 否 |
| `list_chat_tag_rules` | 查询会话标签规则列表（含关联关键词规则与绑定标签） | 否 |
| `create_chat_tag_rule` | 创建会话标签规则：关键词规则 + 至少 1 个企微标签 | **是** |
| `delete_chat_tag_rule` | 删除会话标签规则 | **是** |
| `list_chat_tag_rule_logs` | 查询打标记录（被自动打标的客户、所打标签、触发规则） | 否 |

### 话术库

| 工具 | 用途 | 是否写操作 |
|------|------|-----------|
| `list_routine_types` | 查询话术分类列表（含分类下话术数量，分页） | 否 |
| `create_routine_type` | 创建话术分类（名称不可重复，≤10 字） | **是** |
| `list_routines` | 查询话术列表（按标题/内容关键字、分类筛选） | 否 |
| `create_routine` | 创建话术（标题 ≤10 字、内容 ≤1000 字、`typeId` 必填） | **是** |
| `update_routine` | 修改话术（未传字段保持不变） | **是** |
| `delete_routine` | 删除话术（逻辑删除） | **是** |

跟进记录、商机、**聊天记录原文**等能力**当前服务端未提供对应工具**，
用户提出这类需求时如实告知暂不可通过本连接器完成，不要臆造参数调用。
注意区分：聊天**关键词规则**只是对会话内容做关键词监控与自动打标，
**不等于可以查询聊天记录原文**。

## 核心规则（务必遵守）

### 1. 人名必须先解析成 userId

所有涉及员工的参数（负责人、接待员工、群主、通知接收人、群成员）只接受
**userId**，不接受昵称或姓名。业务描述里给的是人名时，第一步永远是调用
`list_members` 解析：

- 先 `list_members` 拿到成员列表，再按昵称匹配。
- **若一个昵称命中多人，必须让用户确认是哪一位，不要自行猜测。**

### 2. 不传负责人时只统计当前用户

`count_follow_customers`、`filter_customers` 在未指定负责人时，
**默认只统计当前令牌持有者名下的客户**。想查全公司数据，
必须先 `list_members` 取全部成员，再用负责人参数逐个指定。
`send_moments` 的 `customerEmployeeIds`、`create_mass_message` 的
`customerOwnerUserIds` 同理。

### 3. 分页有两套，先看清工具支持哪种

- **仅条数上限、无游标翻页**：`filter_customers`（`maxResults` 默认 20、最大 100）、
  `list_members`（最大 100）、`list_my_customer_groups`（最大 100，且不返回总数）。
  超过上限时按维度分片拉取、去重合并（客户按添加时间：稀疏用月粒度、密集用周、
  日增超 100 用日）。**某分片正好返回上限条数时可能被截断**，应改细粒度重拉并向
  用户说明；不要用「多翻几页」的思路硬凑。
- **`pageNum` / `pageSize` 游标分页**：`list_work_wx_groups`、
  `list_group_exit_logs`、`export_group_exit_logs`、`list_one_bond_pull_groups`、
  `list_moments_records_with_details`（`pageSize` 最大 **50**）、`list_media`、
  `list_routine_types`、`list_routines`、`list_chat_keyword_rule_categories`、
  `list_chat_keyword_rules`、`list_chat_keyword_rule_whitelists`、
  `list_chat_tag_rules`、`list_chat_tag_rule_logs`（一般 `pageSize` 最大 100）。
  用翻页取全量。

### 4. 时间格式两套，别混用

- **客户筛选**（`filter_customers` / `count_follow_customers` 及群发、朋友圈的
  客户筛选）：`yyyy-MM-dd`，结束日含当天。
- **群与记录类**：`yyyy-MM-dd HH:mm:ss`。其中
  `groupCreateTime`（群创建时间）、`leaveTimeRange` / `joinTimeRange`（退群记录）、
  `createTimeRange`（一键拉群）**必须传 2 个值**（起、止）；
  `sendTime`（定时发送）为单个值。

### 5. 企微群数据有四个入口，先分清要查什么

| 你要查的 | 用哪个 | 数据范围 |
|---|---|---|
| 「我」所在的群（数量/列表） | `count_my_customer_groups` / `list_my_customer_groups` | **仅我作为群成员所在、且我仍在群内的群**，不含我未加入的群 |
| 租户内**全部**客户群（可筛选、分页） | `list_work_wx_groups` | 全部群；可按群名/群主/管理员/群状态/标签/创建时间筛选 |
| 单个群的**详情与成员** | `get_work_wx_group` | 群总人数、客户数、今日入群/退群、群主、群标签、管理员 + 群成员（分页） |
| 全量**导出成文件** | `export_work_wx_groups` | 全量或按条件，返回 xlsx base64 |

- 「我」类的两个工具**口径一致**（只算我作为群成员所在、且我还在群里的群）。
  `list_my_customer_groups` **不支持按群名/群主/标签/群状态筛选**，也不分页，
  按我入群时间倒序，返回群 ID、群名、群主 ID、群人数、群内好友数、群状态、
  群创建时间、我入群时间、我是否群管理员。要筛选或要分页，改用
  `list_work_wx_groups`。
- `export_work_wx_groups` 不传条件即导出全部，数据量大时先加条件缩小范围；
  返回 xlsx 的 base64，解码落成本地文件后交付用户。

**群 `chatId` 的来源**：`get_work_wx_group`、`add_work_wx_group_tags`、
`remove_work_wx_group_tags`、`transfer_work_wx_groups`、
`create_group_mass_message` 都依赖 `chatId`，它只能来自
**`list_work_wx_groups`**、**`list_my_customer_groups`** 或
**`export_work_wx_groups` 的导出结果**，不要凭空构造或猜测。

若群群发确实拿不到 chatId，可改用 `ownerUserIds`（按群主名下全部群发送），
但影响范围更大，**必须先向用户确认**。

刚同步过的群查不到，见规则 8（同步是异步的）。

### 6. 筛选 → 触达 的标准链路（relationId）

`filter_customers` 返回的 **`relationId`（客户关系 ID）** 可直接作为
`create_mass_message` / `send_moments` 的 `customerRelationIds` 入参。
「给某类客户发消息」的标准做法：

1. `filter_customers` 按条件筛出客户，取出 `relationId`（必要时分片，单次 ≤100）
2. 用 `customerSelectionMode: "MANUAL"` + `customerRelationIds` 精确指定收件人
3. 复述内容与人数给用户，确认后提交

若用 `customerSelectionMode: "FILTER"`（默认）按条件圈选，同样先复述条件再提交。
FILTER 模式还可按标签（`includeTagIds` + `tagMatchMode`）、客户备注关键字、
添加时间、性别、客户群（`customerGroupChatIds`）圈选，并可用
`excludeTodaySentCustomers` 排除今天已发送过的客户。

⚠️ `relationId` 是 **19 位数字**（如 `1803008861094472728`），超过 JavaScript 安全
整数范围；`customerRelationIds` 要求**字符串数组**。传递时必须转成字符串，
不要用数字类型，也不要在经过数值运算后再转，否则精度丢失导致收件人错误。

### 7. 素材不能在连接器里创建：图片/链接/视频/文件/小程序去后台建，文章只能同步

**图片 / 链接 / 视频 / 文件 / 小程序**这五类素材**不能通过本连接器创建**——
服务端没有提供任何素材创建接口。用户提出「上传一张图片」「建一个文件素材」
「加一个链接素材」「新建小程序素材」这类需求时，如实说明：**这类素材需要在
鹄得 SCRM 后台创建**，并引导用户前往后台操作。

**文章**素材（`mediaType=4`）是唯一例外：它不能手动创建，只能通过
**`sync_essay_media`** 从**已授权微信公众号**同步产生。

素材库当前只能做三件事：

1. **查分类**：`list_media_categories`（含分类下素材数量）
2. **查素材**：`list_media`（分页，可按分类 / 名称 / 类型筛选）
3. **同步文章**：`sync_essay_media`

`sync_essay_media` 要点：

- `categoryId` **必填**（先用 `list_media_categories` 取）。
- **租户未授权公众号时接口静默不处理**——既不报错也不产生素材。同步后建议用
  `list_media` 复查；素材数没增加时，引导用户先在 SCRM 后台完成公众号授权。
- 素材类型 `mediaType`：1 图片 / 2 链接 / 3 视频 / **4 文章** / 5 普通文件 /
  6 小程序，其中**只有文章（4）能由同步产生**。

**不要绕路实现**——例如把图片塞进群发消息或活码里「当作素材」，或尝试调用
服务端未提供的 `upload_media` / `create_media` / `create_media_category`
（调用会返回 `Tool not found`）。

### 8. 异步与重查询，控制调用量

- `sync_work_wx_groups` 返回成功**仅表示同步任务已触发**，企微群数据要稍后才可见。
  不要在返回后立刻断言「已同步完成」，也不要连续重复触发；
  稍等后再用 `list_work_wx_groups` / `list_my_customer_groups` 复查。
- `list_moments_records_with_details` 每条记录会额外查一次成员、一次客户，
  记录越多查询越重：`pageSize` 控制（默认 10，最大 50），
  可见客户用 `customerPageSize` 控制（最大 100，默认 20），**不要一上来就拉全量**。

### 9. 写操作必须复述确认

写操作类工具会**真实创建资源、触达客户或修改服务端配置**，不可随意撤回。
调用前把关键参数复述给用户，确认后再提交：

- 渠道码：名称、接待员工、欢迎语类型与内容、有效期、自动加好友开关
- 群活码：名称、关联的企微群（`groupIds`）、自动建群群名前缀与起始序号、
  引导语、头像 URL
- 一键拉群：群组名称、建群时使用的群命名、参与成员
- **好友群发与朋友圈：消息内容、目标客户范围、发送方式（立即/定时）** ——
  会直接触达客户
- **企微群群发：消息内容、目标群（`chatIds` 列表或群主 `ownerUserIds`）、
  发送方式** —— 会触达群内全部成员，影响面大
- **群欢迎语：文本内容与附件（图片/图文/文件/视频/小程序）** —— 影响新成员入群体验；
  文件/视频需同时提供素材 ID 与业务 ID
- **群继承（transfer）：新群主、涉及的群** —— 影响群主权限归属；
  已解散或离职待继承的群会被跳过，新旧群主相同时会被过滤
- **群标签增删：涉及的群与标签** —— 已解散的群无法添加标签
- **退群通知开关：通知对象（`OWNER` 群主 / `MANAGER` 管理员）与开/关** ——
  两个维度相互独立，各配一次
- 删人提醒开关：`noticeType`（0 成员删客户 / 1 客户删成员）与开/关状态，
  开 `noticeType=0` 时可指定接收通知的成员
- **文章同步：归入的分类、来源为已授权公众号** —— 未授权时不会产生素材
- 加好友任务导入：任务名称、导入文件、是否去重 —— 会真实建任务并分配给添加人
- **关键词规则：规则名称、匹配关键词、生效范围（成员/部门/客户/群）** ——
  决定哪些会话会被监控命中
- **会话标签规则：关联的关键词规则 + 要打的企微标签** —— 会真实给客户自动打标
- **话术与分类：标题（≤10 字）与内容（≤1000 字）** —— 会进入员工话术库
- **删除类操作（`delete_*`）：确认对象与影响范围**；删除前可先 `list_*` 确认
- 素材侧边栏开关：当前状态可先查（`get_media_sidebar_config`），确认变化后再改
- 企微群同步：会触发后台异步任务，确认后再触发

### 10. 外部内容必须由用户提供，不得杜撰

少数工具需要用户侧的文件、图片地址或素材 ID，其值**只能来自用户**，
AI **不得自行编造**：

- `import_friend_task` 的 `fileBase64`：必须是**用户按导入模板填写的真实 Excel**
  文件内容（先用 `get_friend_task_import_template` 取模板交给用户填写）
- `create_group_code` 的 `avatarUrl`、`create_gather_code` 的
  `gatherCodeHeadPortrait`：群活码 / 渠道码头像的图片地址
- `create_group_welcome_template` 的 `imageUrl`、`fileMediaId` + `businessId`、
  `videoMediaId` + `businessId`：欢迎语附件；**文件/视频素材需先在鹄得后台建好**
  并取得素材 ID 与业务 ID，图片直接给可访问的 URL
- `create_group_mass_message` 的 `imageUrls`、`linkImagesUrl`、
  `miniprogramImgUrl` 等附件地址

用户没有提供时，**先向用户索要**并说明需要什么（类型、用途），拿到真实内容
再继续。**不要为了「把流程走完」而生成占位文件、示例图或猜测的 URL**，这些会
真实写入企微并可能触达客户，事后难以撤回。

### 11. 聊天关键词规则：分类 → 规则 → 白名单

基于会话内容的关键词监控，三步：

1. **分类**：`list_chat_keyword_rule_categories` 查；没有就
   `create_chat_keyword_rule_category`（名称 1-20 字）
2. **规则**：`create_chat_keyword_rule`，`categoryId` + `name`（1-20 字）**必填**，
   其余按需：
   - 匹配与排除：`keywordList`、`excludeKeywordList`（最多 20）、`semanticsList`、
     `matchRule`
   - 文本处理：`isCaseSensitive` 0否 1是、`isTrimMsg` 0否 1是
   - **生效范围**：`userIdList`（≤100）、`departmentIdList`（≤100）、
     `externalContactIdList`（≤100）、`chatIdList`（≤2000）、`msgScope`、
     `targetTypeList`、`chatTypeList`、`sessionTypeList`
   - **敏感信息排除**：`excludeMobileList`、`excludeBankCardList`、
     `excludeEmailList`（各 ≤100）——用于避免手机号/银行卡/邮箱被误命中
3. **白名单**：`add_chat_keyword_rule_whitelist`（`userIds`，已在白名单会自动跳过）
   / `remove_chat_keyword_rule_whitelist` / `list_chat_keyword_rule_whitelists`。
   **白名单成员不受关键词规则约束**，增删后立即刷新规则快照。

修改用 `update_chat_keyword_rule`（**`id` + `categoryId` + `name` 三个都必填**）；
删除用 `delete_chat_keyword_rule`。

⚠️ **删除分类前必须先删空其下规则**——`delete_chat_keyword_rule_category`
在分类下仍有规则时会直接失败。

### 12. 会话标签规则：关键词规则 + 企微标签 → 自动打标

把关键词规则与企微标签绑定，客户命中关键词后**自动打标**：

1. `list_chat_keyword_rules` 取关键词规则 ID（作为 `chatRuleId`）
2. `list_all_tags` 取企微标签 ID（可按 `tagName` / `tagState` 筛选，
   `tagState` 0 禁用 / 1 启用）
3. `create_chat_tag_rule`：`name` + `chatRuleId` + `tagIds`（**至少 1 个标签**）
4. 核查效果：`list_chat_tag_rule_logs`（`tagRuleId` 必填）——看哪些客户被自动
   打标、打了什么标签、由哪条关键词规则触发

即：客户在会话中说了关键词 → 命中关键词规则 → 自动给该客户打上绑定的企微标签。

### 13. 话术库：分类 → 话术

1. `list_routine_types` 查分类（含分类下话术数量）；没有就 `create_routine_type`
   （名称 ≤10 字，**不可重复**）
2. `create_routine`：`title`（≤10 字）+ `content`（≤1000 字）+ `typeId` **必填**；
   `typeName` 不传时按 `typeId` 自动查询
3. `update_routine`：`id` 必填，**未传入的字段保持不变**（只改要改的）
4. `delete_routine`：逻辑删除

⚠️ **`typeId` 与话术 `id` 必须用字符串传递**——接口明确要求「字符串形式，避免
大整数精度丢失」。这类 ID 是 19 位级大整数，与 `relationId` 同理，
用数字类型会精度丢失导致操作到错误的对象。

## 常用筛选参数

`count_follow_customers` 与 `filter_customers` 共用同一套条件；
群发与朋友圈的 FILTER 模式也基本对齐：

| 参数 | 说明 |
|------|------|
| 客户状态 | 1 未转化 / 2 无效 / 3 已转化 |
| 性别 | 0 未知 / 1 男 / 2 女 |
| 关键词 | 模糊匹配客户名称、备注、手机号 |
| 联系电话 | 模糊匹配 |
| 标签 | 包含/排除标签 ID，配合 `tagMatchMode`（1 任一 / 2 全部） |
| 添加时间范围 | yyyy-MM-dd，结束日含当天 |
| 负责人 | userId 列表，不传默认当前用户 |
| 来源 / 来源渠道 / 客户级别 | 按业务字段过滤 |

## 字段语义速查

- `customerStatus`：1 未转化 / 2 无效 / 3 已转化，空值表示未设置，展示为「未知」
- `gender`：0 未知 / 1 男 / 2 女
- **`relationId`：客户关系 ID，群发与朋友圈指定收件人的关键字段（19 位数字，传参需转字符串）**
- `tagIds`：返回 `{tagId, tagName}` 对象数组；**群标签**另用
  `search_work_wx_group_tags` 查（按标签分组返回，默认最多 50 个）
- `chatId`：企微群 ID，群详情 / 群标签 / 群继承 / 群群发的入参，只能从
  `list_work_wx_groups`、`list_my_customer_groups` 或
  `export_work_wx_groups` 的结果获取
- `groupStatus`：群状态，0 正常 / 1 跟进人离职 / 2 离职继承中 / 3 离职继承完成
- `mediaType`：1 图片 / 2 链接 / 3 视频 / 4 文章 / 5 普通文件 / 6 小程序；
  **只有 4 文章能由 `sync_essay_media` 同步产生，其余类型需在鹄得后台创建**
- `type`（群欢迎语模板）：1 文件 / 2 图片 / 3 链接 / 4 小程序
- `codeType`：群活码类型，`1` 企微群活码（需 `groupIds` + `groupNamePrefix`）
- `gatherCodeWelcomeType`：渠道码欢迎语类型，1 码欢迎语（`welcomeTexts` 必填）/
  2 默认欢迎语 / 3 不发
- `customerSelectionMode`：`MANUAL` 指定关系 ID / `FILTER` 按条件圈选
- `sendType`：0 立即发送 / 1 定时发送（需 `sendTime`，格式 `yyyy-MM-dd HH:mm:ss`）
- `noticeType`：0 成员删除客户（可指定 `notifyUserIds`）/ 1 客户删除成员；`onOff` 1 开 0 关
- `noticeTarget`（退群通知）：`OWNER` 通知群主 / `MANAGER` 通知管理员，与 `onOff` 各配一次
- `tagState`（`list_all_tags`）：0 禁用 / 1 启用
- `isCaseSensitive` / `isTrimMsg`（关键词规则）：0 否 / 1 是
- `typeId` / 话术 `id`：**必须用字符串传递**，避免大整数精度丢失（同 `relationId`）

## 导出与报表（与报表技能协作）

需要导出客户名单文件或生成可视化分析报表时，**优先配合
「鹄得 SCRM 客户分析报表」技能**（wenear-scrm-report，技能市场可安装）完成：

1. 用本连接器的 `filter_customers` 按时间分片取数，保存为 JSON 文件；
2. 调用该技能的 `report.py --json <数据文件> --out 报表.html` 渲染，
   或用其 `customers.py fetch` 直接全量拉取导出。

注意区分两类「导出」：

- **客户名单 / 分析报表** → 走报表技能（上述流程）
- **企微群列表 / 退群记录** → 本连接器的 `export_work_wx_groups` /
  `export_group_exit_logs`，返回 xlsx 的 base64，解码落成本地文件后交付用户

若用户未安装报表技能：小批量（≤100 条）可自行整理为 csv 交付；
大批量或正式报表建议引导用户先安装该技能。

## 排错

| 现象 | 原因与处理 |
|------|-----------|
| 401 / 403 | 令牌失效或缺失，引导用户重新连接连接器 |
| 接口返回 400 且响应体为空 | 请求头缺少 `Accept`，本连接器已在 mcp.json 中固定注入，如仍出现请反馈开发者 |
| 工具不可用或提示未启用 | 该能力服务端暂未开放，向用户如实说明 |
| 返回数量总是偏少 | 未传负责人（默认只查当前用户），或命中单次 100 条上限 |
| 「我的客户群」数量/列表与预期不符 | `count_*` / `list_my_customer_groups` **只含我作为群成员所在且我还在群内的群**；要看全公司群请用 `list_work_wx_groups` |
| 群列表查不全 / 需要筛选 | `list_my_customer_groups` 最多 100 条、不支持筛选也不返回总数，改用 `list_work_wx_groups` |
| 时间范围参数报错 | 客户用 `yyyy-MM-dd`；群/记录类用 `yyyy-MM-dd HH:mm:ss`，且 `groupCreateTime`、`leaveTimeRange`、`joinTimeRange`、`createTimeRange` 必须传 2 个值 |
| 群同步后数据没变 | sync 是异步任务，稍等后重查，不要连续重复触发 |
| 群标签加不上 | 已解散的企微群不能添加标签；群上已存在的标签会自动跳过，属正常 |
| 群在职分配被跳过 | 已解散或离职待继承的群不能分配，且新旧群主相同会被过滤 |
| 群群发提示 chatId 无效 | chatId 必须来自 `list_work_wx_groups` / `list_my_customer_groups` / 导出结果，不要自行构造；或改用 `ownerUserIds` 按群主发送 |
| 创建素材失败 / 找不到上传工具 | 图片/链接/视频/文件/小程序素材**不能通过连接器创建**，请到鹄得 SCRM 后台创建（规则 7） |
| 提示 `Tool not found: upload_media` / `create_media` / `create_media_category` | 素材创建接口**服务端未提供**，非本连接器能力（规则 7） |
| 同步文章后素材库没变化 | 租户**未授权微信公众号**，接口会静默跳过；先引导用户在 SCRM 后台完成公众号授权 |
| 群欢迎语用文件/视频失败 | `fileMediaId` / `videoMediaId` 需同时传 `businessId`，且素材必须已在后台建好 |
| 朋友圈记录查询很慢 | 该工具逐条附查成员与客户，把 `pageSize` 调小（默认 10，最大 50） |
| 昵称解析命中多人 | 让用户确认具体成员，勿自行选择 |
| 定时发送未生效 | `sendType=1` 时 `sendTime` 必填，格式 `yyyy-MM-dd HH:mm:ss` |
| 导入加好友任务失败 | 未按模板填写或 base64 有误，先重取模板再核对列名；文件必须是用户提供的真实 Excel |
| 删除关键词分类失败 | 分类下还有规则，先 `delete_chat_keyword_rule` 删空规则再删分类 |
| 修改关键词规则报错缺参数 | `update_chat_keyword_rule` 的 `id` + `categoryId` + `name` **三个都必填** |
| 创建会话标签规则失败 | `tagIds` 至少 1 个；`chatRuleId` 需先 `list_chat_keyword_rules` 取真实规则 ID |
| 话术分类创建失败 | 分类名称**不可重复**且 ≤10 字，先 `list_routine_types` 确认 |
| 操作了错误的话术 / 分类 | `typeId`、话术 `id` 未用字符串传递导致精度丢失，改传字符串 |
| 网络超时 | 单次请求上限 30 秒，缩小查询范围后重试 |

## 凭证与隐私

令牌由用户在连接器中填写，仅保存在本机，不上传云端。
默认服务地址为 `https://console.goodscrm.cn/api/mcp`；私有部署用户可在安装后编辑本机
`~/.workbuddy/mcp.json` 中 `wenear-scrm` 的 `url`（必须为 HTTPS）。
本 Skill 及连接器所有配置文件中**不得出现真实令牌、人名、userId、手机号等业务敏感数据**；
示例统一使用「张三」「李四」等占位昵称和 `<userId>` 形式的占位符。
