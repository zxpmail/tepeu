---
name: pcteam-skill
description: 筑享云项目与计划协同技能 - 项目查询、进展分析、计划调整、逻辑关系、关键路径与计划变更
version: "1.0.0"
author: "PCTeam"
---

# 筑享云（PCTeam）Skill

筑享云以项目为核心、以计划为主线，通过平台协同连接项目中的人、软件、设备、数据和产业生态，支撑项目全生命周期、全角色、全要素在线协同。
本 Skill 通过筑享云 MCP 连接器（`pcteam`）调用当前已开放的项目与计划协同能力，所有工具以 `mcp__pcteam__<toolName>` 形式调用。

## 鉴权说明

本连接器采用**双重凭证**模型，两层缺一不可：

1. **应用身份（x-api-key）**：已固化在连接器配置中，长期有效，用户无需关心。
2. **用户身份（OAuth 2.1 Bearer）**：标识「当前操作的是哪位用户」，采用标准 OAuth + PKCE。用户首次点击「连接」时，WorkBuddy 会自动打开浏览器，跳转筑享云登录页，用户输入用户名和密码完成授权即可，**无需手动填写任何 Token**。

- 授权成功后 access_token 由 WorkBuddy 安全托管并**自动续期**（refresh_token），用户无感知。
- 若调用返回鉴权失败（如 401 / token 失效），WorkBuddy 会自动重新拉起浏览器授权流程；用户按提示重新登录一次即可。

## 工具分组速览

| 分组 | 工具 | 用途 |
|---|---|---|
| 用户与项目查询 | getUserBaseInfo / getUserProjectList / getUserProjectDefineList / getProjectById / getProjectVariableById | 当前用户、项目清单、项目详情 |
| 计划与任务查询 | getDefineTreeByProjectId / getPlanTaskTree / getPlanProcessFeedBack / getFollowTaskList / getPredecessorTaskList / getTaskRelationList | 计划定义、任务树、反馈、逻辑关系 |
| 组织与字典 | getEpsTree / getGlobalOrgTree / getGlobalOrgUserTree / getOrgByProjectId / getUsersByOrgId / getProjectOrgUserTree / getDictTreeListByTypeCode / getTenantAllDictMaps / getCalendarList / queryCurrencyList / queryAllRole | 名称→ID 解析、字典、日历 |
| 进度计算 | progressCalculate / simulateProgressCalculate | 关键路径、工期模拟 |
| 项目/计划/任务写操作 | addProject / updateProject / deleteProject / addPlanDefine / updatePlanDefine / deletePlanDefine / addTask / updateTask / deleteTask / updateParentId / releaseTask / cancelReleaseTask | 建项目、建计划、改任务、发布 |
| 逻辑关系写操作 | addTaskRelation / updateTaskRelation / delTaskRelation | 建立/修改/删除紧前后续关系 |
| 计划变更 | addTaskPlanTaskChange / updateTaskPlanTaskChange / deleteTaskPlanTaskChange / cancelPlanTaskChange / releasePlanTaskChange / getPlanTaskChangeTree / getTaskChangeInfo | 变更申请、审批前修改、发布变更 |
| 反馈与团队 | addPlanFeedBack / releasePlanFeedBack / cancelReleasePlanFeedback / addProjectTeam / updateProjectTeam / deleteProjectTeam / getProjectTeamById / queryProjectTeamTreeByProjectId / assignProjectCorporationTeam / getProjectCorporationTeam / deleteProjectCorporationTeam / moveProjectCorporationTeam | 进展反馈、项目团队管理 |
| 隐蔽工程与扩展数据 | getConcealedList / getExtdataFormDataVoByEDCode / saveExtdataValues | 隐蔽验收、自定义表单 |
| 其他 | refreshToken / updateProjectVariable | Token 续期、项目变量 |

## 常用工具详解

### getUserProjectList — 获取用户项目清单

