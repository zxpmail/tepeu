---
name: tcop-log-search
description: >
  Tencent Cloud CLS log search skill. Use it when retrieving or aggregating logs, counting
  errors, analysing trends, locating failures by Trace ID / IP, or when a structured
  search statement fails and must be generated from natural language.
  触发词：日志、CLS、日志检索、日志查询、查日志、错误日志、ERROR、WARN、异常堆栈、堆栈、
  Trace ID、日志量、错误率、日志分析、日志趋势、SQL 检索、自然语言转检索语句。
  何时不触发：指标类查询（Prometheus/APM 指标）、告警历史、RUM 前端监控、资源盘点不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# 日志检索 / Log Search

## 概述 / Overview

本 Skill 覆盖腾讯云 CLS（日志服务）域的 MCP 工具：以结构化检索语句查询日志，并在语句
不可行时改用自然语言生成检索语句。

This skill covers the CLS tool surface: structured log retrieval, plus a natural-language
fallback that generates the search statement for you.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `cls_search_log` | 日志检索：结构化查询 / 自然语言生成检索语句 | `query_mode` |

---

## 1. `cls_search_log` — 日志检索

| `query_mode` 取值 | 语义 | 使用的参数 |
|------------------|------|-----------|
| `query` | 结构化检索日志 | `Query` |
| `natural` | 用自然语言生成检索语句 | `Question` |

### 1.1 `query_mode=query` — 结构化检索

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query_mode` | string | 是 | 固定 `query` |
| `TopicId` | string | 是 | 日志主题 ID（UUID 形态） |
| `Query` | string | 是 | 检索语句；取全量样本可用 `*`，统计用 `* \| SELECT ... FROM log` |
| `From` | number | 否 | 检索起始时间，Unix 毫秒时间戳（示例 1791484800000） |
| `To` | number | 否 | 检索结束时间，Unix 毫秒时间戳（示例 1791484800000） |
| `Limit` | number | 否 | 返回条数上限（字段预检时可用 1） |
| `SamplingRate` | number | 否 | 采样率，如 `0.1` 表示 10% 采样 |

> 可选参数（时间范围、条数上限、采样率）的具体取值形态以工具列表返回的实际 schema 为准。

### 按需工具（不在默认工具面，经 lookup_tool 检索后按名可调）

| 工具 | 用途 |
|------|------|
| `cls_ConvertTimestampToTimeString` | Unix 时间戳 → 可读时间字符串 |
| `cls_ConvertTimeStringToTimestamp` | 可读时间字符串 → Unix 时间戳 |
| `cls_GetRegionCodeByName` | 地区名 → 地区码（Region 参数取值） |

跨域组合查询需先统一时间制式时使用（`syntax_reference` 的 `time_formats` 主题有制式说明）；从「工具面上唯一的换算能力」，检索方式见 `lookup_tool`。

### 1.2 `query_mode=natural` — 自然语言生成检索语句

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query_mode` | string | 是 | 固定 `natural` |
| `TopicId` | string | 是 | 日志主题 ID |
| `Question` | string | 是 | 自然语言问题，如「统计最近 1 小时各状态码的请求量」 |

### 1.3 何时使用 `natural` 分支

自然语言分支是**服务端降级路径**，遇到以下任一情况应立即切换，而不是反复手改检索语句：

- 结构化检索语句报**语法错误**（第 1 次失败后即应切换，不要连续手修重试）。
- 目标字段**未建索引**（报错形如 `field: <name>, can not search on this field, since it is not indexed`）。
  此时应改走全文检索或改用自然语言分支，并在结论中声明口径偏差。

---

## 2. 使用要点 / Usage notes

- **Topic 定位**：日志主题 ID 通常来自资源地图中的 CLS 资源；主题 ID 是 UUID 形态，
  可直接查询，不需要再用名称去"匹配 ID"。
- **字段探索**：结构化 JSON 字段、采集端标签字段、K8s 标签字段分属不同层级，某一层查不到时
  继续在下一层确认，不要据此判定字段不存在。
- **去重统计**：使用 `approx_distinct()` 做去重计数；时间窗 ≥ 24 小时的分位 / 去重必须带采样率。
- **趋势分析**：按小时 / 天维度的趋势必须先执行时间分桶聚合查询，不得用零散样本或字面量外推。
- **超时处理**：先缩窗、再采样、再分段汇总；连续超时后返回当前可得结果并如实说明。
- **空结果**：首次空结果不要直接回复"没有数据"——回看参数、缩小时间窗、去掉部分过滤条件后重试。

---

## 3. 按需工具 / On-demand tools

| 工具名（按名直调） | 触发场景 | 主要参数 |
|-------------------|---------|---------|
| `cls_GetTopicInfoByName` | 仅在**资源地图不可用**且用户只给了 **Topic 名称**（无 ID）时使用；它是名称搜索工具，用 ID 作为搜索词必然零命中 | `SearchText`（必填，逐字使用用户原始输入） |

该工具**默认不出现**在工具列表中，需要时可直接按名调用，或用 `lookup_tool` 检索发现。

> 重要：`cls_GetTopicInfoByName` 的零结果**仅代表名称未命中**，不代表主题不存在。
> 在真正执行过检索之前，不要据此推断主题已删除或不可用。

---

## 4. 用 `lookup_tool` 发现长尾工具 / Discovering tools

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「按名称找日志主题」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

返回候选工具名、**真实参数 schema** 与调用方式；拿到后按名直接调用即可。

---

## 5. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能查询密钥（或授权）所属账号下的日志主题**。
建议使用仅含日志只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接日志服务只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误；③ 重新生成密钥并更新连接配置；④ OAuth 模式下重新授权。
- **429 请求过于频繁**：降低调用频率、缩小时间窗口或提高采样率后**稍后重试**。

## 6. 数据范围与使用边界 / Data scope

- 仅能查询凭证所属账号、已授权地域内的日志主题。
- 所有工具均为只读，不会写入或删除日志。
- 输出中不要展示密钥、授权码等敏感信息；日志中可能含用户数据，如实引用但不要额外扩散。
