---
name: sq-company-dynamic
description: 上奇产业通MCP提供「产业—区域—企业」三维产业数据服务，支持产业链图谱与长短板诊断、区域产业全景与近三年迁移监测、企业工商/经营/融资/供应链/竞争/风险全息画像，以及招商推荐榜单与本地触达路径查询，适用于产业研究、区域规划、招商引资与投前尽调。
version: "1.0.0"
author: "上奇产业通"
metadata:
  category: connector
---

# 上奇产业通 MCP Skill

本 Skill 基于「上奇产业通」MCP Server，提供**产业—区域—企业**三个维度的一体化数据查询能力，覆盖产业链图谱、区域产业全景、企业工商/经营/风险画像、供应链与竞争关系、企业迁移监测、招商推荐与本地触达路径规划。

典型用途：产业研究、区域产业规划、招商选商与以商招商、投前/贷前尽调、竞品与供应链分析。

---

## 一、调用前置规则（务必遵守）

本服务存在三个「基础匹配」工具，是绝大多数分析类接口的**强制前置**步骤。正确顺序如下：

```
产业维度：search_industry_id(关键词) → 产业编码/标准产业名称
区域维度：search_region_id(关键词)   → 区域编码（支持省/市/区/县）
企业维度：search_company_id(关键词)  → 企业标准名称 + 统一社会信用代码
                ↓
产业链环节：get_industry_chain_graph(产业完整名称) → 环节编码 industry_link_code
                ↓
再调用产业/区域/企业类的分析型接口
```

规则要点：

1. 传产业参数的接口（如 `get_region_industry_overview`、`analyze_region_chain_advantages`、`get_region_migration_overview`、`get_company_recommendation_ranking`），**优先传 `search_industry_id` 返回的标准产业名称或产业编码**，直接传自然语言产业名仅在无歧义时使用。
2. 传区域参数的接口，优先使用 `search_region_id` 得到的区域编码；多数接口同时接受区域名称（如「深圳」「广东省」）。
3. 查企业类接口前，**先 `search_company_id` 获取标准企业名称**，再用 `company_name` 或 `credit_code` 调用，可显著降低同名/简称错配率。
4. 需要限定产业链某个环节时，先用 `get_industry_chain_graph` 拿到 `industry_link_code`，再传入目标接口（传环节表示统计该环节及其全部下级环节）。
5. 企业类接口的 `company_name` 与 `credit_code` **二选一即可，但建议两个都传或传信用代码**，信用代码为唯一标识。

---

## 二、工具总览

| # | 工具名 | 类别 | 一句话用途 |
|:--:|--------|------|-----------|
| 1 | `search_industry_id` | 基础匹配 | 产业名称/环节名 → 产业编码 |
| 2 | `search_region_id` | 基础匹配 | 区域名称 → 行政区划编码 |
| 3 | `search_company_id` | 基础匹配 | 企业名称 → 标准名称 + 信用代码 |
| 4 | `get_industry_chain_graph` | 产业链 | 生成产业链上下游图谱 + 末级环节重点企业 |
| 5 | `analyze_region_chain_advantages` | 产业链 | 域内产业链 1-3 级环节长短板诊断 |
| 6 | `get_region_industry_overview` | 区域产业 | 区域产业全景：统计 + 下级分布 + 核心企业 |
| 7 | `get_region_migration_overview` | 区域产业 | 近三年产业企业迁入/迁出概况与明细 |
| 8 | `get_company_recommendation_ranking` | 招商 | 近 12 个月招商推荐企业榜（含推荐理由与 A/B/C/D 评级） |
| 9 | `get_company_connect_path` | 招商 | 目标企业 × 指定区域的本地合作企业触达路径（含联系方式） |
| 10 | `search_company_list` | 企业检索 | 关键词向量 + 结构化条件筛选企业清单 |
| 11 | `get_company_basic_profile` | 企业静态 | 工商基础信息 + 股东/人员/对外投资/分支/变更 |
| 12 | `get_company_business_profile` | 企业静态 | 经营信息 + 产品/许可/资质/专利/标准/平台/上市 |
| 13 | `get_company_financing_history` | 企业动态 | 成立以来的融资轮次、金额、投资方 |
| 14 | `get_company_supply_relations` | 企业关系 | 主要客户 TOP10 / 主要供应商 TOP10 |
| 15 | `get_company_competitive_relations` | 企业关系 | 竞争对手与竞争维度、竞争强度 |
| 16 | `get_company_innovation_relations` | 企业动态 | 产学研/联合研发：联合专利、共同参编标准 |
| 17 | `get_company_risk_overview` | 企业风险 | 近 12 个月 15 类风险因子全景与明细 |

