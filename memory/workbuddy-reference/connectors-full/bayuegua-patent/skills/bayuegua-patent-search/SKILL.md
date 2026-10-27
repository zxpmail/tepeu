---
name: bayuegua-patent-search
display_name: 八月瓜知识产权智能 MCP（专利）
display_name_en: Bayuegua IP Intelligence MCP (Patent)
description: 中国及全球专利的检索与详情查询。覆盖语义检索、结构化指令检索、以图搜专利，以及单件专利的著录项、权利要求、说明书、附图、同族、引用/被引用、相关文献、技术问题与功效分析。当用户要查专利、做技术溯源、侵权比对、技术拆解或专利查新时使用。
description_zh: 中国及全球专利检索与详情查询：语义检索、结构化指令检索、以图搜专利，以及单件专利的著录项、权利要求、说明书、附图、同族、引用/被引用、相关文献与技术问题功效分析。
description_en: Search and inspect Chinese and global patents: semantic search, structured instruction search, image-to-patent search, plus bibliography, claims, description, figures, patent family, citations, related literature, and technical problem/effect analysis.
category: 数据研究
version: 1.1.0
author: 八月瓜
---

# 八月瓜知识产权智能 MCP（专利）

提供中国及全球专利的检索与详情查询能力。**所有查询均为只读操作**，不会修改任何数据。

## 触发场景

- 用户说"查专利 / 找专利 / 专利检索 / 查新 / 专利布局"
- 用户提供公开号（如 CN106816668A）问"这件专利讲了什么、谁申请的、还在不在、保护范围是什么"
- 用户要做技术问题/功效分析（"这件专利解决了什么痛点"）
- 用户要查同族（"这件在海外有没有"）、引证脉络（"谁引用了它"）
- 用户有一张图（产品图、结构图、化合物结构式）想找相近专利
- 用户要做技术拆解、侵权比对、技术溯源

## Available Tools（共 11 个）

### 一、找专利：三种检索入口

#### patent_semantic_search
按技术问题或技术效果找语义相近专利（不需图片）。**首选入口**。
- `text`（必填）：技术描述，**建议全中文**，≤ 4000 字，**不支持全外文**
- `publication_date`（可选）：`[20230112 TO 20240815]` 格式
- `application_date`（可选）：同上
- `result_count`（可选）：返回条数，默认 500
- `minimum_similarity`（可选）：`100 > 阈值 > 0`
- `publication_type`（可选）：枚举，格式 `1,2,3`

#### patent_instruction_search
按结构化字段精确组合检索。当用户给出明确的申请人、IPC、公开号等条件时用。
- `text_content`（必填）：检索内容/指令，支持 `TI=`（标题）、`FULL=`（全文）、`IPC=`、`AP=`（申请人）、`AD=`（申请日）、`PN=`（公开号）等运算符
- `from`（必填）：起始偏移，从 0 开始
- `size`（必填）：返回条数，**1–10**
- 完整运算符手册：https://cosapi.bayuegua.com/InstructionTool.pdf

#### patent_image_search
**以图搜专利**：上传图片找结构或外观相近的专利。
- `type`（必填）：`0` = 外观专利，`1` = 发明与实用新型
- `image_url`（必填）：图像 URL
- `similarity`（可选）：`0 < 最小相似度 < 1`
- `title` / `full`（可选）：标题 / 全文关键词
- 分类过滤：`class_ipc`、`class_cpc`、`class_loc`（洛迦诺）、`class_fi`、`class_uc`、`class_fterm`、`class_ec`、`class_hvp`（战略新兴产业）等
- 主体过滤：`apall`（申请人）、`inventor`、`agency`、`agent`
- 地域与日期：`publication_country`（格式 `CN,EN`）、`publication_date`、`application_date`、`grant_publication_date`
- 运营状态：`simple_legal_status`（`1,2`）、`legal_status_code`、`citation_number_of_times`、`pledge_times`、`license_times`、`reassignment_times`
- `size`（可选）：1–100，默认 20

