---
name: transmind-meetings
display_name: TransMind 会议纪要与课堂笔记
display_name_en: TransMind Notes
description: 查询你的 TransMind 录音、会议纪要与课堂笔记，支持按时间筛选、获取纪要全文、逐字稿、章节与思维导图。触发词：我的会议纪要、上周开了哪些会、这节课讲了什么、复盘这节课
description_zh: 查询 TransMind 录音列表、纪要全文、逐字稿、章节与思维导图
description_en: Query your TransMind recordings, summaries, transcripts, chapters and mind maps
version: 2.1.0
author: TransMind（道宽科技）
---

# TransMind 会议纪要连接器

## 这个连接器能做什么

读取用户自己的 TransMind 录音与会议纪要。**全部只读**，不能创建、修改、删除任何数据。

可用工具：

| 工具 | 用途 | 参数 | 返回 |
|------|------|------|------|
| `list_recordings` | 查询用户的录音列表 | `page`（页码，默认1）、`page_size`（每页条数，默认20，最大50）、`start_time`（可选，支持 2026-09-01 纯日期）、`end_time`（可选） | 录音数组：id、标题、时长、状态、创建时间 |
| `get_recording_summary` | 获取某场录音的完整纪要 | `recording_id`（必填，从 list_recordings 获取） | 纪要全文、章节列表、思维导图；超长纪要会截断并带 `truncated: true` |
| `get_recording_transcript` | 获取某场录音的逐字转写稿（分页） | `recording_id`（必填）、`page`（默认1）、`page_size`（默认100，最大200） | 句子数组：每句含时间显示、毫秒时间戳、说话人、文本；`hasMore=true` 时翻页继续取 |

## 典型用法

- "列出我这周的会议纪要" → 调 `list_recordings`（可用 start_time/end_time 限定范围）
- "昨天那场会讲了什么" → 先 `list_recordings` 找到录音 id，再 `get_recording_summary`
- "把最近一次会议的思维导图给我" → 同上，取返回中的 mindMap
- "复盘这节课的提问和发言" → `get_recording_transcript` 从第 1 页开始取，`hasMore=true` 就取下一页，取完再分析；只要大意时用 `get_recording_summary`（更省上下文）

## 认证与错误处理

- 认证方式：标准 MCP OAuth。首次连接时 WorkBuddy 会发起授权流程，用户在 TransMind App 扫码确认后即完成连接，**无需手动填写任何凭证，也不要向用户索要**
- 返回"授权已失效" → 提示用户：打开 WorkBuddy 连接器设置，重新连接 TransMind 连接器（重新走一遍扫码授权即可）
- 返回"该 Token 无权访问此接口" → 这是只读授权的正常限制，说明用户请求了写操作，告知用户连接器只支持查询
- 授权只能看到授权账号当前身份空间的数据：个人空间（C）或机构空间（B）。如果用户说"查不到我机构里的录音"，提示他在 App 里切换到对应身份后重新授权

## 注意

- 数据范围严格限于授权用户本人，查不到其他人的录音
- `get_recording_summary` 的纪要超长时会被截断（`truncated: true`)，向用户说明并可引导去 App/分享页看完整版