---

## 三、基础匹配类工具

### 3.1 search_industry_id — 产业编码匹配

根据产业关键词匹配产业目录，返回匹配方式、产业名称、产业编码；支持产业名精确匹配、产业名模糊匹配、环节名反向匹配三种策略（仅返回已展示、自有、有产业链的产业）。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 产业搜索关键词，支持产业名或环节名，优先精确匹配产业名 |

**使用示例**：

```text
# 查标准产业名 + 产业编码（后续所有产业类接口的前置步骤）
search_industry_id(keyword="集成电路")

# 用环节名反向匹配所属产业（知道环节、不知道产业时用）
search_industry_id(keyword="动力电池")
→ 返回所属产业名与产业编码，再把产业完整名称传给 get_industry_chain_graph

# 标准调用链
search_industry_id(keyword="新能源汽车")
  → 得到标准产业名称"新能源汽车"与产业编码
  → 传给 get_region_industry_overview / analyze_region_chain_advantages / 
     get_region_migration_overview / get_company_recommendation_ranking
```

**何时调用**：调用 `get_industry_chain_graph`、`get_region_industry_overview`、`analyze_region_chain_advantages`、`get_region_migration_overview`、`get_company_recommendation_ranking` 之前。

---

### 3.2 search_region_id — 区域编码匹配

根据区域关键词搜索行政区划编码，返回区域名称与区域编码；支持精确匹配、去行政后缀模糊匹配、向量模糊匹配。结果唯一时直接返回，否则返回候选列表。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 区域关键词，支持省/市/区/县，含后缀或简称均可 |
| limit | string | - | 候选返回数量上限，默认 5，最大 10 |

**使用示例**：

```text
# 精确查市级编码
search_region_id(keyword="深圳市")
→ 440300

# 简称也可匹配（自动去除行政后缀）
search_region_id(keyword="广东")
→ 440000（广东省）

# 查区县级编码，用于下钻分析
search_region_id(keyword="海淀区")

# 名称有歧义时扩大候选数后由用户确认
search_region_id(keyword="高新区", limit="10")
→ 返回多个候选区域及编码，需与用户确认后再进入后续分析
```

---

### 3.3 search_company_id — 企业编码匹配

根据企业名称关键词搜索企业，返回企业 ID、标准企业名称、统一社会信用代码；精确命中直接返回，模糊命中返回候选列表供确认，并按注册资本、成立年限、上市状态等权重排序。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 企业搜索关键词，支持全称、简称 |

**使用示例**：

```text
# 用全称精确命中
search_company_id(keyword="小米科技有限责任公司")
→ 直接返回企业 ID + 标准企业名称 + 统一社会信用代码

# 用简称模糊命中（返回候选，需确认后再查详情）
search_company_id(keyword="小米")
→ 返回候选列表，按注册资本/成立年限/上市状态排序，确认后取 credit_code

# 标准调用链
search_company_id(keyword="某某新能源科技股份有限公司")
  → 取 credit_code
  → 传给 get_company_basic_profile / get_company_business_profile /
     get_company_risk_overview / get_company_financing_history 等
```

---

## 四、产业链分析类

### 4.1 get_industry_chain_graph — 产业链图谱

生成指定产业的产业链图谱，并为末级环节补充重点企业列表；返回产业链环节（上下游）结构、末级环节定义、环节重点企业 TOP（如 TOP5）。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 产业完整名称，建议先用 `search_industry_id` 获取 |

**使用示例**：

```text
# 生成完整产业链图谱
search_industry_id(keyword="集成电路")     → 得到标准产业完整名称
get_industry_chain_graph(keyword="集成电路")
→ 上游（半导体材料、设备）→ 中游（设计、制造、封测）→ 下游（应用）
  及每个末级环节的 TOP5 重点企业

# 取环节编码用于下钻（核心用法）
get_industry_chain_graph(keyword="集成电路")
  → 从返回结果中提取"芯片设计"环节的 industry_link_code
  → 传给 get_region_industry_overview(industry_link_code=...)
    统计该环节及其下级环节的企业与指标
  → 或传给 get_company_recommendation_ranking(industry_link_code=...)
    只推荐该环节的企业

# 强链补链分析
get_industry_chain_graph(keyword="新能源汽车")
  → 看清缺失环节后，再调 analyze_region_chain_advantages 定位本区域短板
```

**用途**：强链/补链/延链分析、环节识别与重点企业挂链画像；其返回的 `industry_link_code` 是多个接口的可选下钻参数。

---

### 4.2 analyze_region_chain_advantages — 域内产业链长短板分析

按产业通长短板口径标注产业链 1-3 级各环节的优劣势：

