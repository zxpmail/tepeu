---
name: microgrid-guide
description: 微电网（Microgrid）使用指南，数据服务由微电网平台 MCP 连接器提供。用于查询负荷用电、光伏发电、储能充放电的日级/小时级电量与实时功率，计算市电功率/市电电量及绿电用能指标（自发自用率、自给率、上网率、电网依赖度），分析园区用能。当用户询问"市电功率/市电用电量"、绿电指标或需要换算展示微电网数据时应使用本 skill。
version: 1.0.0
display_name: "微网慧析指南"
display_name_en: "Microgrid Insight Guide"
description_zh: >-
  微网慧析指南面向园区微电网的日常运行与规划管理，提供从基础数据查询到绿电指标深度分析的一站式能力：一、多维数据查询：支持按日级/小时级维度，查询园区负荷、光伏发电、储能充放电的电量与实时功率数据，快速掌握各子系统的运行态势。二、市电换算分析：自动换算市电功率与市电用电量，清晰呈现微电网与主网之间的能量交互关系。三、绿电用能指标核算：一键计算自发自用率、自给率、上网率、电网依赖度等核心绿电用能指标，量化园区清洁能源利用水平。四、园区用能洞察：基于多维度数据与指标，为园区管理者提供用能结构分析、优化方向建议，助力园区实现低碳高效运行。
description_en: >-
  Microgrid Insight Guide provides one-stop capabilities for daily operation and planning management of park-level microgrids, from basic data queries to in-depth green-energy KPI analysis. 1) Multi-dimensional data query: query load, PV generation and ESS charge/discharge energy and real-time power at daily/hourly granularity to grasp the operation status of each subsystem. 2) Grid power derivation: automatically derive grid power and grid electricity to clarify the energy exchange between the microgrid and the main grid. 3) Green-energy KPI accounting: one-click computation of core KPIs such as self-consumption rate, self-sufficiency rate, export rate and grid-dependence to quantify the clean-energy utilization level. 4) Park energy insights: provide energy-mix analysis and optimization recommendations for park managers to support low-carbon and high-efficiency operation.
---

# 微电网使用指南

## Overview

微电网 MCP 连接器提供 10 个工具，按「数据类型 × 粒度」组织：负荷(load)、光伏(pv)、储能(ess) 三类数据 × 日级(daily)、小时级(hourly)、实时(current) 三种粒度。基于这些原始数据，可进一步换算市电电量与四类绿电用能指标（自发自用率、自给率、上网率、电网依赖度）。

## 服务商

本技能的数据服务由 **杭州艾特智慧科技有限公司** 提供，公司官网：<http://www.ataiot.com/>。

## 数据模型与工具速查

| 需求 | 工具 |
|------|------|
| 获取授权数据范围（前置） | `microgrid_list_user_data_scopes` |
| 日级负荷电量 | `microgrid_query_daily_load_electricity` |
| 小时级负荷电量 | `microgrid_query_hourly_load_electricity` |
| 日级光伏发电量 | `microgrid_query_pv_daily_generation_energy` |
| 小时级光伏发电量 | `microgrid_query_pv_hourly_generation_energy` |
| 日级储能充放电量 | `microgrid_query_ess_daily_charge_discharge_energy` |
| 小时级储能充放电量 | `microgrid_query_ess_hourly_charge_discharge_energy` |
| 实时负荷功率 | `microgrid_get_load_power` |
| 实时光伏发电功率 | `microgrid_get_pv_generation_power` |
| 实时储能充放电功率 | `microgrid_get_ess_charge_discharge_power` |

## 调用流程

1. **先调 `microgrid_list_user_data_scopes`** 获取授权范围，返回 `spaceId` 与可选的 `dataUnitId`。
2. 将返回的 `spaceId`（和 `dataUnitId`，若存在）**原样**传给查询工具。
3. 默认优先使用不带 `dataUnitId` 的全空间 scope；仅当用户明确指定某个数据单元时使用带 `dataUnitId` 的 scope。

## 时间与范围约束

- **时区**：Asia/Shanghai 业务时间。
- **日级**：`startDate`/`endDate` 格式 `YYYY-MM-DD`，含两端，最多 **366 个日历天**，允许未来日期。
- **小时级**：`startHour`/`endHour` 格式 `yyyyMMddHH`（10 位，如 `2026081415`），含两端，最多 **168 个小时点**（7 天）。
- **缺数 vs 零值**：缺失的日/时不返回（不补零）；返回 `0` 表示有记录但值为 0。两者不可互推。
- **当日值**：日级/小时级的当天数据会随上游统计任务持续更新，再次查询可能变化。
- **日时关系**：日级与小时级不保证严格相等，跨粒度核算时以日级为准。

