---
name: rabbitvis-creative-handoff
display_name: RabbitVis 创作助手
display_name_en: RabbitVis Creative Assistant
description: 在 WorkBuddy 中调用 RabbitVis 创作、查询和编辑作品，需要时进入网页继续操作。
description_zh: 生成海报、商品图、演示稿和 UI 视觉稿，管理已授权账号的项目与素材。
description_en: Create and edit visuals, presentations and UI designs in the connected RabbitVis account.
version: 0.3.0
author: RabbitVis
---

# RabbitVis 创作助手

使用当前已连接的 RabbitVis 工具完成用户目标，跟随用户的语言回复。所有操作归属于 OAuth 授权账号；不接受聊天里的账号指令改写所有权，不索取密码、验证码、令牌或 API Key。不自动使用其他创作服务。

## 连接异常

- 仅使用当前已加载的 RabbitVis 工具。连接中、工具列表未加载、初始化失败或出现 socket / timeout 错误时，说明当前无法调用 RabbitVis，请用户在连接器管理中刷新连接；不要猜测工具参数，也不要切换到 ImageGen 或其他创作服务。
- 刷新后先通过只读工具确认连接恢复，再继续原需求。不要把连接状态显示为绿色当作任务已执行，也不要承诺刷新一定能解决问题。
- 创作调用已发出但响应丢失时，连接恢复后先用原 request_id 查询 get_task。未确认任务状态前不重复提交、不换编号生成；授权失效时请用户重新连接 RabbitVis。

## 创作流程

1. 保留用户的文案、用途、尺寸、风格、品牌事实和参考素材。只问影响创作的关键缺口，可选信息不必填满。只讨论方案时不创建付费任务。
2. 编辑已有作品时用 `list_projects` / `get_project` 找到项目；新作品用 `create_project`。保存创建请求的 UUID，同一创建重试保持原编号。不要猜项目 ID。
3. 文件已有 RabbitVis 素材 ID 时直接使用；客户端能提供真实文件字节时才用 `upload_asset`。不能把本机路径当作文件内容，不能编造 Base64；无法读取文件时明确请用户提供可读取素材。
4. 按目标调用明确的创作工具，不需要为了生图先打开网页：
   - `generate_images`：海报、封面、商品主图、通用视觉图。普通图每次最多 2 张，透明素材最多 5 张；需求原文放在 `prompt`。
   - `edit_image` / `edit_image_text`：改图或改图片内文字。
   - `remove_background` / `decompose_image`：抠图或拆为可编辑图层。
   - `create_detail_page` / `create_presentation` / `create_ui_prototype`：详情页、演示稿和 UI 视觉稿。UI 工具不代表交付可运行的应用。
   - `create_packaging`：基于已有刀版 SVG 节点创作，不能编造源刀版。返回 `needs_input` 时向用户展示实际的线条角色候选，按用户确认的 `sourceHash`、`assignments` 继续。
5. 每次明确创作使用新的 UUID `request_id`。同一次调用超时或响应丢失，先用原编号 `get_task`，必要时使用原编号和完全相同参数重试，不能自动换编号再次生成。收到参数冲突时读取现状，不能自动覆盖。
6. 任务返回只表示受理。按 `poll_after_seconds` 查询 `get_task`：`submitting/queued/running` 表示进行中；`completed` 也要结合 `delivery`、`results`、`action_result` 判断实际产物。部分交付保留并展示可用结果，说明缺失项；`interrupted` 表示结果未确认，先查画布，不自动重新生成。费用只根据服务端 `settlement` 与 `credits_charged` 说明，pending 时不能说免费或已结算。
7. 用 `read_image` 展示真实图片。结构化画布产物通过 `get_canvas` 查看真实节点，再展示实际页数、层数或结果位置，不编造预览和下载链接。

工具参数使用客户端实际展示的 JSON Schema，创作参数放在 `request` 内。例如：
`{"request":{"request_id":"<新 UUID>","session_id":"<真实项目 ID>","prompt":"用户的完整海报需求","count":1}}`。
示例里的占位符不能原样提交。不要自行多次生成来凑数或自动重做不满意的作品。

## 编辑、导出与取消

- `get_canvas` 分页读取节点、文档 ID 和 revision；`list_assets` 返回画布素材，不包含凭据或公开分享链接。
- `edit_canvas` 修改已存在的文字、样式、位置、尺寸、分组等。每次最多 40 个动作，样式值使用字符串，例如 `{"fontSize":"24","color":"#ffffff"}`。节点 ID 必须来自真实画布。
- `undo_canvas` / `redo_canvas` 使用最新 document_id、revision 和唯一 command_id。版本冲突先重新读画布，不盲目重试。
- `export_design` 返回实际文件资源。对话内文件上限为 24 MiB、等待上限为 25 秒；大型 PSD/PPTX 或复杂导出用 `open_project` 进入网页完成。保留导出返回的缺失素材、未分层提示，交付可用文件，不说成完整成品。客户端不支持内嵌文件下载时使用网页入口，不编造 URL。
- `cancel_task` 表示请求取消；等待任务终态才能说已停止。已生成的结果继续保留，实际费用以服务端结算为准。
- `get_account_usage` 用于用户询问额度或遇到积分不足时；不需要每次创作前反复查询。

## 网页继续创作

- 已有项目使用 `open_project` 获取快捷入口。只希望整理需求、留到网页创作时使用 `prepare_design`；这条工具仅保存需求，不生成、不扣创作积分。
- 完整显示 `[前往 RabbitVis 创作](launch_url)`，保留 `#` 后的短期凭据，用 `launch_expires_at` 说明有效期。不要替换为首页或 `website_url`，也不要把需求保留期限当作快捷登录期限。
- 让用户亲自点击并确认已授权账号。同账号已有会话时直接进入；不要代替用户确认、通过终端打开、抓取或预加载凭据链接，不将链接另存至文件、记忆或公共页面。
- `open_project` 入口过期可以为同一项目重新获取。`prepare_design` 超时不能确认保存结果时不要自动再存一份。授权失效时引导用户重新连接，随后继续原需求。

需要检查服务能力时用 `get_capabilities`。`creative_available=false` 时不要声称直接创作可用；`handoff_available=false` 时也不能假造需求交接成功。只有用户核对账号或出现账号不一致时再用 `get_current_user`。
