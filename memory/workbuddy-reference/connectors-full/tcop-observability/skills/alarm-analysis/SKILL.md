---
name: tcop-alarm-analysis
description: >
  Tencent Cloud alert (Cloud Monitor) analysis skill. Use it when querying alarm history,
  aggregating alarms by dimension, inspecting a single alarm or policy, or assessing alarm
  coverage and policy health.
  触发词：告警、告警历史、告警记录、告警统计、告警分布、告警详情、告警策略、策略详情、
  覆盖检查、未绑策略、僵尸策略、重复策略、未配通知、告警治理、接收人。
  何时不触发：Prometheus 告警规则盘点、APM 链路分析、RUM 前端监控、CLS 日志检索不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# 告警分析 / Alert Analysis

## 概述 / Overview

本 Skill 覆盖腾讯云监控告警域的 MCP 工具：告警历史的聚合统计与明细检索、单条告警与策略
详情下钻，以及告警覆盖治理（按需）。

This skill covers the alert domain: aggregated and detail-level alarm-history queries,
single-alarm / policy drill-down, and (on demand) alert-coverage governance.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `alarm_history_query` | 告警历史查询：聚合统计 / 明细分页 | `result_mode` |
| `alarm_detail` | 告警详情三入口：单条告警 / 关联指标 / 策略详情 | `target` |

> 时间参数制式：告警域使用**东八区（CST+8）字符串**，格式 `2006-01-02 15:04:05`，
> 例如 `2025-08-06 10:30:00`。与 APM（Unix 秒）、Prometheus（RFC3339）组合时必须换算。

---

## 1. `alarm_history_query` — 告警历史查询

| `result_mode` 取值 | 语义 |
|-------------------|------|
| `aggregate` | 按维度聚合统计分布 |
| `detail` | 按过滤条件分页拉取明细 |

### 1.1 `result_mode=aggregate`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `result_mode` | string | 是 | 固定 `aggregate` |
| `start_time` | string | 是 | 开始时间，`2006-01-02 15:04:05`（CST+8） |
| `end_time` | string | 是 | 结束时间，`2006-01-02 15:04:05`（CST+8） |
| `group_by_fields` | array | 是 | 聚合字段列表，取值见下方支持清单 |
| `conditions` | array | 否 | 过滤条件列表 |

### 1.2 `result_mode=detail`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `result_mode` | string | 是 | 固定 `detail` |
| `start_time` | string | 是 | 开始时间，`2006-01-02 15:04:05`（CST+8） |
| `end_time` | string | 是 | 结束时间，`2006-01-02 15:04:05`（CST+8） |
| `conditions` | array | 是 | 过滤条件列表（与 aggregate 分支支持完全相同的字段） |
| `page_number` | number | 否 | 页码，从 1 开始，默认 1 |
| `page_size` | number | 否 | 每页数量，默认 10 |
| `order_by` | string | 否 | 排序字段，如 `FirstOccurTime` |
| `order` | string | 否 | 排序方向：`asc` / `desc` |

> 两个分支的 `conditions` 语义完全一致，通常配合使用：先 `aggregate` 看分布，再 `detail` 取样本。
> 当明细返回条数小于总数时，不要仅凭部分数据下全局结论，应回到 `aggregate` 看整体分布。

### 1.3 `conditions` 元素结构

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `field_key` | string | 是 | 字段名 |
| `field_values` | array | 是 | 字段值列表 |
| `operator` | string | 是 | `EQUAL` / `WILDCARD` / `NOT_EQUAL` / `IN` / `NOT_IN` / `REGEXP` / `RANGE` |

**操作符与值数量约束**：仅 `IN` / `NOT_IN` / `RANGE` 支持多个值；其余操作符只允许 1 个值，
违反会直接报错。`AlarmObject` 与 `AlarmContent` 在对话中常描述不完整，过滤时优先用 `WILDCARD`。

