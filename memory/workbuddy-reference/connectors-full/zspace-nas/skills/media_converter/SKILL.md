---
name: zspace-media-converter
description: 视频转码、图片处理、字幕等批处理任务。 触发词：极空间 / NAS / 影像处理。
description_zh: 视频转码、图片处理、字幕等批处理任务。
description_en: Video transcoding, image processing and subtitle batch jobs.
version: 2.0.3
author: zspace
allowed-tools: media_convert,nas_query
---

# 影像处理（media_converter / imgProcessing）

视频转码、图片处理、字幕等批处理任务。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/media_converter`
- 对外前缀：`/converter/v1/*`
- 依赖应用：`imgProcessing` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `media_convert` | 「影像处理」应用：视频转码任务的查询与操作。⚠️ 后端是**三步流程**（真机核对源码 `VideoTask`）：`create`（建任务，表单 `task_info`）→ `add_file`（`file_path[]`）→ `execute`（开始转码）。本工具的 `create` 会自动完成「建任务 + 加入源文件」，**默认不开始转码**；确认源文件与输出格式后再调 `execute`（会长时间占用 NAS 算力）。输出默认写到源文件同目录下的 `compressed video/` 文件夹（可用 out_path 指定已存在目录）。`execute`/`pause`/`cancel` 会回读 `task_status` 自证（返回 `verified`/`observed_state`）。同一时间只允许一个任务：已有任务处于 created/pending/running 时 create 会返回 N006020，请先用 `list` 查看状态：pending 的可 `execute`/`cancel`；created 且无源文件的"卡住空任务"可用 `add_file` 挂上任一已存在文件 → 等扫描到 pending → `cancel` 自助释放（零转码算力，实测可行），实在不行才到极空间客户端「影像处理」中删除。⚠️ `list` 不带 task_id 时**只返回最新一条**任务记录，历史任务需按 task_id 查询；**不能用 `list` 判断转码槽位是否空闲**（后端 `/converter/v1/video/task` 没有任务列表接口，占用槽位的任务可能不在 list 里）——槽位是否空闲请以 `create` 是否返回 `N006020` 为准；N006020 的错误文案里会带上本次能探测到的占用任务信息。 | 写 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| GET | `/converter/v1/video/task` | 任务查询（GET + query task_id） | 只读 | —（写操作，仅专用工具） |
| POST | `/converter/v1/video/task` | 任务操作：**表单** action + task_info(JSON) / task_id / file_path[] | 写 | —（写操作，仅专用工具） |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「把这段视频转成 mp4」→ `media_convert {action:"create", source_path, target_format:"mp4"}`（写操作，先确认；再 `execute` 才真正开始转码）
- 「转码进度」→ `media_convert {action:"list"}`

## 6. 备注

- 该服务 vhost 使用 `auth_master.lua`，子账号可能被拒。
- ⚠️ 真实契约（真机核对源码 `routers/api/v1/video.go` + 实测 P2）：**表单**提交，字段 `action` / `task_info`(VideoConverterConfig JSON) / `task_id` / `file_path[]`；`out_format`（不是 target_format）、`out_path` 必须是已存在目录（空=与源文件同目录）。
- ⚠️ 三步流程：`action=create` 建任务 → `action=**file**` 加文件（**注意不是 `add_file`**，写成 add_file 会落到 else 分支返回 `N400000`）→ `action=execute` 开始。连接器的 `create` 自动完成前两步，`execute` 单独调用。
- ⚠️ 加文件后后端会**异步扫描**，扫描期间 `task_status` 未到 `pending`，此时 `execute` 会得到 `N006005 当前状态无法执行任务`。连接器在 execute 前会轮询等待扫描收敛（最多 20s）。
- ⚠️ 同一时间只允许一个任务：最新任务处于 created/scanning/pending/running 时 `create` 返回 `N006020`；请先用 `list` 查看状态——`pending` 的可 `execute`/`cancel`，而 `created` 且未挂上源文件的"卡住空任务"两者都会被拒（见下条自助释放路径）。
- ⚠️ `list` 语义：**不带 `task_id` 时后端只返回最新一条**任务记录（响应带 `scope:"latest"` + `note`）；更早的任务必须用 `media_convert {action:"list", task_id:"…"}` 逐条查询——不要据此认为设备上只有一个任务。**也不能用 `list` 判断转码槽位是否空闲**：后端 `/converter/v1/video/task` 没有任务列表接口，占用槽位的 created 任务可能根本不在 list 里；槽位是否空闲请以 `create` 是否报 `N006020` 为准（M6）。
- 输出默认落在源文件同目录的 `compressed video/` 文件夹（可用 `out_path` 指定）。
- ⚠️ 固件限制与**自助释放**（BUG-101/114/119）：`created` 且无源文件的任务，`cancel` 返回 `N006007`、`execute` 返回 `E_TASK_HAS_NO_FILE` 且不推进状态。但**"API 无法释放"是旧文案的错误结论**——实测（20260921 第 4 轮）可行路径：`add_file` 挂上任一已存在文件 → 后端异步扫描 → `task_status` 转 `pending`（`service.IsAllowCancelTaskStatus` 允许 pending）→ `cancel` 返回 `ok:true` 并把任务置为终态 `cancelling`，**全程不消耗转码算力**；只有当设备上没有该服务可解析的路径时才需到极空间客户端「影像处理」中删除。
- ⚠️ 任务动作**回读自证**（BUG-117）：后端对 `execute`/`pause`/`cancel` 只回 `{}`，连接器会回读 `task_status` 并返回 `verified` / `observed_state` / `state_changed`（读不到状态时给 `state_readable:false` + 明确提示）。⚠️ 本固件的 `cancel` 终态就是 **`cancelling`**（源码 `models.CancellTask` 写入该值，注释即"已经取消"；`service.IsAllowCreateTaskStatus` 接受它 → 槽位已释放），它不会继续推进，属正常现象。
- ⚠️ 取消失败文案**按状态分支**（BUG-118）：对 `completed` 会明确"任务已完成，无需也无法取消"；对 `cancelling` 会说明"已在取消中，槽位已释放"；只有 `created` 才给出"卡住空任务"的自助释放说明——不再对所有状态套用同一段解释。
- 连接器已在 `create` 前**校验源文件存在**（不存在直接 `E_PARAM`，不建任务），并在 `add_file` 失败时尝试释放槽位并如实回传 `task_released`。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
