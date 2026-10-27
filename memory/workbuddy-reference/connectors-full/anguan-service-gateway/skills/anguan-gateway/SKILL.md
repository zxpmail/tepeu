---
name: anguan-gateway
display_name: 安管服务网关
description: 安管服务网关连接器使用说明。接入运输企业安全管理平台后，通过自然语言完成培训下发、公告、风险通知、车辆/驾驶员/证件台账管理。所有写入操作不可撤回，推送前必须经用户明确许可。
description_zh: 安管服务网关连接器使用说明。接入运输企业安全管理平台后，通过自然语言完成培训下发、公告、风险通知、车辆/驾驶员/证件台账管理。所有写入操作不可撤回，推送前必须经用户明确许可。
description_en: Usage guide for the Anguan Service Gateway connector. After connecting the transport safety platform, complete training dispatch, announcements, risk notices, and vehicle/driver/document records through natural language. All write operations are irreversible and require explicit user approval.
version: 1.8.0
author: 安管工匠
---

# 安管服务网关（连接器技能）

接入企业安全管理平台后的能力使用说明。工具清单以 `tools/list` 实时返回为准（随平台版本动态更新），本文件只讲**怎么用对**。

## 前提

- 连接器已连接（用户已在表单填入 `ak_`/`ms_` 密钥）；
- 未连接时提示用户在连接器管理中完成连接，**不要索要或复述密钥明文**（掩码规则：前 6 后 4）。

## 能力速查（以 `tools/list` 实时返回为准）

| 类别 | 工具 | 说明 |
|---|---|---|
| 认证与连接 | authenticate / check_link / get_web_login_url | 握手自检、链路体检、拿网页登录链接 |
| 培训 | create_training / list_trainings / list_training_categories / get_training_progress | 下发学习任务、查完成率与签字 |
| 培训 AI 提交 | plan_training_ai_submit / execute_training_ai_submit / cancel_training_ai_submit / get_training_ai_submit_status | AI 批量生成与提交培训内容，**plan→execute 顺序不可颠倒** |
| 公告 | create_announcement | **广播全员，不可撤回** |
| 风险 | list_risk_notices / create_risk_notice / get_risk_detail | **按车推送隐患通知，不可撤回** |
| 车辆 | query_vehicles / create_vehicle / create_vehicle_document / get_vehicle_documents | 台账建档、证件新增与查询、到期提醒 |
| 驾驶员 | query_drivers / export_driver_roster / create_driver_document / get_driver_documents | 台账、花名册导出、证件管理 |
| 人车关联 | bind_driver_vehicle / unbind_driver_vehicle | 绑定/解绑须先确认车牌与驾驶员姓名 |
| 检查任务 | create_inspection_task / list_inspection_tasks / list_inspection_items / list_inspection_combos / get_inspection_detail | 安全检查任务下发与跟踪 |
| 待办与入企 | get_pending_summary / get_pending_receipts / list_pending_applications / approve_join_application / reject_join_application / get_invite_link | 待办概览、入企申请审批、邀请链接 |
| 体系 | get_dashboard / get_safety_assessment / get_document_reminders / get_document_reminder_details / get_assistant_guidance | 首页概览、安全评分、证照提醒、智能建议 |

> 身份差异：不同身份（企业 / 服务机构）可见工具集由平台按权限自动分流，**以 `tools/list` 实时返回为准**。调用某工具返回 403 或"无权限"时，说明当前身份不具备该能力，如实向用户说明即可，**不要换接口、改参数或反复重试绕行**。

## 铁律（违者即停）

1. **不可撤回的写入操作**（create_announcement / create_risk_notice / create_training 等）：先把内容与对象**完整呈现给用户确认**，拿到明确许可再调用；
2. **只查不推**：查到名单（如证件到期、未完成培训）**只如实汇报**，不诱导、不主动问"要不要推送给他们"；
3. **数据边界**：一家企业的数据不出这家企业；跨企业对比类请求拒绝；
4. **不伪造**：查不到就说查不到，禁止用估算/示例数据填充；
5. **报错翻译**：接口报错翻译成人话，不把原始报错、URL、工具名直接抛给用户；
6. **培训 AI 提交按序执行**：`plan_training_ai_submit` 仅生成方案（无副作用），须把方案完整呈现给用户并取得许可后，方可调用 `execute_training_ai_submit` 真正提交；用户未许可前不得执行提交，也不得跳过 plan 直接执行；
7. **权限失败不绕行**：遇 403 / "无权限" 时如实说明当前身份不具备该权限并停手，禁止换接口、改参数或反复重试绕过权限校验。

## 调用要点

- 参数以各工具 `inputSchema` 为准，必填缺失先问用户，不要猜；
- 车牌/姓名类查询先精确匹配，无结果再提示用户确认写法；
- 列表类工具注意分页参数，汇总前确认是否已取全；
- 单次请求超时 30 秒，超时重试一次仍失败则如实告知并建议稍后再试。
