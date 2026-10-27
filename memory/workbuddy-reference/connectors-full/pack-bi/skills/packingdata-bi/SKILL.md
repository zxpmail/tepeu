---
name: packingdata-bi
description: 通过派可BI 智能问数 MCP 查询指标与数据。当用户问到「指标」「维度和度量」「按某维度看某度量的数据」「某指标有哪些字段」等派可BI 取数需求时使用。
---

# 派可BI 智能问数

派可BI 通过 MCP 暴露 3 个工具，**必须按 `searchMetrics` → `getMetricDetail` → `executeQuery` 的顺序调用**。

## 1. searchMetrics

按关键词搜索当前密钥有权限的指标，返回 `id` 和 `name`。

- `keyword`：搜索关键词。**用户没指明关键词时传空字符串**，返回全部有权限指标。
- 返回空数组 `[]` 表示该密钥对应的用户**没有任何有权限的指标**（不是密钥失效；密钥失效会返回 `-32002`）。

## 2. getMetricDetail

用上一步拿到的 `metricId` 获取完整字段定义。

- `metricId`：指标的 32 位 UUID。
- 返回 `dimensions`（每个维度含 `name` 和可选 `attributes`）、`measures`（度量名称列表）、`measureMeta`。
- **确认指标后必须调此工具**，拿到准确的维度名、属性名和度量名，不要凭猜测拼字段。

## 3. executeQuery

执行结构化查询并返回数据。

- `metricId`：指标 ID。
- `dimensions`：**JSON 字符串**，格式 `[{"name":"维度名称","attributes":["属性名1"]}]`。不按维度拆分时传 `[]`。
- `measures`：**JSON 字符串**，格式 `["度量名","度量名2"]`。
- `filters`：**JSON 字符串**，可为空数组。格式 `[{"field":"维度属性名","operator":"=","value":"2025"}]`；
  `operator` 支持 `=`、`>`、`<`、`>=`、`<=`、`like`、`in`（`in` 时 `value` 为逗号分隔字符串）。
- 返回 `rowsMap`（数据行）、`columnsMap`（列名映射）、`total`（**为 0 表示查询结果为空**）、`columnUnitType`（度量单位 map，**有单位时必须使用这里的单位**）、`sql`。

## 关键约定

- **字段名必须来自 `getMetricDetail`**：维度用 `name`，属性用 `attributes` 里的原始名（可能带前缀，如 `W0.年月日_年`、`Q公司a_营业范围`）。
- **维度与度量不要混用**：`measures` 里只能放度量名。
- **单位**：结果列在 `columnUnitType` 中有单位时，展示数据必须带上该单位，不要自行推断。
- **权限**：指标可见范围由密钥绑定的用户决定。同一个 MCP 地址换密钥，返回的指标集合会完全不同。
- **常见错误**：JSON 参数必须是字符串化后的 JSON，而不是对象；传对象会被判为参数非法。

## 典型流程

1. 用户问「有哪些指标」→ `searchMetrics("")`，列出名称。
2. 用户问「按 X 看 Y」→ `searchMetrics(关键词)` 找到指标 → `getMetricDetail` 确认字段 →
   `executeQuery` 传入对应维度和度量。
3. 结果为空时先检查：过滤条件是否过严、维度属性名是否来自 `getMetricDetail`。
