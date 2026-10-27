---
name: tcop-apm-diagnosis
description: >
  Tencent Cloud APM diagnosis skill. Use it when investigating application performance,
  slow or failing interfaces, error traces, span trees, database/MQ hotspots, or when
  you need to drill from business system to application to operation.
  触发词：APM、应用性能、接口耗时、慢接口、错误率、异常请求、Trace、TraceID、Span、调用链、
  链路追踪、业务系统、应用列表、数据库慢查询、MQ 堆积、探针、用量套餐。
  何时不触发：Prometheus 指标查询、告警历史检索、RUM 前端监控、日志全文检索、云产品资源盘点
  等非 APM 域问题不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# APM 应用性能诊断 / APM Diagnosis

## 概述 / Overview

本 Skill 覆盖腾讯云 APM（应用性能监控）域的 MCP 工具：从业务系统 → 应用 → 接口三级下钻，
再以 Span 列表 / Trace 调用树 / 多维指标定位性能与错误根因。

This skill covers the APM tool surface: three-level drill-down (business system → application →
operation), plus span lists, trace trees and metric queries for root-cause analysis.

所有工具均为只读查询，不会修改任何资源配置。

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `apm_overview` | APM 概览查询：业务系统 / 应用 / 接口三级列表 | — |
| `apm_trace_drill` | 链路下钻：搜 Span 列表、查指定 Trace 调用树 | `view` |
| `apm_metric_query` | APM 指标查询：按场景或按维度字段 | `shape` |

> 时间参数制式：APM 域全部使用 **Unix 秒时间戳（number）**，如 `1744786829`。
> 跨域组合查询时（如与告警域、Prometheus 域混用）必须先换算制式，禁止直接复用同一数值。

---

## 1. `apm_overview` — APM 概览

业务系统 → 应用 → 接口的三级下钻主入口，一次调用返回当前层级的列表与关键指标。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_time` | number | 是 | 开始时间，Unix 秒时间戳 |
| `end_time` | number | 是 | 结束时间，Unix 秒时间戳 |
| `region` | string | 是 | 地域，如 `ap-guangzhou`；省略时默认 `ap-guangzhou` |
| `instance_id` | string | 否 | 业务系统 ID（`apm-` 前缀），传入即下钻到应用层 |
| `instance_name` | string | 否 | 业务系统名称，支持模糊匹配 |
| `service_id` | string | 否 | 应用 ID（`svc-` 前缀）；填写时 `instance_id` 必填 |
| `service_name` | string | 否 | 应用名；填写时 `instance_id` 必填，传入即下钻到接口层 |
| `operation` | string | 否 | 接口名，如 `GET /slowRequest`；填写时 `instance_id` 必填 |
| `page_index` | number | 否 | 页码，从 1 开始，三种维度均生效 |
| `page_size` | number | 否 | 每页数量，三种维度均生效 |

**分页机制（重要）**

- 查任一列表都必须显式传 `page_index=1` 与 `page_size`，否则服务端只返回首页 20 条导致漏数据。
- 翻页判定统一公式：`page_index * page_size_limit < *_total_count`，成立则 `page_index + 1` 继续。
- 单页有效上限 `page_size_limit`：业务系统维度 `50`，应用 / 接口维度 `100`。
  禁止用 `page_index * page_size < total` 判定翻页（请求值与有效上限不一致会漏页或多翻）。
- 响应中的真实总数分别对应：`instances_total_count`（业务系统）、`services_total_count`（应用）、
  `operations_total_count`（接口）。
- 上述分页机制同时以**展示型语法工具** `syntax_reference` 的 `apm_pagination` 主题对外声明
  （无需调用该工具，说明在工具列表阶段即可阅读），两者表述保持一致。

---

## 2. `apm_trace_drill` — 链路下钻

| `view` 取值 | 语义 |
|------------|------|
| `span_list` | 搜索 Span 及其属性，用于按条件捞出候选 TraceID |
| `span_tree` | 查询指定 Trace 的调用树明细 |

### 2.1 `view=span_list`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_time` | number | 是 | 开始时间（Unix 秒） |
| `end_time` | number | 是 | 结束时间（Unix 秒） |
| `region` | string | 否 | 地域，默认 `ap-guangzhou` |
| `instance_id` | string | 是 | 业务系统 ID |
| `filters` | array | 否 | 过滤条件列表，元素形如 `{"key": "...", "type": "=", "value": "..."}` |
| `limit` | number | 否 | 返回数量上限 |
| `offset` | number | 否 | 分页偏移量 |
| `order_by` | string | 否 | 排序字段 |

`filters` 支持的 `key`：`status.code`（`0,1` 正常 / `2` 错误）、`span.kind`（`server`/`client`/`producer`/`consumer`/`internal`）、`duration`（微秒）、`operationName`、`error.object`、`component`、`peer.service`、`process.serviceName`、`traceID`、`spanID`。

`type` 支持：`=`、`in`、`!=`。

### 2.2 `view=span_tree`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_time` | number | 是 | 开始时间（Unix 秒） |
| `end_time` | number | 是 | 结束时间（Unix 秒） |
| `region` | string | 否 | 地域 |
| `instance_id` | string | 是 | 业务系统 ID |
| `trace_id` | string | 否 | 指定 TraceID 查询调用树 |
| `span_id` | string | 否 | 指定 SpanID 查询 |
| `service_name` | string | 否 | 应用名（抽样查询用） |
| `operation` | string | 否 | 接口名（抽样查询用） |
| `scene_type` | string | 否 | 特殊场景：`ERROR` / `SQL` / `MQ` |
| `object` | string | 否 | 聚焦对象，配合 `scene_type` 使用（错误类型 / 数据库名 / 队列名） |

