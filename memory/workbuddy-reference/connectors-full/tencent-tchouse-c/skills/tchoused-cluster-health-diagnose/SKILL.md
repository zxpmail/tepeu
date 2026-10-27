---
name: 腾讯云TCHouse-D 集群健康诊断与故障排查
description: >
  TCHouse-D（Doris）集群健康诊断与故障排查 Skill。AI 拉取集群监控指标（CPU/内存/磁盘/IOPS/连接数/Tablet 副本健康度）、告警历史及节点状态，输出集群健康评分，定位异常根因，并给出修复建议（参数调整/Compaction 调优/节点重启等）。
  触发词：集群健康、健康检查、巡检、节点故障、磁盘告警、CPU告警、内存告警、OOM、磁盘满、磁盘空间不足、节点异常、副本异常、Tablet异常、查询堆积、连接数满、集群状态、cluster health、node failure、disk alert、replica unhealthy、集群诊断、故障排查、监控异常、告警处理、节点状态、资源告警、性能瓶颈、IO高、网络异常、FE异常、BE异常、BE Down、Compaction压力、cdwdoris、TCHouse-D、Doris集群。
  本 Skill 包含 3 个子能力：①集群全面健康巡检 ②告警事件排查与根因定位 ③节点故障诊断与修复建议。
  何时不触发：慢 SQL 诊断与自动调优（单条 SQL 性能分析）、集群选型与架构推荐、智能建表与数据建模、NL2SQL、AI Function 智能编排、集群扩缩容操作、权限管理、数据导入导出等非集群健康/故障相关问题不走本 Skill。
allowed-tools:
  - TCHouseDDescribeInstance
  - TCHouseDDescribeInstanceNodes
  - TCHouseDDescribeEventTasks
  - TCHouseDDescribeClusterConfigs
  - TCHouseDDescribeOnlineQueryLoads
  - MonitorGetMonitorData
  - ask_user # WorkBuddy 中为 AskUserQuestion
---

# 集群健康诊断与故障排查

## 概述

本 Skill 提供 TCHouse-D（Doris）集群的健康诊断与故障排查能力，包含三个子能力：

1. **集群全面健康巡检**：拉取监控指标、节点状态、告警事件，输出健康评分和全面诊断报告
2. **告警事件排查与根因定位**：针对具体告警事件（磁盘/CPU/内存/Tablet 副本异常），定位根因并给出修复建议
3. **节点故障诊断与修复建议**：针对节点异常（FE/BE 宕机/不可达/资源耗尽），分析原因并给出恢复方案

## 依赖与运行环境

本 Skill 的所有调用通过 MCP Tool 完成（云 API 类工具由平台封装为 MCP Tool，Agent 直接调用工具名即可）。

**依赖工具清单**：

| #   | Tool 名称                        | 能力定位                                             | 参考文档                                                         |
| --- | -------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------- |
| 1   | TCHouseDDescribeInstance         | 集群基本信息获取（含状态与流程进度 `StateInfo`）     | [参考](references/tchoused-describe-instance.md)           |
| 2   | TCHouseDDescribeInstanceNodes    | 节点列表与状态（FE/BE/CN）                           | [参考](references/tchoused-describe-instance-nodes.md)     |
| 3   | TCHouseDDescribeEventTasks       | 告警/事件历史查询                                    | [参考](references/tchoused-describe-event-tasks.md)        |
| 4   | TCHouseDDescribeClusterConfigs   | 集群配置参数获取                                     | [参考](references/tchoused-describe-cluster-configs.md)    |
| 5   | TCHouseDDescribeOnlineQueryLoads | 当前运行中的查询负载                                 | [参考](references/tchoused-describe-online-query-loads.md) |
| 6   | MonitorGetMonitorData            | 集群监控指标查询（Monitor 产品，Doris 指标集）       | [参考](references/monitor-get-monitor-data.md)             |
| 7   | ask_user                         | 向用户询问确认信息（WorkBuddy 中为 AskUserQuestion） | —                                                                |

## 凭证 / 环境变量

- `instance_id`：从会话 context 的 X-Context header 自动注入
- `region_id`：从会话 context 的 X-Context header 自动注入（可能是 `RegionId` 数字，也可能是 `Region` 字符串，也可能是中文地域名）
- 若以上参数缺失，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）询问用户