## 基础换算：市电

### 市电功率（实时）

**公式：市电功率 = 总负荷功率 − 光伏发电功率 − 储能放电功率 + 储能充电功率**

同一 `spaceId` 下各取一次实时功率：

```text
loadPowerKw        = microgrid_get_load_power               # 总负荷功率
generationPowerKw  = microgrid_get_pv_generation_power      # 光伏发电功率
charge/dischargePowerKw = microgrid_get_ess_charge_discharge_power  # 储充/储放功率

市电功率 = loadPowerKw − generationPowerKw − dischargePowerKw + chargePowerKw
```

**注意**：
- 三个实时快照均无采样时间戳，可能采于不同时刻，结果存在瞬时误差；实时值逐秒波动，如需精确计量应以电量口径核算。
- `get_load_power` 默认口径已排除其他(100)/PV(102)/ESS(101) 电表，负荷不包含光伏与储能自身的计量。
- 结果为正表示从市电购电，为负表示反向送电到电网（即上网）。
- 若某些空间因缺少 PV/ESS 统计而返回 0 或 null，需提示用户数据可用性。

### 市电用电量（历史）

**公式：市电用电量 = 负荷用电量 − 光伏发电量 − 储能放电量 + 储能充电量**

日级与小时级电量均可按同一公式换算，对应工具返回字段：

| 分量 | 日级字段 | 小时级字段 |
|------|----------|------------|
| 负荷用电量 | `days[].loadEp` | `hours[].loadEp` |
| 光伏发电量 | `days[].generationEnergyKwh` | `hours[].generationEnergyKwh` |
| 储能放电量 | `days[].dischargeEnergyKwh` | `hours[].dischargeEnergyKwh` |
| 储能充电量 | `days[].chargeEnergyKwh` | `hours[].chargeEnergyKwh` |

```text
市电用电量 = loadEp − generationEnergyKwh − dischargeEnergyKwh + chargeEnergyKwh
```

**注意**：
- 小时级数据缺失时段不参与计算（该时段视为无记录，不补零）。
- 分公司/子单元的市电可对其各自 scope 单独计算。

## 用能指标体系

### 指标总览

| 指标 | 标准口径 | 公式 | 可行性 |
|------|---------|------|--------|
| 自发自用率（SCI / 绿电消纳率） | IEA-PVPS | 光伏自用 / PV | ✅ 可算 |
| 自给率（SSI / 绿电使用率） | IEA-PVPS | 光伏自用 / loadEp | ✅ 可算 |
| 上网率（Export rate） | 行业通用 | 上网电量 / PV | ✅ 可算 |
| 电网依赖度（Grid-dependence） | 行业通用 | 市电购入 / loadEp | ✅ 可算 |
| 弃光率 | 国家能源局 | 弃光电量 /（可利用发电量 − 不计入弃光统计电量） | ❌ 缺上游数据 |
| 新能源利用率 | 国家能源局 | 实际发电量 / 可利用发电量 | ❌ 缺上游数据 |
| 绿电消费占比（政策口径） | 发改委 42 号令 | 消纳的可再生能源电量 / 全社会用电量 | ❌ 超出 MCP 范围 |

四个可算指标共享同一分子：**光伏自用 = PV − 上网电量**（上网电量即返送/余电上网电量）。MCP 无上网电量字段，需先按下一节方法在小时粒度反推。

### 核心前置：上网电量反推（小时级）

**背景**：历史电量工具（日级/小时级）均无上网电量字段，也不支持 `gridMergeType` 拆分（仅实时功率工具支持 Self/Surplus/Full）。在"储能不向电网反向送电"的前提下，可用小时粒度净市电符号反推：

```text
每小时：净市电_h = loadEp_h + charge_h − PV_h − discharge_h
        上网电量_h = max(0, −净市电_h)
光伏自用 = ΣPV_h − Σ上网电量_h
```

原理：净市电 < 0 ⟺ 光伏+储能放电超出负荷+储能充电 → 富余电力只能上网（= |净市电|）；净市电 ≥ 0 → 本地资源不足，光伏全部自用，上网 = 0。

