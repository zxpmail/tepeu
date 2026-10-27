---
name: wangke-martech
display_name: 旺小宝·旺客+
display_name_en: Wangke+ Martech
description: 查询旺客+营销系统中当前账号有权访问的租户、项目、客户、经营指标、销售排名和渠道数据。
description_zh: 查询旺客+营销系统中当前账号有权访问的租户、项目、客户、经营指标、销售排名和渠道数据。
description_en: Query authorized Wangke+ tenants, projects, customers, business metrics, sales rankings, and channel data.
version: 2.2.0
author: 旺客+
---

# 旺小宝·旺客+连接器

使用本连接器查询旺客+营销系统中的只读业务数据。连接器不提供客户分配、客户阶段变更、渠道报备、成交、合同、佣金、导出或其他写操作。

## 认证与动态上下文

WorkBuddy 通过 OAuth 2.1 + PKCE 打开浏览器，用户只需使用旺客+账号的手机号和密码登录。登录时不选择租户或项目，OAuth Token 只绑定登录手机号。不得向用户索要账号密码、Access Token、Refresh Token、`client_id` 或 `client_secret`，也不得把凭证作为 Tool 参数传递。

租户和项目是每次业务调用的显式上下文，不固定在 Token 中。用户在同一连接和同一对话中可以切换租户或项目，无需退出、断开连接或重新登录。

执行具体业务查询前必须遵循以下规则：

1. 对话中尚未确定租户和项目时，先调用 `wangke_mcp_list_accessible_tenant_projects`。
2. 用户要求查看权限范围、切换租户、切换项目，或当前项目含义不明确时，重新调用 `wangke_mcp_list_accessible_tenant_projects`。
3. 后续项目级 Tool 必须同时传入同一权限树节点中的 `tenantId` 和 `estateId`。两个 ID 都是十进制字符串，必须原样传递，不能转换为数字。
4. 租户级 Tool 必须传入权限树中的 `tenantId`。不得根据项目名称、历史对话或其他租户的项目猜测 ID。
5. 项目名称在全部租户中只匹配一项时，可以使用该项目所属节点的 `tenantId + estateId`。存在跨租户同名项目或多个候选时，向用户展示“租户名称 / 项目名称”候选并请求确认。
6. 用户要求切换项目时，放弃此前选定的租户项目组合，按最新权限树和用户选择使用新的 `tenantId + estateId`。
7. `userId` 由 gateway 从权限树租户节点取得。不得向用户展示、询问、推断或作为 Tool 参数传递 `userId`。
8. 权限树在服务端缓存 30 分钟。管理员新增或减少权限后，结果最多可能延迟 30 分钟生效；重新 OAuth 登录不会主动清除此缓存。

## 空权限处理

`wangke_mcp_list_accessible_tenant_projects` 的结构化结果包含 `status`、`message`、`tenants` 和各租户的 `projects`。必须按下列规则向用户说明，不得把空集合解释为查询异常：

- `status=NO_ACCESSIBLE_TENANT` 或 `tenants=[]`：回复“当前账号暂未配置可访问租户，请联系管理员配置租户权限。”
- `status=NO_ACCESSIBLE_PROJECT`：回复“当前账号具有租户权限，但暂时没有可查询的项目，请联系管理员配置项目权限。”
- 单个租户的 `projects=[]`：回复“你可以访问该租户，但该租户下暂时没有可访问项目。”
- 权限树中不存在用户指定的租户或项目：说明当前账号没有对应权限，并展示实际可选项；不得尝试绕过校验。

## 通用调用规则

- 所有查询均为只读。用户要求修改、分配、报备、成交或导出数据时，应明确说明当前连接器不支持该操作。
- 不得编造租户 ID、项目 ID、客户 ID、客户机会 ID、外部项目 ID、手机号、日期或统计指标。
- `cusId` 和 `oppId` 必须取自客户查询结果；需要这两个值时先调用 `wangke_mcp_search_customers`。
- `startTime` 和 `endTime` 使用 Unix 毫秒时间戳，且 `startTime` 不得晚于 `endTime`。根据用户时区解释自然语言日期；没有明确时区时按用户当前时区处理。
- `startDate` 和 `endDate` 使用 `yyyy-MM-dd` 格式，且开始日期不得晚于结束日期。
- 查询结果包含脱敏信息时保持脱敏状态，不推断、补全或重复暴露敏感数据。
- 需要翻页时，从 `pageNo: 1` 开始；`pageSize` 默认 10，最大 20。
- Tool 返回参数错误时，根据错误信息修正参数后最多重试一次。认证失效或持续返回未授权时，提示用户在 WorkBuddy 中重新连接；不要反复调用。
- 下游服务错误或数据为空时，如实说明查询失败或未找到数据，不得生成虚构业务结果。

## 推荐流程

### 选择或切换项目

