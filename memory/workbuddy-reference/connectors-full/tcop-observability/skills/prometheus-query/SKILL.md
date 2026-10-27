---
name: tcop-prometheus-query
description: >
  Tencent Cloud Prometheus (TMP) query skill. Use it when exploring metric/label metadata,
  running PromQL, listing existing alert rules, or diagnosing collection/scrape problems.
  触发词：Prometheus、TMP、PromQL、指标查询、指标名、标签、label、序列、rate、topk、
  告警规则、告警策略、录制规则、采集诊断、抓取配置、Target down、cardinality、基数。
  何时不触发：APM 链路、RUM 前端监控、告警历史明细、CLS 日志检索、云产品资源盘点等不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# Prometheus 指标查询 / Prometheus Query

## 概述 / Overview

本 Skill 覆盖腾讯云 Prometheus（TMP）域的 MCP 工具：实例发现、指标与标签元数据探查、
PromQL 查询、已有告警规则盘点。

This skill covers the Prometheus tool surface: instance discovery, metric/label metadata
exploration, PromQL queries and existing alert-rule inventory.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `prom_instance` | 列实例 / 查实例详情 | `action` |
| `prom_explore` | 探查指标名、标签名、标签值 | `target` |
| `prom_query` | 执行 PromQL 查询（唯一数据出口） | — |
| `prom_list_alert_rules` | 列出实例下已有告警规则 | `rule_kind` |

> 时间参数制式：Prometheus 查询类工具使用 **RFC3339 字符串**（`startRfc3339` / `endRfc3339`，
> 省略时默认近 1 小时）。与 APM / 告警域组合查询时必须先换算制式。

---

## 1. `prom_instance` — 实例查询

| `action` 取值 | 语义 |
|--------------|------|
| `list` | 按地域列出账号下的 Prometheus 实例 |
| `detail` | 查单个实例详情 |

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `action` | string | 是 | `list` 或 `detail` |
| `Region` | string | `list` 时可选 | 地域，如 `ap-beijing`；省略默认 `ap-guangzhou`。**仅 `list` 生效** |
| `InstanceId` | string | `detail` 时必填 | 实例 ID，如 `prom-abcd1234` |

> 重要：`action=detail` 的地域由服务端按实例反查，**传入 `Region` 会被静默忽略**（不报错、
> 也不生效）。请勿依赖它在 `detail` 场景指定地域。

---

## 2. `prom_explore` — 元数据探查

三步下钻：指标名 → 标签名 → 标签值。