- 环节企业数占同级环节企业总数比例 **≥ 0.5** → **优势环节**（附代表企业 TOP5）
- 有企业但比例 **< 0.5** → **覆盖环节（短板环节）**
- 无企业 → **缺失环节**

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 产业名称或产业编码 |
| region_code | string | ✅ | 区域编码（必须指定省/市/区，**全国口径不适用**） |

**使用示例**：

```text
# 深圳市人工智能产业链长短板诊断
search_industry_id(keyword="人工智能")
search_region_id(keyword="深圳市")          → 440300
analyze_region_chain_advantages(keyword="人工智能", region_code="440300")
→ 优势环节（含代表企业 TOP5）/ 覆盖环节（短板）/ 缺失环节

# 区县级下钻：看深圳南山区的短板
analyze_region_chain_advantages(keyword="人工智能", region_code="440305")

# 与招商推荐联动（最常用链路）
analyze_region_chain_advantages(keyword="人工智能", region_code="440300")
  → 得到"缺失/短板"环节
  → get_industry_chain_graph(keyword="人工智能") 取该环节 industry_link_code
  → get_company_recommendation_ranking(industry_code="人工智能",
        region_code="440300", industry_link_code=<短板环节编码>)
  → 得到针对短板的招商目标清单
```

**用途**：识别产业链短板 → 指导补链强链招商。

---

## 五、区域产业类

### 5.1 get_region_industry_overview — 区域产业全景概览

某产业在指定区域内的全景概览，**三种输出模式**：

1. **默认模式**（不传 `sub_region` 与 `list_type`）：区域统计指标（全部/上市/小巨人/专精特新/国高新/科技中小/独角兽企业数量、授权专利数、标准数、创新平台数及国家级/省级分布、投融资事件数与金额）+ 汇总（下级区域企业数量前三、核心企业列表）
2. **sub_region 模式**：传具体下级区域名（如「深圳市」）返回该下级区域详情；传「列表」/「全部」/「排名」返回下级区域排名列表
3. **名单模式**（`list_type="核心企业"`）：核心环节企业名单（按环节层级 + 企业总分排名）

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 产业名称或编码 |
| region_code | string | ✅ | 区域名称或编码；传省级下钻到市级，传市级下钻到区县级 |
| industry_link_code | string | - | 产业环节编码，传入后限定该环节及下级环节 |
| sub_region | string | - | 下级区域名称，或「列表」/「全部」/「排名」 |
| list_type | string | - | 传「核心企业」返回核心环节企业名单 |
| page_size | string | - | 每页条数，默认 5，最大 100 |
| page_number | string | - | 页码，默认 1 |

**使用示例**：

```text
# ① 默认模式：深圳市人工智能产业全景
get_region_industry_overview(keyword="人工智能", region_code="440300")
→ 企业总数/上市/小巨人/专精特新/国高新/独角兽数量、授权专利、标准、
  创新平台（国家级/省级）、投融资事件数与金额 + 下级区域前三 + 核心企业

# ② 下级区域排名：广东省各市该产业对比
get_region_industry_overview(keyword="人工智能", region_code="440000",
                             sub_region="列表", page_size="20")

# ③ 指定下级区域详情
get_region_industry_overview(keyword="人工智能", region_code="440000",
                             sub_region="深圳市")

# ④ 名单模式：拉核心企业清单（做招商池）
get_region_industry_overview(keyword="人工智能", region_code="440300",
                             list_type="核心企业", page_size="50", page_number="2")

# ⑤ 限定环节：只看"芯片设计"环节
get_industry_chain_graph(keyword="集成电路")   → 取 industry_link_code
get_region_industry_overview(keyword="集成电路", region_code="440300",
                             industry_link_code=<环节编码>)
```

**口径提示**：专利数量仅统计**已授权**专利（不含申请中）；创新平台仅统计**国家级 + 省级**（不含市级）。

---

### 5.2 get_region_migration_overview — 产业企业迁移概况

某产业近三年（整月对齐）在区域间的迁移概况综述 + 分页企业明细。

- **迁入概况**：迁入企业总数、来源地前三、注册地变更/全资子公司/控股子公司/参股子公司细分
- **迁出概况**：迁出企业总数、目的地前三、四类细分同上

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | ✅ | 产业名称或编码 |
| emigration_region_code | string | - | 迁出地区域编码（与迁入区域至少传一个，不传默认全国） |
| immigration_region_code | string | - | 迁入地区域编码（与迁出区域至少传一个，不传默认全国） |
| industry_link_code | string | - | 产业环节编码，限定该环节及下级环节 |
| page_size | string | - | 每页企业数量，默认 5，最大 100 |
| page_number | string | - | 页码，默认 1 |

