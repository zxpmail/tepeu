---
name: tcop-resource-map
description: >
  Tencent Cloud resource discovery skill. Use it when enumerating resources from a resource
  map, searching cloud product instances, or listing instances that can be bound to an alarm
  policy — usually the first step before any metric, alarm or log investigation.
  触发词：资源、资源地图、资源盘点、有哪些实例、实例列表、云产品实例、CVM、CDB、云服务器、
  数据库实例、可绑定告警的实例、资源概览、按关键词搜实例、资源归属。
  何时不触发：指标查询、告警历史、日志检索、链路分析等需要真实数据的场景不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# 资源发现 / Resource Discovery

## 概述 / Overview

本 Skill 覆盖腾讯云可观测资源发现工具：从资源地图列举或语义搜索实例、查看地图概览、
按云产品列举或检索实例、列举可绑定告警策略的实例。

This skill covers resource discovery: enumerating or semantically searching resource-map
instances, map summary, cloud-product instance listing / search, and alarm-bindable instances.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `resource_discover` | 资源发现统一入口 | `source` + `mode` |

---

## 1. `resource_discover` — 资源发现

| `source` | `mode` | 路由到的语义 | 说明 |
|----------|--------|-------------|------|
| `resource_map` | `list` | 分页列举资源地图实例 | 最常用的资源盘点入口 |
| `resource_map` | `search` | 语义搜索资源地图实例 | 按关键词找实例 |
| `resource_map` | `summary` | 资源地图概览统计 | 按监控类型 / 云产品聚合的数量总览 |
| `cloud_product` | `list` | 按云产品分页列举实例 | 需要产品 ID 与地域 |
| `cloud_product` | `search` | 按关键词模糊检索实例 | 全产品 / 全地域范围 |
| `alarm_bindable` | `list` / `search` | 列举可绑定告警策略的实例 | 两个取值等价，均返回同类结果 |

### 1.1 `source=resource_map`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | string | 是 | 固定 `resource_map` |
| `mode` | string | 是 | `list` / `search` / `summary` |
| `resource_map_id` | string | 是 | 资源地图 ID |
| `query_text` | string | `mode=search` 时必填 | 搜索关键词 |
| `cloud_product_id` | string | 否（仅 `mode=search`） | 云产品 ID 精确过滤，如 `cvm`、`cdb` |
| `page` | number | 否（仅 `mode=list`） | 页码，从 1 开始，默认 1 |
| `page_size` | number | 否（仅 `mode=list`） | 每页数量，默认 20，最大 50 |

`mode=summary` 仅返回地图概览（名称、实例总数、按监控类型与云产品的聚合），不接受分页参数。

### 1.2 `source=cloud_product`

`mode=list`：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | string | 是 | 固定 `cloud_product` |
| `mode` | string | 是 | 固定 `list` |
| `cloud_product` | string | 是 | 云产品 ID，也可输入中文名称做模糊匹配 |
| `region` | string | 是 | 地域，如 `ap-guangzhou`、`ap-beijing` |
| `page_number` | number | 否 | 页码，从 1 开始，默认 1 |
| `page_size` | number | 否 | 每页数量，默认 20，最大 50 |
| `need_api_metric_info` | boolean | 否 | 是否返回实例的常用 API 指标及其维度，默认 `false` |

`mode=search`：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | string | 是 | 固定 `cloud_product` |
| `mode` | string | 是 | 固定 `search` |
| `query_text` | string | 是 | 模糊检索关键词，如实例 ID、实例名称，不能为空 |
| `cloud_product` | string | 否 | 云产品 ID；不填则检索所有产品 |
| `region` | string | 否 | 地域；不填则全地域搜索 |
| `need_api_metric_info` | boolean | 否 | 同上，默认 `false` |

### 1.3 `source=alarm_bindable`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `source` | string | 是 | 固定 `alarm_bindable` |
| `mode` | string | 是 | `list` 或 `search`（两者等价） |
| `alarm_strategy_type` | string | 是 | 告警策略类型，如 `vpc_eip_packet_loss_monitor`、`COS`、`model_svc` |
| `region` | string | 否 | 地域，指定时仅返回该地域实例 |
| `page_number` | number | 否 | 页码，从 1 开始，默认 1 |
| `page_size` | number | 否 | 每页数量，默认 20，最大 50；不传或 0 表示全量 |

返回的分页信息中 `total_count` / `total_page` 为**近似值**（按实体粒度去重前统计），
判断是否还有下一页请以 `is_end` 字段为准。

---

## 2. 使用建议 / Usage notes

- 本域通常是排查的第一步：先拿到实例 ID / 维度，再用指标、告警、日志类工具深入。
- 资源地图可用时优先走 `source=resource_map`；地图不可用时退回 `source=cloud_product`。
- 返回实例的 `service_metadata` / 维度信息可直接作为其它域工具的入参（如日志主题 ID、
  告警对象维度），无需再次解析名称。
- 结果过多时先用 `mode=search` 或 `mode=summary` 收敛范围，再列举明细。

---

## 3. 按需工具 / On-demand tools

本域全部工具均在默认工具列表中，无按需层工具。

若需要本域以外的长尾能力，可用 `lookup_tool` 检索：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「找可绑定告警的 EIP 实例」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

`lookup_tool` 返回候选工具名、**真实参数 schema** 与调用方式；拿到后按名直接调用即可。

---

## 4. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能发现密钥（或授权）所属账号下的资源**。
建议使用仅含监控只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接监控只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误；③ 重新生成密钥并更新连接配置；④ OAuth 模式下重新授权。
- **429 请求过于频繁**：降低调用频率、缩小分页范围后**稍后重试**。
- **空结果排查**：确认资源地图 ID 是否正确、云产品 ID 与地域是否匹配、关键词是否过于具体。

## 5. 数据范围与使用边界 / Data scope

- 仅能发现凭证所属账号、已授权地域内的资源；跨账号资源不可见。
- 所有工具均为只读，不会创建、修改或删除资源。
- 输出中不要展示密钥、授权码等敏感信息。
- 资源清单来自平台侧快照，可能与控制台实时状态存在同步延迟；发现异常时以实际查询为准。
