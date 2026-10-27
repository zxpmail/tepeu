---
name: tcop-rum-analysis
description: >
  Tencent Cloud RUM (Real User Monitoring) skill. Use it when analysing web front-end
  performance metrics, JS errors, PV/UV, static-resource loading, or RUM raw logs.
  触发词：RUM、前端监控、真实用户监控、QAPM、页面性能、LCP、FCP、TTFB、首屏、JS 错误、
  异常、PV、UV、错误率、静态资源、慢加载、白屏、前端日志、版本对比、地区分布。
  何时不触发：APM 后端链路、Prometheus 指标、告警历史、CLS 日志检索不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# RUM 前端监控分析 / RUM Analysis

## 概述 / Overview

本 Skill 覆盖腾讯云 RUM（前端监控）域的 MCP 工具：应用列举、多维指标查询与全量日志检索。

This skill covers the RUM tool surface: project discovery, multi-dimensional metric queries
and full raw-log retrieval.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `rum_query` | 列应用 / 查指标 / 查日志 | `target` |

> 时间参数制式：RUM 使用 **Unix 秒时间戳（number）**，如 `1744786829`。
> 与告警域（CST+8 字符串）、Prometheus 域（RFC3339）组合时必须换算制式。

---

## 1. `rum_query` — RUM 查询

| `target` 取值 | 语义 | 使用的过滤参数名 |
|--------------|------|-----------------|
| `projects` | 列举 RUM-WEB 应用 | — |
| `metric` | 查询聚合指标 | `metric_filters` |
| `log` | 查询全量日志（支持分页与指定返回字段） | `log_filters` |

### 1.1 `target=projects`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target` | string | 是 | 固定 `projects` |
| `ProjectId` | string | 否 | 应用 ID，数字字符串 |
| `ProjectName` | string | 否 | 应用名称，精确匹配；与 `ProjectNameLike` 不可同时使用 |
| `ProjectNameLike` | string | 否 | 应用名称模糊匹配 |

### 1.2 `target=metric`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target` | string | 是 | 固定 `metric` |
| `ProjectId` | string | 是 | 应用 ID |
| `Metric` | string | 是 | 指标类型：`network` / `exception` / `pv` / `uv` / `performance` / `resource` |
| `Limit` | number | 是 | 查询条数，推荐 100，最大 1000 |
| `StartTime` | number | 否 | 开始时间（Unix 秒），默认 12 小时前 |
| `EndTime` | number | 否 | 结束时间（Unix 秒），默认当前时间 |
| `Region` | string | 否 | 区域：`ap-guangzhou`（默认）/ `ap-singapore` / `na-siliconvalley` |
| `metric_filters` | array | 否 | 过滤条件数组，元素形如 `{"Key": "...", "Operator": "...", "Value": "..."}` |
| `GroupBy` | array | 否 | 聚合分组字段列表（单字段也必须传数组） |

`Metric` 选择速查：请求次数 / 耗时 / 状态码错误率 / 网络异常 → `network`；JS 与 Promise 错误 →
`exception`（JS 错误过滤 `level=4`）；页面性能（LCP/FCP/TTFB 等）→ `performance`；PV → `pv`；UV → `uv`；静态资源加载 → `resource`。

`metric_filters` 操作符：`=`、`!=`、`like`、`not like`。**地区过滤使用 `region` 字段**。
常用字段：`brand`、`browser`、`device`、`env`、`error_msg`（仅 exception）、`ext1`~`ext10`、
`from`（resource 不支持）、`is_abroad`、`is_err`、`isp`、`level`（仅 exception）、`method`、
`net_type`、`os`、`platform`、`region`、`ret`、`status`、`url`、`version`。

`GroupBy` 可选值同上，另加时间维度 `time(1m)` / `time(5m)` / `time(1h)` / `time(1d)`。
**多维分析应逐字段分别 GroupBy**，不要一次 GroupBy 全部维度（会产生笛卡尔积导致结果被截断）。

