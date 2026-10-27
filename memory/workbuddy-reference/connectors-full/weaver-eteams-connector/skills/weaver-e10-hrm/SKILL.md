---
name: weaver-e10-hrm
display_name: 泛微E10组织管理
display_name_en: Weaver E10 Organization Management
description: 泛微E10组织管理（员工/组织/岗位查询）的 E10 查询能力，通过 weaver-work-cli hrm 命令执行。
description_zh: 泛微E10组织管理（员工/组织/岗位查询）的 E10 查询能力，通过 weaver-work-cli hrm 命令执行。
description_en: Weaver E10 Organization Management capabilities for E10, covering employee, organization and position queries, delivered through the weaver-work-cli hrm command.
version: 1.0.28
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
cliHelp: "weaver-work-cli hrm --help"
---

# 泛微E10组织管理

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

本技能覆盖组织管理三大业务域：**员工（employee）**、**组织（org）**、**岗位（position）**，全部为只读查询，无写操作。代理商查询（`containsDls=true`）属于禁用边界，见 [`references/employee.md`](references/employee.md)。组织域额外提供**多维（虚拟组织维度）解析** `hrm.org.resolve`，见下方「多维解析」。

## 命令入口

```text
weaver-work-cli hrm --help
weaver-work-cli hrm schema
weaver-work-cli --json hrm run <operation> --input -
```

`schema` 是可用能力的唯一事实来源。禁止把本技能或源文档里的接口路径当成可直接调用的地址，也禁止把未出现在 `schema` 中的能力当作可用 operation。

## 认证

所有请求复用 `weaver-work-cli` 托管的 E10 会话，业务输入只描述业务对象和意图。会话凭证（Cookie / ETEAMSID / 业务 Token）由 `weaver-work-cli` 认证体系统一托管获取与保存，本技能不自行登录、不保存、不转发任何凭证，禁止硬编码凭证或使用浏览器自动化登录兜底。禁止读取、列出、打印或解析 `.e10-cli` 等认证目录和文件；认证诊断只能通过 `weaver-work-cli auth` 系列命令、`weaver-work-cli doctor --e10` 和业务命令返回的 JSON 错误完成。

## Reference 路由表

命中任一条件时，执行下一步前读取对应 reference。

| 触发条件 | Reference |
| --- | --- |
| 查员工、工号/手机/邮箱、查下属、部门成员、批量人员、人员详情 | [`references/employee.md`](references/employee.md) |
| 查分部、查部门、组织架构、层级路径、组织详情 | [`references/org.md`](references/org.md) |
| **按「多维名称 + 部门/分部名称」找组织**（多维 / 虚拟组织 / 维度） | [`references/org.md`](references/org.md) 的「多维解析」 |
| 查岗位、岗位详情、批量岗位 | [`references/position.md`](references/position.md) |
| **按部门/分部反查人员**（某部门下有哪些人） | [`references/employee.md`](references/employee.md) 的「按组织反查人员」 |
| **按岗位反查人员**（某岗位下有哪些人） | 本技能不支持，需跨到 `weaver-e10-jucailin`；先读下方「反查人员的能力边界」 |

## 多维解析（`hrm.org.resolve`）：一次调用，禁止自己分两步推理

E10 组织树按**多维**（虚拟组织维度，如「行政组织」「1号维度」「备份维度」）切分，同名部门/分部在不同维度下是**不同节点**；组织浏览接口不传 `companyId` 会**默认落到「行政组织」维度**。所以「按名称找部门/分部」需要先定位多维 id，再带 `companyId` 查组织。

**这段编排已在 CLI 内实现，Agent 只调一次 `hrm.org.resolve`，不要自己先查维度、再查组织。**

```text
weaver-work-cli --json hrm run hrm.org.resolve --input-json '{"virtual":"1号维度","name":"第一团队"}'
```

