---
name: zspace-zfilev2
description: 文件浏览/读写/上传下载/回收站/分享/标签/异步任务——极空间的文件核心服务（含 notebook、receiver、recycle 等虚拟应用）。 触发词：极空间 / NAS / 文件管理。
description_zh: 文件浏览/读写/上传下载/回收站/分享/标签/异步任务——极空间的文件核心服务（含 notebook、receiver、recycle 等虚拟应用）。
description_en: File browsing, read/write, upload/download, recycle bin, sharing, labels and async tasks (ZSpace file core service).
version: 2.0.3
author: zspace
allowed-tools: copy,delete,download_file,file_info,file_labels,file_tasks,list_dir,mkdir,move,nas_query,read_file,recyclebin,rename,share,upload_file,user_space,write_file
---

# 文件管理（zfilev2）

文件浏览/读写/上传下载/回收站/分享/标签/异步任务——极空间的文件核心服务（含 notebook、receiver、recycle 等虚拟应用）。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/zfilev2`
- 对外前缀：`/v2/file/* · /v2/share/* · /v2/labels/* · /v2/receiver/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `copy` | 复制极空间文件/文件夹到目标目录（异步任务，完成后可用 file_tasks 查看进度） | 写 |
| `delete` | 删除极空间文件/文件夹（默认进回收站，可恢复）。skip_bin=1 为彻底删除且不可恢复：⚠️ 必须先向用户列出完整对象清单并取得明确同意；批量超过 100 项同样需要先说明规模。 | **高危** |
| `download_file` | 下载极空间文件到本地（流式写盘；offset>0 时从断点续传，要求本地文件大小正好等于 offset）。⚠️ 本工具会**写本机文件**（local_path，任意路径）：offset=0 且目标已存在时默认拒绝，需显式 overwrite=true 才覆盖。 | 写 |
| `file_info` | 获取单个文件/文件夹的元信息（大小/类型/时间/标签等）。⚠️ `is_dir`/`is_sys` 是**字符串** `"0"`/`"1"`（`"0"` 在 JS 里是 truthy）——请用布尔别名 `is_dir_bool`。 | 只读 |
| `file_labels` | 极空间文件标签管理：查看全部标签、新建标签、给文件打/移除标签、按标签取文件、重命名与删除标签。标签以**名称**标识。写操作会回读校验并返回 verified（后端对这些接口只回 {}，必须自证才能确认生效）。 | 写 |
| `file_tasks` | 极空间文件异步任务（复制/移动/压缩/解压等）的查询与暂停/继续。⚠️ 后端语义：任务在 1 秒内完成时（小文件常见）会立即回收记录、列表里不再出现，此时 copy/move/delete 的响应会给出 finished:true；用 action:"status" 可确认某条 id 的归属。⚠️ 后端**没有** path/state 过滤参数（`file_tasks {path:"…"}` 会被静默忽略）：要按目录/操作/状态筛选请用 `path_prefix`/`opt`/`state`，它们是**连接器侧**过滤（会翻页扫描，返回 `scanned`/`truncated` 说明扫描范围）。⚠️ action="pause"/"continue" 会改变任务状态（非只读），需要用户确认后调用。 | 写 |
| `list_dir` | 列出极空间目录内容（文件/子目录，分页+排序）。`ftype` 可只列目录或只列某类文件：`dir`（=10，只要目录）、`file`（=100）、`image`/`video`/`audio`/`doc`/`compress` 等（详见参数说明）。⚠️ 每条记录的 `is_dir`/`is_sys` 是**字符串** `"0"`/`"1"`（`"0"` 在 JS 里是真值！）——判断目录请用布尔别名 `is_dir_bool`，不要写 `if (x.is_dir)`。 | 只读 |
| `mkdir` | 在极空间上新建文件夹 | 写 |
| `move` | 移动极空间文件/文件夹到目标目录（异步任务，完成后可用 file_tasks 查看进度）。⚠️ 超过 100 项时应先向用户说明规模；不提供"移动磁盘根目录"等越权操作。 | 写 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `read_file` | 读取极空间文件内容（默认最多 64KB；可用 offset/max_bytes 分段读取；二进制文件用 encoding=base64） | 只读 |
| `recyclebin` | 回收站管理：list=列表（支持分页）/ restore=恢复 / empty=清空。⚠️ empty 不可恢复，调用前必须向用户确认。⚠️ 部分机型/固件**回收站不可枚举**（未启用可枚举的回收站目录，list 恒返回 `{empty:true, hint, raw:{}}`）——此时 `restore` 因拿不到回收站路径而**实际不可用**，请改到极空间客户端的「回收站」中恢复。 | **高危** |
| `rename` | 重命名极空间文件/文件夹 | 写 |
| `share` | 极空间分享管理：查看我的分享、创建分享链接、修改有效期、启用/取消、删除分享。⚠️ 创建分享会生成**外网可访问**链接：先向用户确认分享对象；建议设置有效期与提取码。注意：提取码由 NAS 随机生成（4 位），无法指定；`create` 会直接返回链接与提取码。`list` 的提取码默认脱敏（字段统一为 `extract_code`），需要把提取码转给别人时传 `show_extract_code:true`。`list` 无分享时返回 `{list:[], total:0, empty:true}`。 | 写 |
| `upload_file` | 上传本地文件到极空间（自动分片）。中断后重新调用会从已上传位置续传；返回 resumedFrom 表示本次起点。⚠️ 本工具会**读取本机文件**（local_path，任意路径）并把内容上传到 NAS——属于数据外发操作，调用前必须让用户确认「本地路径 + 目标 NAS 路径」。 | 写 |
| `user_space` | 发现当前用户在极空间上的可用空间：设备目录列表 + 最近文件（用于确定可读写路径，如 /sata11/my/data）。⚠️ 条目的 `is_dir`/`is_sys` 是**字符串** `"0"`/`"1"`（`"0"` 是 truthy）——请用布尔别名 `is_dir_bool`。 | 只读 |
| `write_file` | 创建或写入极空间文件。rename=3 为覆盖已有文件（⚠️ 需先向用户确认目标路径与影响）；默认 1 自动改名避免覆盖。支持 `encoding:"base64"` 写入二进制内容（**建议 ≤ 8MB**；更大文件请用 upload_file 走分片+续传，避免把整段 base64 塞进一次请求）。 | 写 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/v2/file/list` | 列目录（path/start/num/sortby/order/ftype；`num` 上限 20000，超限后端静默回退 50） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/file/info` | 文件元信息 | 只读 | ✅ 可直接调用 |
| GET | `/v2/file/download` | 下载（支持 Range） | 只读 | ⚠️ 需传必填参数 |
| HEAD | `/v2/file/download` | 取文件大小（连接器用于钳制 Range） | 只读 | ✅ 可直接调用 |
| POST | `/v2/file/create` | 写入文件（header: path/rename） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/upload` | 分片上传（header: path/size/seek/split/uuid/rename） | 写 | —（写操作，仅专用工具） |
| GET | `/v2/file/tmpinfo` | 上传进度查询（注意是 **GET**，必填参数 `path` + `uuid`；缺参会返回 `N001212 参数有误`） | 只读 | ✅ 可直接调用 |
| POST | `/v2/file/upload/whyfail` | 上传失败原因 | 只读 | —（写操作，仅专用工具） |
| POST | `/v2/file/newdir` | 新建目录 | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/modify` | 重命名 | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/move` | 移动（异步任务） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/copy` | 复制（异步任务） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/remove` | 删除（skip_bin=1 为彻底删除） | **高危** | —（写操作，仅专用工具） |
| POST | `/v2/file/recyclelist` | 回收站列表 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/file/restore` | 恢复指定项 | 写 | —（写操作，仅专用工具） |
| POST | `/v2/file/rclean` | 清空回收站 | **高危** | —（写操作，仅专用工具） |
| POST | `/v2/file/tasks` | 异步任务进度 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/file/devicedir` | 设备目录（个人空间前缀） | 只读 | ✅ 可直接调用 |
| POST | `/v2/file/apphome` | 用户空间与最近文件 | 只读 | ✅ 可直接调用 |
| POST | `/v2/share/create` | 创建分享链接（expire=秒、has_pass=0/1；提取码由 NAS 随机生成，无法指定） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/share/list` | 我的分享列表 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/v2/share/setexpire` | 改有效期（ids[] + expire 秒） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/share/delete` | 删除分享（ids[]） | 写 | —（写操作，仅专用工具） |
| POST | `/v2/labels/alllabels` | 全部标签 | 只读 | ✅ 可直接调用 |
| POST | `/v2/labels/savefilelabel` | 给文件打标签 | 写 | —（写操作，仅专用工具） |
| POST | `/v2/labels/getfiles` | 按标签取文件 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 第一次操作先 `user_space` 拿路径前缀
- 读文档：`list_dir` 确认 → `read_file`（大文件用 offset 分段；二进制用 encoding=base64）
- 上传：`upload_file`（中断后重试自动续传，返回 resumedFrom）
- 下载：`download_file`（流式写盘；续传要求本地文件大小 == offset）
- 整理：`move`/`copy` → `file_tasks` 看进度

## 4. 风险与确认

- `delete` 且 `skip_bin=1`：彻底删除不可恢复——必须先列清单并取得用户同意
- `recyclebin {action:"empty"}`：清空回收站不可恢复
- `write_file {rename:"3"}`：覆盖已有文件
- `share {action:"create"}`：生成外网可达链接——先确认对象，建议设提取码与有效期

## 5. 明确不提供

- 注意避开内部路由：`/local/v2/file/share/create`、`/local/v2/file/labels/add` 在网关被 `allow 127.0.0.1; deny all;` 拦截，连接器不可达（对外路由是 `/v2/share/create`、`/v2/labels/*`）

## 6. 备注

- 路径以 `/` 分隔，禁止 `..`；`/share/...` 通常只读；回收站在 `/.recycle/my`
- ⚠️ 分享有效期参数是 `expire`（**秒**），NAS 默认 172800 秒（2 天）；**没有** `expire_days` 参数，传错会静默取默认值。
- ⚠️ 提取码**无法自定义**：`has_pass=1` 由 NAS 随机生成 4 位，`has_pass=0` 则无提取码；**没有** `password` 参数。连接器会在返回里给出链接、提取码与天数。
- `setexpire` / `delete` / `modify` 都按 `ids[]` 操作（不是 `share_id`）。
- 列表类返回已被连接器整形：`total` 为后端自报的全量计数（兼容字符串），有下一页时给出 `truncated` 与 `next_start`；连接器**不再二次切片**，第 N 页可直接续取。
- `recyclebin {action:"list"}` 在"回收站为空/该机型无可枚举回收站"时返回 `{empty:true, hint}`（而不是空对象），与接口异常可区分；恢复操作依赖列表中的路径，此类机型请在客户端操作。
- `read_file` 分段读取按 UTF-8 字符边界对齐，并返回 `bytes`/`next_offset`（下次请以此作为 offset）；文本分段拼接后与原文逐字节一致，二进制请用 `encoding:"base64"`。
- `write_file` 支持 `encoding:"base64"` 写入二进制内容（小文件够用；大文件仍建议 `upload_file`，可续传）。
- `move` / `copy` 失败时错误信息会带上目标目录与源项数（后端原始错误如 N007008 不含路径）。
- ⚠️ 安全：工具输出统一脱敏（`password`/`token`/`secret` 等字段掩码为 `***`）；分享提取码字段名为 `extract_code`，不受影响。
- `share {action:"delete"}` / `setexpire` 会在提交后**复核分享列表**：未生效时返回 `E_SHARE_DELETE_NOT_EFFECTIVE` / `E_SHARE_EXPIRE_NOT_EFFECTIVE`，绝不返回"假成功"（后端在 ids 为空等情况下会静默返回 OK）。
- ⚠️ `share {action:"delete"}` 的 `deleted[]` 只统计**确证删除**的 id：删除前先读一次列表，id 全部不存在时报 `E_SHARE_NOT_FOUND`，部分不存在时把它们放进 `not_found[]`（不进 `deleted[]`）；复核失败时 `deleted` 为空并给 `submitted` + `verified:null`。
- ⚠️ 分享链接走**极空间公网（zconnect）**解析：设备若使用内网/测试网关（例如 hosts 指向自建 OAuth 网关），手机端可能打不开链接（PC 端直连仍返回 SPA 壳 200）。这属**环境问题**而非链接失效——判断依据：`share list` 里 `state=1` 且 `expire` 未过期。请在**正式公网环境**下再验证手机端（v6 报告 P10）。
- `file_labels {action:"list"}` 已把后端的 `label_name` 归一化为 `name`，与 create/assign/delete 的参数名一致。
- `file_labels` 的写操作（create/assign/unassign/rename/delete）后端只回 `{}`，连接器会**回读自证**并返回 `verified`；`assign`/`unassign` 以 `/v2/labels/getfiles` 为准（`list_dir` 条目里的 `labels` 字段由异步索引提供，有延迟）。⚠️ `verified:true` 只用于**可自证**的 create/assign/rename/delete，`unassign` 永不返回 `verified:true`（见下条）。
- ⚠️ 固件限制：`unassign`（从文件上移除某个标签）在本固件**不可靠** —— 后端只删了标签库记录，文件 xattr `user.labels` 仍保留该标签，索引重建后关联会恢复（后端写入时带 `_<用户ID>` 后缀、删除时按不带后缀的名字比对）。**因此 unassign 永远不会返回 `verified:true`**：窗口内看到回滚 → `verified:false` + `reverted_after_reindex:true`；窗口内没看到 → `verified:false` + `effective_now:true` + `reverted_after_reindex:"possible"` + `verification_confidence:"low"`（回滚时机不定，实测存在 8.2s 窗口之后才回滚的情况，窗口不能作为成功证据），不会谎报成功。**要真正移除请用 `delete` 删除整个标签**（实测可清理 xattr），或在极空间客户端中解绑。
- ⚠️ 任务语义：小文件 `copy`/`move`/`delete` 通常在 1 秒内完成，后端随即**回收任务记录**并以 `otype:"sync"` 返回，此时响应里 `finished:true`，`file_tasks` 列表**不会**出现该 id（正常现象，不是列表陈旧）。需要追踪单条任务用 `file_tasks {action:"status", task_id}`。
- `share {action:"list"}` 的提取码默认脱敏（`extract_code:null` + `extract_code_masked:"****"`，不再回传后端的明文 `pass` 字段）；需要把提取码发给对方时显式传 `show_extract_code:true`。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
- 本服务参考：`skills/zfilev2/references/file-protocol.md`
