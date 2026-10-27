---
name: dop-platform-mcp
display_name: DOP 数字运营平台
display_name_en: CSCEC DOP Platform
description: 通过 DOP 平台 MCP 工具查询用户信息与所属组织、申请角色权限、查询角色详情与权限现状、查看待办/已办任务与消息；首次使用需企微扫码登录。触发词：DOP、登录、待办、已办、消息、角色申请、组织、菜单权限、权限现状。
description_zh: 通过 DOP 平台 MCP 工具查询用户信息与所属组织、申请角色权限、查询角色详情与权限现状、查看待办/已办任务与消息；首次使用需企微扫码登录。
description_en: Query user info and organizations, apply for role permissions, inspect role details and permission status, and manage tasks and messages on the CSCEC DOP platform via natural language. First-time use requires WeCom QR code login.
version: 1.0.0
author: 中建三局
---

# DOP 数字运营平台 MCP 使用说明

工具按**业务场景**分组，共用**扫码登录**鉴权。新增场景时在此追加子章节。

**执行原则（所有场景）：每轮提问在前置数据就绪后立即发起，并把该时机所有已可问的收集/确认项合并进同一次提问（开放输入与选择可混排）；用户初始请求已说明的信息不再重复问，提交/执行前的最终确认一轮完成。向用户要开放输入（关键词/理由等自由文本）时，问题文本附示范说法（不是预设候选，不得据此代答）；选项类提问无需示例。凡候选/开放项：用户已指明或仅一个候选则不问，不得代选、代编；选项一律取真实返回结果或显式「用户自行输入」。**

**通用约定（所有场景）：ID/编码类入参（orgId/roleId 等）一律取自前置节点返回的清单或结果，字符串原样透传，不得转数字、不得编造。**

## 公共场景：认证

登录：企微扫码 `portalQrLoginStart → portalQrLoginWait`（展示 `loginUrl` 给用户扫码）；token 有效则跳过。

- 失效处理：任何工具返回 `HTTP 401/402` 或 `token 无效` → 重新扫码登录（`portalQrLoginStart` → `portalQrLoginWait`）→ 重试原操作。
- 当前组织与所属组织清单见 `getUserInfo`（orgs 字段）；切换组织见「场景：切换组织」（`switchOrg`）。
- 门户/预览链接（viewUrl、portalUrl、noticeCenterUrl、预览 html 等）：说明用途后告知用户，可询问是否打开；若 DOP 门户未登录，需登录后再打开

## 场景：切换组织

| 节点 | 动作 | 说明 |
|---|---|---|
| 1 查清单 | switchOrg(不带 orgId) | 返回当前组织 + 可切换组织清单(`orgs[].orgId`/`name`) |
| 2 选组织 | — | **用户未指定目标组织时才问**(从清单中挑) |
| 3 切换 | switchOrg(orgId) | 返回切换后实际生效组织 `currentOrg`;消息里同时带最新组织清单,便于再次切换/回切 |

规则：
- 目标 `orgId` 必须来自节点 1 返回的清单
- 切换成功后，后续所有工具自动按新组织执行，无需重新扫码
- `switched:false` 且带清单为查询模式；`currentOrg` 与目标不一致时 `message` 会明确说明（此时建议重新扫码）

## 场景：角色权限申请、权限自助申请、申请角色

调用链：`portalQrLoginStart → portalQrLoginWait → getUserAuthInfo → 一次问全（组织/角色/审批范围/理由） → 搜索验证（角色、审批范围） → 提交`

| 节点 | 动作 | 说明 |
|---|---|---|
| 1 登录 | portalQrLoginStart → portalQrLoginWait | token 有效则跳过 |
| 2 用户信息 | getUserAuthInfo | 得可申请组织 `userOrgList`、各组织已有角色 `userHasRoleList` |
| 3 收集申请信息 | — | 一次性向用户问全所有缺的项：①申请组织——orgId 从 `userOrgList` 中选（仅一个组织则不问）；②要申请的角色；③是否设置审批范围——选「不设置」则不参与流程审批，要设置则给审批组织名称/简称；④申请理由——用户提供；提问时附下方**用户要求示例** |
| 4 搜索验证 | 角色: 4a getRolesByKeyword(roleName ≥4 字) / 4b getMenuByKeyword(keyword ≥3 字) → getRolesByMenuId;③要设置: getOrgByKeyword(orgId=节点3所选组织) | 按节点 3 的回答搜索，候选唯一直接采用、不唯一让用户挑（审批范围挑后得 `[{orgId,orgName}]`）；③不设置则跳过该搜索 |
| 5 提交 | submitRoleApply | 提交前一轮最终确认（文案见下方**节点 5 最终确认示例**）。回 `{success,billId,viewUrl}`：向用户回报申请单号并附 `viewUrl`；随后询问是否打开查看，需要则 `open` 打开 |

**节点 3 用户要求示例**（用户没一次说全时补问中附给用户，引导其说清四项；A=带审批范围，B=不带，C=菜单权限式）：

您可以这样一句话提诉求：

1. 示例A："我要申请一公司的产品经理角色，审批范围为一公司，原因是：岗位调整"
2. 示例B："我要申请xx公司xx项目的项目经理，不需要审批范围，原因是：xxxx"
3. 示例C："我要申请“数字会议”的菜单权限，原因是：项目用车需要"