- 入参：`virtual`（多维名称）+ `name`（部门/分部名称）；`type` 可选（`department`/`subcompany`，**不传时同时检索两者、按部门优先定位**）；歧义候选**默认最多回 20 条**，要一次性看全时传 `maxCandidates`（1~200）。
- 匹配分**三级**，"能不能自动往下走"完全不同，别混为一谈：

| 级别 | 判定 | CLI 行为 |
| --- | --- | --- |
| ① 精确 | 忽略大小写/空白的全等 | 唯一 → 继续；多个 → `ambiguous` |
| ② 包含（模糊） | 名称是关键字子串 | 唯一 → 继续；多个 → `ambiguous` |
| ③ **近似（打字级差异，如「客服维度」→「客户维度」）** | 编辑距离阈值内 | **只回 `suggestions`，`status = not_found`，绝不继续** |

### 🔴 不要替用户做主（三条硬边界，违反即算答错）

1. **禁止自行替换/纠正名称**。用户说「客服维度」，就只能在「客服维度」不存在时**如实告知**；不许把「客户维度」当成用户的意思直接用（这是最典型的越权：看着像就替用户认了）。
2. **禁止在 `ambiguous` 里替用户选**。不许取第一条、不许取「看起来最像的」、不许取「唯一直属根节点的」——一律列表让用户挑。
3. **`not_found` ≠ 换个名字再试**。必须把 `data.message` + `data.suggestions`（近似名称）+ `data.availableVirtuals`（该租户全部多维）原样交给用户，等用户确认；**用户确认后**才用 `virtualId`（维度）/ `orgId`（组织）复调，不要再用名称猜。

> 实例：`{"virtual":"客服维度","name":"云桥测试"}` → `not_found`（`suggestions: [客户维度]`）。正确回复是「本租户没有『客服维度』，最接近的是『客户维度』（id …），要用它继续查『云桥测试』吗？」——**不是**直接返回客户维度下的结果。
> 反过来，③ 之外的 ①② 是 CLI 既定行为（属于系统收敛，不属于你改名），命中了就正常往下走。

- **结果判定只看 `data.status`，不要只看 `ok`**（本操作三种结果都是 `ok: true`）：

| `data.status` | 含义 | Agent 必须做什么 |
| --- | --- | --- |
| `resolved` | 已定位唯一组织 | 用 `data.org`（`id`/`name`/`type`/`fullPath`/`link`）直接回答，用 `[名称](link)` 呈现；**若同时回 `data.alsoMatched`（同名但另一类型的节点，如既有同名部门又有同名分部），必须一并告知用户**，不要只报 `org` |
| `ambiguous` | 精确或包含命中多个 | **停下来问用户**：把 `data.candidates` 以表格（名称 / `fullPath` / link）列出让用户选，不要自行挑一个；候选被截断（`truncated: true`）时先告知总数，用户要全量就用 `maxCandidates` 复调 |
| `not_found` | 名称不存在（含"只有近似名称"的情况） | 原样转述 `data.message`，并把 `data.suggestions`/`data.availableVirtuals` 一并给用户确认；**禁止自行替换名称后重试** |

- 用户确认候选后**复调同一个操作**：把候选的 `id` 作为 `orgId` 传回（若是维度歧义，把维度 `id` 作为 `virtualId` 传回），一次即可定位；**不要再用名称重试猜**。
- **🔴「这个租户有哪些多维」暂不支持**（`hrm.org.virtuals` 已下线，`hrm schema --full` 的 `withheldOperations` 里有声明）：
  - 没有列维度的 operation，**也不许绕**——不要故意传一个不存在的维度名去套 `not_found.availableVirtuals` 来"列清单"，那属于构造假查询。
  - `availableVirtuals` 只用于**纠错**（用户给的名字对不上时，顺手告诉他本租户有哪些维度可参考），维度 id 也只从 `not_found.availableVirtuals` / 维度歧义的 `candidates` 里取。
  - 用户明确问"有哪些多维"时 → 直接回答**该能力暂不支持**，并请其改用「维度名 + 组织名」的方式查询。
