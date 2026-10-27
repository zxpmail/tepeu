---
name: zspace-file-search
description: 按文件名搜索与办公文档全文搜索。 触发词：极空间 / NAS / 文件搜索。
description_zh: 按文件名搜索与办公文档全文搜索。
description_en: Filename search and office-document full-text search.
version: 2.0.3
author: zspace
allowed-tools: nas_query,search_content,search_files
---

# 文件搜索（file-search / bigsearch·search）

按文件名搜索与办公文档全文搜索。

- 服务类型：**源码不在本仓库（运行于 NAS 的外部仓库服务）**
- 对应目录：`NasServices/file-search`（不在本仓库，见备注）
- 对外前缀：`/file_search/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `search_content` | 全文搜索极空间上的办公文档内容（依赖 NAS 全文索引，需已开启高级搜索） | 只读 |
| `search_files` | 按文件名搜索极空间文件。⚠️ 语法以**真机实测**为准（NAS 侧是子串/分词匹配，**不是 glob**）：纯关键词与扩展名可用（`wav`、`.wav`、`*.wav` 都能命中）；`-排除`、`+必含`、`"短语"`、`?` 等操作符**均不生效**（`-测试` 与 `测试` 结果相同）。连接器会把查询里的 `*` 去掉（后端无法匹配字面 `*`，此前 `*.wav` 恒为 0 条），因此 `*.wav` 与 `.wav` 等价。可用 file_path 限定目录、按类型/大小过滤。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/file_search/file_search` | 文件名搜索（子串/分词匹配；**不支持 glob/操作符**；参数边界：`num` ≤ 500、`order_by` 1~12、`shared_only` 0~5、`start` ≥ 0） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/file_search/office_search` | 办公文档全文搜索（doc_type 0~5） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「找文件名含合同的文件」→ `search_files {name:"合同"}`
- 「哪份文档里提到了 XX」→ `search_content {name:"XX"}`（依赖 NAS 全文索引）

## 6. 备注

- ⚠️ 该服务源码不在本仓库的 `NasServices/` 下（应用 `bigsearch`/`search` → 外部仓库 `file-search`），接口细节来自连接器实测。
- 网关用 `proxy_pass http://127.0.0.1:8023/;` 剥掉 `/file_search/` 前缀，后端路由是 `/file_search` 与 `/office_search`。
- 出错时 `code` 是**数字**（2=参数错误、8=搜索失败、9=结果失败），与其它服务的字符串码不同。
- ⚠️ 搜索语法以**两轮真机实测**为准：NAS 侧是**子串/分词匹配，不是 glob**——`wav`、`.wav`、`wav*`、`*wav*`、`*wav` 均命中 4 条，而 `*.wav` 命中 **0** 条（字面 `*` 永远匹配不到）；`-排除`、`+必含`、`"短语"`、`?` **一律不生效**（`-测试` 与 `测试` 结果相同）。
- 连接器会把查询里的 `*` **去掉**（`*.wav` → `.wav`，实测由 0 条变为 4 条），使最常见的 glob 写法可用；查询以 `-`/`+` 开头或含引号时返回 `warning` 说明操作符不受支持。
- 已知范围限制：文件名搜索**未覆盖公共空间**（真机实测 `/nvme19/public/演示/*.jpg` 搜不到，个人空间正常）——疑似索引只含个人空间，属服务端行为。
- `search_content` 无结果时返回 `{empty:true, hint}`：区分"确实没有"与"全文索引未建立/重建中"（响应里的 `reindexing` 会带入提示）。`search_files` 在索引重建中同样会给 hint。
- ⚠️ NAS 会在命中项的 `name` 里插入高亮控制符（`\u0001`/`\u0002`）；连接器已**剥离**这些不可见字符（原始值保留在 `match_string`），下游可安全用于拼接/重命名。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
