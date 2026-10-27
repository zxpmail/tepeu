---
name: tfc-taikang-insurance-skill
description: tfc-taikang展业助手 — 查询名下客户与保障分析、日程待办，检索泰学堂培训知识与保险产品图谱。
version: '1.0.0'
author: 'Taikang Buddy Team'
---

# tfc-taikang连接器 —— 工具调用说明

本连接器将泰康内部接口封装为 5 个只读 MCP 工具，数据实时来自泰康系统。

## 认证前置条件

| 工具 | 认证 |
|---|---|
| taikang_query_customer / taikang_get_coverage_analysis / taikang_query_schedule / taikang_query_taixuetang | 需要：用户在 WorkBuddy 中完成连接（绑定应用 → 登录泰康账号），以该员工身份调用，数据范围由泰康按员工权限决定 |
| taikang_query_product_graph | 不需要：公共产品知识库，未连接也可用 |

授权失效时工具返回"泰康登录已失效"，引导用户重新连接即可恢复。

## 登录失效与恢复（所有泰康 skill 通用）


MCP 工具失败时，`isError` 文本是固定结构，先读字段再决定动作：

```text
error_code: TAIKANG_REAUTH_REQUIRED
login_url: https://<服务域名>/workbuddy/login-web?code=<一次性码>
action: open_login_page
instruction: 必须调用 present_files({"url": "<login_url>"}) ……
message: 泰康登录已失效，请重新登录。……
```

**`instruction` 是接口给出的操作说明，优先严格照它执行**；本节只是兜底解释。

> MCP SDK 会在文本最前面加 `Error executing tool <工具名>: `，所以按 `error_code:` / `login_url:` / `action:` 行内匹配，不要依赖「第一行」。

`action` 取值：

| action | 含义 |
| --- | --- |
| `open_login_page` | 用 `present_files` 打开 `login_url` |
| `reconnect_connector` | 提示用户重新授权连接器（无链接可开） |
| 无该字段 | 无需恢复动作，按 `message` 处理 |

| error_code | 含义 | 处理 |
| --- | --- | --- |
| `TAIKANG_REAUTH_REQUIRED` | 泰康 token 失效且无法静默续期 | 用 `present_files` 打开 `login_url` |
| `USER_NOT_BOUND` | 从未绑定或已解绑 | 引导用户在 WorkBuddy 完成连接器授权；无链接可开 |
| `MCP_UNAUTHORIZED` | MCP 凭证失效，工具未执行 | 提示重新授权连接器 |
| `TAIKANG_NOT_FOUND` | 查无记录或无权限 | 核对客户号/姓名，不要反复重试 |
| `TAIKANG_UNAVAILABLE` | 泰康服务不可用 | 稍后重试；连续失败则停止并告知 |
| `TAIKANG_BIZ_ERROR` | 泰康业务错误（多为参数问题） | 按 `message` 修正参数后重试 |

### 打开登录页的硬性要求

1. **必须**调用 `present_files({ "url": "<login_url>" })` 在侧边栏打开；只回复文字、或只贴裸 URL 都不算完成。
2. `login_url` **原样使用**：不要修改域名、端口、路径，也不要省略或改写 `?code=`。
3. 该链接是**一次性码**，有效期不超过 10 分钟，立即打开，不要缓存或延迟。
4. 登录页是 **WorkBuddy 专用**（UA 需含 `WorkBuddy/`），普通浏览器打开会 403。
5. 打开后告知用户：完成后回复「已完成」，再重新调用原工具。
6. `login_url` 为空时**不要臆造地址**，改为说明需要重新授权连接器。

### 不需要登录的工具

产品图谱 / 绿通属于公共服务数据，**不依赖泰康登录**；遇到 `TAIKANG_REAUTH_REQUIRED` 时，该类问题仍应正常回答。

## 工具

### 1. taikang_query_customer

用途：按姓名模糊查询当前员工名下的客户，返回客户号等基本信息。客户只有姓名时先调本工具换取 `customer_id`。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| name | string | 否 | 不传返回名下全部客户 | 客户姓名，支持模糊匹配 |

调用示例：

```json
{"name": "张伟"}
```

返回格式：

```json
{
  "copywriting": "为您找到**1**位客户：",
  "customers": [
    {
      "name": "张伟",
      "sex": "男",
      "customer_id": "zk_a1b2c3d4e5f60718",
      "description": "41岁",
      "tags": ["准客户"],
      "redirect_url": "https://sspuat.taikang.com/...客户详情页"
    }
  ],
  "total": 1
}
```

`customers` 为空列表表示无匹配客户，与用户核对姓名，不要编造客户号。

### 2. taikang_get_coverage_analysis

用途：查询指定客户的家庭保障分析（年金/重疾/医疗/意外的已有保障、推荐额度与缺口分析文本）。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| customer_id | string | 二选一 | 无 | 客户号（zk_ 开头），已知时优先使用 |
| customer_name | string | 二选一 | 无 | 客户姓名 |

调用示例：

```json
{"customer_id": "zk_a1b2c3d4e5f60718"}
```

返回格式：

