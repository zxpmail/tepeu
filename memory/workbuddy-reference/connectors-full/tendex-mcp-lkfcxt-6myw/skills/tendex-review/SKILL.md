---
name: tendex-review
display_name: "合规审查（意必得 · 智能标书平台）"
description: "用 eChance Tendex 对投标成稿做合规审查，返回资格性 / 符合性 / 废标风险关卡结论、问题清单与评分自评。当用户要检查投标文件是否响应了招标要求时使用。"
description_zh: "对投标成稿做合规审查，给关卡结论与问题清单"
description_en: "Review a bid document for compliance and list the findings"
version: 1.0.0
author: "意必得 · 智能标书平台"
---

# 合规审查

使用连接器「意必得 · 智能标书平台」提供的 MCP 工具，不要自己编造公司或任务。首次使用需在 WorkBuddy 里点「连接」，浏览器会打开 eChance Tendex 的授权页，用 eChance Tendex 账号登录并同意即可；没有账号请联系管理员开通。

## 调用前先向用户确认（缺一不可，不要猜）

1. 招标任务（须已解析完成）——先调 `tendex_projects_list` 让用户选。
2. 审哪份：用户提供的 Word 成稿（docx / doc / wps，一次一份，不收 PDF），还是平台上的写作稿。
3. 写作还没完成时，是否仍审当前这一版（`options.acknowledgeIncomplete`）。
4. 同任务已有审查在跑时，是否排队。

## 步骤

1. 审用户提供的成稿先上传：`tendex_file_upload`（`purpose=REVIEW`，超过 10MB 走 `_begin`/`_part`/`_finish` 分片），记下 `fileId`；审平台写作稿跳过这步。
2. 发起：`tendex_review`（`project` 必填，`files` 给上一步的 `fileId`，`wait` ≤ 20）。
3. 没做完：`tendex_job_get`；问题清单：`tendex_job_result` 带 `section=findings`。

## 结果怎么读

- 先看 `ok` 与 `job.status`：没做完就按 `job.pollAfterMs` 调 `tendex_job_get`，不要重复提交。
- `subject.*.matchLevel` 不是 EXACT / CHOSEN 时，先把匹配到的名称念给用户确认。
- 返回 `SUBJECT_AMBIGUOUS` 时把 `error.candidates` 列给用户选，把选中项的 `choice` 原样传回 `error.field` 指的参数再调一次；`SUBJECT_NOT_FOUND` 时用 `error.accessible` 让用户选，不要自己编名字。
- 全量结果用 `tendex_job_result` 按 `section` 分页取（分区名见 `meta.counts` 的键）。
- 结果文件下载链接 30 分钟有效，过期重新调 `tendex_job_result` 即换发。
- 收到 401 / 授权失效时，告诉用户在 WorkBuddy 里重新「连接」完成授权。
- `data.gates` 是关卡结论、`data.findings` 是问题清单；结论一律以平台返回为准，不要自行改判。

## 注意

- 平台以授权人的身份执行：只看得见授权人看得见的公司与任务，新建任务计入授权人的额度。
- 长任务不要同步等：`wait` 最多 20 秒，超时按作业号轮询，重复提交会被判为并发超限。
- 用户可以随时在平台「开放能力 › 凭证」里撤销这次授权，撤销后本连接器立即失效。
