---
name: 腾讯云TCHouse-X 实例选型与架构推荐
description: >
  TCHouse-X（离在线一体化云原生数据仓库）实例选型与架构推荐 Skill。面向首次开通 TCHouse-X 实例的用户，AI 根据用户提供的业务画像（应用场景、数据规模、并发/延迟要求、是否需要资源隔离、是否有潮汐峰谷、是否有离线 ETL、预算约束），推荐适合的实例架构（标准/自适应 Warehouse 拓扑、节点规格与个数、弹性策略、是否需要 Serverless、用户体系、SSL/TLS、TCLake 依赖）并通过两步实时询价（`TCHouseXDescribeGoodsDetail` + `BillingCalculatePrice`）输出多方案费用估算。
  触发词：TCHouse-X 选型、TCHouse-X 推荐、新建实例、新购实例、购买实例、创建实例、Warehouse 规格、CU 数、Small/Medium/Large、标准 Warehouse、自适应 Warehouse、Serverless、离在线一体、TCLake 存储、计算组、多计算组隔离、弹性计划、分时弹性、定时启停、自动弹性、机型推荐、架构推荐、容量规划、sizing、cluster recommendation、cluster sizing、tchousex 新购、买多大的实例、实例配置推荐、费用估算、成本对比、按量计费。
  本 Skill 包含 3 个子能力：①业务画像与前置条件采集 ②Warehouse/Serverless 架构方案生成 ③基于两步实时询价的多方案费用估算与最终建议。
  何时不触发：已有实例的慢查询诊断、SQL 优化、Warehouse 扩缩容操作、实例健康诊断与故障排查、NL2SQL、智能建表、权限管理、数据导入导出等非新购选型相关问题不走本 Skill。
allowed-tools:
  - TCHouseXDescribeRegionZone
  - TCHouseXDescribeSpec # 售卖规格档位（Cpu/Memory）与 Spark 规格档位交叉参考
  - TCHouseXDescribeGoodsDetail # 获取询价所需的商品配置（GoodsCategoryId + GoodsDetailStr），本接口不返回金额
  - BillingCalculatePrice # 通用询价接口，将 DescribeGoodsDetail 的返回结果传入以拿到真实金额（含折扣）
  - ask_user # WorkBuddy 中为 AskUserQuestion
---

# TCHouse-X 实例选型与架构推荐

## 概述

本 Skill 提供 TCHouse-X（离在线一体化云原生数据仓库）新购场景下的选型与架构推荐能力，包含三个子能力：

1. **业务画像与前置条件采集**：确认邀测资格 / TCLake / VPC 等前置项，采集应用场景、数据规模、并发、隔离与潮汐诉求
2. **Warehouse/Serverless 架构方案生成**：基于业务画像输出 2-3 个方案，覆盖标准/自适应 Warehouse 拓扑、CU 规模、弹性策略、Serverless 使用、权限体系
3. **多方案费用估算与最终建议**：通过两步实时询价链路（`TCHouseXDescribeGoodsDetail` → `BillingCalculatePrice`）拿到真实金额（含折扣），失败时回退公开 CU 单价估算，输出可复用于控制台下单的部署参数

> 📌 **产品模型速览**（务必先建立正确认知）：
>
> - **实例（Instance）= TCLake 存储 + 一个或多个 Warehouse + 可选 Serverless**
> - **Warehouse**：在线 OLAP 计算资源。分两种：
>   - **标准 Warehouse**：多个同规格节点，规格档位 `2X-Small(4CU) / X-Small(8CU) / Small(16CU) / Medium(32CU) / Large(64CU)`，支持分时弹性 / 定时启停 / 自动弹性
>   - **自适应 Warehouse**（Beta）：仅需配置 `Warehouse 最大 CU` + `单 SQL 最大 CU`，系统自动按查询复杂度扩缩虚拟集群
> - **Serverless**：离线批处理资源。通过 SQL 前置 `/*+engine=batch*/` hint 触发；可组合 `executor_specs / executor_count / driver_specs` 调整规格。默认 Driver=Executor=`2X-Small × 1`
> - **TCLake**：底层统一存储。**单主账号单地域仅一份**，实例创建的前置依赖，独立计费（不在本 Skill 询价范围）
> - **CU**：计算资源单位，`1 CU = 1 vCPU + 4 GiB RAM`
> - **接入服务节点**：由 X 托管，**不计费**，用户无需选型

## 依赖与运行环境

本 Skill 的所有调用通过 MCP Tool 完成（云 API 类工具由平台封装为 MCP Tool，Agent 直接调用工具名即可）。

**依赖工具清单**：

