---
name: 腾讯云TCHouse-D 集群选型与架构推荐
description: >
  TCHouse-D（Doris）集群选型与架构推荐 Skill。面向首次开通或新增 Doris 集群的用户，AI 根据用户提供的业务画像（数据规模、增长速率、查询模式、并发要求、延迟要求、预算约束），推荐适合的集群规格（FE/BE/CN 节点数、CPU/内存/存储类型、副本数、是否存算分离），并输出多方案费用对比。
  触发词：集群选型、集群推荐、新建集群、新购集群、购买集群、创建集群、集群规格、节点选型、机型推荐、架构推荐、容量规划、sizing、cluster recommendation、cluster sizing、TCHouse-D 新购、cdwdoris 新建、Doris 选型、选什么规格、买多大的集群、集群配置推荐、存储选型、磁盘选型、高可用方案、跨AZ部署、费用估算、成本对比、包年包月还是按量、存算分离、Compute Node、CN节点。
  本 Skill 包含 3 个子能力：①业务画像采集与需求分析 ②集群规格与架构方案推荐 ③多方案费用对比与最终建议。
  何时不触发：已有集群的慢查询诊断、SQL 优化、集群扩缩容操作、集群健康诊断与故障排查、NL2SQL、权限管理、数据导入导出等非新购选型相关问题不走本 Skill。
allowed-tools:
  - TCHouseDDescribeSpec
  - TCHouseDDescribeRegionZone
  - TCHouseDDescribeGoodsDetail
  - BillingCalculatePrice
  - ask_user # WorkBuddy 中为 AskUserQuestion
---

# 集群选型与架构推荐

## 概述

本 Skill 提供 TCHouse-D（Doris）集群的选型与架构推荐能力，包含三个子能力：

1. **业务画像采集与需求分析**：通过结构化问题收集用户的数据规模、查询模式、并发/延迟要求和预算约束
2. **集群规格与架构方案推荐**：基于业务画像和可用规格，推荐 FE/BE/CN 节点类型/数量/存储/高可用/网络方案
3. **多方案费用对比与最终建议**：输出 2-3 个方案的配置详情与费用对比，帮助用户决策

## 依赖与运行环境

本 Skill 的所有调用通过 MCP Tool 完成（云 API 类工具由平台封装为 MCP Tool，Agent 直接调用工具名即可）。

**依赖工具清单**：

