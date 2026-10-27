---
name: zspace-downloader
description: BT/磁力/HTTP 下载任务的创建、进度与启停。 触发词：极空间 / NAS / 下载。
description_zh: BT/磁力/HTTP 下载任务的创建、进度与启停。
description_en: Create and monitor BT / magnet / HTTP download tasks.
version: 2.0.3
author: zspace
allowed-tools: download_add,download_tasks,nas_query
---

# 下载（downloader / downloads）

BT/磁力/HTTP 下载任务的创建、进度与启停。

- 服务类型：**应用商店类（需先安装应用）**
- 对应目录：`NasServices/downloader`
- 对外前缀：`/downloader/*`
- 依赖应用：`downloads` —— 调用前连接器会自动过门禁，未安装返回 `E_APP_NOT_INSTALLED`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `download_add` | 「下载」应用操作：新建下载任务（uri+dir），或暂停/继续/删除已有任务（ids）。⚠️ `dir` **必须是已存在的目录**：目录不存在时后端会建出一条注定失败的任务（`status:21 任务发生异常`），完全不传 dir 时后端甚至会**静默不建任务**——因此连接器按「显式 dir → 应用默认目录 → 应用可选目录 → 个人空间/下载（必要时自动创建）」解析出一个真实存在的目录（结果见 `dir`/`dir_source`/`dir_created`），并在创建后回读任务列表确认真实结果（`created`/`task`/`verified`），不返回"假成功"。delete 会移除任务，建议先向用户确认任务清单。 | 写 |
| `download_tasks` | 查看「下载」应用的任务列表（BT/磁力/HTTP 下载进度、速度、状态） | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/downloader/list` | 任务列表（start/size） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/downloader/add/link` | 新建下载（表单 uri 必填 / dir 选填，**dir 必须已存在**） | 写 | —（写操作，仅专用工具） |
| POST | `/downloader/get/basic/setting` | 下载应用基础设置（含默认目录 download_dir、可选池 pools_name_v3） | 只读 | ✅ 可直接调用 |
| POST | `/downloader/add/torrent` | 新建种子下载 | 写 | —（写操作，仅专用工具） |
| POST | `/downloader/stop` | 暂停 | 写 | —（写操作，仅专用工具） |
| POST | `/downloader/start` | 继续 | 写 | —（写操作，仅专用工具） |
| POST | `/downloader/del` | 删除任务 | 写 | —（写操作，仅专用工具） |
| POST | `/downloader/downloaddir` | 默认下载目录 | 只读 | ✅ 可直接调用 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 「帮我下载这个链接」→ `download_add {action:"add", uri, dir}`（先确认保存目录）
- 「下载好了吗」→ `download_tasks {}`

## 4. 风险与确认

- `download_add {action:"delete"}`：会移除任务，先确认任务清单

## 6. 备注

- 分页字段是 `start`/`size`（不是 num）。
- ⚠️ 目录语义（真机实测 v6 报告 P1）：`add/link` 的 `dir` **必须是已存在的目录**——目录不存在时后端**仍会建任务**，随后立刻 `status:21「任务发生异常」`；`dir` 缺省时后端甚至**静默不建任务**（接口仍回成功）。
- 连接器因此按 `显式 dir → 应用默认目录（get/basic/setting.download_dir）→ 应用可选目录（downloaddir）→ <个人空间>/下载（必要时自动创建）` 解析出真实存在的目录，并在创建后**回读任务列表自证**：返回 `dir`/`dir_source`/`dir_created` 与 `created`/`verified`；未生效时报 `E_DOWNLOAD_ADD_NOT_EFFECTIVE`，目录不可用时提前报 `E_DOWNLOAD_DIR_NOT_FOUND`（附候选与可写空间清单）。
- ⚠️ 真机实测该设备的**应用默认目录可能是失效的旧存储池路径**（`/nvme13/my/data/下载`，而设备只剩 nvme19/nvme20）——所以存在性校验不可省略。
- 下载失败最常见的两种原因：① 目标域名在 NAS 上**无法解析**（实测 `speed.hetzner.de` DNS 无应答）；② 源站拒绝（401/403/需 UA）。连接器会在任务创建后回读并把 `status:21` 与 `errorString` 直接返回，并给出排查提示。
- 状态码：`0/3` 完成、`5` 调度中、`21` 任务发生异常（配合 `errorString` 判断原因）。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
