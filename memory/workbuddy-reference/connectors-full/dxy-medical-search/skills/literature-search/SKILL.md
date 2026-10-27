---
name: literature-search
display_name: 文献查询
display_name_en: Literature Search
description: 检索医学文献，返回题录、摘要、DOI、PMID 与来源链接。
description_zh: 根据疾病、药物、干预措施或研究问题检索医学文献。
description_en: Search medical literature by disease, drug, intervention, or research question.
allowed-tools: search_literature
version: 1.0.1
author: 丁香园
user-invocable: false
---

# 文献查询

当用户需要查找、筛选或了解医学研究文献时，使用 `search_literature`。典型问题包括：某疾病的研究进展、某药物的疗效或安全性证据、某干预措施的临床研究，以及含有 DOI、PMID、作者、期刊或发表时间的文献查找请求。

## 连接与认证

使用已连接的“丁香园医学检索”MCP 连接器。首次使用由 WorkBuddy 引导用户在浏览器登录临床决策账号并授权。未连接或授权失效时，引导用户连接或重新授权；不要索取、输出或手工拼接 Token，不通过 shell 或 HTTP 请求替代 MCP 工具。

## 调用规则

1. 根据用户明确的检索意图使用本工具，无需选择场景胶囊。不要因为结果为空、结果不充分或用户顺带提及指南/药品而自动调用其他 Tool。
2. 使用用户原始的自然语言问题作为 `query`。可保留用户明确给出的疾病、药物、研究对象、人群、结局、研究类型、语言或时间范围。
3. 问题明显不完整但仍可检索时，先执行一次查询；只有缺少会显著改变结果范围的信息时才追问，例如同名药物、研究对象或疾病亚型。
4. 一次用户请求只调用一次本工具。不要因排序、摘要不足或结果为空而自动重试。

## 调用示例

调用 `search_literature`，参数如下（仅为检索请求，不代表真实结果）：

```json
{
  "query": "查找 SGLT2 抑制剂治疗心力衰竭的随机对照研究"
}
```

`query` 为必填字符串，无默认值；结果是列表，没有总数或分页字段。

## 结果处理

- 输入仅含必填字符串 `query`，不能为空白；不要添加分页、数量、筛选或客户端 IP 参数。
- 成功结果位于 MCP `content` 的 text 中，内容是 JSON 数组；先检查 `isError`，错误文本不能当作结果解析。
- 仅陈述 Tool 返回的事实。不得虚构文献、作者、期刊、DOI、PMID、研究结论或链接。
- 默认按 MCP 返回顺序展示前 5 条；用户要求时可展示更多返回结果。保留 MCP 返回顺序，不声称“最新”或“最高等级证据”，除非返回字段足以支持该判断。
- 每条结果优先展示：`documentTitle`、`author`、`journalName`、`publishDate`、`doi`、`pmid`、`abstractText`、`url`。字段缺失时省略该字段，不使用推断内容补全。
- `sourceType` 固定为 `medicalLiterature`。`publicationDetails` 可作为出版信息展示，不自行拼接卷期页码。
- `url` 缺失时省略来源链接；发布日期保留返回的文本与精度，不猜测缺失日期。

## 输出格式

先用一句话说明检索主题与结果数量，再按以下格式列出结果：

```markdown
### 文献结果

1. **{documentTitle}**
   - 作者：{author}
   - 期刊：{journalName}
   - 发表日期：{publishDate}
   - DOI：{doi}
   - PMID：{pmid}
   - 摘要：{abstractText}
   - [查看来源]({url})
```

## 边界与异常

- 这是文献检索结果，不等同于临床诊断、处方或个体化治疗建议。
- 若返回空数组，说明“本次未返回文献结果”。当前服务在无匹配、预处理不可用或部分下游失败时均可能返回空数组，不能据此断言不存在匹配文献，也不能确定是服务故障。可建议用户补充疾病、药物通用名、研究人群、干预措施或时间范围。
- 若 Tool 返回授权、额度不足、超时或服务错误，直接说明可识别的错误原因；不要重试、不要臆测结果，也不要调用其他 Tool 作为替代。

## 错误恢复

- 未连接、登录或授权失效：引导用户通过 WorkBuddy 重新连接；令牌刷新由客户端处理，不自行刷新或循环重试。
- `query 不能为空`：补齐非空检索需求，由用户确认后发起新的查询。
- 工具执行失败或超时：说明本次查询失败，用户明确要求再试时再发起一次查询。错误信息不可当作检索结果。
- 不请求其他工具补齐结果。