- 完整契约、匹配规则与示例见 [`references/org.md`](references/org.md) 的「多维解析」。

## 反查人员的能力边界

"这个部门/岗位下有哪些人"是高频误判点，先按下表判断，**不要默认认为 hrm 整个模块都做不到**：

| 起点 | 支持情况 | 走哪里 |
| --- | --- | --- |
| 部门 / 分部 | ✅ 支持 | `hrm.dept.members`（同模块，**只返回直属成员，不含子部门**） |
| 岗位 | ❌ 不支持反查 | `position.*` 只给 `userCount` 人数，`employee.query` 不支持按岗位过滤；需跨到聚才林通讯录高级搜索 `ehr.hr.contact.search` + `saCondition` |

只有**岗位反查**才需要跨模块。以下护栏**自包含**，跨模块前读完；聚才林技能（`weaver-e10-jucailin`，连接器版 `weaver-e10-jucailin-connector`）的通讯录高级搜索条件文档是补充，不是前置依赖（读不到也必须按本节执行）。

**陷阱 1 · 条件元素漏字段键 → 整个条件被静默丢弃**

`saCondition` 的每个字段值是「条件对象数组」，**元素内部必须再带一个服务端认识的键**（字段同名键，或 `xxx_condition` 算子键）：

| 字段类型 | 正确写法 |
| --- | --- |
| BROWSER（岗位/部门/分部/上级） | `"position": [{ "position": [{ "id": "<id>", "includeSub": true }] }]` |
| SELECT（状态/性别） | `"personnel_status": [{ "personnel_status": ["normal"] }]` |
| INPUT（姓名/工号） | `"username": [{ "username_condition": "in", "username": "杰" }]` |

写成 `"position": [{ "id": "<id>" }]`（元素里只有裸 id）时**不报错、不返回错误码**，服务端直接忽略该条件——看起来"有结果"，其实完全没过滤。

**陷阱 2 · `saCondition` 不继承"默认在职"**

便捷字段路径默认在职；一旦改用 `saCondition` 且不写 `personnel_status`，就是**不限状态（含离职）**。两者叠加就是"无过滤 + 不限状态"。

**结果自检（必做，不得跳过）**

跨模块反查后必须核对 `total`，再决定是否回复用户：

1. 先取该租户的**全量基线 N**：用 `personnel_status: ["all"]`（或空条件）查一次，得到"不限状态总人数"；
2. 条件生效时 `total` 必须**明显小于 N**；
3. **一旦 `total` 等于或接近 N，就判定为"条件被吞"**——不要回复"有这么多结果"，回到上面两条检查条件结构后重查；
4. 岗位反查的合理量级通常是**个位数到几十**（单个岗位不会等于全公司人数）。

> 量级示例（本次实测，随租户不同，**不可当常量**）：全量含离职 N=44853、在职=11976、离职=32692，而 `{"position":[…正确…]}` 得到的真实岗位人数=1。写下这些数字只为说明"条件生效后 total 会掉几个数量级"。

## 跨模块约定（必须先知道）

三个业务域统一走 E10 HRM 内部浏览/详情接口，响应统一 `WeaResult` 契约，已在 CLI 内归一化，但语义差异仍会影响结果解读：

