---
name: 腾讯云TCHouse-X 集群健康诊断与故障排查
description: >
  TCHouse-X（Impala）集群健康诊断与故障排查 Skill。AI 拉取实例信息、节点拓扑、计算组（Warehouse）状态、资源使用量、集群操作记录以及 SQL 运行历史，输出集群健康评分（100 分制），定位异常根因，给出修复建议（KILL QUERY / 元数据刷新 / 计算组隔离 / 扩缩容建议等）。
  触发词：集群健康、健康检查、巡检、节点故障、磁盘告警、CPU告警、内存告警、OOM、磁盘满、磁盘空间不足、节点异常、查询堆积、连接数满、集群状态、cluster health、node failure、disk alert、集群诊断、故障排查、监控异常、告警处理、节点状态、资源告警、性能瓶颈、IO高、网络异常、Impala异常、Catalog异常、StateStore异常、Warehouse异常、计算组异常、tchousex、TCHouse-X、Impala集群。
  本 Skill 包含 3 个子能力：①集群全面健康巡检 ②资源告警排查与根因定位 ③节点/服务/计算组故障诊断与修复建议。
  何时不触发：慢 SQL 诊断与自动调优（单条 SQL 性能分析）、集群选型与架构推荐、智能建表与数据建模、NL2SQL、AI Function 智能编排、集群扩缩容操作、权限管理、数据导入导出等非集群健康/故障相关问题不走本 Skill。
allowed-tools:
  - TCHouseXDescribeInstance
  - TCHouseXDescribeInstanceWarehouse
  - TCHouseXDescribeInstanceResourceUsage
  - TCHouseXDescribeInstanceOperationsV1
  - TCHouseXDescribeSqlHistoryRecords
  - TCHouseXDescribeInstanceProfile
  - ask_user # WorkBuddy 中为 AskUserQuestion
---

# 集群健康诊断与故障排查（TCHouseX / Impala）

## 概述

本 Skill 提供 TCHouse-X（云原生 Impala 数仓，采用**存算分离 + 计算组隔离**架构）集群的健康诊断与故障排查能力，包含三个子能力：

1. **集群全面健康巡检**：拉取实例拓扑、计算组状态、资源使用量、SQL 运行历史、集群操作记录，输出 100 分制健康评分与诊断报告
2. **资源告警排查与根因定位**：针对具体告警（磁盘/CPU/内存/连接数），定位根因并给出修复建议
3. **节点/服务/计算组故障诊断与修复建议**：针对节点或 Impala 服务（Catalog/StateStore/Coordinator）或 Warehouse 计算组异常，分析原因并给出恢复方案

## 依赖与运行环境

本 Skill 的所有调用通过 MCP Tool 完成（云 API 类工具由平台封装为 MCP Tool，Agent 直接调用工具名即可）。全部工具均为**云 API 管控面接口**，**不需要用户输入数据库账号/密码**。

**依赖工具清单**：

