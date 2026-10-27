---
name: zhitu-knowledge
description: 通过智途（Zhitu）连接器检索企业知识库、上传与升版文档、管理知识库，并与智途 AI 助理多轮对话。当用户提到"知识库"、"查公司资料/制度/文档"、"上传到知识库"、"更新文档版本"、"问智途助理"等场景时使用。
---

# 智途知识库连接器使用指南

智途是企业级 AI 知识管理平台。本连接器以用户所属企业的身份（Bot Token 鉴权）访问该企业的知识库与 AI 助理，**数据按企业严格隔离**。

## 前置条件

- 用户已在 WorkBuddy 连接表单中填写智途 Bot Token 与用户 ID。
- 所有工具返回 JSON。`status: "error"` 表示失败，需按错误信息处理；`status: "success"` 表示成功。

## 工具总览

| 工具 | 用途 |
|------|------|
| `search_zhitu_knowledge` | 检索企业知识库（最高频） |
| `chat_with_assistant` | 与智途 AI 助理多轮对话（RAG 增强） |
| `list_knowledge_bases` | 列出企业全部知识库 |
| `create_knowledge_base` | 创建企业知识库 |
| `upload_to_personal_kb` | 上传文件到个人知识库 |
| `upload_to_enterprise_kb` | 上传文件到企业知识库 |
| `request_oss_upload_url` | 大文件直传第一步：获取预签名 URL |
| `upload_from_oss` | 大文件直传第三步：确认入库 |
| `upload_temp_attachment` | 上传临时附件（24h 过期，不进知识库） |
| `list_kb_documents` | 列出知识库文档 |
| `supersede_document` | 文档升版（新文件替换旧文档） |
| `download_kb_file` | 下载知识库文档 |
| `check_zhitu_health` | 检查服务可用性 |

## 核心工作流

### 1. 知识库检索（无需确认，直接调用）

```
search_zhitu_knowledge(query="差旅报销标准")
```
- `query`（string，必填）：自然语言问题或关键词，最多 500 字符。
- `user_id`（string，可选）：审计用业务侧用户标识。
- 返回 `results[]`：含 `source`、`doc_name`、`content`、`score`。回答用户时**注明来源文档名**。
- 若返回带 `notice`/`disclaimer`，说明结果含平台通用知识库或网络资源，需向用户声明仅供参考。

### 2. 与智途 AI 助理对话

```
chat_with_assistant(message="总结入职流程", session_id=null, enable_rag=true)
```
- `message`（string，必填）。
- `session_id`（int，可选）：**首次传 null，后续必须传回返回值中的 `session_id` 以保持上下文**。
- `enable_rag`（bool，默认 true）：是否启用知识库检索增强。
- 用户表达"问智途助理/让智途回答"时使用；多轮对话必须维护 session_id。

### 3. 上传文件到知识库

**第一步：确定目标知识库（kb_id 必填，不可省略，系统无默认知识库兜底）**

1. 用户提及名称（如"传到行政文档库"）→ 先调 `list_knowledge_bases()`，用名称模糊匹配（容错个别错字）。
2. 唯一匹配 → 直接使用该 kb_id。
3. 多个近似匹配 → 列出候选让用户选择。
4. 无匹配 → 询问用户：选择现有知识库，还是创建新知识库（确认名称后调 `create_knowledge_base`）。
5. 用户未提及名称 → 调 `list_knowledge_bases()` 展示列表让用户选。

**第二步：选择上传方式（按文件大小）**

| 场景 | 方式 |
|------|------|
| < 10MB 且有文件内容 | `file_content`（base64）+ `file_name` |
| 有可下载 URL | `file_url`（服务端流式下载，10~100MB 适用） |
| > 10MB 大文件 | OSS 直传三步（见下） |

**第三步：确认用途（learn_mode，需询问用户，默认 1）**

- `learn_mode=1`：学习入库，触发 AI 解析+向量化，之后可被 `search_zhitu_knowledge` 检索。
- `learn_mode=0`：仅保存，不学习、不可检索。
- 用户未明确表态时默认 1，但**若文件含敏感内容应主动确认**。

**企业库上传的审核规则**（返回值 `reviewStatus` 会说明）：
- 管理员身份 → 直接入库；员工身份 → 提交后待企业管理员审核。
- 需如实向用户转达审核状态（"已提交，等待管理员审核"）。

### 4. 大文件 OSS 直传（>10MB，三步走）

1. `request_oss_upload_url(file_name="report.pdf")` → 得到 `uploadUrl`、`objectKey`（30 分钟有效）。
2. 用 **HTTP PUT** 把文件内容上传到 `uploadUrl`（无需认证头）。
3. `upload_from_oss(object_key="...", file_name="report.pdf", learn_mode=1, target_kb="enterprise", kb_id=...)` 完成入库。
- `target_kb`：`"enterprise"`（默认）或 `"personal"`；enterprise 时 `kb_id` 必填（解析流程同上）。

### 5. 文档升版

```
supersede_document(old_doc_id=123, kb_id=4, file_content="...", file_name="产品手册v2.pdf")
```
- 用户要求"更新/替换/升版"文档时使用。`old_doc_id` 通过 `list_kb_documents` 获取。
- ⚠ **必须先向用户确认**：管理员直接生效（旧版本自动归档、向量清理，不可逆）；员工上传会进入审核，旧文档在审核通过前保持生效。

## 错误处理

| 错误特征 | 含义与处理 |
|----------|------------|
| `status: "byom_not_configured"` | 企业未配置专属 AI 模型。转达 suggestion 内容：联系企业管理员前往智途平台「设置 → AI 模型配置」绑定模型。**不要反复重试**。 |
| `缺少 X-Bot-Token 请求头` / Token 无效 | 凭证问题。引导用户断开重连连接器，检查 Bot Token 是否正确（企业管理员在智途平台 Bot 管理页创建）。 |
| `kb_id 为空`/上传被拒 | 未解析目标知识库，先走 `list_knowledge_bases` 解析流程。 |
| HTTP 421 | 服务端 Host 校验拒绝，稍后重试或反馈给智途运维。 |
| 超时 | 大文件请改用 OSS 直传三步走；检索类超时可稍后重试。 |

## 红线与边界

- 本连接器**不提供任何资金、支付、合同签署类操作**。用户提出此类需求时，告知请登录智途平台网页端人工操作。
- 企业库上传、创建知识库、文档升版属于写操作：创建/升版前必须向用户复现关键参数并确认。
- 上传前务必按 learn_mode 流程询问用户用途，不要替用户默认选择敏感文件的用途。
- 临时附件（`upload_temp_attachment`）24 小时过期，不要引导用户将其作为长期存储。