1. 调用 `wangke_mcp_list_accessible_tenant_projects` 获取完整“租户 → 项目”列表。
2. 根据用户提供的租户名、项目名与列表进行精确匹配。
3. 唯一匹配时保存该节点的字符串 `tenantId` 和 `estateId` 作为当前对话上下文。
4. 多个候选时展示“租户名称 / 项目名称”，由用户确认后再调用业务 Tool。
5. 用户提出切换时重复以上流程，不要求重新登录。

### 查询项目业务数据

1. 尚未选择项目时先执行“选择或切换项目”流程。
2. 调用经营总览、日报、销售排名或渠道报备工具，并显式传入已选择的 `tenantId + estateId`。
3. 回答中说明租户、项目、统计区间、指标口径和查询结果；多个数据块不要混淆。

### 查询客户及其记录

1. 尚未选择项目时先执行“选择或切换项目”流程。
2. 调用 `wangke_mcp_search_customers`，传入已选择的 `tenantId + estateId`，并使用客户姓名或完整手机号作为 `keyword`。
3. 出现多条同名记录时，展示脱敏后的候选项并请用户选择，不擅自选择。
4. 查询摘要详情或接访记录时使用完整 11 位手机号，并继续传入同一 `tenantId + estateId`。
5. 查询跟进记录时使用搜索结果中的 `cusId` 和 `oppId`。

### 查询外部项目映射

1. 查看租户下全部映射时调用 `wangke_mcp_list_external_project_mappings`，传入已确认的 `tenantId`。
2. 已知外部项目 ID 时调用 `wangke_mcp_resolve_wangke_project_by_external_project`，传入已确认的 `tenantId` 和真实 `externalProjectId`。
3. 返回多个匹配项时全部展示，并保留 `mappingSource`；`third` 和 `wangke` 是不同来源，不得自行合并为一条。

## Tool 说明

### `wangke_mcp_list_accessible_tenant_projects`

用途：列出当前登录手机号有权访问的所有租户，以及该手机号在每个租户下有权访问的项目。

参数：无。

返回的 `tenantId` 和 `estateId` 是字符串。必须从同一个租户节点选择项目，不能跨节点拼接。

### `wangke_mcp_get_current_context`

用途：确认本次业务调用选择的租户和项目。

参数：`tenantId`、`estateId` 均必填，必须来自同一个权限树节点。

### `wangke_mcp_list_external_project_mappings`

用途：列出指定租户下当前用户可访问项目的外部项目映射。

参数：`tenantId` 必填。

### `wangke_mcp_resolve_wangke_project_by_external_project`

用途：按外部项目 ID 反查指定租户中的旺客+项目 ID。

参数：`tenantId`、`externalProjectId` 均必填。

### 项目级业务 Tool

下列 Tool 均必须传入同一个权限树节点中的字符串 `tenantId` 和 `estateId`：

- `wangke_mcp_search_customers`：另需 `keyword`，可选 `pageNo`、`pageSize`。
- `wangke_mcp_get_customer_detail`：另需完整 11 位 `phone`。
- `wangke_mcp_get_customer_follow_history`：另需来自搜索结果的 `cusId`、`oppId`。
- `wangke_mcp_get_customer_visit_history`：另需完整 11 位 `phone`，可选 `pageNo`、`pageSize`。
- `wangke_mcp_get_project_overview`：另需 Unix 毫秒时间戳 `startTime`、`endTime`。
- `wangke_mcp_get_project_daily_report`：另需 `yyyy-MM-dd` 格式的 `startDate`、`endDate`。
- `wangke_mcp_get_sales_ranking`：另需 `startTime`、`endTime`、`dataType`，可选 `keyword`。
- `wangke_mcp_get_channel_report_summary`：可选 `period`，范围 1～7，默认 1。

`wangke_mcp_get_sales_ranking.dataType` 可选值为 `RESERVATION_NUMBER`、`PURCHASE_NUMBER`、`PURCHASE_PRICE`、`SIGNING_NUMBER`、`SIGNING_PRICE`、`CUSTOMERS_NUMBER`、`MATERIAL_PROMOTIONS_NUMBER`、`MATERIAL_VISITORS_NUMBER`、`PRE_CONVERSION_RATE`、`SUB_CONVERSION_RATE`、`SIGN_CONVERSION_RATE`、`SALE_RECEIVE_NUMBER`。用户没有明确排名指标时先询问，不要猜测。

## 回答要求

- 回答开头先给结论，再说明租户、项目、时间范围和数据依据。
- 多租户、多项目、多客户或多映射结果使用清晰列表展示，避免把不同对象的数据混在一起。
- 金额、数量、比例和日期保持 Tool 返回的原始含义；Tool 未给出口径时不要补充推断口径。
- 结果为空时说明检索条件和当前租户项目上下文，并建议用户核对姓名、手机号、日期范围或重新选择项目。
