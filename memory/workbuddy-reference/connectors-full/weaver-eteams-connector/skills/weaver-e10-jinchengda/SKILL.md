---
name: weaver-e10-jinchengda
display_name: 泛微今承达合同管理
display_name_en: Weaver Jinchengda Contract Management
description: "泛微 E10 今承达合同管理：查询合同类型/模板/条款、引用条款、管理签约主体，并通过确认链发起、保存、提交和催办 12 类合同审批流程。"
description_zh: "泛微今承达合同管理。触发场景：找合同模板或合同条款、查或新建签约主体、发起/保存/提交/催办合同审批、查审批进度与意见。所有能力通过 weaver-work-cli contract 命令执行。"
description_en: "Weaver E10 Jinchengda contract management skill for contract types, templates and clause discovery, clause reference writes, signing subject management, and prepare/apply confirmation chains across 12 contract approval workflows, driven by weaver-work-cli contract."
version: 1.1.1
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
dependencies:
  - weaver-e10-shared
cliHelp: "weaver-work-cli contract --help"
---

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。
认证和登录态只能按共享规则通过 `weaver-work-cli auth ...` 命令判断；禁止直接读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
所有命令通过 `weaver-work-cli --json contract run <operation>` 执行：简单 JSON 优先传 `--input-json '<json>'`，复杂或多行 JSON 先写入 UTF-8 文件再传 `--input <path>`，只有确认当前 shell 能稳定传管道时才使用 `--input -`。调用前先按需读取 references 下对应的文件，查参数结构，不要猜字段；**references 是第一信息源**，`weaver-work-cli contract schema` 是 operation、字段和风险等级的合约来源。
命令示例、提示词和临时说明必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、Python 启动器、文件删除、目录查看或文件比对时，同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。
涉及合同正文、附件、图片、本地文件或远程 URL 文件的操作，执行前必须提醒用户：文件内容可能被上传到 E10 今承达，并可能进入当前大模型上下文用于理解和处理；必须等待用户明确确认后才继续。

## 请求头 / 认证

本技能发起的 API 请求通过 `weaver-work-cli` 执行，CLI 从 E10 登录会话自动注入以下凭证头：

| 请求头 | 值 |
|--------|-----|
| `Cookie` | `<e10-login 返回的完整原始 Cookie 串>`（禁止裁剪/去重/改写） |
| `eteamsid` | `<e10-login 返回的 ETEAMSID>` |
| `User-Agent` | `AgentType=<agentType>,IsAgent=true` |

上述凭证由 `weaver-work-cli auth login --agent_type WorkBuddy` 建立会话后自动注入，业务输入（`--input-json` / `--input`）**不得包含** `userid`、`userId`、`token`、`cookie`、`eteamsid` 等字段，用户 ID 由 CLI 从会话注入。

## 适用场景与触发条件

当用户表达命中以下任一类意图时，优先加载本 skill 并路由到 `weaver-work-cli contract`：

- 关键词：合同类型、合同模板、模板推荐、条款、条款分类、条款标签、条款推荐、条款引用、签约主体、我方签约主体、客户签约主体、供应商签约主体
- 合同审批场景：销售合同、采购合同、协议合同、合同变更、合同解除、合同关闭、合同开票、合同收款、合同付款、履约计划、进度提报、供应商准入
- 动作词：查条款 / 推荐条款 / 引用条款、查或新建签约主体、发起合同、保存合同表单、提交审批、催办、查审批进度、查审批意见

使用时机：最终动作落在今承达合同对象上，且必须通过 CLI 完成查询或写入时。仅查询企业工商资信或做通用 E10 对象操作时不要路由到这里（见 [`contract-disabled-capabilities.md`](references/contract-disabled-capabilities.md)）。

## 路由优先级（先判断是不是今承达合同，再选 operation）

