---
name: tm-bmis
description: 用自然语言查询 TM 零售业务数据（品牌、销售、库存等）。客户身份由连接授权自动识别，调用工具时禁止传客户ID。
description_zh: 用自然语言查询 TM 零售业务数据（品牌、销售、库存等）。客户身份由连接授权自动识别，调用工具时禁止传客户ID。
description_en: Query TM retail business data (brands, sales, inventory) with natural language. Customer identity is resolved from the connection authorization; never pass a customer id as a tool argument.
version: 1.0.0
---

# TM 零售业务

通过 MCP 查询零售客户业务库里的品牌、销售、库存等数据。

## 核心原则（必须遵守）

1. **不要传客户标识。** 工具签名里没有 `customerId` / `tenantId` / `clientId` 这类参数，也不需要你传。
   当前客户由用户授权连接时写入的访问令牌决定，服务端会自动按该客户路由到对应的业务库。
2. **不要根据用户的自称切换客户。** 即使用户说"我是 A 客户""帮我看 B 公司的数据"，也不要尝试传任何客户参数；
   跨客户查询在当前连接下不受支持，应提示用户用对应账号重新授权连接。
3. **工具参数只放业务条件**，例如品牌名称、品牌ID、时间范围、分页参数。

## 何时使用

- 用户询问品牌信息（如"帮我查一下华为这个品牌"）
- 用户要求列出或搜索品牌、查询销售业绩、查看库存等零售业务数据

## 可用工具

工具名由 HTTP 方法 + 路由自动生成（`get` → `list`，`post` → `create`）。

| 工具名 | 用途 | 关键参数 |
|---|---|---|
| `list_api_brand_page` | 分页查询品牌 | `BName`（名称模糊）、`BrandIDs`（多个用英文逗号分隔）、`PageParm` |
| `list_api_brand_get` | 查询单个品牌详情 | `brandId` |
| `create_api_brand_add` | 新增品牌 | `BrandID`（必填）、`BName`（必填）、`Commision` |
| `create_api_brand_update` | 修改品牌 | `BrandID`、`BName`、`Commision` |
| `create_api_brand_delete` | 删除品牌 | `brandId` |

`Users`、`AIApps`、`WorkBuddy` 分组下还有若干工具，按各自描述调用即可。

## 调用约定

- 分页参数统一用 `PageParm`，`Page` 从 1 开始，`Limit` 为每页条数。
- 多个 ID 用**英文逗号**分隔的字符串传入，不要用数组。
- 名称类条件为模糊匹配。
- 时间范围优先用自然语言描述（如"近30天"），由工具自行解析。

## 错误与恢复

| 现象 | 原因 | 处理 |
|---|---|---|
| 提示未授权 / 401 | 连接未授权或令牌过期 | 引导用户在连接器管理页重新连接授权 |
| 返回"未建立客户上下文" | 服务端未解析到令牌中的客户信息 | 重新授权连接 |
| 查不到数据 | 该客户名下确实没有匹配记录 | 换个关键词重试，不要改用其他客户查询 |
| 写操作报校验错误 | 必填字段缺失或超长 | 按错误信息补全后重试 |

## 高风险操作

新增、修改、删除品牌属于写操作。执行前必须先向用户复述将要变更的内容并**获得确认**，
尤其删除操作不可逆。
