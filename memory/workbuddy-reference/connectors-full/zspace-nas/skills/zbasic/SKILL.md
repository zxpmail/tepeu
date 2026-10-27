---
name: zspace-zbasic
description: 设备状态、磁盘、登录设备、操作日志、服务进程——极空间的核心系统服务。 触发词：极空间 / NAS / 系统与设备。
description_zh: 设备状态、磁盘、登录设备、操作日志、服务进程——极空间的核心系统服务。
description_en: Device status, disks, logged-in devices, operation logs and service processes (ZSpace core system service).
version: 2.0.3
author: zspace
allowed-tools: device_info,disk_info,nas_query,service_status,system_info
---

# 系统与设备（zbasic）

设备状态、磁盘、登录设备、操作日志、服务进程——极空间的核心系统服务。

- 服务类型：**非应用商店类（核心服务）**
- 对应目录：`NasServices/zbasic`
- 对外前缀：`/system/* · /disk/* · /device · /action/* · /account/*`
- 真机状态：✅ 真机验证通过

## 1. 可用工具

| 工具 | 用途 | 风险 |
|---|---|---|
| `device_info` | 极空间设备与账号活动：已登录设备清单、登录历史（按 start/num 分页）、操作事件日志（谁在什么时候做了什么）。⚠️ 后端 /device/loginlist 不支持分页且实测一次返回 170+ 条（40KB+），连接器在本地切片并回填 total/next_start。 | 只读 |
| `disk_info` | 极空间磁盘信息：挂载清单、磁盘设置与存储模式、用户空间用量、磁盘休眠历史。⚠️ 部分机型/账号下 `/disk/mounts` 的 `disks` 恒为 `null`，此时用 `disks_from_system` 兜底（取自 system_info 的 disk 区段）；**仅在 `disks` 为 null 的机型上**才额外给出扁平化 `disk_summary`（dev/model/**sn**/total/used/temp/health），`disks` 非空的机型请直接读 `mounts.disks`；`smart_report` 的 `sn` 优先取 `mounts.disks[].sn`，取不到就用 `disk_summary[].sn`（= `smart.serial_number`）。 | 只读 |
| `nas_query` | 兜底只读查询：调用极空间上未封装成专用工具的**只读**接口（路径必须在只读白名单内）。仅当没有合适的专用工具时使用；写操作会被直接拒绝，请改用对应的专用工具。⚠️ 兜底路径**不做服务级整形、没有分页**：响应超过 max_bytes（默认 64KB）时会自动切片/摘要并回传 oversized 元信息（如 `/AppStore/v1/list` 实测 400KB+，应改用专用工具 `app_status`）。📌 返回结构：`{ok:true, data:{via:"fallback", path, data:<后端 data 字段>, ...}}` —— 后端载荷在 **`data.data`**，取用时别只看第一层（这是最常见的误读）。 | 只读 |
| `service_status` | 极空间服务与进程状态：服务清单（12 项，哪个服务在跑）、服务分组资源占用（26 组）、进程总览（默认只给总数 + CPU/内存 Top N，全量用 full_processes=true），可选附带应用商店安装/升级任务进度。⚠️ 任一子项读取失败会在 errors 中给出原因（取值为 null 表示「未知」，不代表该服务不存在）。 | 只读 |
| `system_info` | 极空间设备状态：CPU/内存/温度/风扇/网络/磁盘/电源（UPS）等区段可选；include_polling=true 时附带存储池与设备总览。设备健康、负载、开机时长都用它。 | 只读 |

## 2. 关键接口

> 除下列已封装工具外，其余**只读**接口可用 `nas_query {path, method, params}` 兜底调用（须在只读白名单内）。