**使用示例**：

```text
# ① 产业流失监测：深圳有哪些新能源汽车企业外迁、去了哪
get_region_migration_overview(keyword="新能源汽车", emigration_region_code="440300")
→ 迁出企业总数、目的地前三、注册地变更/全资子公司/控股/参股细分 + 企业明细

# ② 招商引资来源分析：迁入深圳的企业从哪来
get_region_migration_overview(keyword="新能源汽车", immigration_region_code="440300")
→ 迁入企业总数、来源地前三 + 企业明细

# ③ 省际定向分析：广东 → 江苏的迁移
get_region_migration_overview(keyword="新能源汽车",
                              emigration_region_code="440000",
                              immigration_region_code="320000")

# ④ 只看某环节企业的迁移
get_region_migration_overview(keyword="人工智能", emigration_region_code="440300",
                              industry_link_code=<环节编码>, page_size="50")

# ⑤ 翻页拉取完整名单
get_region_migration_overview(keyword="人工智能", emigration_region_code="440300",
                              page_size="100", page_number="2")
```

**用途**：区域产业迁移综合研判、招商引资与产业流失监测、撰写迁移分析报告。

---

## 六、招商与触达类

### 6.1 get_company_recommendation_ranking — 招商推荐企业榜

按产业（可叠加目标区域、产业链环节、企业名关键词）给出近 12 个月招商推荐企业名单。

推荐逻辑：企业命中画像标签（区域拓展快/新增专利多/新获投资多/员工增长快）或近 12 个月招商动态事件（获域外大型项目/域外招聘/政府基金投资及政府背景投资/域外设立分公司/域外股权投资），优先保留双命中企业；再由模型生成推荐理由与推荐程度 **A > B > C > D**。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| industry_code | string | ✅ | 产业名称或编码 |
| region_code | string | - | 目标招商区域；传入后按**事件去向区域**过滤（如深圳市 = 筛选拟向深圳扩张的企业） |
| industry_link_code | string | - | 产业链环节编码，含下级子树 |
| keyword | string | - | 企业名称关键词，模糊过滤候选名单 |
| page_size | integer | - | 每页企业数量，默认 5，最大 50 |
| page_number | integer | - | 页码，默认 1 |

**使用示例**：

```text
# ① 全国范围某产业招商推荐
get_company_recommendation_ranking(industry_code="人工智能", page_size=20)

# ② 定向招商：筛选"拟向深圳扩张"的人工智能企业（最常用）
get_company_recommendation_ranking(industry_code="人工智能",
                                   region_code="440300", page_size=20)
→ 返回企业名称、信用代码、省市区、综合评分、推荐程度 A/B/C/D、
  推荐理由、企业标签、企业动态

# ③ 针对短板环节精准推荐
get_company_recommendation_ranking(industry_code="集成电路",
                                   region_code="440300",
                                   industry_link_code=<"芯片设计"环节编码>,
                                   page_size=30)

# ④ 已知企业名，查其推荐评级与理由
get_company_recommendation_ranking(industry_code="人工智能", keyword="机器人")

# ⑤ 翻页
get_company_recommendation_ranking(industry_code="人工智能",
                                   region_code="440300",
                                   page_size=50, page_number=2)
```

---

### 6.2 get_company_connect_path — 本地合作触达路径

查询目标企业与**指定区域本地企业**的合作关系及合作方联系方式，用于规划本地触达路径。覆盖四类合作渠道：

1. 共同参研标准（同一标准起草单位）
2. 联合专利（同一专利联合申请人）
3. 投资三向（共同投资 / 投资本地企业 / 获本地企业投资）
4. 供应链合作（目标企业为供应方或采购方的中标合作）

返回本地合作企业列表（含名称、信用代码、关系类型、合作事项、合作时间、合作状态、联系方式），联系方式来自公开渠道（每企业最多 8 条），无公开联系方式时提示「暂无」；按最新合作时间倒序。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| region | string | ✅ | 区域名称或行政编码，如「深圳」/「440300」/「广东省」 |
| company_name | string | △ | 企业名称（与 credit_code 至少传一个） |
| credit_code | string | △ | 统一社会信用代码 |
| page_size | integer | - | 每页条数，默认 5，最大 50 |
| page_number | integer | - | 页码，默认 1 |

**使用示例**：