今承达合同管理不是通用 E10 对象。**只要用户的核心对象是合同类型、合同模板、合同条款、条款引用、签约主体，或 12 类合同审批流程，就优先使用 `weaver-e10-jinchengda`。**

### 明确归 `weaver-e10-jinchengda` 的高优先级语义

- 合同类型 / 合同模板 / 模板分类 / 模板详情
- 条款 / 条款分类 / 条款标签 / 条款搜索 / 条款推荐 / 条款引用
- 我方签约主体 / 客户签约主体 / 供应商主体 / 签约主体库
- 销售合同、采购合同、协议、变更、解除、关闭、开票、收款、付款、履约计划、进度提报、供应商准入的合同审批流程
- 发起合同 / 保存合同表单 / 提交合同审批 / 催办 / 审批进度 / 审批意见

**判定规则：** 只要最终动作是对今承达合同做模板条款查询、主体查询、主体新建、条款引用写入、流程发起、表单保存、提交审批或催办，就归 `weaver-e10-jinchengda`。企业资信查询、撤回/退回流程等源资料未给出接口的能力不在范围内（见 [`contract-disabled-capabilities.md`](references/contract-disabled-capabilities.md)）。

## 选哪个命令

| 想做什么 | 命令 | 按需读取 reference |
|---|---|---|
| 查看可用 operation、字段和风险等级 | `contract schema` | [`contract-agent-entry.md`](references/contract-agent-entry.md) |
| 查合同类型树 | `contract run contract.type.tree` | [`contract-template-selector.md`](references/contract-template-selector.md) |
| 查模板分类树 / 模板列表 / 模板详情 | `contract run contract.template.tree/list/detail` | [`contract-template-selector.md`](references/contract-template-selector.md) |
| 查条款分类 / 条款列表 / 条款组 / 条款搜索 / 条款标签 | `contract run contract.clause.tree/list/group.list/search/tags` | [`contract-clause-recommender.md`](references/contract-clause-recommender.md) |
| 查合同文档已引用条款 | `contract run contract.clause.reference.get` | [`contract-clause-reference-save.md`](references/contract-clause-reference-save.md) |
| 准备/确认把条款写入合同文档 | `contract run contract.clause.reference.save.prepare/apply` | [`contract-clause-reference-save.md`](references/contract-clause-reference-save.md) |
| 解析 appId / objId / 字段 / 选项 | `contract run contract.subject.app/form/fields/options` | [`contract-signing-subjects.md`](references/contract-signing-subjects.md) |
| 查询签约主体 | `contract run contract.subject.query` | [`contract-signing-subjects.md`](references/contract-signing-subjects.md) |
| 准备/确认新建签约主体 | `contract run contract.subject.create.prepare/apply` | [`contract-subject-create.md`](references/contract-subject-create.md) |
| 解析 workflowId / 表单字段 / 字段选项 | `contract run contract.workflow.query/list/form.info/field.options` | [`contract-workflow-discovery.md`](references/contract-workflow-discovery.md) |
| 准备/确认创建合同流程实例 | `contract run contract.request.create.prepare/apply` | [`contract-request-write.md`](references/contract-request-write.md) |
| 准备/确认保存合同表单 | `contract run contract.request.save.prepare/apply` | [`contract-request-write.md`](references/contract-request-write.md) |
| 准备/确认提交合同审批 | `contract run contract.request.submit.prepare/apply` | [`contract-request-write.md`](references/contract-request-write.md) |
| 准备/确认催办 | `contract run contract.request.supervise.prepare/apply` | [`contract-request-write.md`](references/contract-request-write.md) |
| 查审批状态 / 签字意见 | `contract run contract.request.status/log` | [`contract-request-status.md`](references/contract-request-status.md) |
| 企业资信、撤回/退回等未实现能力 | 无可用命令 | [`contract-disabled-capabilities.md`](references/contract-disabled-capabilities.md) |

处理链：

