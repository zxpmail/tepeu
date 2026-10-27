---
name: fatianshi-contract-workflow
description: >
  指导 AI 通过法天使合同库 MCP 完成「搜索模板 → 创建副本 → 勾选条款 → 填写变量 → 下载文件」工作流。
  当用户要求生成合同、起草合同、查找合同模板、根据模板生成 Word/PDF 时使用。
---

# 法天使合同库 · 模板生成合同工作流

## 认证前置条件

- 首次连接时 WorkBuddy 会打开浏览器完成 OAuth 2.1 + PKCE 授权（短信验证码登录）。
- 需要用户具备 `right:template` 会员权益；授权失败或 401/403 时引导用户重新连接连接器。
- 勿在对话或示例中写入真实 Token。

## 适用场景

生成合同 / 起草合同 / 查找合同模板 / 根据模板起草合同 / 导出 Word 或 PDF。

## 工具一览

| 步骤 | 工具名 | 必填参数 | 说明 |
|------|--------|----------|------|
| 1 | `SearchTemplates` | `keyword` 或 `summary` 至少一个 | 混合检索，每页最多 5 条；可用 `page` 翻页 |
| 2 | `CreateCopy` | `templateId` 或 `templateNumber` 至少一个 | 创建副本，记住返回的 `copyId` |
| 3 | `SubmitCopyChecklist` | `copyId`, `checkIdsJson` | 条款勾选，可重复覆盖 |
| 4 | `GetCopyVariables` | `copyId` | 获取待填变量列表 |
| 4 | `SubmitCopyVariables` | `copyId`, `valuesJson` | 提交变量，可多批次追加 |
| 5 | `GenerateCopyFile` | `copyId` | 生成下载链接；`format` 为 `docx`（默认）或 `pdf` |
| 辅助 | `GetCopy` | `copyId` | 查询副本状态 |
| 辅助 | `RenderCopy` | `copyId` | HTML 预览（下载前非必须） |
| 可选 | `PrepareDocumentUpload` | `fileName` | 下载后修订：准备上传会话 |
| 可选 | `ApplyDocumentRevisions` | `fileHandle`, `revisionsJson` | 按原文→修订文替换并返回新下载链接 |

## 推荐调用链路

用户有具体定制需求时：

```
SearchTemplates → CreateCopy → SubmitCopyChecklist（可重复）
  → GetCopyVariables → SubmitCopyVariables（可多批）
  → GenerateCopyFile
```
SubmitCopyChecklist会影响Variables，注意SubmitCopyVariables前需要调用GetCopyVariables


下载后用户提出修改，且 Agent **不具备**本地修订能力时：

```
PrepareDocumentUpload → HTTP PUT 上传原文件字节 → ApplyDocumentRevisions
```

Agent 自身能修订文档时，优先用自身能力，不必走上传修订工具。

## 参数约定

### SearchTemplates

- `keyword`：合同类型、名称、标签等关键词。
- `summary`：语义概括，如「甲公司向乙公司采购办公设备的买卖合同」。
- `page`：从 1 起，默认 1。
- 从返回的 `templates` 中综合 `score`、`summary`、`scenarios`、`type` 筛选；记下 `id` / `number`。

### CreateCopy

- `templateId`（GUID）与 `templateNumber`（编号）双键，至少其一；都提供时优先 `templateId`。
- 成功后务必记住 `copyId`。

### SubmitCopyChecklist

- `checkIdsJson` 必须是 **JSON 数组字符串**，例如 `["1","1_1","1_1_2"]`，不要传对象。
- `choiceType=single` 组内通常只选一个；`multiple` 可选多个。
- 用户有明确需求时协助勾选；无需求时与用户确认后再提交。

### SubmitCopyVariables

- `valuesJson` 为 **JSON 对象字符串**，key 为变量 `id`，例如 `{"partyA":"某某公司"}`。
- `kind=part` 的变量：value 用 `\n` 分隔各段。
- 可只提交部分变量，未提交的保持原值。

### GenerateCopyFile

- 将返回的 `downloadUrl` 交给用户；注意 `expiresAt`（通常约 1 小时）。
- 无需先调用 `RenderCopy`。

### ApplyDocumentRevisions

- `revisionsJson` 示例：`[{"originalText":"甲方应在30日内付款","revisedText":"甲方应在15日内付款"}]`
- 每个 `fileHandle` 只能用一次；多次修订需重新上传最新文档。
- PDF 的 `pdfOutput`：`both`（默认）/ `clean` / `annotated`。

## 返回格式

工具返回可读 YAML，通常包含 `workflow`、`step`、`nextAction` 及业务字段。按 `nextAction` 推进，不要跳步。

## 错误与恢复

| 情况 | 处理 |
|------|------|
| 401 / 授权失效 | 提示用户重新连接连接器完成 OAuth |
| 403 / 权益不足 | 说明需要合同库（template）权益 |
| 校验错误（缺参数等） | 根据错误信息补全参数后重试 |
| 模板不合适 | 用 `nextPage` 翻页或调整 `keyword`/`summary` |
| 下载链接过期 | 再次调用 `GenerateCopyFile` |

## 高风险操作确认

- 生成并分享下载链接前，确认条款勾选与关键变量已与用户对齐。
- 上传用户合同文件做修订前，须征得用户同意。
- 勿在日志或回复中输出完整凭证或敏感隐私字段。
