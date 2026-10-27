---
name: 3chat-customer-growth
description: 当用户首次使用或需要检查 3Chat 启动配置，或者需要查询、筛选、分析客户、对话和群聊，查看沟通上下文，准备或发送已明确确认的消息、附件及已审批 WhatsApp 模板，或者跟踪批量发送任务时使用。可以检查并引导尚未完成的启动里程碑，但不能代替用户配置 3Chat Agent 或渠道。
---

# 3Chat 私域客户运营

当用户需要获得 3Chat 启动引导、查询客户、筛选对话、分析客户或群聊沟通内容、准备触达内容、执行已确认的消息发送，或者查询批量任务进度时，使用本技能。

## 依赖

本技能依赖名称为 `3chat-customer-growth` 的 MCP Server。只使用服务当前提供的工具和实时 `inputSchema` 中存在的参数。

## 可用工具

### 空间初始化状态

- `get_3chat_workspace_readiness`：读取当前 3Chat 空间是否曾经完成两个启动里程碑：发布过一个 3Chat Agent，以及连接过一个消息渠道。该工具不报告当前运行状态，也不会创建或修改任何配置。

### 查询与分析

- `search_3chat_customers`：按标准字段、自定义字段、最后一次咨询时间等条件查询客户。
- `search_3chat_conversations`：按条件分页查询单聊或群聊对话实体，不返回消息明细。
- `search_3chat_customers_context`：批量查询客户会话记忆、对话、消息、自定义字段和可选 `agent_logs`。
- `search_3chat_groups`：查询群聊群组、渠道接入账号和可选群成员。
- `search_3chat_groups_context`：批量查询群聊会话记忆、对话、消息、自定义字段和可选 `agent_logs`。

### 附件与发送

- `upload_3chat_attachment`：没有合法最终下载直链时，将文件上传到 3Chat。
- `send_3chat_customer_message`：向一个指定客户发送消息。
- `send_3chat_customer_messages`：批量向多个指定客户发送消息。
- `send_3chat_customer_whatsapp_template`：通过 YCloud 向客户发送已审批的 WhatsApp 模板。
- `send_3chat_group_message`：向一个指定群聊群组发送消息。
- `send_3chat_group_messages`：批量向多个指定群聊群组发送消息。
- `get_3chat_batch_send_status`：查询客户或群聊批量发送任务的进度及逐项结果。

## 启动检查与引导

启动检查用于识别当前 3Chat 空间是否需要首次配置引导，不是客户查询、上下文分析或消息发送的业务前置校验。

- 当前对话首次处理 3Chat 启动或使用引导请求（例如“我如何进行客户运营？”），或者依赖 3Chat 数据或操作的请求，且尚无当前空间的成功检查结果时，调用 `get_3chat_workspace_readiness`。概括性的使用咨询不需要先查询客户或对话。
- 返回 `READY` 时，在当前对话中复用结果，不主动展示启动引导，并继续处理用户原始请求。不得据此声称 Agent 或渠道当前可用。
- 返回 `ACTION_REQUIRED` 且本对话尚未展示对应引导时，先阅读 [references/startup-readiness.md](references/startup-readiness.md)，再回答：
  - 两个里程碑都未完成：依次说明 3Chat 的客户接待能力，以及发布 Agent、接入渠道如何帮助办公智能体(WorkBuddy)开展客户运营；给出创建并发布 Agent、接入消息渠道、体验真实接待三个步骤；最后说明可以返回 **3Chat 搭建助手**持续优化。上述内容均须包含，可以简洁表达，不得只列缺失项和配置链接。
  - 只有一个里程碑未完成：只展示对应的缺失步骤。
  - 使用 `data.actions[]` 中匹配 action 的原始 URL；具体匹配方式见参考文件。
- 国内启动引导需要举例消息渠道时，优先使用企业微信、小红书、抖音，不默认举例 WhatsApp 等海外渠道；用户明确指定渠道时按其选择引导，实际接入选项以 3Chat 渠道页面为准。
- 对用户介绍搭建功能时，统一称为 **3Chat 搭建助手**，名称加粗，不使用 Builder 或 Agent Builder；用自然的业务说明连接能力介绍和操作步骤，不输出“自然衔接一下”等写作指令。
- 展示配置步骤前，阅读 [references/guidance-widget.md](references/guidance-widget.md)。按有序列表逐步输出文字说明，每个有跳转入口的步骤紧接一个独立的链接组件；组件不替代步骤文字，也不把所有步骤合并成一张卡片。宿主组件能力不可用时，在对应步骤下改用 Markdown 链接。
- 展示 `ACTION_REQUIRED` 后，用户在后续回合再次提出依赖 3Chat 数据或操作的请求时，应重新检查，即使用户没有明确说明已经完成配置。
- 用户明确表示完成了缺失步骤，或者要求查看、刷新启动进度时，立即重新检查。
- 用户明确表示暂不配置或跳过时，不再主动重复检查或展示引导，直到用户报告配置进展、要求刷新，或者切换 3Chat 空间。
- 同一个助手回合或同一条多工具业务链路中，最多执行一次启动检查。