### 1.4 支持聚合 / 过滤的字段

`Date`（形如 `20250806`）、`MonitorType`、`AlarmCategory`（`METRIC` / `EVENT`）、
`CloudProductID`（如 `cvm`、`cdb`、`crs`）、`Region`（如 `ap-guangzhou`）、`AlarmStrategyType`、
`MetricName`、`AlarmContent`、`AlarmStatus`（`Trigger` / `Recover` / `NoData` / `NoConf` / `Stateless`）、
`AlarmObject`、`PolicyID`、`OccurHourTime`（形如 `2025080812`）、`AlarmLevel`（`Warn` / `Remind` / `Serious` / `None`）、
`UnionReceiver`（`UID-` 前缀为单个接收人、`GROUP-` 前缀为接收人分组）、`UIDReceiver`、`GroupReceiver`、
`IsPredefinedPolicy`（`true` / `false`）、`IsShielded`。

---

## 2. `alarm_detail` — 告警详情

| `target` 取值 | 语义 | 需提供 |
|--------------|------|--------|
| `history` | 单条告警历史详情（含同对象、同策略近 7 天聚合上下文） | `alarm_id` |
| `metrics` | 告警关联的指标时序数据（支持同比） | `alarm_id` |
| `policy` | 告警策略详情（无告警 ID 时的治理入口） | `policy_id` |

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target` | string | 是 | `history` / `metrics` / `policy` |
| `alarm_id` | string | `history` / `metrics` 时必填 | 告警唯一标识 |
| `policy_id` | string | `policy` 时必填 | 告警策略 ID，`policy-` 前缀 |
| `start_time` | string | 否（仅 `metrics`） | `2006-01-02 15:04:05`（CST+8），省略默认最近 1 小时 |
| `end_time` | string | 否（仅 `metrics`） | `2006-01-02 15:04:05`（CST+8），省略默认当前时间 |
| `compared_time_mode` | string | 否（仅 `metrics`） | 同比模式：`yesterday` / `last_week` / `last_month`；不传则不返回同比 |

`target=history` 一次返回三部分：告警详情、相同告警对象近 7 天聚合统计、相同策略近 7 天聚合统计，
适合需要完整上下文时使用。

---

## 3. 按需工具 / On-demand tools

| 工具名（按名直调） | 触发场景 | 判别参数 |
|-------------------|---------|---------|
| `alarm_coverage_check` | 「**告警覆盖治理**」：检查对象是否被正确绑定告警策略，或分析某类策略的触发健康度 | `check_kind` |
| `dash_get_dashboard_metric_data` | 取云监控（Cloud Monitor）原生指标时序（控制台大盘同源数据） | `mode` |

以下工具**默认不出现**在工具列表中。需要时可直接按名调用，或用 `lookup_tool` 检索发现。

### 3.1 `check_kind=objects_binding` — 对象绑定检查

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `check_kind` | string | 是 | 固定 `objects_binding` |
| `monitor_type` | string | 是 | 监控类型，如 `MT_QCE` |
| `objects` | array | 是 | 待检查对象列表，元素形如 `{"id": "可选", "dimension": {"InstanceId": "ins-xxx"}}` |
| `object_dimension_type` | string | 是 | `BindingUserInput`（告警绑定时用户输入维度）或 `MonitorViewName`（基础监控视图维度） |
| `alarm_strategy_type` | string | 是 | 待检查的告警策略类型 |
| `object_region` | string | 否 | 地域，如 `ap-guangzhou` |

> `objects` 要求严谨匹配，应先通过 `resource_discover` 获取对象维度，再据此构造；不要凭记忆拼写。

### 3.2 `check_kind=strategy_type` — 策略健康检查

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `check_kind` | string | 是 | 固定 `strategy_type` |
| `monitor_type` | string | 是 | 监控类型，如 `MT_QCE` |
| `alarm_strategy_type` | string | 是 | 告警策略类型，如 `cvm_device` |
| `from_time` | string | 否 | 起始时间（CST+8），默认近 7 天 |
| `to_time` | string | 否 | 截止时间（CST+8），默认当前时间 |
| `need_zombie_policy` | boolean | 否 | 是否检查僵尸策略，默认 `false` |
| `zombie_policy_days` | number | 否 | 僵尸策略天数阈值，默认 `7` |
| `include_recommend_metrics` | boolean | 否 | 是否返回推荐指标列表，默认 `false` |

返回结果覆盖：高频触发策略、重复配置策略、僵尸策略、未配置通知模板的策略、推荐指标。

### 3.3 `dash_get_dashboard_metric_data` — 云监控原生指标取数

取云监控（Cloud Monitor）原生指标时序（即控制台大盘同源数据），用于「知道实例、要看指标走势」的场景。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `mode` | string | 否 | `fuzzy`（默认，传模糊指标名 + 实例关键词，系统自动解析为精确值）或 `strict`（上下文已明确 API 指标名与命名空间时使用） |
| `start_time` / `end_time` | string | 是 | 时间范围，**RFC3339 格式**（与告警域 CST+8 字符串、APM/RUM Unix 秒不同，跨域组合前须换算） |
| `fuzzy_metrics` | array | `mode=fuzzy` 时必填 | 模糊指标名列表，须同属一个云产品 |
| `fuzzy_instances` | array | `mode=fuzzy` 时必填 | 实例关键词列表（实例 ID 或名称），须同属一个云产品 |
| `api_namespace` | string | `mode=strict` 时必填 | API 指标命名空间，如 `QCE/CVM` |
| `metric_names` | array | `mode=strict` 时必填 | API 指标名列表 |
| `instances` | array | `mode=strict` 时使用 | 实例维度键值对列表 |
| `region` | string | `mode=strict` 时必填 | 地域，如 `ap-guangzhou`；`fuzzy` 模式可选，仅用于缩小实例搜索范围 |
| `period` | number | 否 | 数据粒度（秒），默认 `300` |

> 返回 Prometheus-like 的指标矩阵（时序数据）。不确定有哪些 API 指标时，可先用
> `resource_discover`（`need_api_metric_info=true`）或 `catalog_search` 拿到指标名与维度再调用。
> 实例关键词与指标必须属于同一云产品，禁止混用不同产品的实例或指标。

---

## 4. 用 `lookup_tool` 发现长尾工具 / Discovering tools

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「检查对象有没有绑定告警策略」「接口告警频繁触发」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

`lookup_tool` 返回候选工具名、**真实参数 schema** 与调用方式；拿到后按名直接调用即可。

---

## 5. 语法参考 / Syntax reference

默认工具集中有一个**展示型语法工具** `syntax_reference`（无需调用，说明在工具列表阶段即可阅读）。
与本域相关的是 `time_formats` 主题：告警域时间参数为**东八区（CST+8）字符串**，
与 APM（Unix 秒）、Prometheus（RFC3339）不同，跨域组合查询前必须换算。

---

## 6. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能查询密钥（或授权）所属账号下的告警历史与策略**。
建议使用仅含监控只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接监控只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误；③ 重新生成密钥并更新连接配置；④ OAuth 模式下重新授权。
- **429 请求过于频繁**：降低调用频率、缩小时间窗口后**稍后重试**。
- **常见参数错误**：时间格式非 `2006-01-02 15:04:05`（CST+8）、多值操作符与值数量不匹配、
  `alarm_id` / `policy_id` 与 `target` 不配套。请按第 1、2 节逐项核对后再重试。

## 7. 数据范围与使用边界 / Data scope

- 仅能查询凭证所属账号下的告警数据；跨账号不可见。
- 所有工具均为只读，不会创建、修改或删除告警策略与通知模板。
- 输出中不要展示密钥、接收人隐私、授权码等敏感信息。