**节点 5 最终确认示例**：「确认提交吗？申请组织：X；角色：X；审批范围：X（未设置则注明「不设置，不参与流程审批」）；申请理由：X」

规则：
- 节点 3 选出的申请组织 `orgId` 即节点 5 `submitRoleApply` 的入参
- 分支 a 的 `roleName`、分支 b 的 `keyword` 过短会被直接拒（不发起后端查询）
- ③仅搜索候选依赖①，提问并入①②④一次问完；候选挑选在搜索之后

## 场景：查询角色信息

| 步骤 | 工具 | 入参 | 用途 |
|---|---|---|---|
| 1 | getRoleDetail | roleId | 查询角色详细信息（角色编码/类型/状态/所属组织/分组等），并生成角色权限清单预览 html |

规则：
- `roleId` 来自 `getRolesByKeyword`（分支 a）、`getRolesByMenuId`（分支 b）、`getUserAuthInfo.userHasRoleList` 等
- `roleId` 为 19 位数字字符串
- 返回 `rolePermPreviewURL` 为角色权限清单 html（布局同「查询当前权限现状」）；角色无所属组织时为空

## 场景：查询用户的待办/已办

| 步骤 | 工具 | 入参 | 用途 |
|---|---|---|---|
| 1 | getTaskList | type, keyword?, page?, size? | 查询当前用户的待办(type=1)或已办(type=2)任务列表；待办 `completeTime` 为空，已办带 `completeTime` |

规则：
- `type` 必填：1-待办 2-已办
- `keyword` 模糊匹配标题/发起人
- **分页**：返回含 `maxPage`（总页数）；`page < maxPage` 时翻页，保持 `type`/`keyword`/`size` 不变、`page`+1（size 最大 100）
- 查询结果展示后，通知用户可前往返回体中的 `portalUrl`（门户工作台）查看待办/已办

## 场景：查询消息列表

| 步骤 | 工具 | 入参 | 用途 |
|---|---|---|---|
| 1 | getMessageList | status?, keyword?, page?, size? | 查询当前用户的消息列表（分页） |

规则：
- `status` 可选：0-未读 1-已读，不传查全部
- `keyword` 模糊匹配标题/发送人
- **分页**：同「查询用户的待办/已办」，保持不变的参数为 `status`/`keyword`/`size`
- 查询结果展示后，通知用户可前往返回体中的 `noticeCenterUrl`（门户消息中心链接）查看消息详情

## 场景：查询当前权限现状

| 节点 | 动作 | 说明 |
|---|---|---|
| 1 用户信息 | getUserAuthInfo | 得可申请组织 `userOrgList`、各组织已有角色 `userHasRoleList` |
| 2 选组织 | — | **仅当用户未指定时在本节点问**想看哪个组织下的权限点；orgId 从 `userOrgList` 中选 |
| 3 查权限 | getUserPerms | 生成该组织下权限清单 html，返回摘要 + url |

规则：
- 返回 `url` 为本地 html 文件：左侧应用 tab，右侧权限树，已拥有节点 ✓ 高亮，暂未拥有 ○ 标记
- 对话只展示摘要（应用数/权限总数/已拥有数），细节看 html

## 工具速查

| 工具 | 说明 | 入参要点 |
| --- | --- | --- |
| `help` | 本使用说明 | 无 |
| `portalQrLoginStart` | 发起企微扫码登录，返回 `loginUrl`（展示给用户用企业微信扫码） | timeoutSeconds 可选（30~300，默认 120） |
| `portalQrLoginWait` | 等待用户扫码，确认登录结果（token 有效则直接成功） | timeoutSeconds 可选（30~300，默认 120） |
| `getUserInfo` | 当前用户信息 + 登录态（`loggedIn`）+ 当前组织 + 所属组织清单 `orgs[]` | 无 |
| `getUserAuthInfo` | 用户基本信息 + 所属组织（各组织已拥有角色 `userHasRoleList`）——角色申请/权限现状的起点 | 无 |
| `switchOrg` | 查询可切换组织清单 / 切换当前组织 | orgId 可选（不传=查询清单，返回 `switched:false`） |
| `getOrgByKeyword` | 按关键字查组织清单（不传 orgId 时限定当前登录组织） | keyword ≥3 字；orgId/page/size 可选 |
| `submitRoleApply` | 提交角色权限申请并发起审批流程，回 `{success,billId,viewUrl}` | roleId/roleName/orgId/remark；审批范围 scope 可选 |
| `getRolesByKeyword` | 按关键字查询角色 | roleName ≥4 字 + orgId |
| `getMenuByKeyword` | 菜单关键字分页查询（含所属应用） | keyword ≥3 字；page/size 可选 |
| `getRolesByMenuId` | 菜单 ID 查包含该菜单的角色 | menuId（19 位数字字符串）；page/size 可选 |
| `getRoleDetail` | 角色详情（编码/类型/状态/所属组织）+ 角色权限清单预览 html（`rolePermPreviewURL`） | roleId（19 位数字字符串） |
| `getUserPerms` | 组织下权限清单：摘要（应用数/权限总数/已拥有数）+ 本地 html（`url`） | orgId（来自 getUserAuthInfo.userOrgList） |
| `getTaskList` | 待办(1)/已办(2)任务列表 | type；keyword/page/size 可选 |
| `getMessageList` | 消息列表(0 未读/1 已读，不传全部) | status/keyword/page/size 可选 |