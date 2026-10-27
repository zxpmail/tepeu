---
name: tencent-auto-overseas-unattended-oauth
description: "腾讯汽车行业海外无人值守 MCP 连接器（API Key 直连）。对接 iAutomark 智能营销平台，聚焦汽车行业海外场景：潜客线索获取、4S 店 / 经销商线索分配、汽车 AI 客服会话监控、AI 销售 / 客服机器人巡检、SOP / 知识库健康度诊断、跟进任务编排、敏感词审计。连接建立后即可调用 MCP 工具完成海外获客与客服场景的日常运营。"
description_zh: "腾讯汽车行业海外无人值守 MCP 连接器 - 通过 X-API-Key 管理员密钥直连 iAutomark 海外客服助手。聚焦汽车行业：海外潜客线索、4S 店 / 经销商分配、汽车 AI 客服会话监控、AI 销售 / 客服机器人巡检、SOP / 知识库健康度、跟进任务编排、敏感词审计。"
description_en: "Tencent Automotive Overseas Unattended MCP Connector - Bridges to iAutomark overseas customer-service assistant via MCP Streamable HTTP with X-API-Key auth, focused on automotive scenarios: overseas lead capture, 4S/dealer assignment, AI sales / customer-service session monitoring, bot patrol, SOP/knowledge health checks, follow-up task orchestration and sensitive-word auditing."
version: "1.0.1"
author: "Tencent Automotive Industry"
category: automotive
tags:
  - automotive
  - overseas
  - customer-service
  - lead-management
  - ai-agent
  - mcp
  - api-key
---

# 腾讯汽车行业海外无人值守 MCP（开发环境 · API Key 直连）

本连接器对接 iAutomark 海外客服助手的开发环境，使用 **MCP Streamable HTTP + X-API-Key 请求头**鉴权，聚焦**汽车行业海外运营场景**。连接即用，无需浏览器授权流程。

## 使用流程

1. **首次使用**：在连接器管理页点击「连接 / Trust」，在弹出的表单中粘贴 iAutomark 平台签发的 MCP API Key（仅保存在本机 ~/.workbuddy，不上传云端）。
2. **连接成功后**：直接通过 MCP 工具调用即可，无需手动传 `token` / `api_key`。
3. **调用返回 401**：说明密钥已失效或轮换，断开连接后重新填写有效的 API Key。

## 鉴权说明

- **Connector 类型**：MCP Streamable HTTP + X-API-Key 请求头
- **MCP 地址**：`https://iautomark.sdm.qq.com/oversea-assistant-mcp/mcp`
- **认证方式**：请求头 `X-API-Key`（用户连接时自填，WorkBuddy 通过 token 模式自动注入，不经过云端）
- **不是 OAuth 模式**：无需浏览器授权，不走 `Authorization: Bearer` 流程

## 汽车行业工具路由

- **可用工具**：连接建立后由 MCP 服务端动态暴露，具体工具列表在连接建立后通过 `list_tools` 获取
- **线索（汽车潜客）**：
  - 海外潜客列表 / 详情 → `lead_list` / `lead_get_overview`
  - 销售顾问下的线索 → `query_agent_leads`
  - 线索分配 / 状态更新 → `assign_lead` / `update_lead_status`
  - 线索 AI 分析 / 跟进洞察 → `lead_ai_analysis_get` / `lead_followup_insight`
- **机器人（汽车 AI 销售 / 客服）**：
  - 机器人列表 / 配置 / 运行时状态 → `list_bots` / `get_bot_config` / `get_bot_runtime_status`
  - 机器人调用链路追踪 → `bot_trace_get`
  - Agent 列表 / 详情 / 客户关联 / 评分 → `agent_list` / `agent_detail` / `agent_customer_relations` / `agent_quality_scores` / `list_agents`
- **会话（海外 4S 店 / 经销商客服会话）**：
  - 今日 / 全量会话 → `today_sessions` / `session_list` / `session_detail`
  - 人工接管 → `takeover_session`
  - 链路分析 → `trace_session_review` / `trace_timeline` / `trace_lead_assignment` / `trace_optimization_proposals`
- **巡检 / 健康度**：
  - 告警 / 活跃会话 → `patrol_alerts` / `patrol_active_sessions`
  - 渠道健康度 / Bot 配置完整度 / 知识库健康度 / SOP 覆盖率 → `patrol_bot_channel_health` / `patrol_bot_config_completeness` / `patrol_knowledge_health` / `patrol_sop_coverage`
  - 知识缺口 → `knowledge_gaps`
- **运营看板 / 任务**：
  - 运营 KPI / 按模型统计线索 → `operations_kpi` / `stats_leads_by_model`
  - 渠道 / 触达模板 → `channel_list` / `outreach_template_list`
  - 跟进任务列表 / 启停 → `followup_task_list` / `toggle_outreach_task`
- **客服问答 / 反馈**：
  - 热点问题 / 全部问题 → `top_customer_questions` / `list_customer_questions`
  - WorkBuddy 发现清单 / 记录 → `workbuddy_finding_list` / `record_workbuddy_finding`
- **合规敏感词**：
  - 词库 / 命中记录 → `sensitive_word_list` / `sensitive_hits`
  - 新增敏感词 → `add_sensitive_word`

## 参数口径约定

- 以 MCP 服务端返回的工具描述（inputSchema）为准，**不要靠模型记忆编造工具名或参数**
- 涉及汽车行业业务字段（车型、车系、经销商编号、区域、线索阶段、跟进状态）时，严格遵循 iAutomark 平台口径
- 修改线索状态、跟进任务、敏感词等操作具备业务副作用，调用前需与用户确认

## 错误与边界

| 场景 | 处理建议 |
| --- | --- |
| 调用返回 401 | API Key 失效或轮换，引导用户断开连接并重新填写有效密钥 |
| 工具调用 403 | 检查密钥权限范围，必要时联系平台侧 |
| 工具调用 5xx | 检查 iAutomark 平台是否可达，必要时引导用户切到生产环境 |
| 返回空结果 | 检查参数口径（时间范围、车型 / 车系、Agent ID、Lead ID 等），不要编造数据 |
| 工具列表为空 | 确认连接已建立；如仍为空请联系 iAutomark 确认工具注册 |
| `isError: true` | 把错误原文贴给用户，并提示可能的原因（参数缺失、口径错误、服务端异常） |

## 注意事项

- 本 Connector 当前指向**开发环境**（`iautomark.sdm.qq.com`），发布到生产前需更新 `mcp.json` 中的 URL 为生产地址
- API Key 由用户自行填写并保存在本机，连接器包内不含任何真实凭证；密钥轮换时在连接器管理页重新填写即可
- 海外汽车业务涉及合规与汇率口径，海外售价、税费、汇率等数字一律以 MCP 返回为准，禁止用模型常识补数
- 涉及客户隐私数据（手机号、邮箱、地址、试驾偏好）按平台合规要求脱敏展示
