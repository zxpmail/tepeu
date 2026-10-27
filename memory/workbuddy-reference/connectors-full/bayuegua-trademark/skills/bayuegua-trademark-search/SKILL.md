---
name: bayuegua-trademark-search
display_name: 八月瓜知识产权智能 MCP（商标）
display_name_en: Bayuegua IP Intelligence MCP (Trademark)
description: 中国大陆、国际（马德里/WIPO）及中国港澳台商标的检索与详情查询。覆盖条件组合检索、以图样搜商标，以及单件商标的详情、评审文书、公告与办理流程。当用户要查商标、做近似排查、查注册状态或跟踪办理流程时使用。
description_zh: 中国大陆、国际及中国港澳台商标检索与详情查询：条件组合检索、以图样搜商标，以及单件商标的详情、评审文书、公告与办理流程。
description_en: Search and inspect Chinese mainland, international and Hong Kong / Macao / Taiwan trademarks: combined-condition search, image-based search, plus detail records, review decisions, announcements and prosecution process.
category: 数据研究
version: 1.1.0
author: 八月瓜
---

# 八月瓜知识产权智能 MCP（商标）

提供中国大陆、国际（马德里/WIPO）及中国港澳台商标的检索与详情查询能力。**所有查询均为只读操作**，不会修改任何数据。

## 触发场景

- 用户说"查商标 / 商标注册了吗 / 有没有被注册 / 近似排查 / 查近似"
- 用户给商标名称或申请/注册号，问"是谁的、什么状态、哪个类别"
- 用户有一张 logo / 图样，想找相同或近似的在先商标
- 用户问"这件商标走到哪一步了"（办理流程节点）
- 用户要查评审文书（无效宣告、异议、撤销的裁定）
- 用户要查商标公告（初审公告、注册公告）
- 用户要按申请人/代理机构/类别/年份做批量筛查

## 最重要的前提：三个库互相独立

**中国大陆、国际、港澳台是三个独立数据库，字段不同，不能合并查询。**

| 想查哪里 | 条件检索 | 以图搜 | 详情 |
|---|---|---|---|
| 中国大陆 | `trademark_condition_search_cn` | `trademark_image_search_cn` | `trademark_detail_cn` |
| 国际（马德里/WIPO） | `trademark_condition_search_intl` | `trademark_image_search_intl` | `trademark_detail_intl` |
| 中国港澳台 | `trademark_condition_search_hmt` | `trademark_image_search_hmt` | `trademark_detail_hmt` |

用户没说明地域时，**默认查中国大陆**；若结果为空或用户明确说"海外/马德里/香港/台湾"，再换对应库。**不要跨库复用 `tid`**。

## Available Tools（共 12 个）

### 一、条件组合检索（3 个）

三个工具参数基本一致，仅**国际与港澳台**多一个 `country_code`。

#### trademark_condition_search_cn / _intl / _hmt
按名称、申请人、类别、状态、年份等条件组合检索。

- `search_word`（**必填**）：关键词。**支持商标名称模糊检索，也支持申请号/注册号精准查找**
- `trademark_cls`（可选）：商标类别（尼斯分类，如 `9`）
- `mark_category`（可选）：商标类型
- `applicant_patentee`（可选）：申请人
- `current_status`（可选）：商标状态
- `application_date`（可选）：申请年份，**年份区间格式 `2019,2020`**
- `agent_name`（可选）：代理机构
- `country_code`（可选，**仅 `_intl` / `_hmt` 支持**）：国别代码
- `start`（可选）：起始偏移，默认 0
- `size`（可选）：返回条数，**1–20，默认 20**

> 只有 `search_word` 是必填。用户可以只给商标名做模糊查，也可以叠加类别 + 申请人做精确筛查。

### 二、以图样搜商标（3 个）

#### trademark_image_search_cn / _intl / _hmt
上传图样，检索结构或外观相近的商标。做**近似排查**时的首选入口。

- `image_url`（**必填**）：图片 URL 或路径
- `start`（可选）：起始偏移，默认 0
- `size`（可选）：返回条数，**1–20，默认 20**

### 三、单件商标详情（3 个）

#### trademark_detail_cn / _intl / _hmt
取一件商标的详细信息：名称、申请人、状态、类别、日期等。回答"是谁的、什么状态、哪一类"。

- `tid`（**必填**）：国知局商标 ID 号码

> ⚠️ **类型陷阱**：`trademark_detail_cn` 的 `tid` 是 **string**；`trademark_detail_intl` 与 `trademark_detail_hmt` 的 `tid` 是 **integer**。传错类型会直接报参数校验错误。

### 四、仅中国大陆支持（3 个）

以下三个工具**只有国内商标可用，没有国际/港澳台版本**。对国际或港澳台商标调用会查不到数据。

#### trademark_review_document
查评审文书：无效宣告、异议、撤销等评审决定书与裁定。
- `tid`（**必填**，string）：国知局商标 ID 号码

#### trademark_announcement
查商标公告：注册公告、初审公告等。
- `tid`（**必填**，string）：国知局商标 ID 号码

#### trademark_process
查办理流程与状态节点：申请、受理、初审、公告、注册各步骤及对应时间。回答"这件商标走到哪一步了"。
- `tid`（**必填**，string）：国知局商标 ID 号码

## 工作流建议

**找商标**：
1. 用户给商标名称或注册号 → 先判断地域 → `trademark_condition_search_{cn|intl|hmt}`
2. 用户有 logo / 图样 → `trademark_image_search_{cn|intl|hmt}`（近似排查的核心手段）
3. 用户要按申请人 + 类别 + 年份批量筛 → 条件检索叠加多个可选字段

**看懂一件商标**（从检索结果拿到 `tid` 后按需组合）：
1. `trademark_detail_*` 取概览（谁的、什么状态、哪一类、什么时间）
2. `trademark_process` 看流程节点（仅国内）
3. `trademark_announcement` 看公告（仅国内）
4. `trademark_review_document` 看争议文书（仅国内，有无效/异议/撤销时才查）

**近似排查的标准动作**：
先用条件检索拿到候选（`search_word` + `trademark_cls`），再用图样搜索做交叉验证——文字相同不等于图样近似，两者要一起看。

## 已知限制与调用要点

- **三库独立，字段不同，不可合并查询，也不可跨库复用 `tid`。**
- **`tid` 类型不一致**：国内 = string，国际与港澳台 = integer。这是最容易踩的坑。
- **`search_word` 双重语义**：既做商标名称模糊匹配，也做申请号/注册号精准查找，传注册号时直接命中单件。
- **`size` 上限是 20**（所有检索类工具统一），不是 100，需要更多结果时用 `start` 翻页。
- **`application_date` 是年份区间**，格式 `2019,2020`，不是日期范围，也不支持具体月日。
- **`country_code` 只在国际和港澳台库存在**，国内库没有这个参数，传了会被拒（`additionalProperties: false`）。
- **评审文书 / 公告 / 流程三个工具仅支持中国大陆商标**，无国际与港澳台版本。
- **`image_url` 需为可访问的图片地址**，本地路径在云端场景下可能取不到，优先用公开 URL。

## 与专利 Connector 的分工

- 本 Connector 只处理**商标**（名称、图样、类别、状态、流程、评审）。
- 技术方案、产品结构、发明创造相关的检索请用**八月瓜知识产权智能 MCP（专利）**，走 `patent_*` 系列工具。
- 两者共用同一个八月瓜 API Key，但服务地址不同（商标 `/api/q8mfg1/`，专利 `/api/v6qb6r/`），**不可互换**。
