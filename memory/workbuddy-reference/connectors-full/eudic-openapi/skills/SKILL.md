---
name: eudic-openapi
display_name: 欧路词典系列
display_name_en: Eudic Dictionaries
description: 管理欧路词典、法语助手、德语助手或西语助手中的生词本、笔记、例句和个人语料；当用户要查询、添加、重命名或删除这些数据，或从文章提取词汇提示时使用。
description_zh: 管理欧路词典系列应用中的生词本、笔记、例句和个人语料，并从文章中提取词汇提示。
description_en: Manage vocabulary lists, notes, example sentences, and personal vocabulary in Eudic dictionary apps, and extract vocabulary hints from articles.
version: 1.0.0
author: Eudic
---

# 欧路词典系列 OpenAPI

通过当前连接语言对应的应用管理用户数据：`en` 为欧路词典，`fr` 为法语助手，`de` 为德语助手，`es` 为西语助手。语言在连接器配置中确定，不要用工具参数猜测或切换当前应用。

## 使用原则

- 先判断用户要操作的是生词本、例句、笔记、用户语料，还是文章词汇分析，再选择最窄的工具。
- 分类 ID 必须先通过 `get_category` 获取，不要猜测。向用户展示分类时同时保留名称和 ID，后续写操作使用真实 ID。
- 分页工具的 `page` 从 `0` 开始，最大 `50`；`page_size` 最大 `100`。除非用户要求更多，先取第一页。
- `add_words` 和 `delete_words` 每次最多处理 `1000` 个单词。批量输入先去除空白项和重复项。
- 工具通常以 `message` 表示错误或操作结果，以 `data` 返回数据。`message` 非空且不是明确成功提示时，不要把调用描述成成功。
- 收到认证失败、Token 无效或 `401` 时，停止重试并提示用户重新连接连接器；不要在回复或日志中回显 Token。
- 收到访问过于频繁或 `403` 时停止调用，说明已触发频率限制，不要立即循环重试。

## 安全规则

- `delete_category` 会删除整个分类。调用前先用 `get_category` 核对分类名称和 ID，并在本轮向用户明确说明目标后取得确认。
- `delete_words` 和 `delete_note` 是不可撤销删除。调用前列出将删除的单词或笔记并取得确认。
- `add_note` 会新增或覆盖同一单词的笔记。先调用 `get_note`；如果笔记已存在，展示现有内容并在覆盖前取得确认。
- 不要把 `rename_category` 当作新建分类，也不要把 `delete_words` 当作删除分类。
- `vocab_hints` 只分析文本，不写入生词本或语料库。只有用户随后明确要求保存时，才调用新增工具。

## 工具选择

### 生词本分类

- `get_category()`：列出分类名称和 ID。查询分类或任何依赖分类 ID 的操作都先调用它。
- `add_category(name: string)`：创建分类。
- `rename_category(id: string, name: string)`：重命名分类，不移动或增删其中的单词。
- `delete_category(id: string)`：删除整个分类；遵守删除确认规则。

### 生词本单词

- `get_words(id: string, page: integer = 0, page_size: integer = 100)`：分页读取指定分类的单词。分类参数名是 `id`。
- `get_word(word: string)`：查询单个单词是否已在生词本及其详情。
- `add_word(word: string, star: integer = 1, context_line?: string, category_ids?: integer[])`：新增单词，可附星标、上下文和分类 ID 列表。不传 `category_ids` 时使用默认分组。
- `add_words(category_id: string, words: string[])`：向一个分类批量新增单词，最多 `1000` 个。
- `delete_words(category_id: string, language?: string, words: string[])`：从一个分类批量删除单词，最多 `1000` 个。通常省略 `language`，使用当前连接语言。

### 例句与文章分析

- `get_sentences(page: integer = 0, page_size: integer = 100)`：分页读取用户收集的例句、翻译和备注。
- `vocab_hints(text: string, url?: string, title?: string, word_tags?: string[], phrase_tags?: string[], customize: boolean = false, include_usermemory: boolean = false)`：从文章中提取重点单词、词组和上下文提示，不写入数据。

`word_tags` 可使用 `gk`、`cet4`、`cet6`、`yjs`、`cet8`、`ielts`、`toefl`；`phrase_tags` 可使用 `phrasal`、`idiom`、`collocation` 及服务支持的等级标签。不提供某类标签时，不要求服务生成该类提示。`customize=true` 时包含文章中命中的用户生词，`include_usermemory=true` 时不排除已掌握单词。

### 笔记

- `get_notes(page: integer = 0, page_size: integer = 100)`：分页读取笔记列表。
- `get_note(word: string)`：读取指定单词的笔记。
- `add_note(word: string, note: string)`：新增或覆盖笔记；覆盖前遵守确认规则。
- `delete_note(word: string)`：删除笔记，但不删除生词本中的单词；遵守删除确认规则。

### 用户语料

- `get_user_vocab_entries(recent_days: integer = 0, is_favorited?: boolean, page: integer = -1, page_size: integer = 100)`：查询用户语料。`recent_days <= 0` 表示不限时间；不传 `is_favorited` 表示不过滤；`page < 0` 表示不分页。
- `get_user_vocab_by_words(words: string[])`：批量查询指定词的语料详情，包括释义和上下文。

当前 MCP 不提供用户语料的新增、更新或删除工具。用户提出这类要求时，直接说明当前连接器只支持语料查询，不要尝试用生词本或笔记工具代替。

## 常用流程

### 浏览或管理某个生词本

1. 调用 `get_category` 获取真实分类。
2. 根据用户选定的分类 ID 调用 `get_words`。
3. 用户明确要求新增时，根据数量选择 `add_word` 或 `add_words`。
4. 用户明确要求删除时，核对分类和单词列表、取得确认，再调用 `delete_words`。

### 从文章整理并收藏词汇

1. 使用 `vocab_hints` 分析文章，先向用户展示候选词及上下文。
2. 让用户决定要保留的词和目标分类；需要分类 ID 时调用 `get_category`。
3. 单个词且要保留星标或上下文时使用 `add_word`；只需批量导入词表时使用 `add_words`。
4. 不要因为完成了分析就自动保存全部候选词。

### 查询未收藏的近期语料

调用 `get_user_vocab_entries`，设置 `recent_days` 为用户指定天数、`is_favorited=false`。结果较多时使用非负 `page` 分页；需要若干词的完整上下文时，再调用 `get_user_vocab_by_words`。

## 结果呈现

- 用用户使用的语言概括结果，不直接倾倒原始 JSON。
- 列表结果说明当前页和条数；没有数据时明确说未找到。
- 写操作完成后，仅依据工具返回值说明成功项、失败项和下一步，不推测未返回的状态。