- **响应成功判定**：统一 `code=200`（WeaResult）。业务失败码即使 HTTP 200 也会抛 `api/business_error`；`403` 会抛 `api/permission_denied`（提示联系管理员开启接口开关，不盲目重试）。
- **数据行位置（描述的是上游原始响应，不是 CLI 输出）**：上游列表接口行在 `data.data`（浏览按钮 `browser/data/*` 与岗位批量 `queryListByOrgTree`）；下属按 id 查询（`myDirectSubordinates`/`myIndirectSubordinates`）的行在 `data.page.result`、总数在 `data.page.totalCount`。
  - CLI 已把上述差异归一化：**列表类统一读 envelope 的 `data.rows` + `data.total`，分页参数在 `meta`（current/pageSize）**。不要把上游的 `data.data` / `data.page.result` 当成 CLI 输出路径。
  - ⚠️ **CLI 不会去请求 count 接口**（员工浏览上游的 `data.count` 恒为 0，调 count 也拿不到可靠总数）：除 `subordinate.by-id` 外，`employee.query`/`subordinate.mine`/`dept.members` 的 `data.total` **就是本页行数**，不是真实总数。要真实总数只能按 `current` 逐页翻到 `rows.length < pageSize`；`subordinate.by-id` 的 `total` 来自上游 `page.totalCount`，是真值（并附 `data.page.hasNext/totalPages`）。
- **详情接口**：员工卡片（`card/get`）、组织详情（`organization/getForm`）、岗位详情（`getEditOrAddPostForm`）底层都是 `WeaForm` 单对象。**上游响应**里业务字段在 `data.data`；CLI 会把它**展平上移一层**，因此**最终输出中业务字段直接挂在 envelope 的 `data` 下，不存在 `data.data` 嵌套**（`items`/`groups`/`layout` 等前端表单元数据已丢弃），并附 `link`。
  - **`hrm.employee.detail` 默认只回核心业务字段**（26 键，约完整卡片体积的 1/5；实体数组只留 `{id,name}`、人员数组只留 `{id,name,statusCode,avatar}`、`avatar` 只留 1 个 `url`）。**只有确实要完整卡片时才传 `full: true`** —— 默认别传，它会带回 `password`/`last_android`/`hideFieldList`/`setting`/各类 `*_admin` 等无用元数据，显著撑大上下文。
- **「人员列表」默认只回精简行**：`hrm.subordinate.mine` / `hrm.subordinate.by-id` / `hrm.dept.members` 每行只有 `id`/`name`/`link`（原始行 366B（`mine`/`dept.members`）/ 960B（`by-id`，其中上游 `superior` 对象一项就占 485B），精简后约 116~120B）；`hrm.employee.query` 保留人员信息、每行 6 键（`id`/`name`/`department`/`position`/`statusCode`/`link`，368B → 186B）。需要部门/岗位/电话/上级等完整列时显式传 `full: true`；缺部门岗位信息时，优先对个别人再调 `hrm.employee.detail`（其精简卡片已含 `department`/`position` 的 `{id,name}`），**不要为列表好看就默认开 `full`**。
- **🔴 三套 id 不要混用**：人员行可能出现 `id`（**人员 id**，驼峰 `userId` 与它同值）、`userid`（登录信息 id）、`cid`（`employee.batch` 里叫 `imCid`，IM 联系人 id）。**只有人员 `id` 能用于**卡片链接、`employee.detail`、`employee.batch.ids`、`subordinate.by-id.id`；`userid`/`cid` 分别只用于登录账号与 IM 场景。`employee.query` 默认行只留人员 `id`，另两套仅在 `full: true` 时出现。
- **长整型 id 一律按字符串传递**：人员 id、组织节点 id、岗位 id 都是长整型，避免精度丢失。
- **手机号脱敏**：员工/批量/下属列表返回的 `mobile` 已由 CLI 脱敏（3 位前缀 + `****` + 末 4 位），Agent 直接呈现，不做二次处理。

## 安全边界

