---
name: zspace-scene-health
description: 一次把存储池、磁盘 SMART、设备状态与异常日志都看一遍。 触发词：极空间 / NAS / 设备与存储体检。
description_zh: 一次把存储池、磁盘 SMART、设备状态与异常日志都看一遍。
description_en: One-pass health check: storage pools, disk SMART, device status and recent operation logs.
version: 2.0.3
author: zspace
allowed-tools: device_info,disk_info,nas_query,service_status,smart_report,storage_info,system_info
---

# 场景：设备与存储体检

一次把存储池、磁盘 SMART、设备状态与异常日志都看一遍。

- 组合服务：`zspoolv3` · `zbasic`
- 涉及工具：7 个（各服务的接口细节见对应服务 Skill）

## 步骤

- 1. 容量 → `storage_info`（`pool_list[].total_size/free_size`）
- 2. 磁盘与序列号 → `disk_info {include:["mounts"]}`
- 3. 逐盘健康 → `smart_report {sn}`（sn 取自 `disks[].sn` 或兜底 `disk_summary[].sn`）
- 4. 设备负载/温度/UPS → `system_info {fields:["basic","cpu","memory","gpu","fan","power"]}`
- 5. 异常排查 → `device_info {include:["events"], num:50}`（操作日志）

## 要点

- `/zspool/info` 在部分机型**不返回磁盘数组**，磁盘信息一律以 `disk_info` 为准。
- 体检只读；连接器不提供重启/关机/格式化/RAID 重建等操作。

## 通用约束

- 涉及删除/覆盖/清空等**高危**操作时，必须先列出对象清单并取得用户明确同意。
- 依赖「应用商店类」应用的能力，未安装时返回 `E_APP_NOT_INSTALLED`：向用户说明**是应用没装**，并引导到 NAS 应用商店安装。
- 返回统一为 `{ok:true,data}` 或 `{ok:false,error:{code,message}}`；列表被截断时用 `next_start` 翻页。
- 通用约定与错误码：`skills/zfilev2/references/api-conventions.md`。