```text
# ① 以商招商：找目标企业在深圳本地的合作方作为突破口
search_company_id(keyword="某某科技股份有限公司")    → 取标准名称/credit_code
get_company_connect_path(region="深圳",
                         company_name="某某科技股份有限公司",
                         page_size=10)
→ 本地合作企业总数 + 列表（关系类型/合作事项/合作时间/状态/联系方式）

# ② 用区域编码调用，扩大触达面到全省
get_company_connect_path(region="广东省", credit_code="91440300XXXXXXXXXX",
                         page_size=50)

# ③ 批量跑招商名单（配合推荐榜使用）
get_company_recommendation_ranking(industry_code="人工智能", region_code="440300", page_size=10)
  → 对 Top10 企业逐个：
    get_company_connect_path(region="440300", company_name=<企业名>, page_size=5)
  → 汇总"通过哪些本地企业可以接触到目标企业"

# ④ 翻页取全量
get_company_connect_path(region="深圳", company_name="某某科技有限公司",
                         page_size=50, page_number=2)
```

**注意**：此接口区域参数名为 `region`（非 `region_code`），不要混用。无合作关系时返回未发现提示，此时应如实告知用户，不得编造联系人。

---

## 七、企业检索与画像类

### 7.1 search_company_list — 企业多条件检索

两种用法：

- **关键词模式**：先在业务范围向量库（产品 / 专利技术 / 简介）中语义检索，再叠加结构化条件，按「企业综合评分 × 向量相似度」排序
- **纯结构化模式**：不传关键词，仅按结构化条件筛选，按企业综合评分排序

输出：企业名称、统一社会信用代码、成立日期、省市区、资质标签、规模、注册资本、最新融资轮次、是否上市、综合评分及分页信息；关键词模式下额外返回匹配的产品/专利技术/简介原文。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| keyword | string | - | 产品名、专利技术词、主营业务描述等自然语言 |
| query_scope | string | - | 检索范围：产品/专利/简介，多个逗号分隔，默认全部 |
| region_code | string | - | 区域编码，支持省/市/区县，按层级匹配该区域及下级 |
| establish_start_date | string | - | 成立起始日期，YYYY-MM-DD 或 YYYY-MM |
| establish_end_date | string | - | 成立结束日期，YYYY-MM-DD 或 YYYY-MM |
| scale | string | - | 微型/小型/中型/大型，多个逗号分隔 |
| reg_capital_min | string | - | 注册资本下限（万元） |
| reg_capital_max | string | - | 注册资本上限（万元） |
| qualification_tags | string | - | 小巨人/国高新/省级专精特新/国家级单项冠军/省级单项冠军，任一命中即保留 |
| financing_round | string | - | 天使轮/A轮/B轮/C轮/D轮/E轮及以后/IPO/Pre-IPO/Post-IPO/战略融资/股权融资/债务融资 |
| is_listed | string | - | 是/否 |
| page_size | integer | - | 默认 5，最大 50 |
| page_number | integer | - | 默认 1 |

**使用示例**：

```text
# ① 按产品关键词找企业
search_company_list(keyword="锂电池", page_size=20)

# ② 关键词 + 区域 + 上市状态（"杭州做锂电池的上市企业"）
search_company_list(keyword="锂电池", region_code="330100", is_listed="是", page_size=20)

# ③ 纯结构化：江苏省的小巨人企业
search_company_list(region_code="320000", qualification_tags="小巨人", page_size=30)

# ④ 限定检索维度：只在专利技术库里搜"减速器"
search_company_list(keyword="减速器", query_scope="专利", page_size=20)

# ⑤ 多条件组合：近5年成立、注册资本≥5000万、B轮、非上市的中型企业
search_company_list(keyword="工业机器人",
                    establish_start_date="2020-01",
                    scale="中型",
                    reg_capital_min="5000",
                    financing_round="B轮",
                    is_listed="否",
                    page_size=30)

# ⑥ 拿到清单后逐个下钻
search_company_list(keyword="固态电池", region_code="440300", page_size=10)
  → 对目标企业调 get_company_basic_profile / get_company_business_profile 核验
```

---

### 7.2 get_company_basic_profile — 企业基础信息（工商侧）

不传 `info_type` 时返回基本信息（企业名称、统一社会信用代码、成立时间、企业性质、规模、状态、注册资本、参保人数、省市区、存续状态）+ 股东/主要人员/对外投资/分支机构/变更历史五类条数汇总；传 `info_type` 时返回对应明细（按时间倒序，主要人员按职位排序全量返回）。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |
| info_type | string | - | 股东 / 主要人员 / 对外投资 / 分支机构 / 变更历史 |
| page_size | integer | - | 默认 3，最大 50（主要人员全量返回不分页） |
| page_number | integer | - | 默认 1 |

**使用示例**：

