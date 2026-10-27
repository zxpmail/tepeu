---
name: ruyi-connection
display_name: 如意账号连接
display_name_en: Ruyi Connection
description: Connect to Ruyi with OAuth and recover expired authorization.
description_zh: 登录如意并授权连接，处理授权过期与重新连接。
description_en: Connect to Ruyi with OAuth and recover expired authorization.
version: 1.0.1
author: 明略·秒针 / 如意
---

# 如意连接

## 连接与权限
使用如意 Buddy 内置的 如意 连接器（标识 `ruyi-creative-oauth`）。未连接时引导用户在原生连接器入口登录如意并授权；没有账号可在授权页点击“获取账号”。不索取账号密码、API Key 或 Token，不读取本机凭证配置。

## 执行边界
先读取实际工具清单和 `ruyi_capabilities`；通用创作按 operation 读取实时 inputSchema。作品、素材、音色、积分和模板目录用 `ruyi_read_workspace`，修改用 `ruyi_manage_workspace`；action 和参数先从能力目录的 workspaceActions 查询。仅使用服务器当前声明支持的动作，不能猜接口、字段或将不支持的操作替换成其他付费生成。能力暂不可用时明确说明，不声称已经执行。

仅使用当前用户有权使用的素材、模板、作品及项目。核心提示词、模板配方和受保护如意 Skill 由如意服务端执行，不请求原文、内部任务参数、供应商配置或任意 REST 代理。

## 素材、预算与恢复
上传 Office、文档或 Excel 用 purpose=tool_file；ZIP Skill 用 purpose=skill_zip。工具私有文件返回 assetId，不当作普通素材URL使用；下载链接短时有效，过期重新获取。

用户本次指定附件通过 `ruyi_begin_upload` 上传，用 `ruyi_upload_status` 确認 completed 后再引用。若宿主允许执行 Python，可用 scripts/upload_asset.py 读取该附件，上传凭证通过标准输入提供；否则展示工具返回的 uploadPage，由用户选文件。不要由模型抄写 Base64。

付费操作先预检，展示预计积分和输出，取得本次预算与生成授权后提交一次。批量操作列出每项和总预算。删除、覆盖、分享、恢复版本前展示目标及影响并取得确认。

记录 taskId、jobId、projectId 等真实回执；至少间隔 5 秒并逐步退避查询。超时或 submission_unknown 先查原任务，不自动重提或增加预算。失败项重试需要本次授权，不重复成功项。

交付真实已完成结果及可用下载链接。提交成功、模板封面、某个镜头成功均不等于整项作品完成；说明仍在处理或需要用户操作的步骤。