重新检查后：

- 里程碑有进展时，只展示仍然缺失的步骤。
- 状态变为 `READY` 时，简短告知用户初始配置已经完成，然后继续处理当前请求。
- 缺失状态没有变化时，不重复完整产品介绍，只做一句简短提醒，并在可能的情况下继续处理原始请求。
- 检查失败不代表里程碑未完成。按照结构化错误处理；临时检查故障不得直接解释为客户查询、上下文查询或消息发送不可用。

仅询问产品概念或功能、不使用 3Chat 数据的内容撰写，以及已经取得明确 `job_id` 的批量任务状态查询，不需要启动检查；询问如何开始使用 3Chat 开展客户运营属于启动引导请求。客户或对话查询为空，不能证明启动里程碑未完成。

## 核心规则

1. 按照上文“启动检查与引导”决定是否检查及如何响应；需要展示启动引导时，先阅读 [references/startup-readiness.md](references/startup-readiness.md)。
2. 先判断请求是初始化检查或引导、查客户、查对话、读取完整上下文、分析、拟定内容、发送消息还是查询任务状态，再选择工具。
3. 后续工具需要 ID 时，使用查询结果中的 3Chat 标准 ID；不得用展示名称、外部群 ID、`imRoomId` 或 `im_bot_id` 代替。
4. 查询名单、统计、比较和分析可以保留多个结果。只有下一步必须操作一个对象且用户目标不明确时，才要求用户选择。
5. 拟定文案、分析对象或筛选名单不代表用户同意发送。调用任何发送工具前，展示接收对象、发送账号或渠道、最终内容、附件和发送范围，并取得明确确认。
6. 附件已有无需鉴权、无需自定义请求头、非预览页且不依赖跳转的最终 HTTP/HTTPS 下载直链时直接使用；否则调用 `upload_3chat_attachment`，并优先复用完整 `data.attachment`。
7. 批量任务返回 `job_id` 后，只调用 `get_3chat_batch_send_status` 查询原任务，不重新提交整批。
8. 遵循结构化错误字段和 `aiAction`。发送请求只有在明确返回 `RETRY_SAME_REQUEST` 时，才能使用完全相同的业务参数和原幂等键重试。
9. 默认不向用户展示原始 JSON。回答中区分 3Chat 返回事实与模型推断，不把 `accepted`、`sent` 或 `COMPLETED` 表述成渠道已送达或客户已读。

## 详细规则

- 启动里程碑、引导方式和重新检查条件：阅读 [references/startup-readiness.md](references/startup-readiness.md)。
- 客户筛选、`query_mode`、`last_visit_time` 和客户历史聊天上下文：阅读 [references/customer-intelligence.md](references/customer-intelligence.md)。
- 直接查询对话以及对话字段含义：阅读 [references/conversation-search.md](references/conversation-search.md)。
- 群聊查询、群成员和发送账号：阅读 [references/group-operations.md](references/group-operations.md)。
- 上传附件或发送任何消息前：阅读 [references/outbound-messaging.md](references/outbound-messaging.md)。
- 返回无数据、部分结果、错误或需要重试时：阅读 [references/errors-and-retries.md](references/errors-and-retries.md)。
- 需要参考端到端业务流程和工具选择示例时：阅读 [references/use-cases.md](references/use-cases.md)。

## 当前能力边界

只使用上述 13 个对外工具。可以读取启动里程碑并引导用户，但不得声称本插件能够配置 Agent 或连接渠道。不得把启动状态作为 Agent 或渠道当前可用的证明；当前业务可用性由实际调用的业务工具校验。不得调用内部渠道查询工具，也不得声称能够更新客户资料、创建营销活动或定时任务、配置 3Chat Agent、配置渠道，或者执行当前工具没有提供的服务端聚合分析。