```text
# ① 先拿工商总览 + 五类信息条数
search_company_id(keyword="某某科技有限公司")        → credit_code
get_company_basic_profile(credit_code="91440300XXXXXXXXXX")
→ 基本信息 + 股东/人员/对外投资/分支/变更各有多少条

# ② 查股东构成（股权穿透、判断实控人）
get_company_basic_profile(credit_code="91440300XXXXXXXXXX",
                          info_type="股东", page_size=20)

# ③ 查高管团队（别名「高管」等价于「主要人员」，全量返回不分页）
get_company_basic_profile(credit_code="91440300XXXXXXXXXX", info_type="主要人员")

# ④ 查对外投资布局（别名：子公司/参股公司/控股公司）
get_company_basic_profile(company_name="某某科技股份有限公司",
                          info_type="对外投资", page_size=50)

# ⑤ 查分支机构（别名：分公司），判断区域布局
get_company_basic_profile(company_name="某某科技股份有限公司",
                          info_type="分支机构", page_size=50)

# ⑥ 追溯工商变更（别名：工商变更）
get_company_basic_profile(credit_code="91440300XXXXXXXXXX",
                          info_type="变更历史", page_size=30, page_number=1)
```

**别名映射**：高管 → 主要人员；子公司/参股公司/控股公司 → 对外投资；分公司 → 分支机构；工商变更 → 变更历史。

---

### 7.3 get_company_business_profile — 企业经营信息（经营侧）

不传 `info_type` 时返回基本信息 + 经营统计汇总（主营产品数、行政许可数及最新许可日期、荣誉资质数及当前有效资质、专利总数及发明/实用新型/外观设计分类、参编标准数、创新平台数、上市状态及上市时间）。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |
| info_type | string | - | 主营产品 / 行政许可 / 资质证书 / 荣誉资质 / 专利情况 / 参编标准 / 创新平台 / 上市信息 |
| page_size | integer | - | 默认 3，最大 50（主营产品固定前 5 条、上市信息全量，均不分页） |
| page_number | integer | - | 默认 1 |

**使用示例**：

```text
# ① 经营能力总览（判断技术实力与合规水平的第一眼）
get_company_business_profile(credit_code="91440300XXXXXXXXXX")
→ 产品数、行政许可数及最新日期、荣誉资质数及有效资质、
  专利总数（发明/实用新型/外观设计）、参编标准数、创新平台数、上市状态

# ② 看主营产品在做什么（固定返回前 5 条，不分页）
get_company_business_profile(company_name="某某科技有限公司", info_type="主营产品")

# ③ 盘点专利（技术尽调核心）
get_company_business_profile(credit_code="91440300XXXXXXXXXX",
                             info_type="专利情况", page_size=50, page_number=1)

# ④ 核实上市状态与板块
get_company_business_profile(company_name="某某股份有限公司", info_type="上市信息")

# ⑤ 查资质证书/行政许可（判断准入合规）
get_company_business_profile(credit_code="91440300XXXXXXXXXX",
                             info_type="资质证书", page_size=20)

# ⑥ 看参编标准与创新平台（判断行业话语权）
get_company_business_profile(credit_code="91440300XXXXXXXXXX",
                             info_type="参编标准", page_size=20)
get_company_business_profile(credit_code="91440300XXXXXXXXXX",
                             info_type="创新平台", page_size=20)
```

---

### 7.4 get_company_financing_history — 融资历史

查询企业成立以来的融资情况：融资轮次、日期、金额、投资方等详细记录。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |

**使用示例**：

```text
# ① 查单家企业完整融资轨迹
search_company_id(keyword="某某生物医药有限公司")   → credit_code
get_company_financing_history(credit_code="91440300XXXXXXXXXX")
→ 天使轮/A轮/B轮…的日期、金额、投资方

# ② 尽调场景：判断股东背景与估值走势
get_company_financing_history(company_name="某某科技股份有限公司")

# ③ 批量场景（先用检索缩小范围，再逐个查）
search_company_list(keyword="创新药", region_code="310000",
                    financing_round="B轮", page_size=20)
  → 对候选企业逐个：
    get_company_financing_history(credit_code=<信用代码>)
  → 比对融资节奏与投资方质量

# ④ 与风险/工商交叉验证
get_company_financing_history(credit_code=...)
get_company_basic_profile(credit_code=..., info_type="股东")
  → 核对投资方是否已体现在股东名册中
```

**用途**：评估融资能力与市场吸引力。

---

### 7.5 get_company_risk_overview — 近 12 个月风险全景

覆盖 **15 类风险因子**：股权出质、被列入失信被执行人、被列入被执行人、限制消费令、经营异常、动产抵押、知识产权出质、限制出境、终本案件、裁判文书、开庭公告、立案信息、严重违法、行政处罚、股权冻结。

