---
name: getnote-note
description: 按录音台或录音卡来源查找笔记；使用得到大脑 MCP 保存、浏览和读取笔记，查看原文、转写、附件、时间线、标记、发芽报告、快捷笔记和会议待办，并安全更新、删除或公开分享。标记、发芽与时间线分别读取。
description_zh: 按录音台或录音卡来源找笔记，保存和读取原文、转写、附件、标记、发芽报告、章节时间线、快捷笔记与待办，或安全管理笔记。
description_en: Use when finding station or recorder-card notes, saving or reading notes, transcripts, attachments, marks, sprout reports, chapter timelines, quick notes, meeting tasks, or safely managing notes.
version: "1.1.2"
author: GetNote
---

# 得到大脑笔记

所有笔记、任务和父笔记 ID 都是不透明字符串，必须原样传递。只在 MCP Tool 返回成功结果后确认操作完成。

## 工具路由

| 用户意图 | MCP Tool |
|---|---|
| 浏览最近笔记 | `list_notes` |
| 查看笔记详情 | `get_note` |
| 读取链接或文字原文 | `get_note_original` |
| 读取录音、会议或课堂转写 | `get_note_transcript` |
| 查看图片、音频和文件附件 | `get_note_attachments` |
| 查看录音/会议时间线 | `get_note_timeline` |
| 查看总结中的章节时间点 | `get_note_chapters` |
| 查看笔记标记 | `get_note_marks` |
| 按月列出发芽报告 | `list_sprouts` |
| 查看发芽正文 | `get_sprout` |
| 查看录音快捷笔记 | `get_note_quick_note` |
| 查看会议总结中解析的待办 | `get_note_todos` |
| 保存文字、链接或图片笔记 | `save_note` |
| 查询异步创建任务 | `get_note_task_progress` |
| 修改已有纯文本笔记 | `update_note` |
| 移入回收站 | `delete_note` |
| 创建公开分享链接 | `share_note` |
| 查询图片上传限制 | `get_upload_config` |
| 获取图片上传凭证 | `get_upload_token` |
| 上传图片 | `upload_image` |

## 浏览与读取

- 录音台笔记按 `list_notes.source=vendor_moan`，录音卡按 `source=getseed`，设备全部笔记不额外限定audio/meeting；保留时间和知识库条件，按搜索Skill的设备来源流程完整分页。转写使用 `get_note_transcript`，AI总结和preview不替代转写。
- `get_note_transcript` 使用 `id`，例如 `{"name":"get_note_transcript","arguments":{"id":"<note_id>"}}`；录音台和录音卡先按设备source定位。
- `list_notes.cursor` 首次省略；来源/类型/时间筛选使用新分页的 `pagination.next_cursor`，保留所有条件。仅旧默认列表沿旧响应 `cursor` 继续，不混用新旧游标。
- `get_note` 使用 `id`；需要原图时设置 `image_quality: "original"`。
- 不确定笔记类型时先调用 `get_note`：原文使用 `get_note_original`，录音转写使用 `get_note_transcript`，附件、时间线、快捷笔记和会议待办分别使用对应 Tool。
- `get_note_todos` 返回的是从明确会议总结章节按规则解析的待办，应保留其 `source`，不能宣称为上游原生任务。
- AI 摘要、搜索片段和原文是不同内容，不能相互冒充。
- `get_note_chapters` 返回 `chapter_timeline.items`，`start_ms` 为毫秒；保留 `source`。章节时间线与 `timeline.moments`、标记各自独立，空数组不能用其它数据补齐。
- 标记与 Timeline 分别查询、分别验证，不能因为条数相同就当作同一数据。发芽按 `month` 列出报告，分页原样使用 `since_id`，再用报告真实 `id` 读取正文。

## 保存笔记

`save_note` 支持以下输入：

- 公共字段：`title`、`content`、`tags[]`、`parent_id`、`topic_id`、`client_request_id`。
- 纯文本：`note_type: "plain_text"`，正文放在 `content`。
- 链接：使用服务支持的链接类型并传 `link_url`；若返回异步 `task_id`，继续调用 `get_note_task_progress`，只有最终成功并返回真实笔记 ID 才算完成。
- 图片：先用 `get_upload_config` 确认限制；`upload_image` 接收 `image_base64` 和可选 `mime_type`，返回 `image_url` 后再通过 `image_urls[]` 保存。远程 MCP 不能直接读取调用方本地文件路径。

同一次创建及其网络重试必须复用同一个 `client_request_id`。超时或断流后先查询原任务或最近笔记，不直接再次创建，避免重复笔记。

## 更新、删除与分享

- `update_note` 使用 `note_id`，并只传用户明确要改变的 `title`、`content` 或 `tags`；未指定字段保持不变。
- 覆盖正文或替换全部标签前先说明影响并取得确认。
- `delete_note` 会把笔记移入回收站。调用前必须确认目标笔记，并在执行前取得用户明确确认；成功后只能说“已移入回收站”。
- `share_note` 会创建任何获得链接者都可能访问的公开链接。用户意图不明确时先确认；录音类笔记用 `share_exclude_audio` 明确是否排除音频。
- 用户未要求公开分享时，仅返回服务端已有的私有笔记链接，不自动调用 `share_note`。

## 成功与失败

- 上传成功不等于笔记创建成功，出现 `task_id` 也不等于异步任务完成。
- 只返回服务端给出的真实 ID、标题和 URL，不自行拼接。
- 工具错误时读取 `reason`、`message`、`retryable` 和可选 `request_id`。认证错误引导重新连接；仅对明确可重试的瞬时失败重试。

## 引用笔记与筛选

- get_note 返回完整详情；ref_content 是引用原文，content 是个人评论，reference 保存出处。get_note_original 对 ref 返回 ref_content，不把评论或摘要当原文。
- list_notes 可用 source、note_type、topic_id、reference_resource_type、reference_resource_id、reference_article_url 及 created/edited/record_updated 的 from/to 时间过滤；时间为带时区 RFC3339，起点包含、终点不包含。source=dedao 且 reference_resource_type=ebook/course_article 用于对应真实来源。
- reference_resource_id 按正十进制字符串传，并同时指定 source 与 reference_resource_type；reference_article_url 是精确文稿链接，不能传课程主页。
- 显式 page_size 获取新分页；全局按 pagination.next_cursor，库内 list_topic_notes 按 next_page，保留全部条件。列表用于定位，按需 get_note 深读，不能声称列表已经提供完整正文。
