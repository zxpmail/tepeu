---
name: tendex-write
display_name: "投标写作（意必得 · 智能标书平台）"
description: "用 eChance Tendex 为已解析的招标任务生成投标文件（按模板建稿、撰写并填写），完成后给 Word / PDF 下载链接。当用户要为某个招标任务出投标文件时使用。"
description_zh: "为已解析的招标任务生成投标文件并导出 Word / PDF"
description_en: "Generate and export the bid document for a parsed tender project"
version: 1.0.0
author: "意必得 · 智能标书平台"
---

# 投标写作

使用连接器「意必得 · 智能标书平台」提供的 MCP 工具，不要自己编造公司或任务。首次使用需在 WorkBuddy 里点「连接」，浏览器会打开 eChance Tendex 的授权页，用 eChance Tendex 账号登录并同意即可；没有账号请联系管理员开通。

## 调用前先向用户确认（缺一不可，不要猜）

1. 招标任务（须已解析完成）——先调 `tendex_projects_list` 让用户选。
2. 投标公司——默认用任务绑定的公司；用户要换，用 `tendex_profiles_list` 让用户选。
3. 建稿模板：按招标文件里的响应文件格式（默认），还是用投标模板库里的哪个模板。
4. 模式：full（撰写 + 填写，默认）/ fill（只填写）/ write（只撰写）；已有底稿时是否按模板重建（会清空已写内容）。
5. 投标参数：报价、交货期 / 工期、质保期、投标有效期、交货地点、付款方式、开户信息——没说就沿用任务里已保存的。
6. 人员与材料：授权代表、销售联系人、项目负责人等，以及用哪些证书 / 业绩 / 财务资料（先用 `tendex_profiles_list` 带 `withAssets=true` 看可选项；不说 = 全部）。
7. 导出格式：docx（默认）/ pdf。

## 步骤

1. 发起：`tendex_write`（`project` 必填，`options` 放 `mode` / `fill` / `export`，`wait` ≤ 20）。
2. 整本生成通常要十几分钟以上：按 `job.pollAfterMs` 调 `tendex_job_get` 轮询，中途不要重复提交。
3. 做完后念 `data.fill.report.gaps` 与 `data.progress.blockers` 给用户。
4. 成稿下载链接在 `data.exports`（30 分钟有效）。

## 结果怎么读

- 先看 `ok` 与 `job.status`：没做完就按 `job.pollAfterMs` 调 `tendex_job_get`，不要重复提交。
- `subject.*.matchLevel` 不是 EXACT / CHOSEN 时，先把匹配到的名称念给用户确认。
- 返回 `SUBJECT_AMBIGUOUS` 时把 `error.candidates` 列给用户选，把选中项的 `choice` 原样传回 `error.field` 指的参数再调一次；`SUBJECT_NOT_FOUND` 时用 `error.accessible` 让用户选，不要自己编名字。
- 全量结果用 `tendex_job_result` 按 `section` 分页取（分区名见 `meta.counts` 的键）。
- 结果文件下载链接 30 分钟有效，过期重新调 `tendex_job_result` 即换发。
- 收到 401 / 授权失效时，告诉用户在 WorkBuddy 里重新「连接」完成授权。
- `data.fill.report.gaps` 是缺的资料、`data.progress.blockers` 是卡住的环节，两处都要念给用户；成稿下载链接在 `data.exports`。

## 注意

- 平台以授权人的身份执行：只看得见授权人看得见的公司与任务，新建任务计入授权人的额度。
- 长任务不要同步等：`wait` 最多 20 秒，超时按作业号轮询，重复提交会被判为并发超限。
- 用户可以随时在平台「开放能力 › 凭证」里撤销这次授权，撤销后本连接器立即失效。