- **不传 `info_type`**：返回①风险总览总结句（X 项有记录、Y 项无记录及明细数量）；②风险统计 11 项及 15 类因子逐类数量
- **传 `info_type`**：返回该因子近 12 个月明细（按时间倒序，默认 5 条/页，最多 30 条）

统计口径：司法案件 = 立案信息 + 开庭公告 + 裁判文书；强制执行 = 被列入被执行人 + 终本案件 + 股权冻结；违规处罚 = 行政处罚 + 严重违法；企业担保 = 动产抵押 + 股权出质；限制高消费人员 = 限制消费令 + 限制出境。立案信息/开庭公告/裁判文书合并原告与被告双角色并逐条标注涉诉角色。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |
| info_type | string | - | 上述 15 类风险因子名称之一 |
| page_size | integer | - | 默认 5，最大 30（仅明细生效） |
| page_number | string | - | 默认 1 |

**使用示例**：

```text
# ① 先做风险全景扫描（贷前/投前初筛必做）
get_company_risk_overview(credit_code="91440300XXXXXXXXXX")
→ "X 项有记录、Y 项无记录" + 11 项统计 + 15 类因子逐类数量

# ② 发现涉诉数量异常 → 深挖裁判文书
get_company_risk_overview(credit_code="91440300XXXXXXXXXX",
                          info_type="裁判文书", page_size=10, page_number=1)
→ 逐条含涉诉角色（原告/被告）标注

# ③ 查执行类风险
get_company_risk_overview(company_name="某某建设集团有限公司",
                          info_type="被列入被执行人", page_size=10)

# ④ 查经营与合规风险
get_company_risk_overview(credit_code="91440300XXXXXXXXXX", info_type="经营异常")
get_company_risk_overview(credit_code="91440300XXXXXXXXXX", info_type="行政处罚", page_size=20)

# ⑤ 查股权稳定性（股权出质/冻结常见于资金链紧张）
get_company_risk_overview(credit_code="91440300XXXXXXXXXX", info_type="股权出质")
get_company_risk_overview(credit_code="91440300XXXXXXXXXX", info_type="股权冻结")
```

---

## 八、企业关系类

### 8.1 get_company_supply_relations — 供应关系

查询主要客户与主要供应商：客户总数、主要客户 TOP10、供应商总数、主要供应商 TOP10（含合作次数、金额等）。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |

**使用示例**：

```text
# ① 评估供应链依赖与集中度
search_company_id(keyword="某某新能源汽车股份有限公司")   → credit_code
get_company_supply_relations(credit_code="91440300XXXXXXXXXX")
→ 客户总数/客户 TOP10、供应商总数/供应商 TOP10（含合作次数、金额）

# ② 供应风险排查：单一供应商占比是否过高
get_company_supply_relations(company_name="某某电子科技有限公司")
→ 检查供应商 TOP10 的合作次数与金额分布

# ③ 与竞争关系组合，形成完整格局判断
get_company_supply_relations(credit_code=...)      # 谁依赖谁
get_company_competitive_relations(credit_code=...) # 谁和谁打

# ④ 招商延伸：从供应链 TOP 名单里找可引进的配套企业
get_company_supply_relations(credit_code=...)
  → 取供应商 TOP10
  → 逐个 get_company_connect_path(region=<本地>, company_name=<供应商名>)
```

**用途**：分析供应链上下游依赖、合作集中度与供应风险。

---

### 8.2 get_company_competitive_relations — 竞争关系

查询竞争对手列表、竞争维度（产品 / 技术 / 市场）、竞争强度。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |

**使用示例**：

```text
# ① 竞品对标：谁在跟它抢市场
get_company_competitive_relations(credit_code="91440300XXXXXXXXXX")
→ 竞争对手列表 + 竞争维度（产品/技术/市场）+ 竞争强度

# ② 仅知企业名时
get_company_competitive_relations(company_name="某某智能装备股份有限公司")

# ③ 与经营画像组合，解释"为什么是竞争对手"
get_company_competitive_relations(credit_code=...)
get_company_business_profile(credit_code=..., info_type="主营产品")
→ 对比主营产品重合度，验证竞争关系

# ④ 招商场景：竞争对手在哪，就往哪招
get_company_competitive_relations(credit_code=...)
  → 取竞争对手所在区域
  → 对重点对手调 get_company_recommendation_ranking(region_code=<对手所在区域>)
```

与 `get_company_supply_relations` 互补（合作 vs 竞争）。

---

### 8.3 get_company_innovation_relations — 创新合作动态

查询产学研合作、联合研发等：专利联合申请方、标准共同起草方等。

| 参数 | 类型 | 必填 | 说明 |
|------|:----:|:----:|------|
| company_name | string | △ | 企业名称 |
| credit_code | string | △ | 统一社会信用代码 |