- **🔴 无条件禁止全量拉取**：`employee.query`/`org.query`/`position.query` 必须携带至少一个收敛条件，无条件时先询问用户，禁止空条件调用。`position.list` 无 `org`/`name`/`code` 过滤时必须显式 `all=true`。
- **🔴 分页统一约束：`pageSize` 默认 `10`、最大 `100`**。支持 `pageSize` 的操作只有 `employee.query`/`subordinate.mine`/`subordinate.by-id`/`dept.members`/`org.query`/`position.list`；`position.query` 不接受该参数（传入被忽略，固定按 200 返回）。需要更多数据时用 `current` 递增翻页，**禁止靠放大 `pageSize` 一次取回大结果**——大结果会整段进入上下文。`position.list` 自带自动翻页，通常无需显式传 `pageSize`。
  - 说明：服务端**不校验**该上限（实测 `pageSize=500` 会原样返回 500 行，不传 `pageSize` 时服务端按 20 返回），所以这条是**本技能为控制上下文体积定下的自觉约束**。Agent 必须遵守，不得因为"服务端没拦"就放大取值。
- **🔴 `status` 不能单独使用**：`employee.query` 的 `status`（`["normal"]` 在职 / `["6"]` 离职）必须搭配其它条件（keyword/工号/姓名/手机/邮箱）。
- **批量禁止循环单查**：批量查询一律走 `batch`（每批 ≤100，CLI 自动分批）；岗位批量走 `position.list` 一次性返回，禁止按 id 逐个调详情。
- **姓名/工号/名称模糊匹配**：多人命中时必须列出候选让用户选择，不得自动取第一条；`dept.members` 部门重名时报 `dept_ambiguous`，需改用组织 id。`hrm.org.resolve` 不抛歧义错误，而是回 `data.status = ambiguous` + `data.candidates`，同样必须问用户、不得自动取第一条。
- **🔴 禁止自主任意解读名称**：`hrm.org.resolve` 报 `not_found` 时，`data.suggestions`（近似名称）**只是给用户看的提示，不是给你用的参数**——不得据此改名重试、也不得省略「不存在」这一事实直接给结果。凡「名称对不上」一律先回用户，用户确认后再用 `virtualId`/`orgId` 复调。
- **🔴 禁止绕取未暴露能力**：`hrm.employee.dls`（代理商查询）与 `hrm.org.virtuals`（列多维）都在 `hrm schema --full` 的 `withheldOperations` 里声明为不可用；**不得构造假查询（如故意传不存在的维度名）去套内部字段**、也不得编造或绕过。用户问到就如实回答暂不支持。
- **人员 id、组织 id、岗位 id 都是长整型**，一律按字符串传递。
- **代理商查询禁用**：`hrm.employee.dls`（`containsDls=true`）仅泛微自用系统支持，已列为禁用边界，不得编造或绕过。

## 失败处理

- `code=302` 或认证类错误：引导用户执行 `weaver-work-cli auth login`，不要用其他方式排查。
- 业务失败看 stderr JSON 的 `error.type` / `error.subtype` / `error.message`，不要用退出码 `0` 判断成功。
- 常见业务错误：`empty_condition`（空条件拉取被拦截）、`full_pull_blocked`（岗位全量需 `all=true`）、`dept_not_found`/`dept_ambiguous`（组织未找到或重名，改用 id）、`enum_invalid`（枚举取值非法）、`permission_denied`（接口开关未开启）、`virtual_required`/`name_required`（`hrm.org.resolve` 缺多维或多维名称参数）。
- `hrm.org.resolve` 的「没找到 / 命中多个」**不是错误**：命令仍退出 0、`ok: true`，判定以 `data.status` 为准，不要把 `not_found` 当异常重试。

## 平台兼容

命令示例必须同时兼容 Windows 与 macOS/Linux。涉及 JSON stdin 时：

Windows PowerShell：

```powershell
weaver-work-cli --json hrm run hrm.employee.query --input-json '{"keyword":"张三"}'
```

macOS/Linux（bash/zsh）：

```bash
printf '%s
' '{"keyword":"张三"}' | weaver-work-cli --json hrm run hrm.employee.query --input -
```

复杂 JSON 建议保存为 UTF-8 文件后按平台传给 `--input <file>`。