### 1.3 `target=log`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `target` | string | 是 | 固定 `log` |
| `ProjectId` | string | 是 | 应用 ID |
| `Limit` | number | 是 | 查询条数，推荐 10，最大 1000 |
| `StartTime` | number | 否 | 开始时间（Unix 秒），默认 2 小时前 |
| `EndTime` | number | 否 | 结束时间（Unix 秒），默认当前时间 |
| `Region` | string | 否 | 区域，默认 `ap-guangzhou` |
| `log_filters` | array | 否 | 过滤条件数组（必须是 JSON 对象，不能是字符串） |
| `RespFields` | array | 否 | 指定返回字段列表；不指定返回全部，建议尽量指定以避免回包过大 |
| `LastRowId` | number | 否 | 翻页用：上次返回最后一条的 `row_id` |
| `LastTime` | number | 否 | 翻页用：上次返回最后一条的 `ts` |

`log_filters` 操作符：`eq`、`neq`、`like`、`nlike`、`in`。**地区过滤使用 `city` / `country`（不是 `region`）**。
`level` 字段仅支持 `eq` / `neq` / `in`。大部分信息（如 url）在 `msg` 字段中，查 url 相关内容请用
`msg` 的 `like` 过滤。

### 1.4 两个分支的差异（易错点）

| 维度 | `metric` 分支 | `log` 分支 |
|------|--------------|-----------|
| 过滤参数名 | `metric_filters` | `log_filters` |
| 操作符 | `=` / `!=` / `like` / `not like` | `eq` / `neq` / `like` / `nlike` / `in` |
| 地区字段 | `region` | `city` / `country` |

**传错操作符或字段名会被静默忽略，导致过滤失效但不报错**。请严格按分支使用各自的枚举。

---

## 2. 按需工具 / On-demand tools

以下工具**默认不出现**在工具列表中。需要时可直接按名调用，或用 `lookup_tool` 检索发现。

| 工具名（按名直调） | 触发场景 | 主要参数 |
|-------------------|---------|---------|
| `rum_QueryApmLinkId` | 日志中 `trace` 字段非空，需要打通 RUM 与后端 APM 链路时 | `ProjectId`（必填）、`Region`（可选，默认 `ap-guangzhou`） |
| `rum_QueryResourceByPage` | 需要按页面来源看静态资源加载指标时 | `ProjectId`（必填）、`From`（必填）、`StartTime`、`EndTime` |

> `rum_QueryResourceByPage` 的过滤条件仅支持 `from` 字段；如需按其它维度过滤或 GroupBy，
> 请改用 `rum_query` 的 `target=metric`。

---

## 3. 用 `lookup_tool` 发现长尾工具 / Discovering tools

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「RUM 与 APM 链路打通」「某页面的静态资源加载」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

返回候选工具名、**真实参数 schema** 与调用方式；拿到后按名直接调用即可。

---

## 4. 语法参考 / Syntax reference

默认工具集中有一个**展示型语法工具** `syntax_reference`（无需调用，说明在工具列表阶段即可阅读）。
与本域相关的主题：

| `topic` | 主题 |
|---------|------|
| `rum_operators` | `metric` 与 `log` 分支的操作符与地区字段分歧 |
| `time_formats` | RUM 使用 Unix 秒时间戳，与其它域的换算关系 |

---

## 5. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能查询密钥（或授权）所属账号下的 RUM 应用与日志**。
建议使用仅含监控只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接监控只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误；③ 重新生成密钥并更新连接配置；④ OAuth 模式下重新授权。
- **429 请求过于频繁**：降低调用频率、缩小查询范围后**稍后重试**。
- **返回空结果**：先确认 `ProjectId` 是否正确、时间窗口是否覆盖目标时段，再检查过滤条件
  是否用了错误分支的操作符（最常见的静默失败来源）。

## 6. 数据范围与使用边界 / Data scope

- 仅能查询凭证所属账号下的 RUM 应用与日志。
- 所有工具均为只读，不会修改应用配置。
- 输出中不要展示密钥、授权码、用户隐私字段等敏感信息。
- 日志查询请优先指定 `RespFields`，避免回包过大。