- 选模板：`contract.type.tree` -> `contract.template.tree`（取 `belongId`）-> `contract.template.list` -> 必要时 `contract.template.detail`
- 推荐条款：`contract.clause.tags`（取 `tagId`）-> `contract.clause.tree`（取 `belongId`）-> `contract.clause.search` -> 展示候选名称、状态与推荐理由，**不伪造"智能评分"**
- 引用条款：`contract.clause.reference.get` 查已引用 -> 用户确认具体条款、合同和文件 -> `contract.clause.reference.save.prepare` -> 用户确认 -> `contract.clause.reference.save.apply` -> 回查 `contract.clause.reference.get`
- 签约主体：`contract.subject.app`（应用 Tag 固定 `合同管理`）-> `contract.subject.form`（按意图选 `我方签约主体库` / `客户签约主体库` / `供应商签约主体库`）-> `contract.subject.fields` / `contract.subject.options` -> `contract.subject.query` 或 `contract.subject.create.prepare/apply`
- 发起合同审批：`contract.workflow.query` 或 `contract.workflow.list` 解析 `workflowId`（无需预先获取活动版本）-> `contract.workflow.form.info` 读字段 -> `contract.workflow.field.options` 把业务名称转成选项 ID -> `contract.request.create.prepare/apply` 取 `requestId` -> `contract.request.save.prepare/apply` -> **第二次确认** -> `contract.request.submit.prepare/apply` -> `contract.request.status` 回读 `flowStatus.code`

## 执行原则（减少误路由、误重试和无效消耗）

### 1) 环境相关 ID 必须运行时解析

- `appId`、`objId`、`formId`、`workflowId`、`nodeId`、人员/选项/业务数据 ID 均为环境相关字符串，必须运行时解析
- 未找到就停止并提示用户配置 Tag 或确认名称；**不使用标准环境 ID 兜底**
- 同名流程返回多条时展示候选请用户选择或加 `status=1` 收敛，**不默认取第一条**
- 名称匹配不到选项时展示候选请用户确认，**不得猜测 optionId**

### 2) HTTP 200 不是业务成功的充分条件

- 本组接口的成功码字段不统一：
  - `/api/ebd/**`：顶层 `code = 0` 或 `code = 200` 均视为成功（实测 E10 10.0.9807.01 返回 `200` 且 `msg = "接口返回成功"`），同时要求 `status` 不为 false、`fail` 不为 true
  - `/api/ebuilder/form/obj/getObjsByTag`、`/api/bs/ebuilder/app/tags/getAppIds`、`/api/ebuilder/common/ds/fields`、`/api/ebuilder/common/ds/options`：顶层 `code = 200` 且 `status` 不为 false
  - `/api/ebuilder/form/dataset/v2/**`：`message.code = "0"`
  - `/api/workflow/**`：`message.errcode = "0"`（`getFieldOption` 上游示例为 `200`，同样视为成功）
  - `/api/workflow/core/flowPage/**`、`/api/workflow/core/form/**`、`/api/ebuilder/flow/form/**`（表单三段式）：**没有 `message` 节点**，只有出现非 0/200 的 `errcode`、或 `fail=true` / `status=false` 才算失败；载荷包在 `data` 里
- CLI 已按路径分组判定；Agent 仍需把"请求成功"与"业务生效"分开报告

### 3) 写操作必须走 prepare -> apply 两段确认

- 创建签约主体、引用条款、开启企业监控、创建/保存合同审批流程前，必须展示目标、关键字段和影响的脱敏摘要并获得明确确认
- **提交合同审批和催办需要与"创建并保存"分开的第二次确认**
- 提交后必须回读 `contract.request.status` 的 `flowStatus.code`（1 审批中 / 3 已归档）才算生效，不能只凭第一步返回就宣称"提交成功"
- 创建流程 ≠ 保存表单 ≠ 提交审批，必须分别报告每一步结果，不得概括为"成功"

### 4) 错误语义驱动，而不是盲目重试