| #   | Tool 名称                   | 能力定位                                         | 参考文档                                                        |
| --- | --------------------------- | ------------------------------------------------ | --------------------------------------------------------------- |
| 1   | TCHouseDDescribeSpec        | 获取集群可用规格（FE/BE/CN）                     | [参考](references/api-reference.md#TCHouseDDescribeSpec)        |
| 2   | TCHouseDDescribeRegionZone  | 获取地域/可用区                                  | [参考](references/api-reference.md#TCHouseDDescribeRegionZone)  |
| 3   | TCHouseDDescribeGoodsDetail | 获取询价所需的商品配置信息                       | [参考](references/api-reference.md#TCHouseDDescribeGoodsDetail) |
| 4   | BillingCalculatePrice       | 调用 billing 询价获取真实价格                    | [参考](references/cost-estimation-guide.md)                     |
| 5   | ask_user                    | 向用户询问确认（WorkBuddy 中为 AskUserQuestion） | —                                                               |

## 凭证 / 环境变量

- `region_id`：从会话 context 的 X-Context header 自动注入，或由用户指定（可能是 `RegionId` 数字，也可能是 `Region` 字符串，也可能是中文地域名）
- 若以上参数缺失，通过 `ask_user`（WorkBuddy 中为 `AskUserQuestion`）询问用户

> ⚠️ **地域参数强制规则**：**任何工具调用前**都必须先按 [地域映射表](references/region-mapping.md) 把上下文中的地域信息补齐为 **`Region`（字符串）+ `RegionId`（数字）** 两种形式，禁止凭记忆填写。不同工具对参数形式的要求见 [工具传参形式速查](references/region-mapping.md#工具传参形式速查)：
>
> - `TCHouseDXxx` 系工具（`DescribeSpec` / `DescribeRegionZone` / `DescribeGoodsDetail` 等）：**只传 `Region` 字符串**
> - `BillingCalculatePrice`（询价）：**只传 `Region` 字符串**

> 💡 **多平台兼容说明**：本文档中所有提到的 `ask_user` 工具，在 WorkBuddy 平台中对应为 `AskUserQuestion`。后文不再重复标注。

## 核心工作流

### 步骤 0：业务画像采集

**目标**：收集足够信息以做出合理推荐。以下为必须了解的维度：

| 维度       | 关键问题                                                 | 默认值（用户未提供时）       |
| ---------- | -------------------------------------------------------- | ---------------------------- |
| 数据规模   | 当前总数据量（TB）？日增量？                             | 需询问，不可假设             |
| 数据保留   | 数据保留周期（月/年）？                                  | 需询问                       |
| 查询模式   | OLAP 聚合 / 高并发点查 / 实时分析 / 联邦查询？           | 默认 OLAP 聚合               |
| 并发要求   | 峰值并发查询数？                                         | 默认 10-20 QPS               |
| 延迟要求   | 查询响应时间要求（秒级/亚秒级）？                        | 默认秒级                     |
| 高可用要求 | 是否需要 FE 高可用？BE 副本数（默认 3）？是否跨 AZ？     | 默认 FE 多副本+3 副本，单 AZ |
| 架构形态   | 是否需要存算分离（CN 节点）以独立扩缩计算资源？          | 默认存算一体                 |
| 计费偏好   | 包年包月还是按量计费？                                   | 需询问                       |
| 预算约束   | 月预算上限？                                             | 可选，无则不限               |
| 地域偏好   | 期望部署地域？**（跨 AZ 部署的硬前置项，必须先定地域）** | 需询问                       |

**判断逻辑**：

- ✅ 用户一次性提供了大部分信息 → **强制**按 [地域映射表](references/region-mapping.md) 把地域补齐为 `Region`（字符串）+ `RegionId`（数字）两种形式（任何输入形式都要过这一步：中文名、英文串、数字 ID 都不例外），映射表为固定权威值，**无需再调用 API 二次验证**；补齐后进入步骤 1
- ❌ 信息严重不足（缺少数据规模 + 地域）→ 调用 `ask_user` 结构化询问
- ⚠️ 部分信息缺失 → 对缺失维度使用合理默认值，并在推荐中注明假设
- ❌ 地域信息在映射表中匹配不到（或大区模糊，如"华南地区"）→ 调用 `ask_user` 确认后再补齐
- ⚠️ **跨 AZ 部署校验**：用户提出"跨 AZ""高可用跨可用区"等需求时，**必须先确认地域**（地域是跨 AZ 的硬前置项）；确认地域后在步骤 1a 用 `TCHouseDDescribeRegionZone` 拉取实时数据，统计该地域内 **`Main=true` 且 `ContainerEnabled=1`** 的可用区数量，**至少需要 3 个**才能支持 3 副本跨 AZ 部署；若不足则应主动告知用户可选项（单 AZ / 降为 2 副本 / 更换地域），不得默默按跨 AZ 出方案

  **询问策略**：一次性询问所有缺失信息，避免多轮来回。推荐使用如下模板：

```
为了给您推荐合适的集群配置，请提供以下信息：
1. 数据规模：当前总数据量约多少 TB？每天新增多少 GB？
2. 数据保留周期：数据需要保留多久？
3. 查询模式：主要是报表/聚合分析、高并发点查，还是实时数仓/联邦查询？
4. 并发需求：峰值大约多少个并发查询？
5. 延迟要求：查询响应需要秒级还是亚秒级？
6. 部署地域：期望部署在哪个地域（如广州、上海、北京）？
7. 计费方式：倾向包年包月还是按量计费？
8. 是否需要存算分离架构（CN 节点独立扩缩，适合查询负载弹性大的场景）？
9. 预算：是否有月预算上限？（可选）
```

### 步骤 1：获取可用规格

调用 `TCHouseDDescribeRegionZone` 验证地域/可用区，再调用 `TCHouseDDescribeSpec` 获取可用规格。

#### 步骤 1a：验证地域/可用区可用性

调用 `TCHouseDDescribeRegionZone` 确认地域/可用区在 TCHouse-D 中可用。

**「可部署可用区」判定规则（强约束）**：一个可用区**能否用于新购**，唯一硬校验为 **`Main === true` 且 `ContainerEnabled === 1`**（两者需同时满足）。禁止使用 `Available` / `Count>0` 等字段做可部署性判断（本接口返回体不含 `Available` 字段；`Count` 只是历史存量集群数，不代表当前是否可下单）。字段语义详见 [api-reference.md#TCHouseDDescribeRegionZone](references/api-reference.md#TCHouseDDescribeRegionZone)。

**判断逻辑**：

- ✅ 用户指定的可用区满足 `Main=true && ContainerEnabled=1` → 继续步骤 1b
- ❌ 地域不存在（Items 中匹配不到）→ 告知用户该地域不支持，建议更换
- ❌ 用户指定的可用区 `Main=false` 或 `ContainerEnabled=0` → 从该地域下筛选出所有满足 `Main=true && ContainerEnabled=1` 的可用区，推荐给用户重新选择
- ⚠️ 用户未指定可用区 → 从满足 `Main=true && ContainerEnabled=1` 的候选中选取默认可用区（默认地域为广州 `ap-guangzhou`）
- ⚠️ **跨 AZ 部署校验**：若用户要求 3 副本跨 AZ，统计该地域内 `Main=true && ContainerEnabled=1` 的可用区数量：
  - **≥ 3** → 满足，从中选出 3 个作为 `UserMultiZoneInfos`
  - **= 2** → 不满足 3 副本跨 AZ，主动向用户提供三个可选项：① 单 AZ + 3 副本；② 跨 2 AZ + 2 副本（副本容灾能力降级）；③ 更换到满足条件的地域（如 `ap-beijing` 当前有 3 个可部署可用区）
  - **≤ 1** → 该地域根本不支持多 AZ，直接建议单 AZ 或更换地域
  - **禁止**：在任何情况下都不能默默按"跨 AZ 3 副本"生成方案而不做上述校验

#### 步骤 1b：获取可用规格列表

调用 `TCHouseDDescribeSpec`。

**参数**：

- `Region`：用户指定的地域
- `Zone`：用户指定的可用区
- `PayMode`：`PREPAID`（包年包月）或 `POSTPAID_BY_HOUR`（按量计费）
- `IsSSC`：固定传 `false`（即便方案推荐存算分离架构，本步骤获取规格列表时也统一以存算一体的规格集合为准；存算分离架构相关的 CN 规格在询价/方案描述阶段单独处理）

**返回**：`MasterSpec`（FE 规格）、`CoreSpec`（BE 规格）、`CNSpec`（CN 规格，存算分离时返回）、`AttachCBSSpec`（云盘规格）。

**售罄过滤（强制）**：拉取规格后**必须**先按 `Available=true` 过滤，售罄规格（`Available=false`）一律剔除，不得作为推荐候选。

**主推机型售罄时的自动降级策略**（按优先级）：

1. **同档本地盘机型**：例如 `S_16_64_H`（云盘）售罄 → 降级到 `H_16_64`（本地盘）或同档大数据型机型
2. **相邻档位云盘机型**：例如 16C64G 云盘售罄 → 降级到 4×（4C16G）或 2×（8C32G）等同等算力的小规格组合，保持 BE 总核数/总内存接近
3. **更换可用区**：以上均不可行时，提示用户更换同地域其他 `Available=true` 的可用区
4. **更换地域**：极端情况下提示用户更换地域

降级方案必须在「选型理由」中显式说明：「原推荐 X 在该地域/可用区下 Available=false（售罄），已自动降级至 Y」。

**判断逻辑**：

- ✅ 成功返回且过滤后有可用规格 → 进入步骤 2
- ❌ 过滤后规格为空 → 该可用区主流机型全部售罄，按上述降级策略处理或建议更换可用区
- ❌ `InvalidParameter` → 检查 Region/Zone 格式，修正后重试
- ❌ `InternalError` → 等 3 秒重试，最多 3 次；仍失败则基于经验规格给出建议并注明"未能实时获取规格，建议在控制台确认"

**记录信息**：FE/BE/CN 规格列表（CPU/内存/磁盘类型/磁盘范围 + Available 标志）、是否支持存算分离、可挂载 CBS 类型。

### 步骤 2：规格匹配与方案生成

基于业务画像和可用规格，按 [选型规则](references/spec-recommendation-rules.md) 生成 2-3 个推荐方案。

**方案生成原则**：

| 方案类型           | 定位       | 特点                            |
| ------------------ | ---------- | ------------------------------- |
| 方案 A（推荐）     | 性价比最优 | 满足需求的最小配置 + 适当冗余   |
| 方案 B（性能优先） | 高性能     | 更高规格/更多节点，留足扩展空间 |
| 方案 C（经济型）   | 成本最低   | 满足基本需求，冗余较少          |

**每个方案必须包含**：

1. **FE 节点（Frontend）**：机型（CPU/内存）、节点数量（HA 时 ≥3，建议 1 master + N follower 或 2 follower + 1 observer）、磁盘
2. **BE 节点（Backend）**：机型、节点数量（默认 ≥3 以匹配 3 副本）、磁盘类型、单盘容量、云盘数量
3. **CN 节点（Compute Node，可选）**：仅存算分离架构时存在，独立的计算节点
4. **副本数**：默认 3 副本（建议生产至少 3）
5. **高可用配置**：FE Master+Follower 选举、是否跨 AZ
6. **网络方案**：VPC/子网建议
7. **存储方案**：磁盘类型选择理由（CLOUD_HSSD 增强型 SSD vs CLOUD_SSD vs CLOUD_PREMIUM）
8. **内核版本**：推荐版本及理由

**选型核心逻辑**（详见 [references/spec-recommendation-rules.md](references/spec-recommendation-rules.md#选型决策树)）：

- 数据量 < 1TB → BE 4C16G 起步，3 节点（满足 3 副本）
- 数据量 1-10TB → BE 16C64G，3-5 节点
- 数据量 10-50TB → BE 32C128G 或 64C256G，5-10 节点
- 数据量 > 50TB → 大数据型机型（BIGDATA），10+ 节点 + 冷热数据分层
- 高并发（>50 QPS）→ 增加 FE/BE 节点数，必要时启用存算分离架构 + 多个 CN
- 亚秒级延迟 → 优先 CLOUD_HSSD（增强型 SSD）+ 更多内存 + 物化视图

### 步骤 2.5：存储容量校验（ASSERT）

**校验公式**：

```
集群可用存储 = BE 节点数 × 盘数 × 单盘大小 × 0.9（格式化损耗）
需求存储 = 数据量 × 压缩比 × 副本数 × 1.3（冗余系数）

ASSERT: 集群可用存储 ≥ 需求存储
```

**默认系数表（统一校验口径）**：

| 系数                                | 默认值   | 说明                                       |
| ----------------------------------- | -------- | ------------------------------------------ |
| 压缩比（聚合模型 AGGREGATE/UNIQUE） | **0.30** | 报表/BI 聚合分析，列存压缩效果好           |
| 压缩比（明细模型 DUPLICATE）        | **0.40** | 明细查询/混合负载                          |
| 压缩比（时序数据）                  | **0.20** | 监控/IoT，数值型重复度高                   |
| 副本数                              | **3**    | 生产标配，用户可指定 1/2/3                 |
| 冗余系数                            | **1.3**  | 预留 30% 用于 Compaction 临时空间/增长缓冲 |
| 格式化损耗系数                      | **0.9**  | 文件系统格式化损耗                         |

**其中**：

- `数据量`：用户提供的原始总数据量（GB）
- `压缩比`：按上表选取，用户未明确数据模型时**默认 0.30（聚合模型）**
- `BE 节点数`：BE 节点数量
- `盘数`：每节点的云盘数量（DiskCount）
- `单盘大小`：单块云盘容量（GB）
- 详细压缩比参考表见 [references/spec-recommendation-rules.md#3-存储选型指南](references/spec-recommendation-rules.md#3-存储选型指南)

**校验流程**：

```
对每个方案执行：
1. 计算 集群可用存储 = BE 节点数 × 盘数 × 单盘大小(GB) × 0.9
2. 计算 需求存储 = 数据量(GB) × 副本数 × 1.3
3. IF 集群可用存储 ≥ 需求存储:
     → 校验通过，进入步骤 3
4. ELSE:
     → 自动上调单盘大小或节点数，重新执行校验直到通过
```

**注意事项**：

- 每个方案独立校验
- 调整后需在报告中注明：「已自动上调磁盘大小以满足存储需求」
- 校验计算过程需在「选型理由」中展示
- 存算分离架构下，CN 节点不参与存储校验（CN 仅计算无持久化数据）

### 步骤 3：费用估算

费用估算分为两步：先获取商品配置信息，再调用 billing 询价接口获取真实价格。

> 💰 **价格单位强提示**：**`BillingCalculatePrice` 返回的所有金额字段（`Price` / `TotalCost` / `RealTotalCost`）单位均为「分」，展示给用户前必须除以 100 转换为「元」**。忘记换算会导致报价虚高 100 倍，属于最常见的低级错误，必须在每次读取价格时立即完成换算。

> ⚠️ **跨可用区询价的白名单限制**：`TCHouseDDescribeGoodsDetail` 的 `EnableMultiZones=true`（多可用区部署询价）在部分账户下会返回 `InvalidParameter`，原因是跨 AZ 询价能力受后端白名单控制。**推荐做法**：跨 AZ 与单 AZ 部署的计费单价一致（差异仅在架构层面），因此若跨 AZ 询价报错，直接**改用单可用区参数（`EnableMultiZones=false` 且不传 `UserMultiZoneInfos`）获取单价**即可，无需为白名单问题阻塞流程；在报告中注明「跨 AZ 部署不影响计费单价，仅为架构部署差异」。

#### 步骤 3.1：获取商品配置信息

调用 `TCHouseDDescribeGoodsDetail` 获取询价所需的商品配置信息（GoodsCategoryId、GoodsDetail 等）。

**参数构造**：

- `Region`、`Case=CREATE_QUERYPRICE`、`Zone`
- `HaFlag`：FE 是否高可用（true/false）
- `HaType`（0=非HA / 1=读高可用 / 2=读写高可用）
- `ProductVersion`：内核版本号（如 `2.0`，当前支持 `3.1-new` / `3.0-new` / `4.1` / `starrocks-3.3` / `3.1` / `3.0` / `2.1` / `2.0` / `1.2` / `1.1` / `1.0` / `0.15`，未来可能扩展）
- `Resources`：JSON 数组，每项包含 `Type`（MASTER/CORE/CN）、`SpecName`、`Count`、`DiskSpec`（DiskType/DiskSize/DiskCount）
- `ChargeProperties`：计费信息（ChargeType + TimeSpan + TimeUnit + RenewFlag）
- `EnableMultiZones`、`UserMultiZoneInfos`：跨 AZ 部署参数。**多 AZ 时两者必须一起传**，`UserMultiZoneInfos` 示例：`[{"Zone":"ap-shanghai-2"},{"Zone":"ap-shanghai-5"},{"Zone":"ap-shanghai-8"}]`
- `Details`：**必传** `{"EnableAlarmStrategy": true}`，与控制台真实下单保持一致；缺失会导致计费中心 `billingHashCode` 与已注册 SKU 不匹配，下一步 `BillingCalculatePrice` 会报 `FailedOperation.GetPriceParamError`（all price code not match）
- `TimeZone`：建议传 `UTC+8`，与控制台保持一致

**返回信息**：`GoodsCategoryId`、`GoodsDetail`、`GoodsNum`、`PayMode`、`RegionId`、`ZoneId`。

#### 步骤 3.2：调用 billing 询价

将步骤 3.1 的返回结果传入 `BillingCalculatePrice` 进行真实询价。

**参数**：

- `GoodsCategoryId`、`GoodsDetail`、`GoodsNum`：来自步骤 3.1
- `PayMode`：`prePay` / `postPay`
- `Region`、`Zone`

**返回**：`Price`（原价）、`TotalCost`（总费用）、`RealTotalCost`（折后实付）、`Policy`（折扣率）、`TimeSpan`、`TimeUnit`。

> 💰 **价格单位（再次强调）**：**`Price` / `TotalCost` / `RealTotalCost` 单位均为「分」，输出报告前务必除以 100 转换为「元」**。

**判断逻辑**：

- ✅ 成功 → 进入步骤 4
- ❌ 询价失败 → 最多重试 3 次（每次间隔 3 秒）；仍失败则该方案不提供具体价格，建议「询价失败，无法获取该方案的真实价格，建议前往控制台手动询价确认」

**费用展示**：

- 包年包月：月费用 + 年费用（含折扣）
- 按量计费：小时费用 + 预估月费用（按 720 小时/月）
- 对比维度：总费用、单 TB 存储成本、单 QPS 成本

> ⚠️ **关于折扣**：报价应以折后价（`RealTotalCost`）为准展示，并提示用户「实际折扣因账户而异，最终价格以下单页面为准」。

### 步骤 4：生成推荐报告

按 [输出报告格式](references/spec-recommendation-rules.md#输出报告格式) 生成选型推荐报告。

**报告必须包含**：

1. 业务画像摘要（确认理解正确）
2. 推荐方案对比表（配置 + 费用）
3. 每个方案的详细配置说明（FE/BE/CN 分别列出）
4. 最终推荐及理由
5. 后续操作指引（如何在控制台创建 / 注意事项）

**报告末尾必须给出购买页跳转提示（严格规则）**：

- ✅ **唯一允许的购买页链接格式**：`https://buy.cloud.tencent.com/tchoused?region={Region}`
  - `{Region}` 必须替换为本次推荐使用的 Region 字符串（如 `ap-guangzhou`、`ap-shanghai`、`ap-beijing`），**严禁省略 `?region=` 参数**。
  - 示例：广州 → `https://buy.cloud.tencent.com/tchoused?region=ap-guangzhou`
- ❌ **禁止使用以下任何形式**（这些是常见的错误猜测，模型必须避免）：
  - `https://console.cloud.tencent.com/cdwdoris/createStandard?rid=xxx`
  - `https://console.cloud.tencent.com/cdwdoris/create` 或任何 `cdwdoris/*` 的创建/新建路径
  - 不带 `?region=` 参数的 `https://buy.cloud.tencent.com/tchoused`
  - 任何使用 `rid=` 数字 ID 的形式
- 链接以 Markdown 链接形式呈现，例如：`[前往 TCHouse-D 购买页创建集群](https://buy.cloud.tencent.com/tchoused?region=ap-guangzhou)`，方便用户点击后在浏览器中打开。

### 步骤 5：用户确认与调整

- 用户对方案有疑问 → 解释选型理由
- 用户要求调整 → 基于新约束重新生成方案（回到步骤 2）
- 用户确认方案 → 提供中文友好的部署参数参考

## 频率控制

| 限制                        | 阈值         | 说明                  |
| --------------------------- | ------------ | --------------------- |
| 工具总调用频率              | ≤ 15 次/分钟 | 避免触发平台限流      |
| TCHouseDDescribeSpec 调用   | ≤ 3 次/轮    | 通常只需 1 次         |
| TCHouseDDescribeGoodsDetail | ≤ 5 次/轮    | 每个方案获取配置 1 次 |
| BillingCalculatePrice       | ≤ 5 次/轮    | 每个方案询价 1 次     |

**超限处理**：连续收到 `RequestLimitExceeded` → 等 5 秒重试，连续 3 次仍失败 → 基于经验估算费用，告知用户被限流。

## 错误码与处理策略

| 错误码/场景            | Agent 行为                                                                                                                                                                                            |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `AuthFailure.*`        | 报告鉴权失败，提示用户检查访问权限                                                                                                                                                                    |
| `InvalidParameter.*`   | 检查 Region/Zone/PayMode 格式，尝试修正后重试 1 次；无法修正 → 报告问题                                                                                                                               |
| `ResourceInsufficient` | 该规格在目标可用区售罄，自动推荐替代规格或建议更换可用区                                                                                                                                              |
| `InternalError`        | 等 3 秒重试，最多 3 次；仍失败 → 基于经验给出建议并注明未实时验证                                                                                                                                     |
| `RequestLimitExceeded` | 等 5 秒重试；连续 3 次 → 降低频率，基于经验估算                                                                                                                                                       |
| `UnsupportedRegion`    | 该地域未开通 TCHouseD 产品。**不重试、不自动切换地域**，必须调用 `ask_user` 让用户确认地域。详见 [error-handling.md §1](references/error-handling.md#1-unsupportedregion该接口不支持此地域访问) |
| 内核版本不匹配         | 使用经验沉淀库中的「已知可用内核版本」重试，优先选择「推荐」版本                                                                                                                                      |
| 兜底（未列出错误码）   | 报告完整错误信息 + RequestId                                                                                                                                                                          |

## 安全规则

1. **本 Skill 为纯只读咨询**：所有操作均为查询类（DescribeSpec/询价），不涉及实际创建资源
2. **不自动创建集群**：即使用户说"帮我创建"，也只输出配置方案和参数，不执行 CreateInstance
3. **费用信息仅供参考**：明确标注"实际费用以下单时为准"
4. **凭据安全**：不在输出中展示任何凭据信息
5. **关键参数不假设**：数据规模、地域等核心参数缺失时必须询问，不自行假设

## 经验沉淀库

| 经验                                     | 置信度 | 说明                                                 |
| ---------------------------------------- | ------ | ---------------------------------------------------- |
| BE 节点数建议为 3 的整数倍（3 副本场景） | ⭐⭐⭐ | 默认 3 副本，节点数为 3 的倍数能均衡 Tablet 分布     |
| BE 16C64G 是最通用的起步规格             | ⭐⭐⭐ | 适合大多数中等规模（1-10TB）的 OLAP 场景             |
| FE 至少 1 个 master + 2 个 follower      | ⭐⭐⭐ | FE HA 选举需要奇数节点，3 节点是最小配置             |
| 存算分离适合查询负载弹性大的场景         | ⭐⭐⭐ | CN 节点可独立扩缩，避免为短期峰值买单 BE             |
| 增强型 SSD（CLOUD_HSSD）适合低延迟场景   | ⭐⭐⭐ | IOPS 和吞吐量显著优于高性能云硬盘，亚秒级查询首选    |
| 磁盘容量建议预留 30% 冗余                | ⭐⭐   | 考虑数据增长 + Compaction 临时空间 + 系统开销        |
| 大数据型机型适合冷数据 + 冷热分层        | ⭐⭐   | BIGDATA 机型本地盘容量大、单 TB 成本低，搭配冷热分层 |
| 跨 AZ 部署增加约 10-15% 延迟             | ⭐⭐   | 跨可用区网络延迟，对延迟敏感场景需权衡               |
| 按量计费适合测试/短期项目                | ⭐⭐   | 长期运行（>3 个月）包年包月更划算（通常 6-7 折）     |

## 已知可用内核版本

> 更新时间：2026-06-13

| 版本号      | 状态      | 说明                         |
| ----------- | --------- | ---------------------------- |
| doris-2.1.x | ✅ 推荐   | 当前最新稳定版，支持全部功能 |
| doris-2.0.x | ✅ 可用   | LTS 长期支持版，兼容性好     |
| doris-1.2.x | ⚠️ 仅存量 | 不再推荐新建集群使用         |

**使用规则**：

- 新建集群默认使用「推荐」版本
- 若询价报错提示版本不可用，尝试下一个「可用」版本
- 用户有特定版本要求时优先满足用户需求
