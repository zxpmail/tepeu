---
name: brandct
display_name: 马上赢品牌CT
display_name_en: Mashangying BrandCT
description: Query Mashangying BrandCT projects, explores, fields, metrics and dashboards with natural language.
description_zh: 指导 AI 使用马上赢品牌CT连接器查询项目、探索表、字段、指标与看板数据。
description_en: Guide the AI to query Mashangying BrandCT projects, explores, fields, metrics and dashboards.
category: data
version: 1.0.0
author: 马上赢市场调查
---

# 马上赢品牌CT

## 触发场景

当用户需要查询马上赢品牌CT中的业务数据时使用本技能。典型说法：

- 「查询饮料的所有四级类目」
- 「帮我找一下销售相关的指标」
- 「跑一下上个月各品牌的成交额」
- 「这个看板用了哪些字段」

## 认证前置条件

本连接器使用**用户自填 Token 模式**。用户需在连接时填写自己的马上赢品牌CT个人访问令牌（PAT）。

令牌获取地址：**`https://x.brandct.com/generalSettings/personalAccessTokens`**
（登录马上赢品牌CT后打开该页面 → 生成令牌 → 复制完整令牌串）

若调用返回 401 / 403，说明令牌无效或已过期，应提示用户到上述地址重新生成并更新令牌。

## 核心工作流

### 第一步：确定项目

几乎所有的查询工具都需要 `projectUuid`。**每次调用都要显式传入 `projectUuid`**，否则会报「缺少 projectUuid」。

- 用户未指定项目时，先调用 `list_projects` 列出可选项目，让用户确认。
- 常用项目名：`品牌CT`、`马上赢情报`（开发库后缀带「（开发）」）。

### 第二步：找探索表（explore）

- `find_explores`：按关键词在数据目录里搜探索表，例如 `{ "searchQuery": "饮料" }`。
- `list_explores`：列出项目下全部探索表，带 `groupLabel` 分组，适合盘点口径。

注意：`find_explores` 走目录检索，关键词命中率有限；搜不到时改用 `list_explores` 翻列表。

### 第三步：找字段

- `find_fields`：在指定 explore 内按标签搜字段，返回 `fieldId`、`label`、`type`。

```json
{
  "table": "dim_translation_category_view",
  "fieldSearchQueries": [{ "label": "四级" }, { "label": "类目" }]
}
```

优先使用返回的 `fieldId`（形如 `dim_translation_category_view_cls_4`），不要凭字段名猜。

### 第四步：取数

- `run_metric_query`：扁平参数，适合 1～2 个维度 + 1 个指标 + 简单筛选。
  `dimensions` 只传维度、不传指标时，等价于对该维度做 DISTINCT 分组。
- `run_semantic_metric_query`：当需要复制看板里的整段 Metric Query，或多条件 AND 筛选时使用，参数是 JSON 字符串形式的 `metricQuery`。

筛选条件的写法：

```json
{
  "filters": {
    "dimensions": {
      "id": "f_root",
      "and": [
        {
          "id": "f_1",
          "target": { "fieldId": "dim_translation_category_view_cls_1" },
          "operator": "equals",
          "values": ["饮料和酒"]
        }
      ]
    }
  }
}
```

### 辅助工具

| 工具 | 用途 |
|---|---|
| `search_field_values` | 枚举某维度的实际取值，用于确认筛选值的准确写法 |
| `run_saved_chart` / `get_saved_chart` | 执行或查看已保存的图表 |
| `run_dashboard_tiles` | 批量执行看板上的所有图表 |
| `get_mcp_docs` | 获取该 MCP 服务自身的用法说明 |

## 典型示例

**示例一：查枚举型维度**

用户说：「查询饮料的所有四级类目」

1. 先确认一级类目的准确取值 —— 用 `search_field_values` 查 `dim_translation_category_view_cls_1`，关键词「饮」。
2. 结果发现一级类目里**没有单独的「饮料」**，实际叫「饮料和酒」；「饮料」是它下面的二级类目。
3. 用 `run_metric_query` 按 `cls_1 = 饮料和酒` AND `cls_2 = 饮料` 筛选，取 `cls_3`、`cls_4` 两个维度。
4. 结果按三级类目分组呈现。

**这个例子的教训：用户口语里的类目名和数仓里的名称经常对不上，先验证取值再筛选，不要直接拿用户原话当筛选值。**

**示例二：指标取数**

用户说：「上个月各品牌成交额」

1. `find_explores` 搜索销售类探索表，或 `list_explores` 翻列表定位。
2. `find_fields` 定位品牌维度和成交额指标。
3. `run_metric_query` 传 `dimensions: [品牌]` + `metrics: [成交额]`，加时间筛选。

## 错误处理

| 现象 | 原因 | 处理 |
|---|---|---|
| 缺少 projectUuid | 未传项目参数 | 补传 `projectUuid`，或先 `list_projects` 让用户选定 |
| 返回空数据 | 筛选值写错，或该项目无此口径 | 用 `search_field_values` 核对实际取值 |
| (no tabular columns) | 查询无结果集 | 去掉筛选条件试跑一次，确认字段本身有数据 |
| 401 / 403 | PAT 无效或过期 | 提示用户到 `https://x.brandct.com/generalSettings/personalAccessTokens` 重新生成并更新令牌 |
| 超时 | 查询过重 | 缩小时间范围或减少维度后重试 |

## 使用注意

- 类目 / 品牌等维度名称以数仓实际取值为准，不要直接使用用户口语表述。
- 单次查询维度不超过 3 个、结果行数控制在 1000 行以内，避免超时。
- 涉及金额、销量等指标时，明确口径（时间范围、渠道、门店类型），不要默认全量。
