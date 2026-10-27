---
name: mubu
description: 使用已连接的幕布 MCP 搜索、读取、创建和整理当前用户的文档与文件夹。
---

# 幕布

使用 WorkBuddy 中已连接的幕布 MCP 工具处理当前授权用户的内容。首次使用时按连接器提示完成幕布登录和 OAuth 授权。工具中的文档、文件夹 ID 均为字符串；不知道 ID 时先搜索，或从根目录逐级列出。不要猜测 ID。

## 工具

| 工具 | 用途与参数 | 主要返回 |
| --- | --- | --- |
| `get_user_info` | 查询当前授权用户；无参数。 | `user`：`id`、`name`、`photo`、`proVipEndDate`、`maxVipEndDate`。 |
| `list_documents` | 列出目录内容；`folderId` 可选，省略时为根目录 `0`。 | `documents`、`folders`；列表过滤加密文档及加密或特殊类型文件夹。 |
| `search_document` | 搜索文档和文件夹；`keywords` 必填且非空，`sort` 可选（默认 `time`），`doc_id` 可选，用于限定搜索上下文。 | `documents`、`folders`。 |
| `get_document` | 读取文档；`doc_id` 必填。 | `document`、`user_document`、`folders`、`definition`（正文内容）。加密文档不可读取。 |
| `create_document` | 创建文档；`title` 和非空 `markdown` 必填，`folderId` 可选（默认根目录）。 | `title`、`doc_id`、`url`。 |
| `create_folder` | 创建文件夹；`name` 必填，`parentFolderId` 可选（默认根目录）。 | `folderId`、`url`。 |
| `move_document` | 移动文档；`moveDocId` 必填，`toFolderId` 可选（省略表示根目录）。 | 成功时为空对象。 |
| `move_folder` | 移动文件夹；`moveFolderId` 必填，`toFolderId` 可选（省略表示根目录）。 | 成功时为空对象。 |

## 使用方式

- 用户要找内容时，先用 `search_document`，再凭结果中的 ID 调用 `get_document`。例如 `search_document({"keywords":"项目复盘"})`，随后 `get_document({"doc_id":"搜索结果中的文档 ID"})`。
- 用户要查看目录时，先用 `list_documents({})` 查看根目录，再以结果中的文件夹 ID 调用 `list_documents({"folderId":"文件夹 ID"})`。
- 创建文档时，将用户提供的正文整理为 Markdown 后调用 `create_document({"title":"周会纪要","markdown":"# 周会纪要\n..."})`；如用户指定目录，先确认其文件夹 ID，再传 `folderId`。创建文件夹可用 `create_folder({"name":"项目资料","parentFolderId":"0"})`。
- 移动文档或文件夹前，分别确认来源的名称和 ID、目标文件夹的名称和 ID；明确告诉用户省略目标会移至根目录，并取得用户对这次移动的明确确认后再调用 `move_document` 或 `move_folder`。例如确认后调用 `move_document({"moveDocId":"文档 ID","toFolderId":"目标文件夹 ID"})`。
- 移动成功的返回值可能只是空对象。若工具调用超时或结果不明，先查询当前位置，再决定是否重试；创建操作也先查询是否已经创建，避免重复。

## 常见问题

- 授权失败、过期或返回未授权：提示用户在 WorkBuddy 重新连接幕布，然后重试；不要索取或输出访问令牌。
- ID 不存在或无权限：重新搜索或列出目录以核对 ID，不尝试猜测其他 ID。加密文档无法通过此连接器读取。
- 读取繁忙或请求超时：稍后重试；不要把超时当作写入操作一定失败。
