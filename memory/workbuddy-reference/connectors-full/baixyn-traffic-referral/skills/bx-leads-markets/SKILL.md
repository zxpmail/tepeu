---
name: bx-leads-markets
display_name: 目标市场建议与填写
display_name_en: Target Market Planning
description: Research suitable countries using the host AI and the saved Baixyn company profile, then save only countries the user wants written through the market update tool. Use for long-term target markets, not search-only country overrides.
description_zh: 由宿主AI根据百型已保存公司资料研究适合的国家，并通过国家更新工具保存用户指定的长期目标市场；不用于仅本次找客的国家覆盖。
description_en: Use host AI research to recommend and update long-term target countries while preserving other company profile fields.
version: 1.0.4
author: 百型智能
---

# 目标市场建议与填写

适用于百型海外获客连接器 1.0.5，默认生产服务 `https://leads.baixyn.com/mcp`。核对本会话工具及 `capabilities_get` 的权限和限制，仅按当前动作读取 @references/tools.md 的对应工具段落。本版沿用2026-10-10核对的prod公开合同；WorkBuddy 安装与运行单独验收，只读可见工具不代表有保存权限。市场研究由宿主 AI 完成，没有市场推荐模型接口。OAuth 过期时通过宿主连接器入口重新授权，不在聊天中索取令牌。


## 业务读写授权

本连接器包含长期市场保存的 `profile:write` 及资料读取的 `profile:read`。配套服务端新授权策略部署后，新 OAuth 请求省略 scope 时会申请当前部署允许的完整业务权限，仍需用户完成原生授权；显式只读、已有授权及刷新不会自动扩权。仅当本次要求保存且缺保存工具或权限时，从宿主连接器入口重新授权并检查 `capabilities_get.scopes/tools`。仅问市场建议不写；已明确要求添加、移除或替换国家时按原指令保存，无新增歧义或冲突就不重复确认。
## 形成建议

1. 调用 `profile_get`，读取全部产品及说明、用途、行业、业务领域、公司身份和已有市场。只问当前国家时读取后回答即可；已明确国家增删改时直接进入保存流程，不额外研究。
2. 结合用户给过的供货模式、价格段、认证、渠道及优先市场；只补会改变结论的关键缺项。资料不全仍可给条件化建议，不能补成事实。
3. 使用宿主实际可用的联网能力核对有关国家的应用场景、买家类型、渠道与进入条件。没有联网能力时标明初步推断；不编造链接或把百型公司数据库数量当市场规模。
4. 输出少量有区分度的国家：适合哪类产品/买家、为何优先、依据与待核实项。政策或行业存在不证明某家公司正在采购；合规认证需针对实际产品核实。

## 保存国家

- 用户仅问建议就不写资料。用户已明确“把这些国家填入/加上/去掉”且工具有权限时直接完成相应窄字段写入，无需再次确认相同指令。
- 调用 `profile_markets_update`，传真实 `baseProfileVersion`、合法国家代码、`operation=replace/add/remove` 和幂等键。不回写旧产品、说明或整份资料。
- “加入法国”使用 add；“移除日本”使用 remove；“目标市场改成美德”使用 replace。含义不清且会删除原有国家时，询问是添加还是替换。
- 调用 `profile_options_get` 取得合法字典及国家上限，以当前返回为准，不自行把“欧洲”等地区词当作国家代码。不要为超过上限自动删别的国家或截断列表；明确要求全球时用 `operation=replace,markets:[]`。
- 没有正式资料时，不编造产品来保存国家；用 `profile_draft_create(baseProfileVersion:0)` 建草稿，`profile_draft_patch` 用 `changes.markets:{operation,values}` 暂存国家，再按用户给定资料补齐真实名称/产品。只在授权完整且必填齐全后 `profile_draft_preview/save`；否则保留草稿并指出缺项，不自行生成产品。
- “这次只找美国”属于找客流程：已有路线草稿时由 `search_scope_set` 修改；直接需求报价则由找客流程把国家写进 `query`，不能给不存在的草稿调用范围工具。不要修改长期国家；用户若同时明确两处均改，分别执行并报告。

## 参数与恢复

创建/写入带 `idempotencyKey`；草稿修改带最新 `expectedDraftVersion`。新增工具成功读取 `data`，`ok:false` 时按 `error.code/nextAction` 处理。完整参数见自有工具参考，国家代码以选项字典为准，不发送国家研究全文代替字段值。

## 回执与冲突

- 成功后给出实际保存国家与版本，明确其它资料未被本次请求修改。国家建议理由是宿主研究，不标成公司已核实事实。
- 网页修改了其它字段时由服务端合并；任意国家操作（replace/add/remove）收到服务端冲突时，展示当前集合和拟写集合，用户选择后保持原 `baseProfileVersion/operation/markets`，添加 `resolution:{conflictRevision,choices:[{conflictId,choice}]}` 并用新幂等键重试。不要通过改成最新基线绕过冲突；国家再次变化时重新展示差异。
- 超时用同键恢复；缺 scope 时说明无法写入而非假称已填。授权过期不从聊天缓存继续修改。