- 失败后先看退出码、`error.type`、`error.subtype` 和 `error.message`
- 写请求超时或断线时不得盲目重试，先用查询接口确认是否已生效
- `submitRequest` 返回 `1200301`（当前 userid 为空）：停止并诊断会话状态，不自动切换接口
- `submitRequest` 返回 `1200306`（服务端必填校验）：解析缺失字段列表，逐字段补全后重试（最多 3 轮），不切换接口
- 错误为 `authentication` 或 `session_expired` 时立即停止，先读取共享规则 `../weaver-e10-shared/references/e10-auth-and-session.md` 让用户完成登录

### 5) 附件、图片和文件解析确认

- 执行涉及合同正文、附件、本地文件或远程 URL 文件的步骤前，必须先提醒用户文件内容可能进入大模型上下文，也可能发送到 E10 今承达；用户未明确确认前不要运行。

## 写操作失败处理：`partial/write_uncertain` 决策树

当引用条款、新建签约主体、创建/保存/提交/催办返回 `partial/write_uncertain`，或提示网络中断、回查失败、回查状态不符时：

1. **先停止盲目重试**，不要连续重复提交相同 `.apply`
2. 优先从以下角度解释：写请求可能已到达服务端但连接在结果确认前中断；写接口已返回成功但回查失败或与预期不一致；continuation、目标对象或登录上下文可能已变化
3. 如需确认，只补 **一次** 只读查询（`contract.request.status`、`contract.clause.reference.get`、`contract.subject.query`），不要陷入 query/write 循环
4. 最终给用户明确结论、已知 `workflow.status`、成功/失败项和下一步人工确认建议
5. 提交审批场景最容易因重复提交造成重复流转，必须严格执行上述规则

Windows PowerShell：

```powershell
weaver-work-cli contract schema
weaver-work-cli --json contract run contract.type.tree
weaver-work-cli --json contract run contract.subject.app --input-json '{"tag":"合同管理"}'
weaver-work-cli --json contract run contract.workflow.query --input-json '{"workflowName":"销售合同起草","status":"1"}'
weaver-work-cli --json contract run contract.request.create.prepare --input-json '{"workflowId":"12345","requestname":"WB-SKILL-TEST 销售合同"}'
weaver-work-cli --json contract run contract.request.create.apply --input-json '{"workflowId":"12345","requestname":"WB-SKILL-TEST 销售合同","continuation":"PREPARE_CONTINUATION","confirm":true}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli contract schema
weaver-work-cli --json contract run contract.type.tree
weaver-work-cli --json contract run contract.subject.app --input-json '{"tag":"合同管理"}'
weaver-work-cli --json contract run contract.workflow.query --input-json '{"workflowName":"销售合同起草","status":"1"}'
weaver-work-cli --json contract run contract.request.create.prepare --input-json '{"workflowId":"12345","requestname":"WB-SKILL-TEST 销售合同"}'
weaver-work-cli --json contract run contract.request.create.apply --input-json '{"workflowId":"12345","requestname":"WB-SKILL-TEST 销售合同","continuation":"PREPARE_CONTINUATION","confirm":true}'
```

## 不在本 skill 范围

- 禁止绕过本 skill 的命令面自行复刻旧版「今承达」接口文档里的裸调用，所有能力必须走 `weaver-work-cli contract`。
- 禁止 curl、fetch、浏览器自动化或直接访问 E10 `/api/ebd/**`、`/api/ebuilder/**`、`/api/workflow/**`。
- 禁止读取或复制 CLI runtime 内部 Token、Cookie、ETEAMSID；业务输入不得携带 `userid`、`token`、`cookie`、`eteamsid` 等字段，用户 ID 由 CLI 从会话注入。
- 禁止读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
- 企业资信查询（CM-018）、企业变更、企业监控：源资料只有索引，子文档缺失，未实现。
- 撤回/退回流程：源资料只提及接口存在，未给出路径与参数，未实现。
