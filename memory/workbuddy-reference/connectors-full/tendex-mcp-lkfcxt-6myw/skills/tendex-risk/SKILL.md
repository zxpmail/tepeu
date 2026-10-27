---
name: tendex-risk
display_name: "风险识别（意必得 · 智能标书平台）"
description: "用 eChance Tendex 比对多份不同投标人的投标文件，找出文件雷同、同源制作、报价规律、人员交叉等异常线索（按《招标投标法实施条例》第四十条六种情形归类），供人工核对。当用户要排查多家投标文件雷同、同源时使用。"
description_zh: "比对多份投标文件，给出异常线索供人工核对"
description_en: "Compare several bid documents and surface anomalies for manual review"
version: 1.0.0
author: "意必得 · 智能标书平台"
---

# 风险识别

使用连接器「意必得 · 智能标书平台」提供的 MCP 工具，不要自己编造公司或任务。首次使用需在 WorkBuddy 里点「连接」，浏览器会打开 eChance Tendex 的授权页，用 eChance Tendex 账号登录并同意即可；没有账号请联系管理员开通。

## 调用前先向用户确认（缺一不可，不要猜）

1. 2～8 份不同投标人的投标文件（docx / doc / pdf）。
2. 关联的招标任务（可选）：挂上后用招标文件做基线对消，误报更少。
3. 本次比对的名称（可选）。

## 步骤

1. 逐个上传投标文件：`tendex_file_upload`（`purpose=RISK`，超过 10MB 走 `_begin`/`_part`/`_finish` 分片），记下 `fileId`。
2. 发起：`tendex_risk`（`files` 给 2～8 个 `fileId`，可带 `project`，`wait` ≤ 20）。
3. 没做完：`tendex_job_get`；线索明细：`tendex_job_result` 带 `section=limbs`。

## 结果怎么读

- 先看 `ok` 与 `job.status`：没做完就按 `job.pollAfterMs` 调 `tendex_job_get`，不要重复提交。
- `subject.*.matchLevel` 不是 EXACT / CHOSEN 时，先把匹配到的名称念给用户确认。
- 返回 `SUBJECT_AMBIGUOUS` 时把 `error.candidates` 列给用户选，把选中项的 `choice` 原样传回 `error.field` 指的参数再调一次；`SUBJECT_NOT_FOUND` 时用 `error.accessible` 让用户选，不要自己编名字。
- 全量结果用 `tendex_job_result` 按 `section` 分页取（分区名见 `meta.counts` 的键）。
- 结果文件下载链接 30 分钟有效，过期重新调 `tendex_job_result` 即换发。
- 收到 401 / 授权失效时，告诉用户在 WorkBuddy 里重新「连接」完成授权。
- `data.limbs` 是线索明细、`data.pairs` 是两两比对结果；线索只供人工核对，不是结论，转述时要带上这句。

## 注意

- 平台以授权人的身份执行：只看得见授权人看得见的公司与任务，新建任务计入授权人的额度。
- 长任务不要同步等：`wait` 最多 20 秒，超时按作业号轮询，重复提交会被判为并发超限。
- 用户可以随时在平台「开放能力 › 凭证」里撤销这次授权，撤销后本连接器立即失效。