```json
{
  "date": "2026-10-19",
  "customer_id": "zk_a1b2c3d4e5f60718",
  "chat_id": "chat-e5f60718",
  "analysis": ["客户张伟，41岁，北京\n重疾已有保障30万，推荐额度80万，缺口50万..."],
  "report_url": "https://sspuat.taikang.com/...完整分析报告页"
}
```

`analysis` 为分析文本列表；`report_url` 可直接发给用户点击打开（可能为 null，为空时不输出该字段）。

### 3. taikang_query_schedule

用途：查询当前员工在指定日期范围内的日程（客户生日提醒、客户拜访、续保提醒等）。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| start_date | string | 是 | 无 | 开始日期，格式 YYYY-MM-DD |
| end_date | string | 是 | 无 | 结束日期，格式 YYYY-MM-DD，范围建议不超过 31 天 |

调用示例（用户问"今天/本周"时先算出具体日期，本周指周一到周日）：

```json
{"start_date": "2026-10-19", "end_date": "2026-10-25"}
```

返回格式：

```json
{
  "items": [
    {
      "log_id": "log-001",
      "title": "客户生日提醒",
      "type": "客户生日",
      "start_time": "2026-10-19 00:00",
      "end_time": "2026-10-19 23:59",
      "status": "待处理",
      "location": null,
      "customers": [{"name": "张伟", "level": "钻石卡"}]
    }
  ],
  "total": 1
}
```

`status` 为"待处理"或"已完成"；`location`/`level` 可能为 null；开始时间 00:00 且结束时间 23:59 表示全天事项。日期格式错误会被参数校验直接拒绝，修正后重试。

### 4. taikang_query_taixuetang

用途：检索泰学堂知识库（面向代理人的培训课程：获客、展业、条款讲解等）。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| query | string | 是 | 无 | 检索内容，如「获客的方法」「新人展业」 |

调用示例：

```json
{"query": "获客的方法"}
```

返回格式：

```json
{
  "query": "获客的方法",
  "count": 5,
  "hits": [
    {
      "course_code": "000000000008874",
      "course_name": "5.2 新人获客展业实战宝典",
      "chapter_name": "第一章：持续获客的方法",
      "segment_seq": 1,
      "segment_text": "正所谓巧妇难为无米之炊……（课程原文片段）",
      "category": "客户开拓",
      "stage": "一年内新人",
      "score": 0.816765
    }
  ]
}
```

`hits` 按 `score`（0~1）相似度倒序；`segment_text` 为课程原文片段可直接引用；`count` 为 0 表示未命中，建议用户换个表述，不要编造课程内容。

### 5. taikang_query_product_graph

用途：识别提问中的保险产品，返回产品知识图谱（节点 + 关系）；若识别为绿通服务意图则返回知识文案。

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| query | string | 是 | 无 | 用户关于保险产品或绿通服务的原始提问文本 |

调用示例：

```json
{"query": "幸福人生A款和B款有什么区别？"}
```

返回格式（三种形态）：

识别到产品（单个或多个，多产品为合并图数据）：

```json
{
  "query": "用户问题：...\n\n相关产品名称：【泰康幸福人生A款终身年金保险】",
  "greenpass": false,
  "products": ["泰康幸福人生A款终身年金保险"],
  "record_count": 81,
  "nodes": [{"id": 22345, "name": "泰康幸福人生A款终身年金保险", "label": "保险产品", "properties": {"url": "..."}}],
  "relations": [{"from": 22345, "to": 22346, "type": "HAS_CONTRACT_MODIFICATION", "properties": {}}]
}
```

绿通意图：

```json
{"greenpass": true, "content": "泰康绿通服务申请流程：...", "products": [], "nodes": [], "relations": []}
```

未识别到产品：

```json
{"greenpass": false, "products": [], "nodes": [], "relations": [], "record_count": 0}
```

处理约定（必须遵守）：

- `greenpass` 为 true：`content` 即是答案，直接回复，不要二次加工
- `products` 非空：以 `nodes`/`relations` 为知识依据回答；图谱中没有的信息（佣金、IRR 等）回答"暂无数据"，严禁编造
- `products` 为空：回复兜底引导"暂未找到相关产品信息，请提供完整产品名称，如「泰康××保险」"

## 错误场景与恢复

| 错误消息 | 原因 | 恢复方式 |
|---|---|---|
| 请求未携带有效凭证，请先在 WorkBuddy 中连接泰康账号 | 用户未连接/未授权 | 引导用户在连接器上完成连接 |
| 泰康登录已失效，请在 WorkBuddy 中重新连接泰康账号 | 员工授权过期 | 引导重新连接（重新授权后即恢复） |
| 未找到相关记录，或你没有查看权限 | 客户号/姓名错误，或客户不属于当前员工 | 与用户核对信息；不要反复重试 |
| 泰康服务暂时不可用，请稍后重试 | 泰康侧超时/网络/服务异常 | 稍后重试；连续失败则停止并告知用户 |
| 其他中文错误消息 | 泰康系统业务错误（如参数格式错误） | 按消息提示修正参数后重试 |

## 高风险操作确认规则

全部工具均为**只读查询**（readOnly），不涉及任何写操作，无需确认规则。