| 方法 | 路径 | 说明 | 风险 | nas_query 兜底 |
|---|---|---|---|---|
| POST | `/system/recent/select` | 设备状态分区段查询（fields=basic,cpu,memory,gpu,fan,network,disk,power） | 只读 | ✅ 可直接调用 |
| POST | `/system/polling2` | 存储服务/开机时长/产品型号等总览 | 只读 | ✅ 可直接调用 |
| POST | `/system/temperatures` | 温度传感器 | 只读 | ✅ 可直接调用 |
| POST | `/disk/mounts` | 磁盘与挂载清单（含 sn、型号、smart 摘要） | 只读 | ✅ 可直接调用 |
| POST | `/disk/setting` | 磁盘设置与存储模式 | 只读 | ✅ 可直接调用 |
| POST | `/disk/statics` | 用户空间用量 | 只读 | ✅ 可直接调用 |
| POST | `/action/disk/stats` | 磁盘休眠历史 | 只读 | ✅ 可直接调用 |
| POST | `/device` | 已登录设备清单 | 只读 | ✅ 可直接调用 |
| POST | `/device/loginlist` | 登录历史 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/action/list` | 操作/事件日志（分页） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/manager/srvs` | 服务清单（网关重写到 /AppStore/v1/manager/srvs） | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/manager/v2/processes` | 进程清单 | 只读 | ⚠️ 可兜底，但会按体积护栏切片返回 |
| POST | `/account/oauth2/userinfo` | 当前授权令牌对应的用户信息。✅ nas_query 可兜底，但**必须**带 params：`os`（如 workbuddy）与 `access_token`（值写 `@session_token`，连接器代入当前 OA. 令牌）；只带 Cookie 会返回 401 invalid token format。返回含 base64 头像（约 15KB），超限时被裁剪为前缀+标记。（/account/oauth2/retoken|revoke 会改令牌状态，不在白名单） | 只读 | ✅ 可直接调用 |
| GET | `/docsync/info` | 文档同步（Syncthing）状态——备份巡检用 | 只读 | ✅ 可直接调用 |

> **兜底用法（务必按接口要求调用）**：方法必须一致（POST 路由用 GET 调会 404）；标 ⚠️ 的接口必须传全必填参数（如 `/v2/file/list` 必带 `path`），否则返回 `N001212 参数有误`。能用专用工具时优先用专用工具——参数与返回都已封装。

## 3. 典型用法

- 设备现在怎么样 → `system_info {fields:["cpu","memory","disk","power"]}`
- 谁登录过 → `device_info {include:["devices","logins"]}`
- 最近发生了什么 → `device_info {include:["events"], num:50}`
- 哪些服务在跑 → `service_status {}`

## 5. 明确不提供

- 重启/关机（/system/reboot|shutdown）
- 存储池改名、磁盘休眠策略变更
- 账号新增/改密/删除、设备解绑

## 6. 备注

- 真机实测：备份巡检用到的 `GET /docsync/info`（文档同步状态）可用。
- `disk_info`：部分机型/账号下 `/disk/mounts` 的 `disks` 恒为 `null`，此时连接器自动用 `system_info` 的 disk 区段兜底，返回在 `disks_from_system`（含 `ssds`/`hdds`/`esatas` 等），并额外给出**扁平化 `disk_summary`**（dev/model/**sn**/total/used/temp/health）——`smart_report` 的 sn 从这里取（实测 = `ssds[].smart.serial_number`），不必再翻深层结构（v6 报告 P3）。
- `include` 参数只接受**数组**（如 `["mounts","statics"]`），传字符串会被 schema 拒绝。
- ⚠️ 该服务的业务路由需要 NAS 侧补丁（zbasic `TokenCheck` 接受 `OA.` 令牌）。缺补丁时这 4 个工具全部返回 `N001208`，而文件类工具正常——详见 `nas-patches/README.md` §3。
- `/system/recent/select` 的 `fields` 是**逗号分隔字符串**（`fields=basic,cpu`），不是数组参数。
- ⚠️ PII 脱敏口径（产品决策，非漏脱敏）：`username`（手机号）在 `userinfo`/`device_info` 统一脱敏为 `138****0000`（示例形态）；而 `device_info {include:["events"]}` 里的 **`ip` 保留原值**——登录审计与异常排查需要真实来源 IP，属有意保留；如需一并脱敏请走产品决策。

## 7. 参考

- 跨服务通用约定（响应外层 / 分页家族 / 错误码 / 路径映射 / 禁用清单）：`skills/zfilev2/references/api-conventions.md`