| #   | Tool 名称                   | 能力定位                                                                                                                                                                                                                     | 参考文档                                                                                                   |
| --- | --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 1   | TCHouseXDescribeRegionZone  | 获取 TCHouse-X 支持的地域 / 可用区 / 内核版本列表（以接口返回为准）                                                                                                                                                          | [参考](references/api-reference.md#1-tchousexdescriberegionzone--获取支持的地域可用区与内核版本列表) |
| 2   | TCHouseXDescribeSpec        | 获取售卖规格档位（`Specs[]` 返回 `Name/Cpu/Memory/MinStorage/MaxStorage/Disabled`；`SparkSpecMaps[]` 返回离线任务规格），作为方案生成的实时输入。**内核版本不在本接口返回，需从 `TCHouseXDescribeRegionZone.Versions` 获取** | [参考](references/api-reference.md#3-tchousexdescribespec--获取可选购集群规格档位)                   |
| 3   | TCHouseXDescribeGoodsDetail | 两步询价的 **Step A**：获取商品配置信息（`GoodsCategoryId` + `GoodsDetailStr` + `GoodsNum` + `PayMode`）。**本接口不返回金额**，需将返回结果透传给 `BillingCalculatePrice` 完成真实询价                                      | [参考](references/cost-estimation-guide.md#5-实时询价两步链路主路径)                                       |
| 4   | BillingCalculatePrice       | 两步询价的 **Step B**：通用 billing 询价接口，接收 `TCHouseXDescribeGoodsDetail` 的返回结果，输出含折扣的真实金额（`Price` / `TotalCost` / `RealTotalCost`，单位「分」，展示前除以 100）                                     | [参考](references/cost-estimation-guide.md#5-实时询价两步链路主路径)                                       |
| 5   | ask_user                    | 向用户询问确认（WorkBuddy 中为 AskUserQuestion）                                                                                                                                                                             | —                                                                                                          |

> 🔑 **关键说明**：TCHouse-X 询价走**两步链路**（与 tchousec 一致）——`TCHouseXDescribeGoodsDetail` 拿商品配置（`GoodsCategoryId` + `GoodsDetailStr`），然后透传给 `BillingCalculatePrice` 拿真实金额。因此：
>
> - **本 Skill 的费用估算主路径为两步询价**：`TCHouseXDescribeGoodsDetail` → `BillingCalculatePrice`，每个推荐方案至少调用一次以拿到真实金额
> - **兜底路径为公开 CU 单价估算**：仅在两步询价任一步重试 3 次仍失败时回退到 [cost-estimation-guide.md](references/cost-estimation-guide.md) 中的官网公开 CU 单价表，并在报告中注明「实时询价接口暂不可用，费用为按官网公开 CU 单价估算」
> - 💰 **价格单位强提示**：`BillingCalculatePrice` 返回的所有金额字段（`Price` / `TotalCost` / `RealTotalCost`）单位均为「分」，展示给用户前必须**除以 100** 转换为「元」。忘记换算会导致报价虚高 100 倍。

## 凭证 / 环境变量

- `region_id`：从会话 context 的 X-Context header 自动注入，或由用户指定
- 若缺失，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）询问用户

> 💡 **多平台兼容说明**：本文档中所有提到的 `ask_user` 工具，在 WorkBuddy 平台中对应为 `AskUserQuestion`。后文不再重复标注。

## 核心工作流

### 步骤 0：前置条件确认

TCHouse-X 目前处于**邀测阶段**，实例创建有以下前置依赖，需先与用户确认：

| 前置项              | 说明                                                                                        | 缺失时的动作                                                 |
| ------------------- | ------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| 邀测资格            | 需先获得测试邀请（可在腾讯云官网[申请试用](https://cloud.tencent.com/apply/p/5ash4vrbydt)） | 提示用户先申请，本 Skill 只做方案输出，不阻塞                |
| 实名认证 + 服务角色 | 主账号已完成实名 + 已创建 TCHouse-X 服务关联角色                                            | 提示用户完成，本 Skill 只做方案输出，不阻塞                  |
| TCLake 开通         | **单主账号单地域仅一份 TCLake**，实例创建时可选授权自动开通                                 | 记录到方案的「前置检查」章节，提示用户下单页允许一键授权开通 |
| VPC / 子网          | 需存在目标地域下的 VPC，且**所选子网剩余可用 IP > 6**                                       | 记录到方案，提示用户按此约束准备网络                         |
| SSL/TLS             | 传输加密开关，按需开启                                                                      | 记录为可选项                                                 |

> **本 Skill 不阻塞任何前置项**：即使用户尚未完成邀测/授权，也可以先输出推荐方案供用户参考。上述前置项在报告的「前置检查」章节展示，供用户下单前对照。

### 步骤 1：业务画像采集

**目标**：收集足够信息以做出合理推荐。以下为必须了解的维度：

| 维度        | 关键问题                                                                     | 默认值（用户未提供时）                                        |
| ----------- | ---------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 应用场景    | 实时 OLAP / 实时 OLAP + 资源隔离 / 实时 OLAP + 弹性 / 离在线一体化           | 需询问                                                        |
| 数据规模    | 当前总数据量（TB）？日增量（GB）？                                           | 需询问，不可假设                                              |
| 数据保留    | 数据保留周期（月/年）？                                                      | 需询问                                                        |
| 查询模式    | OLAP 聚合 / 点查 / JOIN 复杂度                                               | 默认 OLAP 聚合                                                |
| 并发要求    | 峰值并发查询数？                                                             | 默认 10-20 QPS                                                |
| 延迟要求    | 查询响应时间要求（秒级/亚秒级）？                                            | 默认秒级                                                      |
| 资源隔离    | 是否存在多业务共存（在线 / ETL / 报表）需要计算隔离？                        | 默认单业务                                                    |
| 潮汐 / 峰谷 | 是否有明显的高峰低谷（决定弹性策略）？                                       | 默认无明显潮汐                                                |
| 离线 ETL    | 是否有离线批处理任务（决定是否规划 Serverless）？                            | 默认无                                                        |
| 权限体系    | CAM 子账号 / 自定义用户 / LDAP（Beta）？                                     | 默认 CAM                                                      |
| 传输加密    | 是否需要开启 SSL/TLS？                                                       | 默认关闭                                                      |
| 计费偏好    | TCHouse-X 目前**仅支持按量计费**（按秒计费，起步 60 秒；每小时生成一份账单） | 固定按量，无需询问                                            |
| 预算约束    | 月预算上限？                                                                 | 可选                                                          |
| 地域偏好    | 期望部署地域？                                                               | 默认广州（已开放：北京 / 重庆 / 广州 / 香港 / 上海 / 新加坡） |

**判断逻辑**：

- ✅ 用户一次性提供了大部分信息 → 补充确认后进入步骤 2
- ❌ 信息严重不足（缺少数据规模 + 应用场景）→ 调用 `ask_user` 结构化询问
- ⚠️ 部分信息缺失 → 对缺失维度使用合理默认值，并在推荐中注明假设

**询问策略**（一次性询问所有缺失信息，避免多轮来回）：

```
为了给您推荐合适的 TCHouse-X 实例配置，请提供以下信息：
1. 应用场景：主要用途是（可多选）
   A. 实时 OLAP 加速（替代 MySQL/PG 做在线分析）
   B. 实时 OLAP + 多业务资源隔离
   C. 实时 OLAP + 弹性伸缩（有明显潮汐）
   D. 离在线一体化（同时有离线 ETL 和在线查询）
2. 数据规模：当前总数据量约多少 TB？每天新增多少 GB？保留多久？
3. 查询模式：主要是报表/聚合分析，还是实时点查？是否有大量 JOIN？
4. 并发需求：峰值大约多少个并发查询？响应时间要求？
5. 是否需要多业务隔离？（例如在线报表 + ETL 各自独立 Warehouse）
6. 是否有潮汐 / 峰谷？（决定是否用分时弹性 / 定时启停 / 自动弹性）
7. 是否有离线批处理任务？（决定是否规划 Serverless）
8. 用户体系：CAM 子账号 / 自定义用户 / LDAP？
9. 是否开启 SSL/TLS 传输加密？
10. 部署地域：期望在哪个地域？（已开放：北京 / 重庆 / 广州 / 香港 / 上海 / 新加坡）
11. 预算：是否有月预算上限？（可选）
```

### 步骤 1.5：地域参数标准化（调用 API 前必需执行）

无论步骤 1 中收集到的地域信息是中文名（如"广州"）、`Region` 字符串（如 `ap-guangzhou`）还是 `RegionId` 数字（如 `1`），都必须先按 [地域映射表](references/region-mapping.md) 统一转为 **`Region` 字符串**（例如 `ap-guangzhou`），再传入后续工具。若匹配不到或大区模糊（如"华南地区"），先 `ask_user` 确认具体地域。

> ⚠️ **强制规则**：本 Skill 依赖的全部云 API 工具（`TCHouseXDescribeRegionZone` / `TCHouseXDescribeSpec` / `TCHouseXDescribeGoodsDetail` / `BillingCalculatePrice`）都只接受 **`Region` 字符串**。禁止凭记忆填写；详见 [region-mapping.md 工具传参形式速查](references/region-mapping.md#工具传参形式速查)。
>
> 注意：`TCHouseXDescribeRegionZone` 可用来校验地域/可用区在 TCHouse-X 产品下是否可用，**不用于反推中文名 → `Region` 字符串**（后者必须直接查本地映射表，不得先盲调 API）。

### 步骤 2：地域 / 可用区 / 内核版本校验

调用 `TCHouseXDescribeRegionZone` 校验步骤 1.5 标准化后的 `Region` 在 TCHouse-X 产品下是否可用，并从返回中 **获取可用内核版本列表**。

**参数**：

- `Region`：步骤 1.5 标准化后的 `Region` 字符串（如 `ap-guangzhou`）
- `InstanceType`：**固定传 `warehouse`**（必填。不传或传错会导致 `Versions` 为空，进而 Step B 询价失败）

**关注返回字段**：

- `Items[].Regions[]`：地域列表，从 `Name` （如 `ap-guangzhou`） 判断目标地域是否开通
- `Items[].Regions[].Zones[]`：当前地域下可用可用区（如 `ap-guangzhou-3`），用于默认 Zone 选择
- `Versions`：**当前可用的内核版本列表**（如 `["2.2.2_REL", "2.2.1_REL"]`），选型方案中的 `ProductVersion` 优先取本字段的最新稳定版本；若为空，回退到 `VersionInfos[?ClusterType=0].Versions`

**判断逻辑**：

- ✅ 接口正常返回，且用户指定地域在 `Items[].Regions[]` 列表中 → 记录 `Versions` 到上下文 → 继续步骤 2.5
- ❌ 接口返回 `UnsupportedRegion`（或用户指定地域不在返回的 `Regions[]` 列表中）→ 按 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问) 处理：**不重试、不自动切换、调用 `ask_user` 让用户确认地域**
- ⚠️ 用户未指定可用区 → 从返回列表中选取默认可用区（广州通常为 `ap-guangzhou-3` / `ap-guangzhou-7`，以接口返回为准）

> 📌 **目前已开放地域**：北京、重庆、广州、香港、上海、新加坡（以 `TCHouseXDescribeRegionZone` 实时返回的 `Regions[]` 为准）。若用户指定其他地域且接口报 `UnsupportedRegion`，向用户说明并让其确认是切到已开放地域，还是联系 TCHouse-X 产研团队申请开通目标地域。

### 步骤 2.5：获取售卖规格档位

调用 `TCHouseXDescribeSpec`（入参：`Region` + `InstanceType: "warehouse"`，**本接口无 `Zone` / `PayMode` 参数**）获取目标地域的实时售卖规格档位，作为步骤 3 方案生成的实时输入。**每轮选型对话都必须调用**（不缓存历史结果），确保规格档位与当前售卖状态一致。

**参数**：

- `Region`：步骤 1.5 标准化后的 `Region` 字符串（如 `ap-guangzhou`）
- `InstanceType`：固定传 `warehouse`

**关注返回字段**：

- `Specs[]`：售卖规格档位数组，每项包含 `Name`（如 `Small`）/ `Cpu` / `Memory` / `MinStorage` / `MaxStorage` / `Disabled`，用于交叉验证 [spec-recommendation-rules.md](references/spec-recommendation-rules.md) 里硬编码的档位表（`Disabled=true` 的档位目前不可售）
- `SparkSpecMaps[]`：Serverless（离线）任务规格档位，用于校验 Executor/Driver 规格是否在售

> ⚠️ **内核版本不在本接口返回**：`ProductVersion` 一律取自步骤 2 `TCHouseXDescribeRegionZone.Versions` 中的最新稳定版本。

**判断逻辑**：

- ✅ 成功返回 `Specs` + `SparkSpecMaps` → 记录到上下文，进入步骤 3
- ❌ 返回为空或字段缺失 → 记录警告，回退到 [spec-recommendation-rules.md](references/spec-recommendation-rules.md) 硬编码档位表，报告中注明「实时规格接口暂不可用，档位为经验值」
- ❌ `InvalidParameter` → 检查 Region 格式，修正后重试 1 次；仍失败则回退硬编码档位表
- ❌ `InternalError` → 等 3 秒重试，最多 3 次；仍失败则回退硬编码档位表

### 步骤 3：Warehouse / Serverless 架构方案生成

基于业务画像，按 [选型规则](references/spec-recommendation-rules.md) 生成 2-3 个推荐方案。

**方案生成原则**：

| 方案类型           | 定位          | 特点                                                                    |
| ------------------ | ------------- | ----------------------------------------------------------------------- |
| 方案 A（推荐）     | 性价比最优    | 单个或少量标准 Warehouse，规格贴合负载；有潮汐则叠加分时弹性 / 定时启停 |
| 方案 B（性能优先） | 高性能 / 隔离 | 更大规格 Warehouse 或多 Warehouse 隔离；离线场景显式规划 Serverless     |
| 方案 C（经济型）   | 成本最低      | 小规格 + 定时启停 + 单 Warehouse；自适应 Warehouse 免运维（Beta）       |

**每个方案必须包含**：

1. **Warehouse 拓扑**：
   - Warehouse 名称、类型（标准 / 自适应）
   - 若标准：节点规格（2X-Small(4CU) / X-Small(8CU) / Small(16CU) / Medium(32CU) / Large(64CU)）+ 节点个数 + 单节点 CU 数
   - 若自适应：Warehouse 最大 CU（可选档位：`4 / 8 / 16 / 32 / 64 / 128 / 256 / 512 / 1024 CU`）+ 单 SQL 最大 CU（Beta，需备注联系腾讯云开通）
2. **弹性策略**（针对标准 Warehouse）：不启用 / 分时弹性 / 定时启停 / 自动弹性；若开启需给出示例参数（高峰/低谷时段、CPU 阈值、观测时间等）
3. **是否规划 Serverless**（离在线一体场景）：Executor 规格与个数、Driver 规格；SQL 中使用 `/*+engine=batch*/` hint
4. **网络**：VPC / 子网建议（子网剩余可用 IP > 6）
5. **权限体系**：CAM / 自定义 / LDAP-Beta；建议默认 CAM
6. **数据安全**：SSL/TLS 是否开启
7. **TCLake**：仅提示"依赖开通"，不做容量选型（TCLake 按量独立计费）
8. **内核版本**：以步骤 2 `TCHouseXDescribeRegionZone` 返回的 `Versions` 中的最新稳定版本为准，未获取到时以官网推荐版本为准

**X 选型核心逻辑**（详见 [references/spec-recommendation-rules.md](references/spec-recommendation-rules.md#选型决策树)）：

- 数据量 < 1TB + 低并发 → 标准 Warehouse `2X-Small × 1~2` 或 `X-Small × 1`
- 数据量 1-10TB + 中并发 → 标准 Warehouse `Small × 2~3`
- 数据量 10-50TB + 中高并发 → 标准 Warehouse `Medium × 3~5`
- 数据量 > 50TB / 极高并发 → 标准 Warehouse `Large × 3+` 或多 Warehouse 隔离
- 多业务隔离 → 多个标准 Warehouse（如 `wh_online` + `wh_etl` + `wh_report`），计算隔离共享 TCLake 存储
- 明显潮汐 → 叠加分时弹性 / 定时启停 / 自动弹性
- 免运维诉求 + 波动不规律 → 自适应 Warehouse（Beta）
- 有离线 ETL → 追加 Serverless 规划（`executor_specs / count / driver_specs`）

> 📌 **档位与版本以实时接口返回为准**：上述档位表为经验起点，实际生成方案时必须用步骤 2.5 `TCHouseXDescribeSpec` 返回的 `Specs[].Name`（`Disabled=false` 的项）交叉验证；`ProductVersion` 优先取步骤 2 `TCHouseXDescribeRegionZone.Versions` 中的最新稳定版本。

### 步骤 3.5：CU 规模校验（ASSERT）

**目标**：确保推荐的 Warehouse 总 CU 满足并发 + 数据规模需求，不满足则自动上调。

**校验公式**（经验值，用于把关，不追求精确）：

```
估算所需 CU = max(
  ceil(峰值并发 QPS × 单查询平均 CU 消耗),
  ceil(热数据量(GB) × 内存缓存系数)
)

其中：
- 单查询平均 CU 消耗：简单聚合 0.5 CU，复杂 JOIN 1-2 CU
- 内存缓存系数：0.02（即热数据 5% 需要缓存在内存，1 CU=4GiB 内存）
```

**校验步骤**：

```
对每个方案执行：
1. 计算方案 Warehouse 总 CU = 节点规格 CU × 节点数（自适应 Warehouse 直接用最大 CU）
2. 计算 所需 CU（按上式）
3. IF 方案总 CU ≥ 所需 CU × 0.8（留 20% 缓冲）:
     → 校验通过
4. ELSE:
     → 优先上调节点数（保持规格不变，便于弹性）
     → 若节点数超过 8 仍不够 → 上调节点规格档位（Small → Medium → Large）
     → 更新方案并在报告中注明「已自动上调 Warehouse 规模以满足负载需求」
```

> **存储容量不由本 Skill 校验**：TCHouse-X 存储基于 TCLake（对象存储），按实际用量独立计费，无需在选型阶段做本地盘容量 ASSERT。

### 步骤 4：费用估算

**主路径：两步实时询价链路**（每个推荐方案都必须走一遍）

费用估算通过两步询价链路完成（详见 [cost-estimation-guide.md §5](references/cost-estimation-guide.md#5-实时询价两步链路主路径)）：

**Step A：调用 `TCHouseXDescribeGoodsDetail` 拿商品配置**

- 参数构造：`Case: "create_instance"` + `IsQueryPrice: true` + `InstanceType: "warehouse"` + `Region` + `Zone` + `Resources[{Component: "impalad", SpecName, Cpu, Memory, Count, DeployMode: 0, VirtualClusterName}, {Component: "impalad", SpecName: "CLB", Count: 1, DeployMode: 2, VirtualClusterName}]` + `TCLakeSpec: {CatalogType: "LAKEHOUSE", CatalogNameType: "SYSTEM"}` + `WarehouseType: 0`（标准 Warehouse）**或** `WarehouseType: 1` + `ServerlessSpec: {WarehouseMaxCuCount: 64, WarehouseMaxClusterSize: 64}`（自适应 Warehouse，两个上限默认均为 64） + `ChargeProperties: {ChargeType: "POSTPAID_BY_HOUR", RenewFlag: 0, TimeSpan: 1, TimeUnit: "h"}` + 弹性计划参数（可选）
- `ProductVersion`：**必填**，取步骤 2 `TCHouseXDescribeRegionZone.Versions` 中的最新稳定版本（如 `2.2.2_REL`）
- 💡 **询价场景下如下字段无需 skill 传入，tool 层会自动补占位值以通过云 API 校验**：`InstanceName` / `VirtualClusterName`（顶层）/ `UserVPCId` / `UserSubnetId` / `TCIMode`；`Resources[].VirtualClusterName` 仍需 skill 传入中文名

> ⚠️ **VirtualClusterName 最少 6 字符**：若 skill 需要传入 `Resources[].VirtualClusterName`，确保长度 ≥ 6 字符（如“我的Warehouse”而非“测试”），否则会被云 API 拒绝。

- 返回：`GoodsCategoryId`、`GoodsDetailStr`（JSON 字符串）、`GoodsNum`、`PayMode`（0=后付费）
- **本接口不返回金额**，仅用于给 `BillingCalculatePrice` 提供商品配置

**Step B：调用 `BillingCalculatePrice` 拿真实金额**

- 参数：`GoodsCategoryId`（透传 Step A）+ `GoodsDetail`（**⚠️ 不可原样透传 Step A 的 `GoodsDetailStr`，必须先按 [cost-estimation-guide.md §5.3.1](references/cost-estimation-guide.md#531-goodsdetailstr--goodsdetail-转换强制步骤不可跳过) 执行 key camelCase 化，所有字段全部保留、严禁删除 `sv_tchouse_x_warehouse_hp` 等字段**）+ `GoodsNum`（透传，通常为 1）+ `PayMode: "postPay"`（TCHouse-X 仅按量）+ `Region` + `Zone`
- 返回：`Price`（原价）、`TotalCost`（总费用）、`RealTotalCost`（折后实付，整型）、`HighPrecisionPrice.RealTotalCostHigh`（折后实付，高精度字符串）、`Policy`（折扣率）、`TimeSpan`、`TimeUnit`
- 💰 **单位换算**：所有金额字段单位均为「分」，展示前必须除以 100 转换为「元」
- 🎯 **精度优先**：展示实付金额时**优先取 `HighPrecisionPrice.RealTotalCostHigh`**（字符串，高精度），回退整型 `RealTotalCost`（整型有四舍五入损失）

**兜底路径：官网公开 CU 单价估算**（仅在两步询价失败时回退）

按 [费用估算指南](references/cost-estimation-guide.md) 中的公开单价表估算：

```
Warehouse 月费 ≈ Warehouse 总 CU × 单价（元/CU/小时）× 720 小时
Serverless 月费 ≈ Serverless 平均 CU × 单价（元/CU/小时）× 实际运行小时数
```

**广州地域单价参考**：Warehouse `0.355 元/CU/小时`；Serverless `0.292 元/CU/小时`；详细单价表（含中国香港、新加坡）见 [cost-estimation-guide.md §地域单价表](references/cost-estimation-guide.md#2-地域cu单价表)

**兜底逻辑**：

- ✅ 两步都成功 → 以 Step B 返回的 `RealTotalCost`（除以 100）为准展示，并附带按 720 小时/月 估算的月费
- ❌ Step A / Step B 任一步返回 `InvalidParameter` / `InternalError` → 最多重试 3 次（每次间隔 3 秒）
- ❌ 重试仍失败 → 回退到公开单价估算，报告中注明「实时询价接口暂不可用，费用为按官网公开 CU 单价估算」
- ⚠️ 部分方案询价失败 → 对失败方案走公开单价估算并标注「实时询价失败，费用为公开单价估算」，其余方案正常展示真实询价金额

**费用展示要点**：

- 计费模式：**仅按量计费**（**按秒计费，起步 60 秒**，仅在运行时产生费用；**每小时生成一份费用账单**），不存在包年包月
- 月费用 = 按 720 小时/月估算；实际按 Warehouse 运行时长（含分时弹性 / 定时启停后的净运行时长）计费
- **TCLake 存储费用未包含**：明确告知用户 TCLake 按量独立计费，参考 TCLake 计费页
- **网络流量费用未包含**：跨 AZ / 公网出网流量独立计费
- **大模型 Token 费用未包含**（除非用户明确用 AI Functions）：独立计费
- 对比维度：Warehouse 月费、Serverless 预估月费（若有）、单 QPS 成本

> ⚠️ **邀测期免费提示**：TCHouse-X 目前处于邀测中，官网明确「邀测期间面向受邀用户开放免费使用」。若用户在邀测期，需在报告中明确「邀测期免费，此处估算为正式商用后的按量费用参考」。

### 步骤 5：生成推荐报告

按 [输出报告格式](references/spec-recommendation-rules.md#输出报告格式) 生成选型推荐报告。

**报告必须包含**：

1. 前置检查（邀测 / TCLake / VPC 子网 IP / SSL）
2. 业务画像摘要（确认理解正确）
3. 推荐方案对比表（Warehouse 拓扑 + 弹性 + Serverless + 月费用）
4. 每个方案的详细配置说明
5. 最终推荐及理由
6. 后续操作指引（如何在控制台创建 / 注意事项）

**报告末尾必须给出购买页跳转提示（严格规则）**：

- ✅ **唯一允许的购买页链接格式**：`https://console.cloud.tencent.com/tchousex/buy?region={Region}&tab=clusterCreate`
  - `{Region}` 必须替换为本次推荐使用的 Region 字符串（如 `ap-guangzhou`、`ap-shanghai`、`ap-beijing`），**严禁省略 `?region=` 或 `tab=clusterCreate` 参数**。
  - 示例：广州 → `https://console.cloud.tencent.com/tchousex/buy?region=ap-guangzhou&tab=clusterCreate`
- ❌ **禁止使用以下任何形式**（这些是常见的错误猜测，模型必须避免）：
  - `https://buy.cloud.tencent.com/tchousex?...`（TCHouse-X 走的是控制台购买页，**不是** `buy.cloud.tencent.com` 域名）
  - `https://console.cloud.tencent.com/tchousex/createStandard?rid=xxx`
  - `https://console.cloud.tencent.com/tchousex/create` 或任何 `tchousex/*` 下非 `buy` 的创建路径
  - 缺少 `tab=clusterCreate` 参数的 `https://console.cloud.tencent.com/tchousex/buy?region=xxx`（这样会落到默认 tab 而非新建集群）
  - 任何使用 `rid=` 数字 ID 的形式
- 链接以 Markdown 链接形式呈现，例如：`[前往 TCHouse-X 购买页创建集群](https://console.cloud.tencent.com/tchousex/buy?region=ap-guangzhou&tab=clusterCreate)`，方便用户点击后在浏览器中打开。

### 步骤 6：用户确认与调整

- 用户对方案有疑问 → 解释选型理由
- 用户要求调整 → 基于新约束重新生成方案（回到步骤 3）
- 用户确认方案 → 提供中文友好的部署参数参考（地域/可用区/Warehouse 规格/弹性策略等均使用「中文名称（英文代码）」格式展示）

## 频率控制

| 限制                        | 阈值         | 说明                                                                                       |
| --------------------------- | ------------ | ------------------------------------------------------------------------------------------ |
| 工具总调用频率              | ≤ 15 次/分钟 | 避免触发平台限流                                                                           |
| TCHouseXDescribeRegionZone  | ≤ 2 次/轮    | 通常只需 1 次                                                                              |
| TCHouseXDescribeSpec        | ≤ 2 次/轮    | 每轮选型对话必调 1 次，用于获取实时售卖档位（内核版本从 `DescribeRegionZone.Versions` 拿） |
| TCHouseXDescribeGoodsDetail | ≤ 5 次/轮    | 两步询价的 Step A，每个方案 1 次；失败即回退公开单价                                       |
| BillingCalculatePrice       | ≤ 5 次/轮    | 两步询价的 Step B，每个方案 1 次；失败即回退公开单价                                       |

**超限处理**：连续收到 `RequestLimitExceeded` → 等 5 秒重试，连续 3 次仍失败 → 回退公开单价估算，告知用户被限流。

## 错误码与处理策略

| 错误码 / 场景          | Agent 行为                                                                                                                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthFailure.*`        | 报告鉴权失败，提示用户检查访问权限                                                                                                                                                                    |
| `InvalidParameter.*`   | 检查 Region/Zone/规格 格式，尝试修正后重试 1 次；仍失败 → 询价接口降级为公开单价估算，其余接口报告问题                                                                                                |
| `ResourceInsufficient` | 该规格在目标可用区售罄，自动推荐替代规格档位                                                                                                                                                          |
| `InternalError`        | 等 3 秒重试，最多 3 次；仍失败 → 询价接口降级为公开单价估算并注明未实时验证                                                                                                                           |
| `RequestLimitExceeded` | 等 5 秒重试；连续 3 次 → 降低频率，询价接口降级为公开单价估算                                                                                                                                         |
| `UnsupportedRegion`    | 该地域未开通 TCHouseX 产品。**不重试、不自动切换地域**，必须调用 `ask_user` 让用户确认地域。详见 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问) |
| 内核版本不匹配         | 使用步骤 2 `TCHouseXDescribeRegionZone.Versions` 中的最新稳定版本，或经验版本                                                                                                                         |
| 兜底（未列出错误码）   | 报告完整错误信息 + RequestId，选型仍以公开单价估算给出                                                                                                                                                |

## 安全规则

1. **本 Skill 为纯只读咨询**：所有调用均为查询类，不涉及实际创建资源
2. **不自动创建实例 / Warehouse**：即使用户说"帮我创建"，也只输出配置方案和部署参数，不调用任何创建接口
3. **费用信息仅供参考**：明确标注「以 `BillingCalculatePrice` 实时询价返回的折后金额为准（询价失败时降级为按官网公开 CU 单价估算），实际费用以控制台账单为准；邀测期免费」
4. **凭据安全**：不在输出中展示任何凭据（SecretId/SecretKey/LDAP 密码等）
5. **关键参数不假设**：应用场景、数据规模、地域等核心参数缺失时必须询问，不自行假设

## 经验沉淀库

| 经验                                                     | 置信度 | 说明                                                                             |
| -------------------------------------------------------- | ------ | -------------------------------------------------------------------------------- |
| `Small × 2~3` 是中等负载的最通用起步配置                 | ⭐⭐⭐ | 适合 1~10TB + 10~30 并发的常规 OLAP 场景                                         |
| 多业务共存必用「多 Warehouse」，勿共用一个               | ⭐⭐⭐ | 计算隔离防止 ETL 拖垮在线查询，官网明示多个 Warehouse 计算隔离共享存储           |
| 明显潮汐必上「分时弹性」或「定时启停」                   | ⭐⭐⭐ | 按量计费下不启停闲置资源就是白花钱                                               |
| 波动不规律优先「自动弹性」，免运维选「自适应 Warehouse」 | ⭐⭐   | 自适应 Warehouse 目前 Beta，需联系腾讯云开通                                     |
| 离线 ETL 用 Serverless 而非把 SQL 跑在 Warehouse         | ⭐⭐⭐ | `/*+engine=batch*/` hint 是离在线隔离关键；Executor/Driver 独立规格              |
| Warehouse 缩容 / 变配开启「优雅变配」                    | ⭐⭐⭐ | 避免影响正在运行 SQL；预留 5 分钟宽限期                                          |
| TCLake 是 Skill 外的独立计费项                           | ⭐⭐⭐ | 报告必须明示 TCLake 费用不含在 Warehouse 报价内                                  |
| 邀测期免费，报价仅供正式商用后参考                       | ⭐⭐⭐ | 官网明确「邀测期间面向受邀用户开放免费使用」                                     |
| 子网可用 IP 必须 > 6                                     | ⭐⭐⭐ | 官网硬约束；VPC 子网准备不足时下单会失败                                         |
| 标准 Warehouse 数据节点规格档位与 CU 换算记牢            | ⭐⭐⭐ | 2X-Small=4 CU / X-Small=8 CU / Small=16 CU / Medium=32 CU / Large=64 CU（5 档）  |
| 自适应 Warehouse 可选最大 CU 档位                        | ⭐⭐⭐ | `4 / 8 / 16 / 32 / 64 / 128 / 256 / 512 / 1024 CU`（9 档，Beta）                 |
| 大模型 Token 独立计费（AI Functions 场景）               | ⭐⭐   | 使用 AI Functions 会额外产生 Token 费用，输入 0.004 / 输出 0.016 元/1kToken/小时 |

## 已知产品能力速查

> 更新时间：2026-07-13（以官网 [实例创建与销毁](https://cloud.tencent.com/document/product/1499/128670) / [标准 Warehouse 计算资源管理](https://cloud.tencent.com/document/product/1499/128673) / [自适应 Warehouse 计算资源管理](https://cloud.tencent.com/document/product/1499/128674) / [计费概述](https://cloud.tencent.com/document/product/1499) 为准）

| 能力             | 状态           | 说明                                                                                                   |
| ---------------- | -------------- | ------------------------------------------------------------------------------------------------------ |
| 标准 Warehouse   | ✅ 正式        | 5 档规格：2X-Small(4CU) / X-Small(8CU) / Small(16CU) / Medium(32CU) / Large(64CU)                      |
| 自适应 Warehouse | ⚠️ Beta        | 需联系腾讯云开通；最大 CU 可选 `4 / 8 / 16 / 32 / 64 / 128 / 256 / 512 / 1024 CU`（9 档）              |
| Serverless 离线  | ✅ 正式        | `/*+engine=batch*/` hint 触发；可用作业列表页新建作业                                                  |
| 分时弹性         | ✅ 正式        | 2-24 条策略，起始时间步长 15 分钟，相邻策略间隔 ≥ 1 小时                                               |
| 定时启停         | ✅ 正式        | 每天 / 每周 / 每月，00:00~23:45 步长 15 分钟                                                           |
| 自动弹性         | ✅ 正式        | CPU 阈值 40%~100%，观测时间 5~999 分钟                                                                 |
| 优雅变配 / 暂停  | ✅ 正式        | 预留 5 分钟宽限期，不影响运行中 SQL                                                                    |
| CAM 用户体系     | ✅ 正式        | 默认推荐                                                                                               |
| 自定义用户       | ✅ 正式        | 用户名 + 密码，绑定 CAM 子账号                                                                         |
| LDAP 用户体系    | ⚠️ Beta        | 需联系腾讯云开通                                                                                       |
| SSL/TLS 传输加密 | ✅ 正式        | 默认关闭，按需开启                                                                                     |
| TCLake 存储      | ✅ 前置依赖    | 单主账号单地域一份，实例创建时可授权自动开通                                                           |
| 计费模式         | ✅ 仅按量      | **按秒计费（起步 60 秒）**，仅在运行时产生费用；**每小时生成一份费用账单**；无包年包月                 |
| 支持地域         | 已开放：多地域 | 已开放北京、重庆、广州、香港、上海、新加坡；实时以 `TCHouseXDescribeRegionZone.Items[].Regions[]` 为准 |

**使用规则**：

- Beta 能力（自适应 Warehouse / LDAP）**不作为默认推荐**，仅在用户明确诉求（例如「免运维」「LDAP 单点登录」）时给出，并注明需联系腾讯云开通
- 内核版本以 `TCHouseXDescribeRegionZone.Versions` 为准；具体售卖规格档位以 `TCHouseXDescribeSpec` 为准；本 Skill 不写死版本号
