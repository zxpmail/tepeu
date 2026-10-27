---
name: tendex-parse
display_name: "标书解析（意必得 · 智能标书平台）"
description: "用 eChance Tendex 解析招标文件，返回招标元数据、资格要求、评分细则、无效标条款、采购需求偏离表等固定字段。当用户给出招标文件（docx / pdf）并要求提炼要求、评分或偏离表时使用。"
description_zh: "解析招标文件，提炼资格要求、评分细则、无效标条款与采购需求偏离表"
description_en: "Parse tender documents into qualification, scoring, rejection clauses and deviation tables"
version: 1.0.0
author: "意必得 · 智能标书平台"
---

# 标书解析

使用连接器「意必得 · 智能标书平台」提供的 MCP 工具，不要自己编造公司或任务。首次使用需在 WorkBuddy 里点「连接」，浏览器会打开 eChance Tendex 的授权页，用 eChance Tendex 账号登录并同意即可；没有账号请联系管理员开通。

## 调用前先向用户确认（缺一不可，不要猜）

1. 招标文件（docx / doc / pdf，可多份）。
2. 投标公司——先调 `tendex_profiles_list` 列出用户可用的公司，让用户选（新建任务必须绑定一家公司）；把 `choice` 原样传给 `bidder`。
3. 招标任务——可以不给（新建任务）；要用已有任务，先调 `tendex_projects_list` 让用户选，把 `choice`（业务编号）传给 `project`。
4. 已有任务时：是直接取最近一次解析结果，还是重新解析（`options.reparse`）。

## 步骤

1. 逐个上传招标文件：`tendex_file_upload`（`name` / `base64` / `purpose=TENDER`，单个 ≤ 10MB），记下 `fileId`；超过 10MB 改走分片：`tendex_file_upload_begin` → 按顺序 `tendex_file_upload_part`（每片 ≤ 5MB）→ `tendex_file_upload_finish`。
2. 发起：`tendex_parse`（`bidder` / `files` / `options`，`wait` ≤ 20）；已有任务改传 `project`，重新解析加 `options.reparse=true`。
3. 没做完：按 `job.pollAfterMs` 调 `tendex_job_get`。
4. 取结果：`tendex_job_result`（可带 `section` 取某个分区的全量并分页）。

## 结果怎么读

- 先看 `ok` 与 `job.status`：没做完就按 `job.pollAfterMs` 调 `tendex_job_get`，不要重复提交。
- `subject.*.matchLevel` 不是 EXACT / CHOSEN 时，先把匹配到的名称念给用户确认。
- 返回 `SUBJECT_AMBIGUOUS` 时把 `error.candidates` 列给用户选，把选中项的 `choice` 原样传回 `error.field` 指的参数再调一次；`SUBJECT_NOT_FOUND` 时用 `error.accessible` 让用户选，不要自己编名字。
- 全量结果用 `tendex_job_result` 按 `section` 分页取（分区名见 `meta.counts` 的键）。
- 结果文件下载链接 30 分钟有效，过期重新调 `tendex_job_result` 即换发。
- 收到 401 / 授权失效时，告诉用户在 WorkBuddy 里重新「连接」完成授权。
- 常用分区：`qualification` 资格要求、`scoring` 评分细则、`rejection` 无效标条款、`deviation` 采购需求偏离表、`metadata` 招标元数据。

## 注意

- 平台以授权人的身份执行：只看得见授权人看得见的公司与任务，新建任务计入授权人的额度。
- 长任务不要同步等：`wait` 最多 20 秒，超时按作业号轮询，重复提交会被判为并发超限。
- 用户可以随时在平台「开放能力 › 凭证」里撤销这次授权，撤销后本连接器立即失效。