> ⚠️ **地域参数强制规则**：**任何工具调用前**都必须先按 [地域映射表](references/region-mapping.md) 把上下文中的地域信息补齐为 **`Region`（字符串）+ `RegionId`（数字）** 两种形式，禁止凭记忆填写。不同工具对参数形式的要求见 [工具传参形式速查](references/region-mapping.md#工具传参形式速查)：
>
> - `TCHouseDXxx` 系工具：**只传 `Region` 字符串**
> - `MonitorGetMonitorData`：**同时传 `Region` 字符串 和 `RegionId` 数字**

> 💡 **多平台兼容说明**：本文档中所有提到的 `ask_user` 工具，在 WorkBuddy 平台中对应为 `AskUserQuestion`。后文不再重复标注。

## 核心工作流

### 步骤 0：参数确认

**必需参数**：

- `instance_id`（集群 ID，格式：`cdwdoris-xxxxxxxx`）
- `region_id`（地域）

**可选参数**（从用户问题中提取，缺失时使用默认值，不自行假设）：

- 时间范围：未指定 → 默认最近 24 小时
- 具体告警事件或节点 IP：从用户问题中提取

**判断逻辑**：

- ✅ 参数齐全 → **强制**按 [地域映射表](references/region-mapping.md) 把地域补齐为 `Region`（字符串）+ `RegionId`（数字）两种形式（任何输入形式都要过这一步：中文名、英文串、数字 ID 都不例外），补齐后进入步骤 1
- ❌ `instance_id` 或 `region_id` 缺失 → 调用 `ask_user` 询问
- ❌ 地域信息在映射表中匹配不到（或大区模糊，如"华南地区"）→ 调用 `ask_user` 确认后再补齐

### 步骤 1：确认集群信息与状态

本步骤为后续所有诊断的**前置基线**：调用一次 `TCHouseDDescribeInstance` 即可同时拿到全局静态信息（版本/规格/HA/付费模式/冷热分层）与运行状态（是否 Serving、有无变更流程，由返回中的 `InstanceInfo.StateInfo` 子对象提供）。

> 💡 不再额外调用 `DescribeInstanceState`：`DescribeInstance` 返回的 `InstanceInfo.StateInfo` 已包含 `InstanceState/InstanceStateDesc/FlowName/FlowProgress/FlowMsg` 等流程进度字段，与 `DescribeInstanceState` 输出一致。

#### 步骤 1.1：DescribeInstance（全局静态信息）

**必须提取并贯穿全流程的关键字段**：

| 字段                                                                             | 用途                                                                   |
| -------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| `InstanceInfo.InstanceName`                                                      | 报告标题与上下文展示                                                   |
| `InstanceInfo.Status` / `StatusDesc`                                             | 集群整体状态（与步骤 1.2 的 `StateInfo.InstanceState` 交叉校验）       |
| `InstanceInfo.Version` / `BuildVersion`                                          | Doris 内核版本（如 `doris-2.0.3-rc06`）；决定可用功能与已知 Bug        |
| `InstanceInfo.Region` / `Zone`                                                   | 地域/可用区，用于跨 AZ 部署判断与监控接口入参                          |
| `InstanceInfo.PayMode`                                                           | 付费模式：`PREPAID` / `POSTPAID_BY_HOUR`；影响告警/恢复时效建议        |
| `InstanceInfo.ExpireTime`                                                        | 包年包月到期时间；临近到期需提示续费                                   |
| `InstanceInfo.MasterSummary` (Spec/NodeSize/Core/Memory/Disk/DiskType/DiskCount) | FE 规格与数量，用于资源瓶颈判断                                        |
| `InstanceInfo.CoreSummary` (Spec/NodeSize/Core/Memory/Disk/DiskType/DiskCount)   | BE 规格与数量，用于资源瓶颈判断                                        |
| `InstanceInfo.HA` / `HaType`                                                     | 是否高可用（0-非HA / 1-FE HA / 2-FE+BE HA）；影响副本与可用性建议      |
| `InstanceInfo.Kind`                                                              | `STANDARD` 存算一体 / `SSC` 存算分离；决定是否拉 CN 节点与冷热分层指标 |
| `InstanceInfo.HasClsTopic`                                                       | 是否启用日志服务；决定能否引用 CLS 日志做根因                          |
| `InstanceInfo.Components`                                                        | 各组件版本（FE/BE/Broker），用于版本兼容性判断                         |

#### 步骤 1.2：StateInfo（运行状态，从同次 `DescribeInstance` 返回中提取）

**必须从 `InstanceInfo.StateInfo` 提取的关键字段**：

| 字段                          | 用途                                                                 |
| ----------------------------- | -------------------------------------------------------------------- |
| `StateInfo.InstanceState`     | `Init` / `Serving` / `Isolated` / `Changing`，是否允许进入诊断       |
| `StateInfo.InstanceStateDesc` | 中文描述，直接用于报告与用户提示                                     |
| `StateInfo.FlowName`          | 当前正在进行的操作（如 `scale_out` / `restart`），与诊断冲突时需暂停 |
| `StateInfo.FlowProgress`      | 操作进度（0-1），辅助判断「等多久再来」                              |
| `StateInfo.FlowMsg`           | 流程错误信息；非 `Serving` 时是首要根因线索                          |

#### 判断逻辑

- ✅ `Status=Serving` **且** `StateInfo.InstanceState=Serving` → 进入步骤 2
- ❌ `Status` 或 `StateInfo.InstanceState` 为 `Init`/`Changing` → 告知用户集群当前状态，说明可能的影响；若有正在进行的流程（`FlowName` + `FlowProgress`），建议等待完成后再诊断
- ❌ `Status` 或 `StateInfo.InstanceState` 为 `Isolated` → 告知用户集群已隔离（一般为欠费/违规），建议联系腾讯云客服
- ❌ 两个状态字段不一致 → 以 `StateInfo.InstanceState` 为准（更实时），并在报告中注明信息不一致
- ❌ 调用失败（AuthFailure）→ 报告鉴权失败，提示检查权限
- ❌ 调用失败（ResourceNotFound）→ 检查 instance_id 格式（应为 `cdwdoris-` 前缀），格式错则修正重试，格式对则请用户确认
- ❌ 调用超时/网络错误 → 等 3 秒重试，最多 3 次；仍失败 → 告知用户服务暂时不可用

**贯穿全流程的基线信息**：把上述 1.1 / 1.2 提取到的字段作为本轮诊断的全局上下文，后续步骤的判断、阈值选择、修复建议都需以此为前提。例如：

- `Kind=SSC`（存算分离）→ 步骤 2 必须额外拉 CN 节点；步骤 4 需补充冷热分层缓存指标（`FileCache*`）
- `HaType=0`（非 HA）→ 不能给出"FE master 切换/Follower 选举"类建议；副本数与可用性建议要相应保守
- `BuildVersion` → 给具体修复指令前先核对版本是否支持（如 `ADMIN COMPACT`、新版 sysvar）
- `PayMode=POSTPAID_BY_HOUR` → 重启/扩缩容建议可更激进；`PREPAID` 临近 `ExpireTime` 需额外提示续费

### 步骤 2：获取节点状态矩阵

调用 `TCHouseDDescribeInstanceNodes` 获取所有节点详细信息：

- 调用入参 `NodeRole` 取值：`fe` / `be` / `cn`，按需分别拉取（如为存算一体则无 `cn`）
- 使用 `DisplayPolicy=All` 获取全量节点

#### 节点 IP 提取规则（关键）

从返回的 `InstanceNodesList` 中按以下字段语义提取 IP，**后续步骤 4 的监控调用将完全依赖这套规则**：

| 变量       | 提取条件                                                                             | 用途                                                  |
| ---------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------- |
| `feIps`    | 所有 `Role === "master"` 的 `Ip`                                                     | FE 节点级指标（FeData 类）                            |
| `beIps`    | 所有 `Role === "core"` 的 `Ip`                                                       | BE 节点级指标（BeData 类）                            |
| `cnIps`    | 所有 `Role === "cn"` 的 `Ip`（仅存算分离）                                           | CN 节点级指标（复用 BeData 类，前端会过滤掉部分指标） |
| `leaderIp` | `InstanceNodesList.find(n => ["MASTER","FOLLOWER","leader"].includes(n.FeRole))?.Ip` | BusinessData 类指标专用（FE Leader 单 IP）            |

> ⚠️ **极易踩的坑**：`DescribeInstanceNodes` 返回里 `Role` 字段对 FE 节点取值是 `"master"`（这是节点角色级的历史命名，含义为「FE 类节点」），**和「FE 集群是否有 Leader」无关**。FE 内部的 leader / follower / observer 由独立字段 `FeRole` 区分（取值 `MASTER` / `FOLLOWER` / `OBSERVER` / `leader`）。判断「集群是否无 FE Leader」必须使用 `FeRole === "MASTER"` 的节点是否存在，**禁止**用 `Role === "master"` 反推。

**判断逻辑**：

- ✅ 所有节点状态为 `Running` → 继续步骤 3
- ❌ 存在非 `Running` 状态的节点 → 标记为异常节点，记录 IP 和状态，作为后续重点关注对象
- ❌ FE 节点存在但 `FeRole === "MASTER"` 的节点不存在（即 `leaderIp` 为空）→ 严重告警，集群可能不可用
- ❌ 调用失败 → 跳过节点级分析，基于集群级数据（事件、配置）给出有限诊断建议

**记录信息**：`feIps` / `beIps` / `cnIps` / `leaderIp`、规格（Core/Memory/DiskSize）、磁盘类型（DiskType）、FE 各节点的 `FeRole`（MASTER/FOLLOWER/OBSERVER）。

#### 步骤 2.1：FE HA 拓扑硬校验（必做项，P1 级）

> 背景：曾出现「1 MASTER + 2 OBSERVER + 0 FOLLOWER」名义 HA、实际不闭环（OBSERVER 不参与选举，MASTER 挂掉后无法选出新 Leader）的真实集群。此校验必须显式执行，不依赖人工注意。

**步骤**：基于步骤 2 收集到的 FE 各节点 `FeRole`，统计 (MASTER, FOLLOWER, OBSERVER) 三元组数量，记为 `(M, F, O)`。

**硬规则**：

- ✅ `M + F ≥ 3` **且** `M + F` 为奇数 → HA 拓扑闭环，通过
- ❌ `M + F < 3` **或** `M + F` 为偶数 → **直接标记 P1 异常项**「FE HA 拓扑不闭环：M=x, F=y, O=z，参与选举节点 (M+F) 不足或非奇数」，在报告「异常项详情」中独立成项，并按下表「畸形拓扑修复动作库」输出对应的固定修复文案
- ⚠️ `HaType` 字段标称 HA 但 `M + F < 3` → 在报告中显式标注「名义 HA / 实际不闭环」
- 单节点 FE（`HaType=0` 且 FE 节点数=1）→ 跳过本规则，不报 P1

> 注意：`M` 必须用 `FeRole === "MASTER"`（大写）的节点数统计；不要用 `Role === "master"`（小写，含义是「FE 类节点」）。

**畸形拓扑修复动作库（固定文案，报告中直接引用）**：

| 拓扑模式 (M, F, O)               | 判定                 | 固定修复文案（P1，二选一给出，让用户按运维习惯选）                                                                                                                                                                                                                                                                  |
| -------------------------------- | -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `M=1, F=0, O≥2`（名义HA/不闭环） | 🔴 P1 不闭环         | **方案 A（推荐，无需扩容）**：将 2 个 OBSERVER 中的 2 台转换为 FOLLOWER，最终 (M, F, O) = (1, 2, O-2)，M+F=3 闭环。<br>**方案 B（保留 OBSERVER 只读能力）**：新增 2 个 FOLLOWER 节点，最终 (M, F, O) = (1, 2, 2)，M+F=3 闭环；额外承担新节点费用。<br>操作路径：控制台 → 集群详情 → 节点管理 → 变更角色 / 扩容 FE。 |
| `M=1, F=1, O=*`                  | 🔴 P1 不闭环（偶数） | 需再加 **1 个 FOLLOWER**，最终 (M, F, O) = (1, 2, O)，M+F=3 闭环。<br>操作路径：控制台 → 集群详情 → 节点管理 → 扩容 FE（角色 FOLLOWER，规格与现有 FE 一致）。                                                                                                                                                       |
| `M=1, F=2, O=*`                  | ✅ 已闭环            | 无需处理；OBSERVER 仅承担只读扩展，不影响选举。                                                                                                                                                                                                                                                                     |
| 其他 `M+F` 偶数或 <3 的组合      | 🔴 P1 不闭环         | 通用建议：补齐 FOLLOWER 至 `M+F` 为 ≥3 的最小奇数（通常为 3），并保持 FE 规格一致。                                                                                                                                                                                                                                 |

> ⚠️ **修复动作输出规则**：报告「异常项详情」中该项必须**同时**输出「当前拓扑三元组」+「上表命中的固定文案」，不得自由发挥或省略方案。

### 步骤 3：检查告警/事件历史

调用 `TCHouseDDescribeEventTasks` 获取集群的告警和事件历史。

**参数策略**：

- 时间范围：根据用户描述确定，未指定则默认最近 7 天
- 时间格式：`YYYY-MM-DD HH:MM:SS`（如 `2026-06-11 20:00:00`）
- 状态过滤：优先查看待处理（Status=1）和处理中（Status=3）的事件
- 排序：按 `create_time` 降序，最新事件优先

**判断逻辑**：

- ✅ 存在未处理的高严重度事件（如 `BeDown`/`FeOom`/`BeOom`/`DiskHigh`）→ 优先分析该事件，作为诊断重点，进入步骤 4
- ✅ 无告警事件 → 继续全面巡检流程（步骤 4）
- ❌ 调用失败 → 告知用户指定时间范围内事件查询失败，继续基于监控指标进行主动巡检
- ❌ 返回为空 → 告知用户无告警事件，继续基于监控指标巡检

**告警严重度分级**详见 [诊断分析框架](references/diagnosis-framework.md#告警事件分级)。

### 步骤 4：拉取监控指标

调用 `MonitorGetMonitorData` 获取集群核心监控指标。

> ⚠️ 该接口属于 Monitor 产品（Namespace=`QCE/CDWDRS`），MetricName 需使用 `CloudMonitorApiName` 的**驼峰格式**。
> **所有指标都必须传 `NodeIps`**——业务监控（BusinessData）也要传 IP，只是固定取 FE Leader 单 IP。`NodeIps` 的取值由步骤 2 的「节点 IP 提取规则」决定。
> **一次调用只查一个指标**（`MetricName` 是单数；如需多指标，按指标循环调用）。
> 时间格式：`YYYY-MM-DD HH:mm:ss`（如 `2026-06-11 20:00:00`），与 `TCHouseDDescribeEventTasks` 的格式一致。

**时间范围策略**：

- 用户指定了时间 → 按用户要求
- 用户说"刚才"/"刚刚" → 最近 1 小时（瞬时窗）
- 用户说"今天" → 当天 0 点到当前
- **未指定 → 默认双窗：1h（瞬时，Period=60） + 24h（趋势，Period=300）**，两份都拉，分别用于「即时异常定位」与「日内趋势 / Compaction 堆积 / 慢周期」识别。报告「监控」段必须分两栏分别呈现

> ⚠️ **强制双窗的原因**：单 1h 窗在巡检场景下看不到 Compaction 堆积速率、慢查询周期、磁盘增长斜率等趋势性问题，易遗漏 P1 隐患。如果总调用预算告急（参见「频率控制」），优先保留瞬时窗、降级 24h 趋势窗的指标粒度（仅拉 CPU/内存/磁盘/Compaction Score 这 4 类核心趋势）。

**推荐查询策略**：

> ⚠️ **前置条件**：步骤 2 已通过 `TCHouseDDescribeInstanceNodes` 提取出 `feIps` / `beIps` / `cnIps` / `leaderIp`。

> 📌 **指标分组与 IP 入参映射**（与 `aa.json` 的 `DescribeMonitorMeta` 分组一致）：
>
> | 指标分组（aa.json）                                                                                                                                                                          | NodeRole | NodeIps                                                                |
> | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ---------------------------------------------------------------------- |
> | `FeData`（FE 节点级 Doris\* / Jvm\* / 通用 Cpu/Mem/Disk/Node\* / `FeUp` / `BrokerUp`）                                                                                                       | `fe`     | `feIps`（多 IP，逗号分隔）                                             |
> | `BeData`（BE 节点级 Doris\* / `FileCache*` / 通用 Cpu/Mem/Disk/Node\* / `BeUp` / `BrokerUp`，含 `DorisFeTabletNum`、`DorisFeTabletMaxCompactionScore` 这两个名字带 Fe 但归属 BeData 的指标） | `be`     | `beIps`（多 IP）                                                       |
> | `BeData`（CN 节点，存算分离时）                                                                                                                                                              | `cn`     | `cnIps`（多 IP）                                                       |
> | `BusinessData`（仅 `DorisFeQuerySlow`、`DorisFeTabletStatusCountTypeUnhealthy`）                                                                                                             | `fe`     | `leaderIp`（单 IP）                                                    |
> | `CcrData`                                                                                                                                                                                    | —        | **本 Skill 未使用**（业务前端监控面板未消费 CCR 指标，且诊断主题无关） |

> ⚠️ **指标命名说明**：以下指标名均来自 Monitor 的 `CloudMonitorApiName`（驼峰格式），可直接传入 `MonitorGetMonitorData` 的 `MetricName` 字段（一次一个指标）。

1. **第一步：查节点存活与基础资源**（按 `NodeRole` + `NodeIps` 拉取）：
   - 存活：FE 节点 → `FeUp`；BE 节点 → `BeUp`；Broker → `BrokerUp`
   - 基础资源：`CpuUsage`、`MemUsage`、`DiskUsage`、`SystemDiskUsage`、`NodeLoad1`
2. **第二步：发现异常后下钻细化**：
   - CPU 异常 → 查 `CpuUsage`、`NodeLoad1`、`NodeLoad5`、`NodeLoad15`
   - 内存异常（BE）→ 查 `MemUsage`、`DorisBeMemoryAllocatedBytes`
   - 内存异常（FE JVM）→ 查 `JvmHeapSizeBytesUsed`、`JvmHeapSizeBytesMax`、`JvmNonHeapSizeBytesUsed`、`JvmOldGcCountV2`、`JvmOldGcTimeV2`、`JvmYoungGcCountV2`、`JvmYoungGcTimeV2`、`JvmThreadBlockedCount`、`JvmThreadWaitingCount`（堆使用率 = `JvmHeapSizeBytesUsed` / `JvmHeapSizeBytesMax`，需自行计算）
   - 磁盘容量 → 查 `DiskUsage`、`SystemDiskUsage`、`DorisBeDisksLocalUsedCapacity`、`DorisBeDisksRemoteUsedCapacity`、`DorisBeDisksTrashUsedCapacity`
   - 磁盘 IO → 查 `NodeDiskReadsCompletedTotal`、`NodeDiskWritesCompletedTotal`、`NodeDiskReadBytesTotal`、`NodeDiskWrittenBytesTotal`
   - 网络异常 → 查 `NodeNetworkReceiveBytesTotal`、`NodeNetworkTransmitBytesTotal`
3. **第三步：检查 Doris 业务指标**（按上表区分 IP 来源）：
   - FE 查询负载（FeData，`NodeIps=feIps`）→ `DorisFeQps`、`DorisFeRps`、`DorisFeConnectionTotalV2`、`DorisFeQueryInstanceNum`、`DorisFeQueryErrRate`
   - FE 查询负载（**BusinessData**，`NodeIps=leaderIp` 单 IP）→ `DorisFeQuerySlow`

> ⚠️ **QPS / 连接数「全 0」判定规则（必做项，P2 级）**：
>
> 对 `DorisFeQps` 与 `DorisFeConnectionTotalV2` 两个指标，必须显式判定「全 0」场景，禁止交给自由发挥：
>
> | 24h 趋势窗表现                                                          | 判定                  | 报告输出                                                                                                                                                                                                                            |
> | ----------------------------------------------------------------------- | --------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
> | 近 24h `DorisFeQps` **全为 0** 且 `DorisFeConnectionTotalV2` **全为 0** | ⚠️ **集群用途待确认** | 「监控」段标注「QPS/连接数 24h 全 0，无法自动区分『日志/备用/离线归档集群正常』与『业务停摆异常』」；「结论」段调用 `ask_user` 询问集群实际用途，用户确认为日志/备用则不计入异常，用户确认为在线业务则升为 🔴 P1 异常项「业务停摆」 |
> | 24h 内曾有 QPS/连接，但**突然掉 0** 且持续 ≥ 15 分钟                    | 🔴 **P1 业务侧异常**  | 「异常项详情」独立成项「业务侧查询突然停止（掉零时间点：`YYYY-MM-DD HH:mm`）」；结合步骤 3 告警事件、步骤 2 FE Leader 状态给出根因假设（FE Leader 切换 / 业务端故障 / 网络阻断）                                                    |
> | 24h 内 QPS/连接持续有值                                                 | ✅ 正常               | 按常规趋势分析记录                                                                                                                                                                                                                  |
>
> **禁止行为**：在报告中出现「QPS=0，如属于日志归档用途属正常，否则需排查」这类把判定甩给读者的兜底表述——判定必须由 Skill 自身完成（正常 / 待确认 / 异常 三选一）。
>
> 完整排查路径与修复建议参见 [诊断分析框架 §4.7](references/diagnosis-framework.md#47-业务查询全-0qps--连接数--0)。

- FE 查询延迟（FeData，`NodeIps=feIps`）→ `DorisFeQueryLatencyMsQuantile075`、`DorisFeQueryLatencyMsQuantile095`、`DorisFeQueryLatencyMs098`、`DorisFeQueryLatencyMsQuantile099`、`DorisFeQueryLatencyMsQuantile0999`
- 副本/Tablet 健康度（**BusinessData**，`NodeIps=leaderIp` 单 IP）→ `DorisFeTabletStatusCountTypeUnhealthy`
- 副本/Tablet 健康度（FeData，`NodeIps=feIps`）→ `DorisFeScheduledTabletNum`、`DorisFeReportQueueSize`、`DorisFePublishTxnNum`
- 副本/Tablet 健康度（BeData，`NodeIps=beIps`）→ `DorisFeTabletNum`（名字带 Fe 但 aa.json 归属 BeData，由 BE 上报转送）
- Compaction 压力（BeData，`NodeIps=beIps`）→ `DorisBeTabletCumulativeMaxCompactionScore`、`DorisBeTabletBaseMaxCompactionScore`、`DorisBeCompactionWaittingPermits`、`DorisBeCompactionUsedPermitsV2`
- Compaction 压力（FE 汇总，FeData，`NodeIps=feIps`）→ `DorisFeMaxTabletCompactionScore`
- Compaction 压力（BeData 汇总，`NodeIps=beIps`）→ `DorisFeTabletMaxCompactionScore`（名字带 Fe 但归属 BeData）

> ⚠️ **Compaction Score 指标降级策略**（新版本内核踩坑）：`DorisBeTabletCumulativeMaxCompactionScore`、`DorisBeTabletBaseMaxCompactionScore`、`DorisFeMaxTabletCompactionScore`、`DorisFeTabletMaxCompactionScore` 在部分较新内核版本（如 `doris-3.0.x`）上会返回 `Timestamps=[]` / `Values=[]` 空数据（指标名已废弃或聚合口径变更）。
>
> **降级路径（按顺序尝试）**：
>
> 1. 若 `DorisBeTabletCumulativeMaxCompactionScore` 与 `DorisBeTabletBaseMaxCompactionScore` 都空 → 改拉 `DorisBeCompactionWaittingPermits` + `DorisBeCompactionUsedPermitsV2` 作为 Compaction 压力代理指标（有排队即压力大）
> 2. 若上述两个也空 → 结合步骤 6 的 BE 配置 `tablet_max_compaction_score` / `compaction_task_num_per_disk` 做静态判断
> 3. 若用户开启了 CLS 日志（`InstanceInfo.HasClsTopic=true`）→ 建议用户在 CLS 中检索 `be.INFO` 的 `max compaction score` 关键字取实际值
> 4. 以上都不可行 → 在报告「监控」段显式标注「Compaction Score 指标在当前内核版本 `{BuildVersion}` 上返回空数据，已降级为 Permits/配置/日志三选一」，**禁止**在报告里挂一个「无数据上报」的空洞而不给出下一步动作。

- BE 导入压力 → `DorisBeStreamingLoadCurrentProcessing`、`DorisBeRoutineLoadTaskCount`、`DorisBeTabletWriterCount`、`DorisBeLoadBytesV2`、`DorisBeLoadRowsV2`
- BE 查询执行 → `DorisBeFragmentInstanceCount`、`DorisBeFragmentRequestsTotalV2`、`DorisBeFragmentThreadPoolQueueSize`、`DorisBeTimeoutCanceledFragmentCountV2`
- BE 线程池压力 → `DorisBeProcessThreadNum`、`DorisBeLocalScanThreadPoolQueueSize`、`DorisBeRemoteScanThreadPoolQueueSize`、`DorisBeHeavyWorkPoolQueueSize`、`DorisBeLightWorkPoolQueueSize`
- BE 元数据/句柄 → `DorisBeAllRowsetsNum`、`DorisBeAllSegmentsNum`、`DorisBeUnusedRowsetsCountV2`、`DorisBeProcessFdNumUsedV2`
- 冷热分层缓存（如适用）→ `FileCacheCapacity`、`FileCacheCacheSize`、`FileCacheHitRatio`、`FileCacheHitRatio5m`、`DorisBeUploadTotalByte`

**指标分组查询**：按 [诊断分析框架](references/diagnosis-framework.md#监控指标阈值) 中的指标列表和阈值进行判断。

**判断逻辑**：

- ✅ 所有指标正常 → 进入步骤 7（综合分析）
- ❌ 发现 CPU 高或查询堆积 → 进入步骤 5（检查运行查询） → 参考经验沉淀库 **#2**
- ❌ 发现磁盘容量告警（`DiskUsage` 高 / `DorisBeDisksLocalUsedCapacity` 接近上限）→ 报告中记录并优先处理 → 参考经验沉淀库 **#1**
- ❌ 发现配置相关问题（内存不足/并发过高/Compaction 慢）→ 进入步骤 6（检查配置）→ 参考经验沉淀库 **#4** / **#5** / **#6** / **#7**
- ❌ 发现副本异常（`DorisFeTabletStatusCountTypeUnhealthy` > 0）→ 在报告中记录副本不健康数量与所在 BE，结合节点状态（步骤 2）与告警事件（步骤 3）给出根因假设 → 参考经验沉淀库 **#3** / **#8**
- ❌ 监控数据缺失（采集中断）→ 标注数据缺失时段，基于可用数据做分析，报告中注明
- ❌ 调用失败 → 等 3 秒重试，最多 3 次；仍失败 → 跳过监控分析，基于节点状态和事件数据给出有限诊断

### 步骤 5：检查当前运行查询（条件触发）

**触发条件**（满足任一即触发）：

- 步骤 4 发现 CPU 高或查询堆积
- 步骤 4 调用失败（监控数据不可用时，作为补充信息源）

调用 `TCHouseDDescribeOnlineQueryLoads` 查看当前正在执行的查询。

**判断逻辑**：

- ✅ 发现长时间运行的查询（QueryDuration > 60 秒）→ 纳入根因分析 → 参考经验沉淀库 **#2**
- ✅ 发现大量并发查询导致资源争抢 → 纳入根因分析 → 参考经验沉淀库 **#2** / **#5**
- ✅ 无异常 → 继续步骤 7
- ❌ 调用失败 → 跳过此步骤，基于已有数据给出建议

### 步骤 6：检查集群配置（条件触发）

**触发条件**（满足任一即触发）：

- 步骤 4 发现可能与配置相关的问题（内存不足/并发过高/Compaction 慢）
- 步骤 4 调用失败（监控数据不可用时，配置信息可提供间接诊断线索）

调用 `TCHouseDDescribeClusterConfigs` 获取关键性能配置，重点关注：

- FE 配置（`fe.conf`）：`qe_max_connection`、`max_running_txn_num_per_db`、`default_replication_num`
- BE 配置（`be.conf`）：`mem_limit`、`write_buffer_size`、`tablet_max_compaction_score`、`compaction_task_num_per_disk`、`storage_root_path`、`max_tablet_version_num`

**判断逻辑**：

- ✅ 成功 → 纳入综合分析（按触发来源对应：内存类 → 经验沉淀库 **#5**/**#6**；Compaction → **#4**；连接数 → **#7**；version 报错 → **#8**）
- ❌ 获取失败 → 跳过配置分析，基于已有数据给出建议，报告中注明

### 步骤 7：综合分析与生成报告

基于收集到的所有信息，按 [诊断分析框架](references/diagnosis-framework.md#常见故障模式) 进行根因分析，按 [输出报告格式](references/report-template.md) 生成诊断报告。

**强制输出 schema（五段，缺一不可）**：

以下五段为本 Skill 输出的**强制结构**，任何一段缺失即视为输出不合格。每段的必填字段清单详见 [报告模板的五段必填字段清单](references/report-template.md#五段必填字段清单)。

| #   | 段名     | 强制必填项                                                                                                                                                                                                                                                                            |
| --- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **基线** | 集群概况（ID/名称/版本/状态/Kind/HA/PayMode/节点规格 & 数量）+ FE HA 三元组 `(M, F, O)` 及闭环判定结果                                                                                                                                                                                |
| 2   | **节点** | 节点状态矩阵（IP / Role / FeRole / Status / CPU / 内存 / 磁盘）+ 异常节点明细                                                                                                                                                                                                         |
| 3   | **事件** | 告警事件列表（时间 / 类型 / 严重度 / 状态），无事件需显式写明「无待处理告警事件」                                                                                                                                                                                                     |
| 4   | **监控** | 监控指标两栏：**1h 瞬时栏**（核心指标当前值）+ **24h 趋势栏**（核心指标日内走势 / 拐点 / 斜率）；数据缺失需逐项注明缺失维度；**QPS/连接数 24h 全 0** 时按步骤 4 的「全 0 判定规则」显式标注（三态之一）；**Compaction Score 返回空**时按步骤 4 的「降级策略」显式标注实际使用的降级项 |
| 5   | **结论** | 健康评分（🟢/🟡/🔴 + 一句话总结）+ 异常项详情与根因 + 修复建议（按 P0/P1/P2 排序）+ 后续建议；**FE HA 拓扑不闭环**时必须引用步骤 2.1「畸形拓扑修复动作库」的固定文案；**QPS/连接数全 0 且用途未知**时必须通过 `ask_user` 确认集群用途后再定级                                         |

**多异常并发时的优先级排序**：

1. 先解磁盘（防数据丢失）
2. 再处理副本异常（防数据可用性受损）
3. 再处理查询堆积（恢复服务）
4. 再优化 CPU/内存/Compaction（性能调优）

## 频率控制

| 限制                            | 阈值           | 说明                                             |
| ------------------------------- | -------------- | ------------------------------------------------ |
| 工具总调用频率                  | ≤ 15 次/分钟   | 避免触发平台限流                                 |
| MonitorGetMonitorData 调用      | ≤ 30 次/轮诊断 | 一次只能查一个指标，多个核心指标需按指标循环调用 |
| TCHouseDDescribeEventTasks 翻页 | ≤ 3 次/轮      | 避免拉取过多历史事件                             |

**超限处理**：连续收到 `RequestLimitExceeded` → 等 5 秒重试，连续 3 次仍失败 → 降低调用频率，告知用户被限流。

## 错误码与处理策略

### 重试总原则：区分「瞬时失败」与「系统性失败」

> 背景：实测中发现，对 `MonitorGetMonitorData` 返回 `FailedOperation / 查询失败` 时盲目重试 3 次后，每次都返回同一错误码——这显然是后端系统性问题，再重试只是浪费 token。

**判定规则（适用于所有可重试错误码）**：

- **瞬时失败**：同一调用首次失败、或失败但**错误码不同**于上一次 → 按下表的重试策略执行（等待 + 重试）
- **系统性失败**：**同一工具调用、同一错误码连续出现 ≥ 2 次** → 立即判定为系统性失败，**停止重试**，进入降级路径（跳过该数据源，基于已收集的其他数据继续诊断），并在报告中标注「{工具名} 系统性失败（{错误码} × N 次），已降级」

**降级路径**：参见各步骤判断逻辑中「调用失败 → 跳过 ... 基于 ... 给出有限诊断」分支；最终在「结论」段对降级影响明确说明。

### 错误码对照表

| 错误码/场景            | Agent 行为                                                                                                                                                                                                          |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthFailure.*`        | 报告鉴权失败，提示用户检查集群访问权限（**不重试**）                                                                                                                                                                |
| `ResourceNotFound`     | 检查 ID 格式（`cdwdoris-` 前缀）；格式错 → 修正重试；格式对 → 请用户确认（**不重试**）                                                                                                                              |
| `InvalidParameter.*`   | 检查参数格式（时间范围、节点角色），尝试修正后重试 1 次；无法修正 → 报告具体问题                                                                                                                                    |
| `UnsupportedRegion`    | 该地域未开通 TCHouseD 产品。**不重试、不自动切换地域**，必须调用 `ask_user` 让用户确认地域。详见 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问)（**不重试**） |
| `InternalError`        | 等 3 秒重试；同错误码连续 ≥ 2 次 → 判定系统性失败，停止重试并降级，报告错误码 + RequestId                                                                                                                           |
| `FailedOperation.*`    | 等 3 秒重试；同错误码连续 ≥ 2 次 → 判定系统性失败，停止重试并降级；典型场景：监控接口返回「查询失败」、缩窗口/换指标也无效                                                                                          |
| `RequestLimitExceeded` | 等 5 秒重试；连续 3 次 → 降低频率，告知被限流                                                                                                                                                                       |
| 监控数据缺失           | 标注缺失时段，基于可用数据分析，报告中注明；若属于「核心维度（CPU/内存/磁盘/Tablet 健康度）」缺失，触发评分封顶 75（详见诊断分析框架 §3）                                                                           |
| 节点信息获取失败       | 跳过节点级分析，基于集群级数据给出有限诊断                                                                                                                                                                          |
| 网络超时               | 等 3 秒重试；同「超时」状态连续 ≥ 2 次 → 判定系统性失败，告知用户服务暂时不可用                                                                                                                                     |
| 兜底（未列出错误码）   | 同一错误码首次出现 → 重试 1 次；再次出现 → 系统性失败，报告完整错误信息 + RequestId                                                                                                                                 |

## 安全规则

1. **本 Skill 为纯只读诊断**：所有操作均为查询类（Describe/监控查询），不涉及写操作，无需用户确认即可执行
2. **修复建议仅为建议**：涉及重启、配置变更等操作时，仅输出建议和步骤，不直接执行；需明确告知影响范围和建议执行时间（低峰期）
3. **凭据安全**：不在输出中展示任何凭据信息（用户密码等）
4. **敏感信息控制**：节点 IP、配置内容等仅在诊断报告中展示，不在非必要场景暴露
5. **数据量控制**：监控指标查询时间范围不超过 7 天，避免 token 消耗过大
6. **关联诊断边界**：如果发现问题根因是慢 SQL 导致的资源耗尽，建议用户使用"慢 SQL 诊断与自动调优" Skill 进一步分析，不越界处理

## 经验沉淀库

> 工作流中各判断分支末尾的「→ 参考经验沉淀库 #N」均指向下表编号，便于在分析与报告环节快速回引证据。

| #   | 经验                                                     | 置信度 | 说明                                                                                                                                                                                 | 关联工作流分支                                                     |
| --- | -------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------ |
| #1  | 磁盘告警最常见原因是分区未及时清理或 Tablet 残留         | ⭐⭐⭐ | 检查分区 TTL（DYNAMIC PARTITION）；可通过 `ADMIN CLEAN TRASH ON BACKENDS` 清理回收站                                                                                                 | 步骤 4「磁盘容量告警」、步骤 7「先解磁盘」                         |
| #2  | CPU 持续高负载通常伴随慢查询堆积                         | ⭐⭐⭐ | 先检查 `SHOW PROCESSLIST` / OnlineQueryLoads，Kill 异常长查询后 CPU 通常立即下降                                                                                                     | 步骤 4「CPU 高/查询堆积」、步骤 5「长查询/高并发」                 |
| #3  | Tablet 副本不健康优先检查 BE 状态                        | ⭐⭐⭐ | BE Down 是最常见根因；其次是磁盘故障导致 Tablet 不可读；自愈通过 Tablet 调度器自动完成                                                                                               | 步骤 4「副本异常」（结合步骤 2 节点状态与步骤 3 告警事件综合判断） |
| #4  | Compaction 堆积通常是高频小批量写入导致                  | ⭐⭐⭐ | 合并小批量写入为大批量（建议单批 ≥ 4096 行），或调高 `compaction_task_num_per_disk`                                                                                                  | 步骤 4「Compaction 压力」、步骤 6（BE 配置）                       |
| #5  | BE OOM 多因单查询无内存限制或查询并发过高                | ⭐⭐⭐ | 设置 session 级 `exec_mem_limit`；建议 `mem_limit` ≤ 物理内存 80%                                                                                                                    | 步骤 4「BE 内存异常」、步骤 5「高并发」、步骤 6（`mem_limit`）     |
| #6  | FE OOM 多因 metadata 量过大或 audit log 堆积             | ⭐⭐   | 检查表/分区/Tablet 数量；调高 FE JVM 堆（`JAVA_OPTS`）                                                                                                                               | 步骤 4「FE JVM 异常」、步骤 6（FE 配置）                           |
| #7  | 连接数耗尽多因客户端连接池配置不当                       | ⭐⭐   | 检查 `qe_max_connection`，建议客户端连接池 `max_idle_time` ≤ 300s                                                                                                                    | 步骤 4「连接数告警」、步骤 6（FE `qe_max_connection`）             |
| #8  | 大量 `version count exceeded` 报错                       | ⭐⭐   | 单 Tablet version 过多，调小写入频率或调大 `max_tablet_version_num`                                                                                                                  | 步骤 4「副本异常」补充、步骤 6（`max_tablet_version_num`）         |
| #9  | FE HA 名义闭环 / 实际不闭环（1M+0F+NO 型）               | ⭐⭐⭐ | OBSERVER 不参选举，仅靠 `HaType` 字段判 HA 会漏判；必须用 `FeRole` 三元组 `(M,F,O)` 硬校验，命中畸形拓扑时直接引用步骤 2.1「畸形拓扑修复动作库」的固定文案，不再让 AI 自由拼修复方案 | 步骤 2.1「FE HA 拓扑硬校验」                                       |
| #10 | Compaction Score 指标在新版本上返空                      | ⭐⭐   | `doris-3.0.x` 上四个 Compaction Score 指标常返回空数据；固定走「Permits → 配置 → be.INFO 日志」三级降级，不允许报告里挂「无数据上报」空洞                                            | 步骤 4「Compaction 压力」                                          |
| #11 | QPS/连接数 24h 全 0 需显式区分「用途未知」与「业务停摆」 | ⭐⭐   | 全 0 未必是异常（日志/备用集群正常）；Skill 必须走「全 0 → ask_user 确认用途」或「掉零 ≥ 15min → P1 业务停摆」的判定链，禁止把兜底表述甩给读者                                       | 步骤 4「FE 查询负载」                                              |
