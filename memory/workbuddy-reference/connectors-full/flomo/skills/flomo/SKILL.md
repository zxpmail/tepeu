---
name: flomo
description: flomo 浮墨笔记操作技能 - 记录、搜索、阅读、更新笔记，整理标签，获取每日回顾与用户记忆
description_zh: 通过 flomo MCP 记录、搜索、读取和更新笔记，整理标签，获取每日回顾与用户记忆。
description_en: Use flomo MCP to capture, search, read and update memos, organize tags, and retrieve daily reviews and user memories.
version: "1.0.1"
author: "flomo"
---

# flomo Skill

flomo 是一款卡片笔记工具，用户用它随手记录想法、读书摘录和灵感。本 Skill 通过 flomo MCP 读写当前授权用户的笔记。

## 使用原则

- **写之前先读规范**：调用 `memo_create` / `memo_update` 前先调用 `get_format_guide`；为用户新建或整理标签前先调用 `get_tag_guide`，并用 `tag_tree` / `tag_search` 优先复用已有标签。
- **回答前先搜索**：用户问到"我记过…""我之前的想法…"时，先用 `memo_search` 查找用户原文，引用原文作答，不要凭空编造。
- **不要基于截断内容改写**：搜索、推荐、每日回顾返回的正文超过 500 字会被截断（`content_truncated=true`）。修改笔记前必须先用 `memo_batch_get` 取完整正文。
- **按用户授权执行写操作**：用户已明确要求的修改可以直接执行；改动范围不明确或需要扩大到批量重命名时，先说明范围并确认。
- **保留并发保护**：更新已有笔记时，将刚读取的 `updated_at` 作为 `local_updated_at` 传回，避免覆盖用户的新修改。

## 可用工具

### memo_search - 搜索笔记

搜索当前用户的笔记，单次最多返回 50 条（默认 10 条）。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| keywords | string | - | 检索词，多个词用空格分隔表示 AND；不要用 `\|` 表示 OR，不要放"昨天""最近"等时间词 |
| tag | string | - | 按标签过滤，不含 `#`，如 `读书/心理学` |
| start_date | string | - | 起始日期，`YYYY-MM-DD` |
| end_date | string | - | 结束日期（含当天），`YYYY-MM-DD` |
| from | string | - | 来源过滤：`human`（手写）/ `ai` / `api` / `import`（导入）/ `paste`（剪藏） |
| has_tag | boolean | - | 是否只看有 / 无标签的笔记 |
| limit | number | - | 返回数量，默认 10，最多 50 |

**使用示例**：
- "我上个月关于产品设计的笔记" → `keywords: "产品设计"`, `start_date` / `end_date` 设为上个月
- "读书标签下提到习惯的笔记" → `keywords: "习惯"`, `tag: "读书"`

### memo_batch_get - 批量获取笔记全文

按 id 获取笔记完整内容，单次最多 10 条，正文累计最多 30000 字。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| ids | string[] | - | 笔记 id 列表（与 id 二选一） |
| id | string | - | 单条笔记 id |

返回中 `omitted_ids` 非空时，对这些 id 再次调用本工具；`content_truncated=true` 的条目不是完整正文。

### memo_create - 创建笔记

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| content | string | ✅ | 笔记正文，标签直接写在正文中（如 `#想法 #读书/心理学`） |
| format | string | - | `markdown`（默认）/ `text` / `html` |

flomo 仅支持加粗、高亮 `<mark>文字</mark>`、下划线 `<u>文字</u>`、有序 / 无序列表，不支持标题、引用、代码块、表格等语法。

### memo_update - 更新笔记

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | string | ✅ | 目标笔记 id |
| content | string | - | 新的完整正文（整体覆盖，含内联标签） |
| format | string | - | `markdown`（默认）/ `text` / `html` |
| local_updated_at | string | - | 读取笔记时拿到的 `updated_at`（RFC3339）。传入后若服务端已有更新版本会返回 `server_has_a_newer_version`，此时应重新读取再修改 |

### memo_recommended - 相关笔记推荐

根据一条笔记找出内容相关的其他笔记，用于补充上下文或发现笔记之间的关联。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| id | string | ✅ | 基准笔记 id |
| limit | number | - | 返回数量，默认且最多 20 |
| no_same_tag | boolean | - | 为 true 时排除与基准笔记同标签的笔记，适合跨主题发现关联 |

### tag_tree - 标签树

获取标签层级结构，默认返回 200 个，最多 1000 个；结果被截断时用 `prefix` / `depth` 缩小范围。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| prefix | string | - | 只看某个标签下的子树，如 `读书` |
| depth | number | - | 相对 prefix 向下展开的层数 |
| limit | number | - | 返回标签数量，默认 200，最多 1000 |

### tag_search - 搜索标签

按关键词模糊搜索标签名，比 `tag_tree` 更轻量。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| keywords | string | ✅ | 标签关键词 |
| limit | number | - | 返回数量，默认 20，最多 50 |

### tag_rename - 批量重命名标签

把所有带 `old_tag` 的笔记改为 `new_tag`（含子标签）。**会批量修改用户笔记，执行前确保用户已明确授权这个范围。**

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| old_tag | string | ✅ | 原标签，不含 `#` |
| new_tag | string | ✅ | 新标签，不含 `#`，不能包含空白、`<`、`#`，不能与原标签相同或位于原标签子树 |
| max_memos | number | - | 允许修改的笔记数上限，默认 200，最大 2000；匹配数超过上限时工具会返回实际数量，确认后再显式传入 |

### get_format_guide / get_tag_guide - 格式与标签规范

无参数。分别返回 flomo 笔记格式规范（支持的 Markdown 子集、标签语法、双链语法）和标签命名与层级组织的最佳实践。

### get_daily_review - 每日回顾

获取 flomo 今天从历史笔记中挑选的回顾内容，适合晨间回顾或灵感激发。

**参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|:----:|------|
| limit | number | - | 返回数量，默认且最多 20 |

### memory_user / memory_context - 用户记忆

无参数。`memory_user` 返回用户画像（身份、性格、价值观、偏好等稳定信息），`memory_context` 返回用户近期记忆（当前工作、兴趣、项目、目标）。用于让回答更贴合用户，用户尚未生成时会返回提示文本。

## 资源

| URI | 说明 |
|-----|------|
| `flomo://memo/{id}` | 读取单条笔记完整内容 |
| `flomo://tag/{tag}/memos` | 读取某标签（含子标签）下最多 50 条笔记，正文可能截断；不能视为该标签的完整集合。tag 不含 `#`，嵌套标签用 `/` 分隔 |

## 认证说明

- 连接时 WorkBuddy 会打开浏览器进入 flomo 授权页，登录后点击授权即可，无需手动填写 Token。
- 需要 flomo **Max 会员**或**领航者会员**。非会员在授权页会看到会员提示；会员到期后授权失效，续费后重新连接即可。
- access token 过期由 WorkBuddy 自动使用 refresh token 续期；仅在自动续期失败、refresh token 失效或会员不满足要求时，提示检查会员并重新连接。不要把单次 401 当作必须重新授权。

## 注意事项

- 搜索、推荐、每日回顾返回的正文超过 500 字会首尾保留、中间截断，`word_count` 为原文长度。
- 单条笔记正文上限 64000 字节（约 2 万个汉字），超长内容请拆成多条笔记。
- 调用过于频繁会触发限流，批量读取请优先用 `memo_batch_get` 而不是逐条读取。
- 笔记双链格式为 `https://v.flomoapp.com/mine/?memo_id={id}`，写入正文即可与对应笔记建立关联。