**使用示例**：

```text
# ① 看企业技术合作网络（联合专利、共同参编标准）
get_company_innovation_relations(credit_code="91440300XXXXXXXXXX")
→ 专利联合申请方、标准共同起草方列表

# ② 判断产学研深度：是否和高校/科研院所联合申请专利
get_company_innovation_relations(company_name="某某新材料股份有限公司")

# ③ 招商延伸：从联合专利方里找可引进的合作企业
get_company_innovation_relations(credit_code=...)
  → 取联合申请方
  → 逐个 get_company_connect_path(region=<本地>, company_name=<合作方>)

# ④ 与创新平台资质交叉验证（本工具看"合作关系"）
get_company_innovation_relations(credit_code=...)
get_company_business_profile(credit_code=..., info_type="创新平台")
```

---

## 九、典型任务链路

### 场景 A：某区域某产业的招商目标筛选

```
1. search_industry_id("人工智能")            → 产业编码
2. search_region_id("深圳市")                → 区域编码 440300
3. get_region_industry_overview(产业, 深圳)   → 全景指标 + 核心企业
4. analyze_region_chain_advantages(产业, 深圳)→ 识别短板/缺失环节
5. get_industry_chain_graph(产业完整名称)     → 取短板环节 industry_link_code
6. get_company_recommendation_ranking(
     industry_code=产业, region_code=深圳,
     industry_link_code=短板环节)            → A/B/C/D 推荐名单
7. 对 Top 企业逐个：get_company_connect_path(region=深圳, company_name=...)
                                            → 本地触达路径与联系方式
```

### 场景 B：企业投前/贷前尽调

```
1. search_company_id(企业名)                 → 标准名称 + 信用代码
2. get_company_basic_profile(credit_code)    → 工商概况
3. get_company_basic_profile(info_type="股东"/"对外投资"/"变更历史")
4. get_company_business_profile(credit_code) → 经营统计（专利/资质/上市）
5. get_company_risk_overview(credit_code)    → 风险全景
   （有风险项时再按 info_type 深挖明细，如"裁判文书"、"行政处罚"）
6. get_company_financing_history(credit_code)→ 融资历史
7. get_company_supply_relations + get_company_competitive_relations
                                            → 供应链依赖与竞争格局
```

### 场景 C：产业流失监测与迁移分析

```
1. search_industry_id + search_region_id
2. get_region_migration_overview(
     keyword=产业, emigration_region_code=本区域)   → 迁出去向与明细
3. get_region_migration_overview(
     keyword=产业, immigration_region_code=本区域)  → 迁入来源与明细
4. 对重点迁移企业用 get_company_basic_profile 核验主体关系
```

### 场景 D：按技术/产品找标的

```
search_company_list(
  keyword="减速器专利", query_scope="专利",
  region_code="320000", qualification_tags="小巨人",
  is_listed="否", financing_round="B轮", page_size=20)
→ 对候选企业逐个 get_company_business_profile(info_type="专利情况") 复核
```

---

## 十、注意事项与错误处理

1. **凭证**：服务需在请求头携带 `Authorization` 秘钥；若调用返回鉴权失败，提示用户前往平台重新获取或更新秘钥，不要跳过认证继续推测数据。
2. **分页**：绝大多数列表接口返回总数/总页数，可据此翻页；不同接口 `page_size` 上限不同（30 / 50 / 100），超出上限会被截断，应说明实际取值而非声称取到全部。
3. **二选一参数**：企业类接口 `company_name` 与 `credit_code` 至少提供一个；迁移概况的迁入/迁出区域至少提供一个。
4. **参数名差异**：`get_company_connect_path` 的区域参数名为 `region`，其余接口为 `region_code`，不要混用。
5. **口径一致性**：
   - 专利数量 = 已授权专利，不含申请中；
   - 创新平台 = 仅国家级 + 省级，不含市级；
   - 长短板为域内相对概念，必须指定省/市/区，全国口径不适用；
   - 资质标签枚举以接口文档为准（小巨人/国高新/省级专精特新/国家级单项冠军/省级单项冠军）。
6. **结果缺失**：企业无合作关系、无融资记录、无风险记录、无公开联系方式时，接口会返回「未发现/暂无」提示。此时应如实转述，**严禁凭常识编造企业、联系人、金额或日期**。
7. **成本控制**：优先用匹配类工具锁定精确 ID，避免同一意图反复多轮试错调用。
8. **输出规范**：涉及金额、数量、占比时保留接口原值的单位与口径；撰写分析报告时注明统计时间范围（如「近 12 个月」「近三年」）与区域口径（省/市/区县）。
