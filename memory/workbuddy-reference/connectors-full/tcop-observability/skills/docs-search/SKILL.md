---
name: tcop-docs-search
description: >
  Tencent Cloud observability knowledge search skill. Use it when looking up product docs by
  alarm content, resolving an alarm strategy type, mapping a cloud product name to its ID,
  or finding API / alarm metric metadata and their recommended thresholds.
  触发词：知识库、文档检索、产品文档、告警文档、告警策略类型、策略类型、云产品 ID、产品标识、
  API 指标、指标元数据、告警指标、指标含义、推荐阈值、指标单位、命名空间。
  何时不触发：实时指标查询、告警历史检索、日志检索、资源盘点等需要真实数据的场景不走本 Skill。
version: "1.0.0"
author: "Tencent Cloud Observability Platform"
---

# 可观测知识检索 / Observability Knowledge Search

## 概述 / Overview

本 Skill 覆盖腾讯云可观测知识库的 MCP 工具：按告警内容检索产品文档、解析告警策略类型与
云产品标识、检索 API 指标与告警指标元数据。

This skill covers the observability knowledge base: product-document lookup by alarm content,
alarm strategy type and cloud product resolution, and API / alarm metric metadata retrieval.

## 工具清单 / Tool surface

| 工具 | 用途 | 判别参数 |
|------|------|---------|
| `catalog_search` | 知识库统一检索：文档 / 策略类型 / 云产品 / API 指标 / 告警指标 | `entity` |

---

## 1. `catalog_search` — 知识库检索

| `entity` 取值 | 检索目标 | 必需参数 |
|--------------|---------|---------|
| `docs` | 按告警内容全文检索产品文档 | `alarm_content` |
| `strategy_type` | 检索告警策略类型 | `query_text` |
| `cloud_product` | 检索云产品标识 | `query_text` |
| `api_metric` | 检索 API 指标元数据 | `search_mode` |
| `alarm_metric` | 检索告警指标元数据 | `strategy_type` + `search_mode` |

公共参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `entity` | string | 是 | 上述五个取值之一 |

### 1.1 `entity=docs`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `alarm_content` | string | 是 | 告警内容，用于全文检索匹配文档 |
| `alarm_strategy_type` | string | 否 | 告警策略类型，传入后精准匹配对应云产品文档 |

### 1.2 `entity=strategy_type` / `entity=cloud_product`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `query_text` | string | 是 | 检索关键词 |

- `strategy_type` 返回：告警策略类型标识、中文名、所属云产品 ID 与中文名。
- `cloud_product` 返回：云产品 ID、中文名与描述。

### 1.3 `entity=api_metric`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `search_mode` | string | 是 | `search_text` 或 `common_metrics` |
| `search_text` | string | `search_mode=search_text` 时必填 | 检索文本 |
| `api_namespace` | string | `search_mode=common_metrics` 时必填 | 云产品命名空间，精确匹配 |
| `cloud_product_id` | string | 否 | 云产品唯一标识，精确匹配 |

### 1.4 `entity=alarm_metric`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `strategy_type` | string | 是 | 告警策略类型，须为精确值（不确定时先用 `entity=strategy_type` 检索） |
| `search_mode` | string | 是 | `search_text` 或 `precise` |
| `search_text` | string | `search_mode=search_text` 时必填 | 检索文本 |
| `metric_identifiers` | array | `search_mode=precise` 时必填 | 指标标识列表，1~20 个，支持底层告警指标名 / API 指标名 / 指标 ID |
| `include_threshold` | boolean | 否 | 是否返回推荐阈值，默认 `false` |

### 1.5 `search_mode` 取值说明（合并后枚举）

| 取值 | 适用范围 | 说明 |
|------|---------|------|
| `search_text` | 所有 entity | 按文本检索（配合 `search_text` 参数） |
| `common_metrics` | 仅 `entity=api_metric` | 取命名空间下最常用的 API 指标（需 `api_namespace`） |
| `precise` | 仅 `entity=alarm_metric` | 按指标标识精准匹配（需 `metric_identifiers`，1~20 个） |

> 用错取值组合会返回错误或空结果，请先确认 `entity` 再选择 `search_mode`。

> `include_threshold=true` 返回的推荐阈值仅为最基础的参考值，实际配置告警时应结合业务场景调整。

---

## 2. 典型检索路径 / Typical flows

| 目标 | 路径 |
|------|------|
| 从告警内容找产品文档 | `entity=docs` + `alarm_content` |
| 确认策略类型的准确标识 | `entity=strategy_type` + `query_text` |
| 从中文产品名找产品 ID | `entity=cloud_product` + `query_text` |
| 查某产品常用 API 指标 | `entity=api_metric` + `search_mode=common_metrics` + `api_namespace` |
| 查某指标的完整元数据 | `entity=alarm_metric` + `search_mode=precise` + `metric_identifiers` |

---

## 3. 按需工具 / On-demand tools

本域全部工具均在默认工具列表中，无按需层工具。

若需要本域以外的长尾能力，可用 `lookup_tool` 检索：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `task` | string | 是 | 自然语言任务描述，如「查告警指标的含义与单位」 |
| `top_k` | integer | 否 | 返回候选数，默认 5，最大 20 |

`lookup_tool` 返回候选工具名、**真实参数 schema** 与调用方式；拿到后按名直接调用即可。

---

## 4. 凭证获取与排障 / Credentials & Troubleshooting

数据可见范围严格由凭证决定：**只能检索密钥（或授权）所属账号可访问的知识库内容**。
建议使用仅含监控只读权限的子账号密钥。

- **获取密钥**：腾讯云控制台 → 访问管理（CAM）→ API 密钥管理 → 新建密钥，得到
  SecretId / SecretKey，并为该子账号挂接监控只读策略。密钥仅保存在本机，不会上传。
- **使用授权登录（OAuth）时**：无需手填密钥；授权失效时在本应用的连接设置中**重新授权**即可。
- **401 鉴权失败**：① 去 CAM 控制台检查密钥是否**被禁用或删除**（最常见原因）；
  ② 检查是否复制错误；③ 重新生成密钥并更新连接配置；④ OAuth 模式下重新授权。
- **429 请求过于频繁**：降低调用频率后**稍后重试**。
- **空结果**：本域是**知识检索**而非实时数据查询，返回空通常说明关键词不匹配或策略类型
  取值不精确，请改用更通用的关键词，或先检索策略类型再检索指标。

## 5. 数据范围与使用边界 / Data scope

- 仅能检索凭证所属账号可访问的知识库内容。
- 所有工具均为只读，不会创建或修改任何配置。
- 输出中不要展示密钥、授权码等敏感信息。
- 本域返回的是**元数据与文档**，不含实时监控数值；需要真实数据请改用指标 / 告警 / 日志类工具。