| #   | Tool 名称                             | 能力定位                                                                                                                                                                           | 参考文档                                                                                                                       |
| --- | ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| 1   | TCHouseXDescribeInstance              | 实例基本信息（含节点拓扑 / 状态 / 计算组列表）                                                                                                                                     | [参考](references/api-reference.md#2-tchousexdescribeinstance--查询-x-实例详情)                                          |
| 2   | TCHouseXDescribeInstanceWarehouse     | 单个计算组（Warehouse）详情（Resources 节点列表 / ClusterStateInfo / 实时 CPU 资源使用）。⚠️ **默认无需调用**：`DescribeInstance.VirtualClusters` 已含等价字段，仅在条件触发时补调 | [参考](references/api-reference.md#13-tchousexdescribeinstancewarehouse--查询单个计算组详情)                             |
| 3   | TCHouseXDescribeInstanceResourceUsage | 实例资源使用量时序数据（Storage / Warehouse / Serverless / BasicService，按小时/天/月聚合）                                                                                        | [参考](references/api-reference.md#14-tchousexdescribeinstanceresourceusage--查询资源使用量)                             |
| 4   | TCHouseXDescribeInstanceOperationsV1  | 集群操作记录（升降配 / 重启 / 配置变更 / 权限变更等），用于关联最近变更是否是故障诱因                                                                                              | [参考](references/api-reference.md#15-tchousexdescribeinstanceoperationsv1--查询集群操作记录)                            |
| 5   | TCHouseXDescribeSqlHistoryRecords     | 在线 SQL 运行记录（含 QueryID/状态/耗时/失败 SQL）                                                                                                                                 | [参考](references/api-reference.md#5-tchousexdescribesqlhistoryrecords--查询在线-sql-运行记录替代-c-的-slowqueryrecords) |
| 6   | TCHouseXDescribeInstanceProfile       | 单条 SQL 的详细 Query Profile（条件触发，关联具体慢 SQL 时使用）                                                                                                                   | [参考](references/api-reference.md#82-通过-tchousexdescribeinstanceprofilex-云-api-describeinstanceprofile)              |
| 7   | ask_user                              | 向用户询问确认信息（WorkBuddy 中为 AskUserQuestion）                                                                                                                               | —                                                                                                                              |

> ⚠️ **MCP 参数已知坑位（拼参数前必读）**：以下两个坑源于服务端 schema 不统一，Skill 无法自动兼容，Agent 必须逐接口按下表拼参数，不要凭直觉复用上一次的写法。
>
> **坑 1：实例 ID 参数名大小写不一致**
>
> | 接口                                      | 参数名           | 大小写                      |
> | ----------------------------------------- | ---------------- | --------------------------- |
> | TCHouseXDescribeInstance                  | `InstanceId`     | 小写 `d`                    |
> | TCHouseXDescribeInstanceWarehouse         | `InstanceId`     | 小写 `d`                    |
> | TCHouseXDescribeSqlHistoryRecords         | `InstanceId`     | 小写 `d`                    |
> | TCHouseXDescribeInstanceOperationsV1      | `InstanceId`     | 小写 `d`                    |
> | TCHouseXDescribeInstanceProfile           | `InstanceId`     | 小写 `d`                    |
> | **TCHouseXDescribeInstanceResourceUsage** | **`InstanceID`** | **全大写 `ID`（唯一例外）** |
>
> 首次调用 `DescribeInstanceResourceUsage` 时如果沿用 `InstanceId` 会收到 `InvalidParameter` 报错，请直接改成 `InstanceID` 重试，不要以为是实例 ID 值错了。
>
> **坑 2：时间参数格式不统一**
>
> | 接口                                      | 参数                                      | 格式                             | 示例                    |
> | ----------------------------------------- | ----------------------------------------- | -------------------------------- | ----------------------- |
> | **TCHouseXDescribeSqlHistoryRecords**     | `QueryOnlineLogReq.StartTime` / `EndTime` | **Unix 时间戳（整数，秒）**      | `1721016000`            |
> | **TCHouseXDescribeInstanceResourceUsage** | `StartTime` / `EndTime`                   | **字符串 `YYYY-MM-DD HH:MM:SS`** | `"2026-07-15 10:00:00"` |
> | TCHouseXDescribeInstanceOperationsV1      | `StartTime` / `EndTime`                   | 字符串 `YYYY-MM-DD HH:MM:SS`     | `"2026-07-15 10:00:00"` |
>
> 同一次巡检里需要**同时准备两套时间**：为 SQL 历史算好 Unix 秒（如 `now - 3600`），为资源使用量/操作记录格式化为字符串。**不要用同一个变量传给不同接口**。

> ⚠️ **明确排除的接口**：
>
> 1. **SR（StarRocks）系列接口**（`ValidateSRLocalUser` / `DescribeSRInstancesHealthStateV1` 等）**不属于本 Skill 的诊断范围**，禁止调用。
> 2. **DMS/SQL 工作区类接口**（`TCHouseXAuthorizedSql` / `TCHouseXExecuteSqlV1` 等）**不在本 Skill 中使用**——这类接口需要用户输入数据库账号/密码，与"零打扰只读诊断"的定位不符；本 Skill 仅通过管控面云 API 完成诊断。

## 凭证 / 环境变量

- `instance_id`：从会话 context 的 X-Context header 自动注入，**`instance-` 前缀**
- `region_id`：从会话 context 的 X-Context header 自动注入
- 若以上参数缺失，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）询问用户

> 💡 **多平台兼容说明**：本文档中所有提到的 `ask_user` 工具，在 WorkBuddy 平台中对应为 `AskUserQuestion`。后文不再重复标注。

## 核心工作流

### 步骤 0：参数确认

**必需参数**：

- `instance_id`（实例 ID，**`instance-` 前缀**）
- `region_id`（地域，详见 [region-mapping.md](references/region-mapping.md)；X 实际支持地域以接口返回为准，遇 `UnsupportedRegion` 参见 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问)）

**可选参数**（从用户问题中提取，缺失时使用默认值，不自行假设）：

- 时间范围：未指定 → 默认最近 24 小时（用于操作记录 / 资源使用量），SQL 历史默认最近 1 小时
- 关注的具体计算组（Warehouse）名称 / 节点 IP：从用户问题中提取

**判断逻辑**：

- ✅ 参数齐全 → 按 [地域映射表](references/region-mapping.md) 把地域补齐为 `Region`（字符串）后进入步骤 1
- ❌ `instance_id` 或 `region_id` 缺失 → 调用 `ask_user` 询问
- ❌ 用户给出中文地域名称 → 按映射表转换。**不做**离线支持地域白名单校验，一律先调用工具；若接口返回 `UnsupportedRegion` 再按 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问) 处理（**不重试、必须 `ask_user`**）

### 步骤 1：确认实例信息与状态

调用一次 `TCHouseXDescribeInstance`，从 `InstanceInfo` 一次性读取：版本、状态、以及 `VirtualClusters[]`（**已达节点级粒度**，含每个计算组的 `Resources[]` / `ClusterStateInfo` / `WarehouseInfo.CurrentRealtimeCpuResource`，等价于 `DescribeInstanceWarehouse` 的返回，无需额外调用）。

**判断逻辑**（Status 完整枚举与后端码映射见 [api-reference.md §2 Status 枚举清单](references/api-reference.md#status-枚举清单)）：

- ✅ `InstanceInfo.Status = "Serving"`（运行中） → 进入步骤 2
- ⚠️ 状态为变更过程中（`Init` / `Starting` / `Restarting` / `Suspending` / `Configuring` / `Changing` / `ScaleChanging` / `ScaleCountExpanding` / `ScaleCountReducing` / `ScaleSpecUpgrading` / `ScaleSpecDescending` / `GracefulServingScaleCountExpanding` / `Isolating` / `Renew` / `Cancelling` / `PrepareCancelling`）→ 告知用户当前状态，若 `VirtualClusters[].ClusterStateInfo` 中有 `FlowName` + `FlowProgress`，建议等待完成后再诊断
- ❌ 状态为 `Suspend`（已暂停）→ 记录为业务受影响状态，继续诊断
- ❌ 状态为 `Isolated`（已隔离）/ `CreateFail` / `DeleteFail` → 记录为高严重度异常，继续诊断以定位原因
- ❌ 状态为 `Deleted`（已销毁）/ `Deleting`（销毁中）→ 告知实例已下线/销毁中，无法诊断
- ❌ 调用失败（AuthFailure）→ 报告鉴权失败，提示检查权限
- ❌ 调用失败（ResourceNotFound）→ 检查 instance_id 格式（应为 `instance-` 前缀），格式错则修正重试，格式对则请用户确认
- ❌ 调用超时/网络错误 → 等 3 秒重试，最多 3 次；仍失败 → 告知用户服务暂时不可用

**记录信息**：Impala 内核版本、实例状态；从 `VirtualClusters[]` 中区分并记录：

- **基础服务组**（`VirtualCluster == "default-cluster"`）：其 `Resources[]` 承载 `catalogd`/`access-server`/`livy`/`chdfs`/`mysql` 等基础组件的节点信息；
- **用户计算组（Warehouse）**（其余项，`Component="impalad"`）：每个含 VC 标识、展示名、CN 节点数与规格、`ClusterStateInfo.InstanceState`、实时 CPU 资源占用。

### 步骤 2（条件触发）：按需补充 Warehouse 实时数据

**默认跳过**。步骤 1 的 `DescribeInstance.VirtualClusters` 已包含每个计算组的 `Resources`（各节点 IP/状态/规格）、`ClusterStateInfo`（含 `InstanceState`/`FlowProgress`）、`WarehouseInfo.CurrentRealtimeCpuResource`（实时 CPU 资源使用），可直接用于步骤 6 的分析。

**触发条件**（满足任一才调用 `TCHouseXDescribeInstanceWarehouse`，逐个 Warehouse 传 `VirtualCluster` = Warehouse 名称）：

1. 步骤 1 返回的 `InstanceInfo.VirtualClusters` 字段缺失或为空（旧版接口回退场景）；
2. 用户明确要求「最新一刻」的实时 CPU / 节点状态，怀疑 `DescribeInstance` 有缓存滞后；
3. 某个计算组 `ClusterStateInfo.InstanceState` 异常且需要交叉验证；
4. 仅关注单个 Warehouse 且希望减少 token 消耗。

**判断逻辑**（复用步骤 1 的 `VirtualClusters` 数据即可完成同样判断）：

- ✅ 所有 Warehouse 的 `ClusterStateInfo.InstanceState` 正常且各 `Resources[].Status` 均为运行中 → 进入步骤 3
- ❌ 存在非运行状态的 Warehouse → 标记为异常计算组，记录名称与状态，作为后续重点
- ❌ 存在非运行状态的 CN 节点 → 记录 IP、Component、Status，作为节点级异常

> 💡 **字段说明**：`Resources[].Status` 是**整数枚举**（非字符串），需按数值判断是否为运行状态。

- ❌ 某 Warehouse 有正在进行的流程（`FlowName` + `FlowProgress`）→ 告知用户变更中，可能影响该计算组的诊断
- ❌ 补调 `DescribeInstanceWarehouse` 失败 → 跳过该 Warehouse 的补充分析，回退到 `VirtualClusters` 中的已有数据继续诊断

### 步骤 3：获取实例资源使用量（存储 & 计算）

调用 `TCHouseXDescribeInstanceResourceUsage` 拉取时序资源使用量，用于判断存储/计算的趋势与容量风险。

**参数策略**：

- `Period`：**`Storage` 默认 `Day`**（存储是缓变量，Day 粒度足够做容量趋势/水位判断，同时规避已知的 Storage+Hour+24h 后端 InternalError）；`Warehouse` / `Serverless` / `BasicService` 默认 `Hour`；用户明确要求天/月粒度时按用户
- `ResourceType`：**至少调用两次**，分别传 `Storage`（存储用量）与 `Warehouse`（计算用量）；`Serverless` / `BasicService` 按需
- `StartTime`/`EndTime`：`Storage` 默认最近 7 天；其他资源类型默认最近 24 小时；格式 `YYYY-MM-DD HH:MM:SS`

> ⚠️ **字段大小写提醒**：本接口的实例 ID 入参名为 `InstanceID`（**全大写 ID**），与其他接口的 `InstanceId` 不同，详见 [api-reference.md §14](references/api-reference.md#14-tchousexdescribeinstanceresourceusage--查询资源使用量)。

> ⚠️ **已知后端问题**：`ResourceType=Storage` + `Period=Hour` + 24h 时间窗当前**大概率返回 `InternalError`**（同参数 `Warehouse` 正常）。默认策略已回避该组合；若命中错误，按下方降级路径处理，不要走通用 `InternalError` 的重试策略（重试无意义）。

**判断逻辑**：

- ✅ 数据成功返回 → 关联 `InstanceInfo.VirtualClusters[]` 中 `default-cluster` 及各 Warehouse 的 `Resources[]` 节点规格上限，计算使用率
- ⚠️ 存储用量在时间窗内快速增长（Day 粒度下增速 > 20%/日）→ 标记为容量风险，进入步骤 4 关联操作记录
- ❌ 存储用量接近节点磁盘上限（> 85%）→ 标记为严重，纳入根因分析
- ❌ **Storage 返回 `InternalError`**（已知后端问题）→ 不走通用重试；若当前 `Period=Hour`，改用 `Period=Day` + 近 7 天重试**一次**；仍失败 → 记录 `RequestId`，在报告"资源使用量摘要"中把 Storage 的"数据可用性"标为"不可用（后端 InternalError，RequestId=xxx）"，并回退到 `InstanceInfo.VirtualClusters[].Resources[]` 已有字段做单点水位快照（若可用），此时**扣分项 C 的 Storage 部分不计入**
- ❌ 其他调用失败（非 Storage+InternalError）→ 跳过对应资源类型的趋势分析，报告中标注"数据不可用"

### 步骤 4：检查最近集群操作记录

调用 `TCHouseXDescribeInstanceOperationsV1` 获取最近的集群操作记录（升降配、重启、配置变更、权限变更等）。

**参数策略**：

- `StartTime` / `EndTime`：默认最近 24 小时（用户描述有明确时间范围则按用户）
- `Limit`：50
- `OperateTimeOrder`：`desc`（最新操作优先）

**判断逻辑**：

- ✅ 存在近期变更操作（升降配/重启/配置变更）→ 记录时间点，作为"是否因变更引发故障"的重要线索
- ✅ 存在失败状态（`State` 表示失败）的操作 → 标记为异常事件，纳入诊断
- ✅ 无异常操作 → 继续步骤 5
- ❌ 调用失败 → 跳过此步骤，基于其他数据继续诊断

> 💡 **说明**：X 目前**没有独立的"告警/事件"接口**（不像 tchousec 有 `DescribeEventTasks`）。集群侧的告警需要用户在控制台/云监控配置外部告警渠道。本 Skill 通过"操作记录 + 资源使用量趋势 + SQL 运行历史"三方联查代偿。

> ⚠️ **关于监控指标数值的说明**：X 当前**没有独立的监控数值查询工具暴露**（不像 tchousec 有 `MonitorDescribeDashboardMetricData`）。监控指标的具体取值需结合控制台或云监控获取；本 Skill 在 X 上的诊断重点是**实例/Warehouse 状态 + 资源使用量 + 操作记录 + SQL 运行历史**。

### 步骤 5：拉取近期 SQL 运行记录

调用 `TCHouseXDescribeSqlHistoryRecords` 拉取最近 1 小时（或用户指定时间窗口）的 SQL 历史，是本 Skill 中 SQL 侧诊断的**主数据源**。重点关注：

- `State = EXCEPTION` 或 `CANCELED` 的失败 SQL
- `Duration > 60000`（即 > 60 秒）的慢 SQL
- 高频异常 QueryID（同一 SQL 反复失败）

**参数策略**：

- `QueryOnlineLogReq.StartTime` / `EndTime`：Unix 秒，用户指定优先；未指定 → 最近 1 小时
- `Limit`：50
- `ExecuteTimeOrder=desc`

**判断逻辑**（阈值详见 [诊断分析框架 §2.1](references/diagnosis-framework.md#21-sql-运行健康)）：

- ✅ **返回 0 条 SQL 记录** → 数据不足以评估 SQL 健康度，报告中注明「当前时间窗口内无 SQL 运行样本，SQL 健康度未纳入评分」，扣分项 D 不计入；建议用户扩大时间窗口或选择业务高峰时段复测（评分等级**不强制降级**）
- ✅ 失败/慢 SQL ≤ 5% → 健康
- ⚠️ 失败/慢 SQL 在 5%~20% → 标记可疑，纳入诊断
- ❌ 失败/慢 SQL > 20% → 标记严重，需立即排查
- ❌ 调用失败 → 跳过此步骤，仅基于步骤 1-4 给出有限诊断

### 步骤 5.5：定位单条问题 SQL 的 Profile（条件触发）

**触发条件**（满足任一）：

- 步骤 5 定位到具体的高频异常 QueryID（同一 SQL 多次失败或耗时 > 300 秒）
- 用户明确要求分析某条 SQL 或提供了 QueryID

调用 `TCHouseXDescribeInstanceProfile`（传 `QueryId`）获取详细 Profile：

- 解析 `ProfileStr` 定位耗时最长的算子（`ScanNode` / `HashJoinNode` / `AggregationNode` / `ExchangeNode`）
- 重点看是否走了 BROADCAST 大表、Runtime Filter 是否生效、`TotalBytesRead` 是否异常

**判断逻辑**：

- ✅ 成功 → 纳入根因分析，把定位到的耗时算子和数据倾斜/BROADCAST 等信号写入报告的"异常项详情与根因分析"；本 Skill 只做到 Profile 关键算子级别的定位，不进入更深层的 SQL 改写调优
- ❌ 调用失败 → 跳过此步骤

### 步骤 6：综合分析与生成报告

基于收集到的所有信息，按 [诊断分析框架 §4](references/diagnosis-framework.md#4-常见故障模式与根因分析) 进行根因分析，按 [报告模板](references/report-template.md) 生成诊断报告，评分遵循 [诊断分析框架 §3](references/diagnosis-framework.md#3-健康评分规则) 的 100 分制扣分体系。

**报告必须包含**：

1. 实例概况（ID/名称/内核版本/`Status`+`StatusDesc`/计算组数量/基础服务节点数/用户计算组节点数——**均从 `InstanceInfo.VirtualClusters` 中按 `default-cluster` vs 其他 VC 区分统计**）
2. 健康评分（🟢/🟡/🔴 + XX / 100 分 + 一句话总结 + **扣分明细表**）
3. 节点/计算组状态矩阵（**基础服务组** `default-cluster` 中各 `Component` 节点 + **每个用户计算组（Warehouse）** 的 CN 节点，字段来源 `VirtualClusters[].Resources[]`）
4. 资源使用量摘要（Storage / Warehouse）
5. 最近集群操作记录（如有）
6. SQL 运行情况摘要（近期失败/慢 SQL 占比、TopN 异常 QueryID）
7. 异常项详情与根因分析
8. 修复建议（按优先级排序：P0 防数据丢失 → P1 性能恢复 → P2 长期优化）

**多异常并发时的优先级排序**：

1. 先解磁盘（防数据丢失）
2. 再处理查询失败/异常堆积（恢复服务）
3. 再优化 CPU/内存与元数据（性能调优）

## 频率控制

| 限制                                       | 阈值                               | 说明                                                                     |
| ------------------------------------------ | ---------------------------------- | ------------------------------------------------------------------------ |
| 工具总调用频率                             | ≤ 15 次/分钟                       | 避免触发平台限流                                                         |
| TCHouseXDescribeSqlHistoryRecords 翻页     | ≤ 3 次/轮                          | 避免拉取过多历史                                                         |
| TCHouseXDescribeInstanceWarehouse 调用     | ≤ Warehouse 数（默认 0，条件触发） | 步骤 2 默认跳过；仅在 `VirtualClusters` 缺失/需实时数据/单 VC 精查时调用 |
| TCHouseXDescribeInstanceResourceUsage 调用 | ≤ 4 次/轮                          | 每种 ResourceType 一次（通常只用 2 种）                                  |
| TCHouseXDescribeInstanceProfile 调用       | ≤ 3 次/轮                          | 仅对 TopN 异常 QueryID 深入分析                                          |

**超限处理**：连续收到 `RequestLimitExceeded` → 等 5 秒重试，连续 3 次仍失败 → 降低调用频率，告知用户被限流。

## 错误码与处理策略

| 错误码/场景            | Agent 行为                                                                                                                                                                                                                                                                                                       |
| ---------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthFailure.*`        | 报告鉴权失败，提示用户检查实例访问权限                                                                                                                                                                                                                                                                           |
| `ResourceNotFound`     | 检查 ID 格式（`instance-` 前缀）；格式错 → 修正重试；格式对 → 请用户确认                                                                                                                                                                                                                                         |
| `InvalidParameter.*`   | 检查参数格式（时间格式、ResourceType 枚举等），尝试修正后重试 1 次；无法修正 → 报告                                                                                                                                                                                                                              |
| `UnsupportedRegion`    | 该地域未开通 TCHouseX 产品。**不重试、不自动切换地域**，必须调用 `ask_user` 让用户确认地域。详见 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问)                                                                                                            |
| `InternalError`        | **默认**：等 3 秒重试，最多 3 次；仍失败 → 报告错误码 + RequestId。**特例（`DescribeInstanceResourceUsage` + `ResourceType=Storage`）**：不走默认重试；若当前 `Period=Hour`，改 `Period=Day` + 近 7 天重试**一次**；仍失败 → 记录 RequestId，在报告中标注 Storage 时序数据不可用，扣分项 C 的 Storage 部分不计入 |
| `RequestLimitExceeded` | 等 5 秒重试；连续 3 次 → 降低频率，告知被限流                                                                                                                                                                                                                                                                    |
| Warehouse 详情获取失败 | 跳过该计算组的详细分析，基于实例级信息给出有限诊断                                                                                                                                                                                                                                                               |
| 节点/资源用量获取失败  | 跳过对应分析，基于其他数据给出有限诊断                                                                                                                                                                                                                                                                           |
| SQL 历史获取失败       | 跳过 SQL 侧诊断，扣分项 D 不计入，报告注明"SQL 侧数据不可用"                                                                                                                                                                                                                                                     |
| Profile 获取失败       | 跳过单条 SQL 深入分析，仅基于历史记录的元信息给出建议                                                                                                                                                                                                                                                            |
| 网络超时               | 等 3 秒重试，最多 3 次；仍失败 → 告知用户服务暂时不可用                                                                                                                                                                                                                                                          |
| 兜底（未列出错误码）   | 报告完整错误信息 + RequestId                                                                                                                                                                                                                                                                                     |

## 安全规则

1. **本 Skill 为纯只读诊断**：所有工具调用都是查询类云 API（Describe），不涉及任何写操作，无需用户确认即可执行
2. **不使用 DMS/SQL 工作区类接口**：本 Skill **禁止**调用 `TCHouseXAuthorizedSql` / `TCHouseXExecuteSqlV1` 等需要用户账号/密码的接口，避免打扰用户输入凭据；所有 SQL 侧信息通过 `DescribeSqlHistoryRecords` + `DescribeInstanceProfile` 管控面接口获得
3. **修复建议仅为建议**：涉及 `KILL QUERY`、重启、配置变更、扩缩容、`INVALIDATE METADATA`、`COMPUTE STATS`、`DROP PARTITION` 等操作时，仅输出建议和步骤，**不直接执行**；需明确告知影响范围和建议执行时间（低峰期）
4. **敏感信息控制**：节点 IP、配置内容等仅在诊断报告中展示，不在非必要场景暴露
5. **数据量控制**：SQL 历史查询时间范围不超过 7 天；资源使用量查询 Period 匹配时间跨度（超过 7 天用 Day 而非 Hour），避免 token 消耗过大
6. **关联诊断边界**：本 Skill 只做集群级健康诊断与 Profile 关键算子级根因定位；单条 SQL 的深度性能调优、SQL 改写、执行计划优化不在范围内，需要更深层次的慢 SQL 分析时告知用户由专门的慢 SQL 诊断能力承接
7. **SR 接口边界**：明确不调用 SR（StarRocks）系列接口（`ValidateSRLocalUser` / `DescribeSRInstancesHealthStateV1` 等）

## 经验沉淀库

| 经验                                                                     | 置信度 | 说明                                                                                                          |
| ------------------------------------------------------------------------ | ------ | ------------------------------------------------------------------------------------------------------------- |
| 存储告警最常见原因是历史分区未清理或 Parquet 小文件过多                  | ⭐⭐⭐ | 检查表分区保留策略，定期 `INSERT OVERWRITE` 重写小文件分区，建议单文件 ≥ 256MB                                |
| CPU 持续高负载通常伴随长时间运行查询                                     | ⭐⭐⭐ | 通过 `DescribeSqlHistoryRecords` 找 `Duration > 60000ms` 的 QueryID，建议 `KILL QUERY <query_id>` 疏散        |
| 内存 OOM（MEMORY_LIMIT_EXCEEDED）多因单查询数据扫描过大或 BROADCAST JOIN | ⭐⭐   | 通过 `DescribeInstanceProfile` 查 ProfileStr 确认大表是否走了 BROADCAST；建议调整 `MEM_LIMIT`                 |
| Catalog 服务异常会导致元数据更新失败                                     | ⭐⭐   | 表现为新建表/修改表延迟生效；建议 `INVALIDATE METADATA <db>.<tbl>` 单表刷新，避免全库 `INVALIDATE METADATA`   |
| 计算组（Warehouse）资源不均衡需要按业务隔离                              | ⭐⭐⭐ | 在线分析和批量 ETL 走同一 Warehouse 会互相影响；用 `DescribeInstanceWarehouse` 查 CN 数与实时 CPU，按业务拆分 |
| 集群变更（升降配 / 重启）后短时间内的故障，优先关联操作记录              | ⭐⭐⭐ | `DescribeInstanceOperationsV1` 里的操作时间与故障时间比对，超过 80% 短时故障可归因于变更                      |
| Parquet 表统计过期会导致优化器选错 JOIN 顺序                             | ⭐⭐   | 建议对高频查询表定期 `COMPUTE STATS <db>.<tbl>`，尤其是数据量变化 > 10% 时                                    |