### 二、看懂一件专利

#### patent_bibliography
取著录项：申请人、发明人、申请日、公开日、IPC、法律状态。回答"是谁、什么时候、什么状态"。
- `pn`（必填）：公开公告号，如 `CN-210554091-U`

#### patent_tech_profile
取**技术问题 + 技术功效**结构化结果。快速回答"解决了什么痛点、达到什么效果"。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条

#### patent_claim
取权利要求（保护范围）。做侵权比对、保护范围分析时用。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条
- `pn_related`（可选）：`1` = 无权利要求时用简单同族替代，`0` = 否（默认）

#### patent_description
取说明书全文（技术实现细节）。做技术拆解时用。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条
- `pn_related`（可选）：同上

#### patent_figures
**浏览某件专利**说明书中的全部附图。
- `pn`（必填）：**单次仅支持 1 条**

### 三、扩展脉络

#### patent_family
查同族（同一发明在不同国家/地区的申请集合）。返回简单同族 + 扩展同族合并结果。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条

#### patent_citation
查引用与被引用关系。做技术脉络分析时用。返回引用 + 被引用合并结果。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条

#### patent_pertinent_literature
查与某件专利相关的其他文献。已有一件目标专利、想扩展找更多相似专利时用。
- `pn`（必填）：多个用逗号分隔，单次 ≤ 100 条

## 工作流建议

**找专利**：
1. 用户给自然语言技术问题 → `patent_semantic_search`
2. 用户给明确字段条件（申请人 + IPC + 时间）→ `patent_instruction_search`
3. 用户有图片 → `patent_image_search`

**看懂一件专利**（拿到公开号后按需组合）：
1. `patent_bibliography` 取概览（谁、何时、什么状态）
2. `patent_tech_profile` 取技术问题与功效（快速判断相关性）
3. `patent_claim` 取保护范围
4. `patent_description` 取实现细节
5. `patent_figures` 看附图（单次 1 条）

**扩展脉络**：
1. `patent_family` 看海外布局
2. `patent_citation` 看技术脉络
3. `patent_pertinent_literature` 找更多相似专利

## 重要：两个易混工具的区别

| 工具 | 用途 | 输入 |
|---|---|---|
| `patent_figures` | **浏览某件专利**的附图 | `pn`，单次 1 条 |
| `patent_image_search` | **上传图片去搜**其他相近专利 | `image_url` + `type` |

二者不可互替。用户说"看看这件专利的图"→ `patent_figures`；用户说"用这张图找相似专利"→ `patent_image_search`。

## 已知限制与调用要点

- **公开号参数是 `pn`**，不是 `publication_number`。格式参考 `CN-210554091-U` 或 `CN106816668A`。
- **语义检索不支持全外文**，`text` 建议全中文。
- **长查询词易超时**：`text` 超过 60 字会显著增加接口超时风险，**建议拆短到 20 字以内**（实测已复现）。
- **`patent_instruction_search` 的 `size` 上限是 10**，不能一次取太多。
- **`patent_figures` 单次仅 1 条**，批量看附图需循环调用。
- **多 `pn` 查询单次上限 100 条**。
- **国别过滤**：`patent_semantic_search` 无国别参数；需要限定国别时用 `patent_image_search` 的 `publication_country`（格式 `CN,EN`），或用 `patent_instruction_search` 的字段运算符。
- **部分接口有兜底**：`patent_family` / `patent_citation` / `patent_tech_profile` 若子接口失败，对应部分置空并附 `error`，**不整体报错**——解析结果时要注意判空。

## 与其它专利工具的分工

- 与 **patsnap-search** 互补：patsnap 长于全球扩展同族、英文检索与文献覆盖；本 Connector 长于中文语义深度、技术问题/功效结构化提取、以图搜专利、附图浏览。
- 交叉验证：拿到本 Connector 给的公开号后，可用 patsnap 取同族或英文摘要做交叉核对。