查询当前用户权限范围内所有项目（EPS→项目树）。

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| nameOrCode | string | 否 | 项目名称或编码模糊过滤 |

返回：多层树，叶子为 `type: "project"` 节点，含 `id / code / name / startDate / endDate / status.id`。

### getDefineTreeByProjectId — 获取计划定义清单

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| projectId | integer | 是 | 项目 ID（来自 getUserProjectList） |

返回：`[{id, name, status}]`，`status: active` 的计划本可用于查询任务树。

### getPlanTaskTree — 获取计划任务树

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| defineIdList | integer[] | 是 | active 计划定义 ID 数组 |
| taskType | string | 否 | 任务类型过滤，0计划/1任务/2开始里程碑/3完成里程碑/4资源任务，逗号分隔 |

返回节点类型 `nodeType`：`project / define / wbs / task`。任务节点含 `completePct`（0-100）、`completeStatus.id`（aqwc按期完成/cqwc超期完成/aqjxz按期进行中/cqwwc超期未完成/cqwks超期未开始/zcwks正常未开始）、`status.id`（EDIT编制中/RELEASE已发布）、`predecessorLink`（紧前关系数组）。

⚠️ 三个状态字段勿混用：
- `status.id` = 发布状态（EDIT/RELEASE）
- `completeStatus.id` = 执行状态（按期/超期/完成）
- `feedbackStatus.id` = 填报状态（0未开始/1进行中/2已完成）

### progressCalculate — 进度计算（CPM）

| 参数 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| projectId | integer | 是 | 项目 ID |
| defineId | integer | 是 | 计划定义 ID |
| nowTime | string | 否 | 计算基准时间，如 "2026-10-01" |

返回关键路径、各任务 ES/EF/LS/LF/TF/FF。`simulateProgressCalculate` 用于模拟，不写库。

### 逻辑关系写操作

- `addTaskRelation`：新增紧前关系。参数 `predecessorTaskId / followTaskId / relationType(FS/SS/FF/SF) / lagNum(小时，8h=1天)`。
- `updateTaskRelation`：修改关系（需 logicId）。
- `delTaskRelation`：删除关系（需 logicId）。

relationType 含义：FS 完成→开始；SS 开始→开始；FF 完成→完成；SF 开始→完成。lagNum 单位为**小时**。

### 计划变更（先变更后发布）

标准流程：`addTaskPlanTaskChange`（建变更）→ `updateTaskPlanTaskChange`（改内容）→ `releasePlanTaskChange`（发布生效）。撤销用 `cancelPlanTaskChange`，删除未发布变更用 `deleteTaskPlanTaskChange`。查询用 `getPlanTaskChangeTree / getTaskChangeInfo`。

## 常见调用序列

**查询某项目进展：**
1. `getUserProjectList` 按名称找到 projectId
2. `getDefineTreeByProjectId` 取 active defineId
3. `getPlanTaskTree(defineIdList=[...])` 拿任务树
4. 需要关键路径时 `progressCalculate(projectId, defineId)`

**名称解析为 ID（写操作前）：**
- 责任主体名称 → `getOrgByProjectId`（项目已建）或 `getGlobalOrgTree`（项目未建）→ orgId
- 责任人姓名 → `getUsersByOrgId(orgId)` → userId
- 计划类型/级别中文 → `getTenantAllDictMaps` 或 `getDictTreeListByTypeCode`

## 错误处理

- **401 / token 失效**：WorkBuddy 会自动触发重新授权（浏览器登录），引导用户完成一次即可，无需人工干预。
- **HTTP 错误**：保留服务端返回的 `message` 字段原样展示给用户。
- **超时**：默认 60s；大项目任务树查询较慢属正常，不要并发重复调用。

## 注意事项

- 所有写操作（add/update/delete/release）会直接修改线上数据，执行前请向用户确认。
- 进度计算、变更发布为耗时操作，调用后告知用户正在处理。
- 树形数据体量大时，主上下文只做摘要与统计，避免整树粘贴。
