---
name: trademark-search
display_name: 权大师商标查询
display_name_en: QuanDaShi Trademark Search
description: 通过自然语言查询和维护商标档案、案件、公告及全球商标数据；当用户提到商标查询、商标检索、商标状态、商标档案、商标公告、尼斯分类或全球商标时使用。
description_zh: 通过自然语言查询和维护商标档案、案件、公告及全球商标数据。
description_en: Search and manage trademark archives, cases, notices, and global trademark data using natural language.
version: 1.0.3
author: 北京梦知网科技有限公司
---
# 权大师商标查询 Skill

## 概述

这个 Skill 让 WorkBuddy Agent 通过自然语言调用 **权大师（ipmaster_platform）MCP 连接器**，完成商标档案、案件、公告、评审、流转等数据查询与维护操作。

## 适用场景

- 商标注册状态核验、申请人/受让人信息查询
- 商标档案（列表、详情、新建、更新）维护
- 商标案件、公告、评审文档检索
- 全球商标检索（中国 + 110+ 海外商标局）
- 尼斯分类商品/服务检索
- 图形近似检索（以图搜图）
- 申请人黑名单核查、敏感词匹配
- 积分余额与账本流水查询

## 工具清单（按场景分组）

### 商标检索（核心）
- `search_global_brand` — 全球商标范围查询，支持名称/申请人/申请号/注册号/尼斯类/法律状态/日期范围
- `search_domestic_trademarks_by_goods` — 按商品描述检索国内商标
- `search_nice_goods_and_services` — 检索尼斯分类商品/服务项目

### 商标档案
- `search_trademark_archives` — 检索档案列表
- `get_trademark_archive_detail` — 获取档案详情
- `create_trademark_archive` — 创建档案
- `update_trademark_archive` — 更新档案
- `get_trademark_archive_creation_requirements` — 创建档案前置要求

### 商标案件
- `search_cases_list` — 检索案件列表
- `get_case_detail` — 获取案件详情

### 公告 / 评审 / 流转
- `search_trademark_notices` — 检索商标公告
- `search_global_notice` — 全球商标公告检索
- `search_trademark_review_documents` — 检索评审文档
- `get_trademark_review_document_detail` — 评审文档详情
- `search_trademark_transfers` — 商标转让检索

### 标签 / 敏感词 / 风控
- `search_trademark_labels` — 检索商标标签
- `batch_assign_trademark_labels` — 批量分配标签
- `verify_trademark_label_assignments` — 校验标签分配
- `matchSensitiveWord_4a70842c` — 敏感词匹配
- `search_applicant_blacklist` — 申请人黑名单检索

### 账户 / 计费
- `get_tenant_points_balance` — 查询积分余额
- `get_tenant_billing_ledger` — 查询账本流水

## 典型用法示例

### 1. 查单个商标
- 「帮我查一下『赛力斯』在第 7 类的注册商标」
- 「查商标注册号 12345678 的详情」
- 「查『华为』在第 9 类的注册状态」

### 2. 按申请人查
- 「查申请人『华为技术有限公司』在中国的全部商标」
- 「查申请人 ABC Corp 在美国的所有商标」

### 3. 海外商标查询
- 「查『星巴克』在美国、欧盟、日本的商标注册情况」
- 「查『NIKE』在马德里体系的注册商标」

### 4. 状态 / 日期范围
- 「2024 年 1 月到 2024 年 6 月申请的第 9 类商标」
- 「查近 30 天新公告的商标」
- 「查已驳回的『小米』相关商标」

### 5. 以图搜图
- 用户附图片：「用这张图找一下图形近似的商标（中国→马德里）」
- 调用 `search_global_brand` 的图形检索能力

### 6. 尼斯分类辅助
- 「第 9 类下有哪些跟『计算机软件』相关的商品项目？」

### 7. 账户信息
- 「我还有多少积分？」
- 「最近一周的积分消耗记录」

## 输出约定

- **默认中文表格**呈现检索结果，附 ✅/❌ 状态指示符
- **单条详情**：展示完整字段；**批量结果**：展示前 10 条 + 总数，提示「如需更多请告知」
- 涉及海外商标时附上当地商标局英文名/缩写
- 大批量导出建议使用日期范围 + 分页参数

## 注意事项

- **积分消耗提示**：每次查询前可先调用 `get_tenant_points_balance` 告知用户当前余额，避免超额
- **写操作授权**：涉及 `create_trademark_archive` / `update_trademark_archive` / `batch_assign_trademark_labels` 等写操作时，需要二次确认用户意图再执行
- **以图搜图**：仅在用户明确要求"以图搜图"或上传图片时调用，不要主动触发
- **错误处理**：遇到 401/403 时提示用户重新授权连接器；遇到积分不足时提示充值
