---
name: boya-os-recordings
display_name: Boya OS 录音助手
display_name_en: Boya OS Recorder
description: 当用户要查看自己的录音、寻找某条录音里说过的内容、要转写正文、AI 纪要、思维导图或待办事项（action items），或想了解已绑定的录音设备与文件夹时使用。触发词：我的录音、录音列表、本周录了什么、找录音、搜录音、提到过、说过、转写、逐字稿、正文、纪要、摘要、总结、思维导图、待办、action items、我的待办、还有什么没做、设备、绑定了什么设备、文件夹、录音放在哪。
description_zh: 检索并阅读你在 Boya OS 的录音、转写正文、AI 纪要、思维导图与待办事项，可按日期、文件夹、设备筛选。
description_en: Search and read Boya OS recordings, transcripts, AI summaries, mind maps and action items, filtered by date, folder or device.
allowed-tools: list_recordings, search_recordings, get_recording, get_transcript, get_summary, get_map, get_action_items, list_devices, list_folders
version: 1.0.1
author: Boya OS
category: 效率
---

# Boya OS 录音助手

只读地检索用户自己的录音及其派生内容（转写正文 / AI 纪要 / 思维导图 / 待办）。本技能**不产生任何写操作**：不录音、不改标题、不生成或重新生成纪要。

所有数据均按当前登录账号隔离，只能访问该用户自己的数据。

## 一、先选工具（决策树）

| 用户想干什么 | 用哪个工具 |
|---|---|
| "我这周/今天录了什么""都有哪些录音" | `list_recordings` |
| "我记得里面说过 X，是哪条录音" | `search_recordings` |
| "录音放在哪些文件夹、各有多少条" | `list_folders` |
| "我绑定了哪些设备" | `list_devices` |
| "这条录音的详情/录了多久/转写了吗" | `get_recording` |
| "把正文/逐字稿给我" | `get_transcript` |
| "纪要给/总结一下这条录音" | `get_summary` |
| "思维导图" | `get_map`（只想要导图）／`get_summary`（要整份纪要） |
| "我的待办""还有什么没做" | `get_action_items` |

**浏览 vs 检索**：按日期/文件夹翻看全部录音 → `list_recordings`；只有模糊关键词、不知道是哪条 → `search_recordings`。

## 二、recording_id 串联（核心范式）

除 `list_recordings` / `search_recordings` / `list_folders` / `list_devices` 外，**其余工具都必须先拿到 `recording_id`**。

```
list_recordings / search_recordings
        → 取返回 JSON 里每条的 recordingId
        → 作为 recording_id 传给 get_recording / get_transcript / get_summary / get_map / get_action_items
```

- `recordingId` 即上传/转写 ID，是唯一稳定的录音标识，**不要用标题当 ID**（标题可重复）。
- 禁止凭空编造 `recording_id`。用户没指明是哪条时，先用 `search_recordings` 或在列表里定位，再取 `recordingId`。

## 三、分页必须取全

`list_recordings` / `search_recordings` 返回 `total`（匹配总数）与当前页 `items`。**只要已返回条数 < `total`，就要递增 `page` 继续取，直到取满或用户不需要更多。**

`page_size` 范围 1–100，默认 20。回答"这周录了什么"这类需要完整枚举的问题时，不要只看第一页就下结论。

## 四、按内容类型判断该调什么

`list_recordings` / `get_recording` 会返回派生标记，**先用标记判断再决定是否调用下游工具，避免无效调用**：

| 标记 | 含义 | 为 true 才值得调用 |
|---|---|---|
| `hasTranscript` | 有可读转写正文 | `get_transcript` |
| `hasSummary` | 有 AI 纪要 | `get_summary` |
| `hasMindmap` | 有思维导图 | `get_map` |
| `hasActionItems` | 有标记，但**只是开关** | 明细一律调 `get_action_items` |
| `status` | `pending` / `processing` / `success` / `failed` | `success` 才可能有内容 |

## 五、"正常返回" vs "错误"——不要说错话

以下情况服务端返回的是**正常响应，不是错误**，需据实告知用户，而不是报故障：

| 情况 | 返回 | 应如何回复 |
|---|---|---|
| 录音未转写 / 转写失败 | `get_transcript` 返回 `status != success`、`content` 为空 | "这条录音还没有转写结果" |
| 没有纪要或纪要生成失败 | `get_summary` 返回 `hasSummary=false`、内容字段为 null | "这条录音还没有生成纪要" |
| 没有思维导图 | `get_map` 返回 `hasMap=false`、`mindmap` 为空串 | "这条录音还没有思维导图" |
| 没有任何待办 | `get_action_items` 返回 `items: []`、`total: 0` | "目前没有待办事项" |

**纪要/导图需要生成或重新生成时，引导用户到 Boya OS App 端操作**——本技能是只读的，永远不会触发生成。

## 六、待办事项的两类来源（务必讲清）

`get_action_items` 返回 `items` 是两类来源合并后的**同一列表**，用 `source` 区分：

- `source = memo` → **已生成的结构化待办**
- `source = main_text` / `mindmap` → **从录音纪要里按规则抽取的行动项**

用 `completable` 区分能力，**回复用户时必须说明差别**：

| `completable` | 含义 | 回复口径 |
|---|---|---|
| `true` | 真实待办，有 `itemId`，可在 App 内完成或设提醒 | 正常列出 |
| `false` | 只是纪要里写着的行动项，**尚未生成待办，无法完成或设提醒** | 必须注明"这些是纪要中提到的，还不是待办" |

其他要点：

- 用户问"**我的**待办"时**不要传 `recording_id`**，否则只能列出该条录音派生的部分。
- 默认只返回未完成项；`include_completed=true` 才含已完成。
- 一旦带上 `due_from/due_to/created_from/created_to` 任一日期参数，**只返回结构化待办**（纪要抽取项没有日期）。

## 七、常见场景 → 调用序列

| 用户说 | 调用序列 |
|---|---|
| "我这周录了什么？" | `list_recordings(period=this_week, page_size=100)` → 不足 `total` 继续翻页 |
| "上周有哪些会议纪要？" | `list_recordings(period=last_week)` → 用 `hasSummary` 筛出有纪要的 → 逐个 `get_summary` |
| "帮我找提到预算的那条录音" | `search_recordings(query="预算")` → 命中项的 `recordingId` |
| "那条录音说了什么？" | 先定位 `recordingId` → `get_transcript(format=text, include_speakers=true)` |
| "这条录音讲了多久，转写了吗？" | `get_recording(recording_id=...)` |
| "我有哪些待办？" | `get_action_items()`（**不传 recording_id**） |
| "这条会议录音有什么行动项？" | `get_action_items(recording_id=...)` |
| "我的录音怎么组织的？" | `list_folders()` → 需要细看某个文件夹时 `list_recordings(folder_id=...)` |
| "我有哪些设备？" | `list_devices()` |

## 八、参数速查与字段定义

各工具完整入参、默认值、返回字段与空态说明见 `@references/tool-params.md`。

## 九、注意事项

- **只读**：不要尝试任何修改、删除、生成动作；相关诉求引导到 Boya OS App。
- **不要用 `list_devices` 的返回值去筛选录音**——该工具不提供按设备筛选。
- **`list_folders.recordingCount` 只统计该文件夹直属的未删除录音**，不含子文件夹、不含根目录未分类录音。
- **`audioUrl` 是 10 分钟有效的预签名链接**，过期需重新调 `get_recording` 获取。
- 无权限或录音不存在时统一返回 `RESOURCE_NOT_FOUND`，不要据此推测录音是否存在。