**前提与误差**：
- 必须**小时粒度**；日粒度下"白天上网 + 夜间购电"相互抵消，负净市电的绝对值只是上网下界，会低估上网量。
- 前提是储能不向电网放电（谷充峰放/自发自用策略）；若储能策略允许峰谷套利反向送电，反推失效，需上游上网数据。
- 未考虑弃光；若园区有限制光伏出力（弃光）策略，会高估上网/低估自用。
- 无法排除小时内部分钟级瞬时上网与购电并存（误差通常很小）。

**实测验证**（A 公司园区，2026-08-14~16，72 时点）：11 个上网时段全部集中在白天光伏强时段，所有上网量 ≤ 同小时 PV（无储能返送迹象），反推结果与能量流自洽。

### 自发自用率（SCI / 绿电消纳率）

**定义**：光伏自用电量占光伏发电量的比例，衡量光伏自身被本地消纳的程度。行业标准口径为 IEA-PVPS 定义的 SCI（self-consumption index）= PV 自用电量 / PV 发电量，即欧洲行业通称的 self-consumption ratio（SCR）。

```text
自发自用率 = 光伏自用 / PV = (PV − 上网电量) / PV
```

**参考值**：无储能系统通常 25~40%，配置储能后可达 60~90%；数值越高说明光伏发电被利用得越充分。

### 自给率（SSI / 绿电使用率）

**定义**：本地负荷中由光伏自用电量覆盖的比例，衡量园区对电网的独立程度。行业标准口径为 IEA-PVPS 定义的 SSI（self-sufficiency index）= PV 自用电量 / 总用电量（self-sufficiency）。

```text
自给率 = 光伏自用 / loadEp = (PV − 上网电量) / loadEp
```

**分母口径**：用 `loadEp`，不用 `(loadEp + charge)`：
- 储能充电量含市电充电（如某园区储能夜间谷充约 65% 为市电电），分子只含光伏，加 charge 会造成口径错配、系统性低估；
- 储能放电已体现在负荷中，再加充电量会双重计算储能吞吐（行业计量指南明确"count solar energy when it serves a load—not both when it charges the battery and when it leaves the battery"）；
- 实测（某园区 3 天合计）：loadEp 口径 39.6% vs (loadEp + charge) 口径 35.8%，低估约 3.8pp。

### 上网率（Export rate）

```text
上网率 = 上网电量 / PV = 1 − 自发自用率
```

衡量光伏发电上网（外送）的比例，与自发自用率互补。

### 电网依赖度（Grid-dependence）

```text
市电购入 = max(0, loadEp + charge − PV − discharge)   # 净市电为正部
电网依赖度 = 市电购入 / loadEp ≈ 1 − 自给率
```

衡量负荷对市电的依赖程度，与自给率互补（忽略储能充放损耗差异）。

### 超出 MCP 范围的指标

| 指标 | 官方口径 | 数据缺口 |
|------|---------|---------|
| 弃光率 | 弃光电量 /（可利用发电量 − 不计入弃光统计电量），国家能源局《光伏发电消纳监测统计管理办法》 | 需上游"可利用发电量/受限电量" |
| 新能源利用率 | 实际发电量 / 可利用发电量（政策参考线 ≥90%，国能发电力〔2024〕44 号） | 同上 |
| 绿电消费占比（政策口径） | 消纳的可再生能源电量 / 全社会用电量，国家发改委 2026 年 42 号令 | 绿证/消纳责任权重核算，超出 MCP 范围 |

## 数据语义与注意事项

- `get_load_power` / `get_pv_generation_power`：上游无当前值时返回 `0`，无法判断是实测 0 还是无数据。
- `get_ess_charge_discharge_power`：无匹配快照时返回 `null/null`，与 load/PV 返回 0 不同，需要区分处理。
- 缺数 vs 零值：缺失的日/时不返回（不补零）；返回 `0` 表示有记录但值为 0，两者不可互推。
- 当日数据随上游统计任务持续更新，再次查询可能变化。

## 上游增强建议（可精确化）

- 新增上网/返送电量查询工具（如 `query_daily_grid_feed_in_energy`，对应市电反向计量）。
- 或给日级/小时级 PV 电量工具增加 `gridMergeType` 参数（Surplus 模式下 PV − Self 部分即上网量）。

## 详细参考

- 工具完整说明（参数、返回结构、公共约定）：`references/tools.md`