| `target` 取值 | 语义 |
|--------------|------|
| `metric_names` | 按正则匹配指标名 |
| `label_names` | 查指定指标可用的标签名 |
| `label_values` | 查指定标签的取值集合 |

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target` | string | 是 | 上述三个取值之一 |
| `InstanceId` | string | 是 | 实例 ID |
| `regex` | string | `target=metric_names` 时必填 | 指标名正则 |
| `labelName` | string | `target=label_values` 时必填 | 标签名 |
| `matches` | array | 否（`label_names` / `label_values`） | Prometheus 选择器数组 |
| `startRfc3339` | string | 否 | 开始时间（RFC3339），默认近 1 小时 |
| `endRfc3339` | string | 否 | 结束时间（RFC3339），默认近 1 小时 |
| `limit` | number | 否 | 返回条数上限（`label_names` 默认 20，`label_values` 默认 2000） |

**语法硬约束（易踩坑）**

- `regex` 是**全匹配语义**（等效 `^regex$`）。传 `cdb` 只会匹配恰好叫 `cdb` 的指标，
  匹配前缀必须写 `qce_cdb_.*`。
- `matches` 的元素必须是**完整选择器**，形如 `{__name__="http_requests_total"}`；
  裸指标名（`["up"]`）不是合法选择器，会被拒绝或返回空。
- `labelName` 是标签名本身（如 `namespace`、`instance`），不是选择器。

---

## 3. `prom_query` — PromQL 查询

这是 Prometheus 时序数据的**唯一查询出口**。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `InstanceId` | string | 是 | 实例 ID |
| `expr` | string | 是 | PromQL 表达式 |
| `queryType` | string | 否 | `instant` 或 `range`；建议显式指定（省略时按后端默认） |
| `startRfc3339` | string | 否 | 开始时间（RFC3339），默认近 1 小时 |
| `endRfc3339` | string | 否 | 结束时间（RFC3339），默认近 1 小时 |
| `stepSeconds` | string | 否 | 范围查询步长，如 `"60s"`、`"5m"` |

**PromQL 生成约束**

- Counter 类指标（`_total` 结尾、单调递增计数）必须套 `rate()` / `increase()`，
  禁止直接查询原始累计值。
- 聚合分组用 `by(...)` / `without(...)`。
- `range` 窗口至少为采集周期的 2 倍（默认抓取间隔下至少 2 分钟），窗口过小会因样本不足返回空。
- 多集群实例查询必须带 `cluster="<clusterid>"` 过滤条件。

---

## 4. `prom_list_alert_rules` — 已有告警规则

| `rule_kind` 取值 | 语义 |
|-----------------|------|
| `alert_group` | 告警组及其下规则 |
| `alert_policy` | 告警策略 |
| `classic` | 经典告警规则 |
| `recording` | 录制规则 |
| `all` | 并发拉取以上四类并合并结果 |

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `rule_kind` | string | 是 | 上述取值之一 |
| `InstanceId` | string | 是 | 实例 ID |
| `Limit` | number | 否 | 每类返回条数上限 |
| `RuleSource` | string | 否 | 仅 `rule_kind=recording` 生效，默认 `prom2` |

---

## 5. 按需工具 / On-demand tools

以下工具**默认不出现**在工具列表中。需要时可直接按名调用，或用 `lookup_tool` 检索发现。

| 工具名（按名直调） | 触发场景 | 主要参数 |
|-------------------|---------|---------|
| `prom_diagnose_collection` | 「**采集诊断**」：一次调用取全实例采集侧原始数据（集群采集组件、Target up/down 明细、集成中心） | `InstanceId` |
| `prom_rule_evidence` | 告警规则优化取证：需要策略触发统计或查询性能数据 | `evidence_kind`（`trigger_stats` / `query_performance`）、`InstanceId` 等 |
| `prom_getAlertTemplates` | 需要对照告警模板库做覆盖率比对 | `ExporterCode`（可选，按 exporter 过滤） |
| `prom_listPrometheusAlarmNotifyHistories` | 分析告警触发频率与降噪 | `InstanceId`、`RuleName`（可选）、`Limit`（可选） |
| `prom_getCurrentTime` | 需要当前时间的 RFC3339 / Unix 表示 | `Format` |

`prom_rule_evidence` 分支说明：

- `evidence_kind=trigger_stats`：策略触发统计，参数 `InstanceId`、`RuleNameFilter`、`startRfc3339`、`endRfc3339`、`BucketBy`（默认 `hour`）。
- `evidence_kind=query_performance`：查询性能数据，参数 `InstanceId`、`Aspect`（默认 `rule_eval`）、`Quantile`（默认 `0.99`）、`RuleGroupNameFilter`（可选）。

---

## 6. 用 `lookup_tool` 发现长尾工具 / Discovering tools

`lookup_tool` 用自然语言描述任务，返回候选工具名、**真实参数 schema** 与调用方式，随后按名直调。

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「Prometheus 采集诊断」「查告警通知历史」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

---

## 7. 查询语法参考 / Syntax reference

默认工具集中有一个**展示型语法工具** `syntax_reference`（无需调用，其说明在工具列表阶段即可阅读），
与本域相关的主题：

| `topic` | 主题 |
|---------|------|
| `prometheus_params` | `regex` 全匹配语义、`matches` 选择器构造、`prom_instance` 的 Region 反语义 |
| `promql_constraints` | Counter 必须套 `rate()`/`increase()`、`by/without` 聚合、`range` 窗口下限 |
| `time_formats` | 各域时间参数制式差异（APM/RUM 用 Unix 秒、Prometheus 用 RFC3339、告警用 CST+8） |

---

## 8. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能查询密钥（或授权）所属账号下的 Prometheus 实例**。
建议使用仅含监控只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接监控只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：凭证无效。① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误（首尾空格、缺字符）；③ 重新生成密钥并更新连接配置；
  ④ OAuth 模式下重新授权。
- **429 请求过于频繁**：已触发限流。降低调用频率、缩短查询窗口后**稍后重试**，
  不要缩短重试间隔连续打。
- **空结果排查顺序**：实例 ID 是否正确 → 时间窗口是否覆盖 → 指标名是否存在（先 `prom_explore`
  确认）→ 标签过滤是否过严。空结果不等于故障。

## 9. 数据范围与使用边界 / Data scope

- 仅能查询凭证所属账号、已授权地域内的 Prometheus 实例与指标。
- 所有工具均为只读，不会创建、修改或删除规则与实例配置。
- 输出中不要展示密钥、授权码等敏感信息。
- 时间参数制式与本域一致（RFC3339）；跨域组合查询时必须换算。