典型用法：先 `span_list` 按 `status.code=2` 捞出错误 TraceID，再 `span_tree` 传 `trace_id` 看调用树；
错误根因分析可走 `scene_type=ERROR` + `object=<错误类型>`。

---

## 3. `apm_metric_query` — APM 指标查询

| `shape` 取值 | 语义 |
|-------------|------|
| `scene` | 按场景查通用指标（错误 / 异常 / 数据库 / 消息队列） |
| `fields` | 按维度字段分组查询指标 |

### 3.1 `shape=scene`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_time` | number | 是 | 开始时间（Unix 秒） |
| `end_time` | number | 是 | 结束时间（Unix 秒） |
| `region` | string | 否 | 地域 |
| `instance_id` | string | 是 | 业务系统 ID |
| `scene_type` | string | 否 | 场景类型：`ERROR` / `SQL` / `MQ` |
| `service_name` | string | 否 | 应用名 |
| `operation` | string | 否 | 接口名 |
| `object` | string | 否 | 聚焦对象（`SQL` 为数据库名、`MQ` 为队列名） |
| `is_metric_line` | boolean | 否 | `true` 返回指标走势线；`false`（默认）返回聚合值 |
| `limit` | number | 否 | 结果条数上限，仅 `is_metric_line=false` 时生效 |
| `span_kind` | string | 否 | 调用角色过滤（`SQL` 场景默认 `client,producer`） |

### 3.2 `shape=fields`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `start_time` | number | 是 | 开始时间（Unix 秒） |
| `end_time` | number | 是 | 结束时间（Unix 秒） |
| `region` | string | 否 | 地域 |
| `instance_id` | string | 是 | 业务系统 ID |
| `metrics` | array | 是 | 指标名列表，如 `["request_count_sum", "error_request_count"]` |
| `filters` | array | 否 | 过滤条件列表，格式同 `span_list` |
| `group_by_fields` | array | 否 | 分组字段，如 `["service.name", "service.version"]` |

常用指标名：`request_count_sum`（请求总量）、`error_request_count`（错误请求数）、`avg_duration`、
`p50_duration` / `p90_duration` / `p99_duration`（响应时间分位）。

---

## 4. 按需工具 / On-demand tools

以下工具**默认不出现**在工具列表中（不在 `tools/list` 里，模型看不到）。需要时可直接按名调用，
或用 `lookup_tool` 检索发现（见第 5 节）。它们的存在性与触发场景如下：

| 工具名（按名直调） | 触发场景 | 主要参数 |
|-------------------|---------|---------|
| `apm_DescribeMetricAnalyze` | 已知某条告警指标，需要找出对应的 TraceID 做根因下钻 | `start_time`、`end_time`、`instance_id`、`metrics_filter`（均必填） |
| `apm_DescribeApmDetail` | 需要线程剖析任务、探针版本、用量统计或套餐包信息时 | `region`、`detail_type`（必填，枚举 `PROFILE` / `AGENT` / `CONSUMPTION` / `COMBO`）、`start_time`、`end_time`、`instance_id`、`service_name`、`detail_object` |

> `apm_DescribeApmDetail` 的 `detail_type=PROFILE` 需同时提供 `instance_id` 与 `service_name`。

---

## 5. 用 `lookup_tool` 发现长尾工具 / Discovering tools

`lookup_tool` 是默认工具集内的检索入口：用自然语言描述任务，它返回候选工具名、**真实参数
schema** 与调用方式；拿到后按工具名直接调用即可，无需再经它中转。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「APM 线程剖析」「告警指标关联 TraceID」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

示例：`lookup_tool(task="查某个 APM 告警指标对应的调用链 TraceID")`。

---

## 6. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能查询密钥（或授权）所属账号下的 APM 资源**。
建议为自己的子账号生成仅含监控只读权限的密钥，避免使用主账号密钥。

- **获取密钥**：登录腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey；随后为该子账号挂接监控 / APM 只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手动填密钥。若长时间未使用导致授权失效，请在本应用的
  连接设置中**重新授权**（重新走一次登录流程）即可恢复。
- **401 鉴权失败**：说明凭证无效。排查顺序——① 去 CAM 控制台检查密钥是否**被禁用或删除**
  （最常见原因）；② 检查密钥是否被错误复制（首尾空格、缺字符）；③ 若密钥已被删除或泄密，
  请重新生成密钥并更新连接配置；④ OAuth 模式下走**重新授权**路径。
- **429 请求过于频繁**：已触发平台或下游限流。请降低调用频率、合并查询窗口后**稍后重试**；
  连续触发说明需要拆分时间范围，而不是缩短重试间隔。
- **返回空数据不等于出错**：先核对时间范围、地域与实例 ID，再逐项放宽过滤条件重试。

## 7. 数据范围与使用边界 / Data scope

- 仅能查询凭证所属账号、已被授权地域内的 APM 资源；跨账号数据不可见。
- 所有工具均为只读，不会创建、修改或删除任何 APM 配置。
- 输出中不要展示密钥、授权码等敏感信息。
- 时间参数必须为 Unix 秒时间戳；与其它域工具组合时注意制式换算。
