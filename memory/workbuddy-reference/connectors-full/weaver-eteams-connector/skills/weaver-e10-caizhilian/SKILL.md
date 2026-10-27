---
name: weaver-e10-caizhilian
display_name: 泛微E10采知连知识管理
display_name_en: Weaver E10 Document Management
description: "泛微E10采知连知识管理系统Skill，包含多个功能模块，覆盖文档创建/搜索/编辑/比对、附件管理、文件夹操作、参与人与标签管理、文档共享与权限设置、文件存储/备份/加解密/附件设置、FTP/SFTP文件采集、本地文件上传。"
description_zh: "泛微E10采知连知识管理系统Skill，包含多个功能模块，覆盖文档创建/搜索/编辑/比对、附件管理、文件夹操作、参与人与标签管理、文档共享与权限设置、文件存储/备份/加解密/附件设置、FTP/SFTP文件采集、本地文件上传。"
description_en: "Weaver E10 document management Skill with multiple functional modules covering document CRUD, attachments, folders, sharing, permissions, file settings, FTP/SFTP collection, and file upload."
version: 1.1.33
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
cliHelp: "weaver-work-cli caizhilian --help"
---

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

认证与登录态只能按共享规则通过 `weaver-work-cli auth ...` 判断：本 Skill 不自行发起 HTTP 请求，请求头凭证由 CLI 统一注入，Agent 不手写、不索取、不转存，也不得直接读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。业务入参里出现认证字段会被 CLI 拒绝或剥离。请求头三件套由 CLI 统一注入、缺一不可：Cookie: <完整原始 Cookie 串>（禁止裁剪/去重/改写）、eteamsid: <ETEAMSID>、User-Agent: AgentType=<agentType>,IsAgent=true。完整契约见共享规则「请求头契约」章节。

所有命令通过 `weaver-work-cli --json caizhilian run <operation>` 执行：简单 JSON 优先传 `--input-json '<json>'`，复杂或多行 JSON 先写入 UTF-8 文件再传 `--input <path>`，只有确认当前 shell 能稳定传管道时才使用 `--input -`。**不要写内联脚本包装调用**——自写脚本在中文与 Windows 环境下容易出现编码、转义问题，且脚本报错时无法判断调用是否已生效，容易导致误判或重复提交。调用前先按需读取 references 下对应的文件查参数结构，不要猜字段；**references 是第一信息源**，`weaver-work-cli caizhilian schema` 是 operation、字段和风险等级的合约来源，`weaver-work-cli caizhilian operations` 是按资源分组的能力目录（含 `withheldOperations`）。

命令示例、提示词和临时说明必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、文件上传下载、目录查看或文件比对时，同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。

涉及附件、封面图片、正文文件、本地文件上传或下载落盘的操作，执行前必须提醒用户：文件内容可能被上传到 E10 采知连文档中心或文件服务器、下载内容会写入本地磁盘，并可能进入当前大模型上下文用于理解和处理；必须等待用户明确确认后才继续。

## 路由优先级（先判断是不是采知连文档中心，再选 operation）

采知连是 E10 的文档中心 / 知识库能力，不是通用网盘，也不是流程表单。**只要用户的核心对象是采知连文档、知识库文件夹、文档共享与权限、文档标签/参与人、文档附件与封面、文档比对，就优先使用 `weaver-e10-caizhilian`。**

### 明确归 `weaver-e10-caizhilian` 的高优先级语义

出现以下任一语义时，优先走本 Skill：

- 采知连 / 文档中心 / 知识库 / 公共知识库 / 个人知识库 / 系统知识库
- 文档 / 在线文档 / 文档检索 / 文档详情 / 文档版本 / 建文档 / 写正文 / 换正文 / 改作者
- 文件夹 / 虚拟文件夹 / 目录树 / 文件夹设置 / 默认共享人 / 默认参与人
- 文档权限 / 下载权限 / 复制权限 / 移动权限 / 删除权限 / 新建文档权限 / 新建文件夹权限 / 设置权限 / 按权限反查
- 文档共享 / 共享给某人某部门 / 取消共享 / 共享级别
- 标签 / 公共标签 / 个人标签 / 参与人 / 协作者
- 附件 / 上传附件 / 替换附件 / 下载附件 / 附件转 PDF / 附件历史版本 / 封面 / 封面图
- 文档比对 / 比对链接 / 左右文件对比

**判定规则：** 只要最终动作是对采知连文档或知识库文件夹做查询、创建、编辑、共享、权限设置、标签/参与人维护、附件与封面处理或文档比对，就归 `weaver-e10-caizhilian`。用户处理的是 E10 流程表单、人事、财务、邮件、会议等非文档中心业务时，不使用本 Skill。

## 选哪个命令

| 想做什么 | 命令 | 按需读取 reference |
|---|---|---|
| 查看可用 operation、字段和风险等级 | `caizhilian schema` / `caizhilian operations` | [`agent-entry.md`](references/agent-entry.md) |
| 接口连通性与登录态自检 | `caizhilian health` | [`agent-entry.md`](references/agent-entry.md) |
| 检索文档 / 查文档详情 / 查当前登录人 | `caizhilian search`、`caizhilian get`、`caizhilian run caizhilian.document.search` / `.get`、`caizhilian.user.current` | [`document.md`](references/document.md) |
| 创建文档（HTML / Markdown / Office / 批量上传） | `caizhilian run caizhilian.document.create-html｜create-markdown｜create-office｜create-by-upload.prepare/apply` | [`document.md`](references/document.md) |
| 改文档基础字段 / 改作者 / 存正文 / 替换正文 | `caizhilian run caizhilian.document.update-field｜update-author｜update-html｜replace-content.prepare/apply` | [`document.md`](references/document.md) |
| 建文件夹 / 查虚拟文件夹 / 查文件夹设置 | `caizhilian run caizhilian.folder.create.prepare/apply`、`.search-virtual`、`.get-settings`、`.query-by-name` | [`folder.md`](references/folder.md) |
| 文件夹默认共享与八类权限、按权限反查、同步管理员/维护/下载/删除/复制/新建/移动/默认参与人/默认共享人到子文件夹 | `caizhilian run caizhilian.folder.default-share.*`、`caizhilian.folder.permission.*`、`.query-by-permission`、`.sync-setting`、`.sync-maintain`、`.sync-download`、`.sync-delete`、`.sync-copy`、`.sync-create`、`.sync-move`、`.sync-participant`、`.sync-default-share` | [`folder.md`](references/folder.md) |
| 标签增删查、参与人增删查、文档共享增删查 | `caizhilian run caizhilian.tag.*`、`caizhilian.participant.*`、`caizhilian.share.*` | [`collaboration.md`](references/collaboration.md) |
| 把人名/部门/分部/岗位/角色/客户解析成 ID | `caizhilian run caizhilian.org.search-*`、`caizhilian.user.current` | [`org-browser.md`](references/org-browser.md) |
| 附件上传/替换/下载（含 PDF 版）、封面上传/设置/移除、文档比对 | `caizhilian run caizhilian.attachment.*`、`caizhilian.cover.*`、`caizhilian.compare.*` | [`files.md`](references/files.md) |
| 查附件历史版本（按附件 ID 列出该附件的全部历史版本） | `caizhilian run caizhilian.attachment.list-versions` | [`files.md`](references/files.md) |
| 附件设置（上传最大、格式控制、批量下载等） | `caizhilian run caizhilian.setting-attach.get` / `.set.prepare` / `.set.apply` | [`setting-attach.md`](references/setting-attach.md) |
| 查某个审批流程的附件上传到哪些文件夹（含各附件字段与通用设置的目录，去重合并） | `caizhilian run caizhilian.setting-attach.list-upload-folders` | [`setting-attach.md`](references/setting-attach.md) |
| 按名称搜索审批流程（拿流程 ID，供查附件上传目录用） | `caizhilian run caizhilian.setting-attach.search-workflows` | [`setting-attach.md`](references/setting-attach.md) |
| 备份设置（开关、规则 CRUD、启停） | `caizhilian run caizhilian.setting-backup.get` / `.set.prepare` / `.set.apply` / `.rule.*` | [`setting-backup.md`](references/setting-backup.md) |
| 加解密设置（8 项配置） | `caizhilian run caizhilian.setting-encrypt.get` / `.set.prepare` / `.set.apply` | [`setting-encrypt.md`](references/setting-encrypt.md) |
| 存储设置（实例与规则管理） | `caizhilian run caizhilian.setting-storage.instance.*` / `.rule.*` | [`setting-storage.md`](references/setting-storage.md) |
| 文件采集（FTP/SFTP 任务管理） | `caizhilian run caizhilian.file-collection.task.*` / `.scan.*` / `.run.*` / `.cancel.*` / `.delete.*` | [`file-collection.md`](references/file-collection.md) |
| 新建采集任务 | `caizhilian run caizhilian.file-collection.task.save.prepare/apply` | [`file-collection.md`](references/file-collection.md) |
| 文件上传（本地文件上传到文件服务） | `caizhilian run caizhilian.file-upload.contract` / `.prepare` / `.upload.prepare` / `.upload.apply` | [`file-upload.md`](references/file-upload.md) |
| 任何写操作的确认链、票据报错、写结果不确定 | `<name>.prepare` -> 用户确认 -> `<name>.apply --continuation TOKEN --yes` | [`write-protocol.md`](references/write-protocol.md) |
| 环境列表 / 配置查看 / 重载配置 / 切换环境等未暴露能力 | 无 caizhilian 命令，改用框架命令 | [`withheld.md`](references/withheld.md) |

文件上传支持秒传、普通流式上传和分片流式上传；成功必须有非空文件 ID、大小校验与私有回执。
上传协议的 `lastModified`、`lastModifiedDate` 必须使用相同的整数毫秒值，由 CLI 自动处理；
小数毫秒可能触发 E10 整数解析错误，详见 [`file-upload.md`](references/file-upload.md) 的时间戳规则与失败处理。
认证沿用 `weaver-work-cli` 的共享规则；不执行独立文件上传 Skill 的 bundle 或独立浏览器登录。

处理链：

- 检索并阅读：`caizhilian.document.search` -> 用户选条目 -> `caizhilian.document.get` 取详情
- 建文档：`caizhilian.folder.query-by-name` 取 `folder_id` -> `caizhilian.folder.permission.detail`（需要时确认允许创建的文档类型）-> `caizhilian.document.create-*.prepare` -> 用户确认 -> `.apply`
- 改文档：`caizhilian.document.get` 取现状 -> `caizhilian.document.update-*.prepare` -> 用户确认 -> `.apply` -> 必要时 `.get` 复核
- 共享给某人：`caizhilian.org.search-user`（或 department/subcompany/...）取 `id` -> `caizhilian.share.form` 取 `obj_types[].id` 与 `share_level` 合法值 -> `caizhilian.share.add.prepare` -> 用户确认 -> `.apply`
- 文件夹权限：`caizhilian.folder.permission.form` 取级联参数 -> `caizhilian.folder.permission.list` 看现状 -> `.add` / `.delete` / `.set-auth` 的 prepare -> 用户确认 -> `.apply`
- 附件：`caizhilian.document.get` 或 `caizhilian.compare.list-file-objects` 取 `attachment_id` -> `caizhilian.attachment.upload｜replace.prepare` -> 用户确认 -> `.apply`；下载用 `caizhilian.attachment.download`（需要 PDF 版用 `.download-as-pdf`）
- 附件历史版本：`caizhilian.attachment.list-versions` 直接按 `attachment_id` 查询（`doc_id` 可选）
- 查流程附件上传目录：用户只给流程名称时先 `caizhilian.setting-attach.search-workflows` 拿流程 ID，再 `caizhilian.setting-attach.list-upload-folders` 查目录
- 文档比对：`caizhilian.compare.search-documents` / `.list-file-objects`（本地文件用 `.upload-file`）取左右 `file_id` -> `caizhilian.compare.submit.prepare` -> 用户确认 -> `.apply` -> 把 `compare_link` 交给用户
- 写操作通用：只读定位 -> `prepare` -> 向用户摘要并取得明确确认 -> `apply`（入参与 prepare 逐字一致 + `--continuation` + `--yes`）

## 执行原则（减少误路由、误重试和无效消耗）

### 1) 先拿最小必要信息，再执行

- ID 一律来自查询，不要猜：`doc_id` 来自 `document.search` / `document.get`，`folder_id` 来自 `folder.query-by-name`，人员/部门 ID 来自 `org.search-*`，权限与共享的合法取值来自 `*.form` / `*.list`。
- **参数名以 `schema` 为准，不要凭操作名推断**：同样以 `update-` 开头，标题字段叫 `name`、摘要叫 `abstract_text`、作者叫 `user_id`（不是 `author_id`）。对某个操作的字段或用法没把握时，先看 reference，必要时用 `weaver-work-cli caizhilian schema` 核对，不要换了名字盲试。
- **需要从查询结果里挑的参数，必须让用户选择**：标签要先用 `tag.search` 列出候选、参与人要先用 `org.search-user` 确认是人、共享/参与人条目要来自对应 `list` 的真实条目（`share_entry_id` / 条目 `ids`）。禁止自行从候选里挑一个直接用，也禁止编造 ID。
- 列表默认小页（`page_size=10`，组织浏览器默认 50，比对检索默认 20）；用户没有要求全量时不要自动翻完整列表。
- 用户已给出 `doc_id` 时直接用 `caizhilian get --doc-id <id>`，不要先搜索再过滤。
- 同一轮里已有新鲜查询结果时不要重复查询同一列表或同一详情；`*.form` 的表单结构同轮内复用。

### 2) 大结果只摘要给用户

- CLI 会把完整 JSON envelope 写 stdout；Agent 回复时**不要原样贴完整 JSON**。
- 文档与文件夹列表默认用 Markdown 表格摘要（标题/所在文件夹路径/作者/创建或更新时间），最多先给前 10 条；`doc_id`、`folder_id`、`share_entry_id`、`file_id` 等内部 ID 只作为后续入参，不作为展示主体。
- 权限、共享、参与人、标签列表用表格摘要（对象名称/类型/级别/是否可下载/条目 ID）。
- 超长响应（文档正文 HTML、批量结果）优先落本地文件，回复里给摘要 + 文件路径。
- `warnings` 数组必须原样转述给用户，不要静默丢弃。
- **分页默认只查第一页**：结果还有更多时，明确告诉用户「当前只返回了第一页，还有更多结果」，由用户决定是否继续；用户要求「继续」后再查下一页，不要自动翻页拉全量。返回里的计数可能为 0 或不准确，不要用它推断总量或下结论。

### 3) 已知对象时直达动作

- 已拿到 `doc_id`、`folder_id`、`attachment_id` 或本地文件路径时，直接调用对应 operation。
- 不要默认走 `search -> get -> form -> list -> prepare -> apply` 全链路；对象已明确时应压缩步骤。
- 只读操作可以放心多查，但一次只跑一个文件上传/下载（120 秒超时），不要并发批量传输。

### 4) 错误语义驱动，而不是盲目重试

- 失败后先看进程退出码、`error.type`、`error.subtype` 和 `error.message`。
- **除非错误明确提示可恢复或需要补充参数，否则不要重复刷同一个 operation。**
- 写请求已经发出后遇到结果不确定必须停止；不要把 `.apply` 当成可重试的读操作。
- **错误为 `authentication` 或 `session_expired` 时：立即停止当前操作，不要用示例占位域名或自行猜域名登录，也不要盲目重试。** 先读取共享规则 `../weaver-e10-shared/references/e10-auth-and-session.md`，按其中流程让用户输入 E10 登录域名或选择已有 profile，完成登录后再从 `prepare` 重新开始。
- `continuation_*` / `plan_changed` / `file_changed` 一律重新 prepare，不要试图修改票据或只改 apply 入参。
- **明确的业务错误直接转达，不要深挖**：服务端返回的业务提示（如「该附件不支持转为 PDF」「无该文档的共享权限」）是权威结论，直接转述给用户并给出应对建议；不要翻查源码、写调试脚本或追查原始响应找「为什么」。只有错误提示本身含糊（空 message、`INTERNAL_ERROR`）时才进一步诊断。
- **不要编造失败原因**：不了解真实原因时，直接说明并请用户联系文档负责人或管理员确认；禁止推测平台行为、杜撰因果关系（如「加了附件后权限会变」），也不要替用户断言其本地环境或文件状态。转达时用用户能理解的自然语言，不要把技术字段名、状态码原样抛给用户。
- **调用类错误先核对再重试**：出现未知字段、缺少必填、格式不合法或操作名不存在时，先查对应 reference（必要时用 `caizhilian schema` / `caizhilian operations`）核对字段名、用法与正确的操作名，再用正确参数重试**一次**；禁止换个写法反复盲试。

### 5) 附件、图片和文件解析确认

- 执行 `caizhilian.attachment.upload｜replace`、`caizhilian.attachment.download｜download-as-pdf`、`caizhilian.cover.upload｜set`、`caizhilian.compare.upload-file`、`caizhilian.document.create-office｜create-by-upload｜replace-content`，或任何会读取本地文件、上传文件内容、下载落盘、解析图片/PDF/Office 的步骤前，必须先提醒用户：文件内容可能进入大模型上下文，也可能发送到 E10 采知连文档中心或文件服务器，或写入本地磁盘。
- 用户未明确确认前，不要运行上传、下载或解析命令；确认摘要必须包含文件路径、文件名与大小。
- 下载只在用户指定路径写入，同名文件按 `名称(1).扩展名` 自动改名，不覆盖已有文件。

### 6) 与用户沟通不暴露内部实现

向用户提问或反馈时用业务语言，不要把 CLI 内部细节抛给用户：

- 禁止出现：operation 名称（如 `caizhilian.document.create-html`）、接口路径、入参/返回字段名与取值（如 `has_next`、`page_no`、`total`）、`continuation` 票据、`prepare` / `apply` 等链路术语。
- 文档、文件夹、人员等业务对象与 ID 可以正常提及；ID 只在需要用户核对时展示。
- **呈现登录与环境信息时用准确口径**：账号标识只用账号 ID；租户（组织）名称、部门名称常与账号同名，**不得称为「账号」**，应表述为「租户 X」「部门 Y」。
- 示例：不说「还有更多结果（has_next=true），要翻页吗？」「共 total=128 条，当前 page_no=1」，要说「还有更多结果，需要继续查看吗？」「共找到 128 条记录」。
- 用户说「新建一个 html 文档」时，「html」只是文档类型提示，按对应创建流程处理即可；不要反问用户「用什么方式创建」，也不要告知「我准备走 XX 流程」。
- 写操作向用户摘要确认时，用「要创建/要删除/要共享给谁」这类业务表述，不要出现准备/确认链路术语（如「我先 prepare 一下」）。

### 7) 可选参数按需询问，不打扰用户

- 用户已给出、或可从上下文推断的参数 → 直接传入，不重复询问。
- 操作有默认值的参数 → 直接用默认值，执行后在结果里说明实际取值。
- 确实缺失且无默认值的参数 → 才向用户询问。
- 创建文档时只确认用户关心的业务信息（标题、存放位置、正文来源）；标签、封面、参与人、虚拟文件夹、摘要、关键词、失效日期等可选信息**不逐项盘问**，用户主动提到才处理。

### 8) 只做用户要求的事，不替用户做主

> 用户要的是「按我说的做法完成这件事」，不是「不惜一切代价达成这个结果」。当路径受阻（无权限、类型不符、找不到目标、报错）时，**不要另辟蹊径**——停下如实报告，由用户决定下一步。以下各条都是这一原则的具体化。

- **操作范围以用户要求为界**：用户要的是某个具体动作，不是「达成某个结果」；不得自行扩大为其他写操作（新建文档、复制权限、删除重建、改共享等），哪怕自认为这是「等效」或「顺手帮忙」的。
- **不替用户判断「什么更好」**：不要因为自认为某个做法更完整、更安全、更方便，就替用户决定或「先做了再说」——这类判断属于用户。
- **不可执行时停下报告**：因类型、权限、前置条件不满足而无法执行时，如实说明原因并停止；不要换一种方式继续尝试达成目的。
- **查询结果为空或不符合预期时，不得自行换方法绕行**：禁止改用逐个对象扫描、按命名规律枚举、自写循环或脚本批量调用等自创方式「换一条路」把结果凑出来；也不要据此断言「某接口不支持某能力」。先核对入参（ID 是否来自真实查询、取值是否在枚举内、是否用字符串传 ID），仍不符合预期就如实报告现象并停下，由用户决定下一步。
- **不要把「没查到」说成「没有」**：空结果只表示当前条件下没有匹配，不等于业务上不存在；需要给结论时先和用户确认检索条件或范围。
- **不得自行切换登录身份**：用户当前提供的登录态是唯一合法的操作身份；因权限不足无法完成时，停下如实报告，不得改用其他账号、凭证或身份重试——哪怕知道存在更高权限的账号。
- **遵循用户给出的处理指示**：用户对失败或局限已给出处理方式时（如「提示无权限就行」「不用重试」），严格照做——如实告知即可，不要在此基础上自作主张地尝试解决、反复重试或提出替代方案。
- **建议可以提，执行必须等确认**：可以向用户建议替代做法并说明影响，但必须等用户明确选择后才能执行。
- **权限类 / 类型类失败不要重试**：这类失败是确定性的，重试或绕行都不改变结果。

### 9) 先明确需求，再动手

- **动手前先确认理解一致**：对象、动作、范围、预期结果四个方面是否都清楚——清楚才动手。
- **分辨不清必须澄清**：需求含糊、可能有多种理解、信息不足时，通过对话向用户确认；不要自己挑一种理解就执行。
- **一次问清**：需要澄清时把关键问题一次问全，不要挤牙膏式反复追问。
- **自检信号**：当你心里出现「用户应该是想要……」「这样应该也满足需求」「大概就是这个意思」时——停下，先确认，不要替用户拍板。

## 写操作失败处理：`partial/write_uncertain` 决策树

当创建、编辑、共享、权限、标签、参与人、附件或比对操作返回 `partial/write_uncertain`，或提示网络中断、部分成功、回查失败、回查字段不一致时，按下面规则处理：

1. **先停止盲目重试**，不要连续重复提交相同 `.apply`，也不要复用同一个 `continuation`。
2. 优先从以下角度解释：
   - 写请求可能已到达服务端，但连接在结果确认前中断；
   - 多步写入（`cover.set` = 上传 + 改字段，`document.replace-content` = 上传 + 替换 + 通知，`document.create-by-upload` = 批量上传）可能只完成了前几步；
   - 批量类操作可能部分成功，`data.summary` 与 `warnings` 里已有成功/失败计数；
   - continuation、目标对象、本地文件或登录上下文可能已经变化。
3. 如需确认，只补 **一次** 只读查询（`document.search` / `document.get` / `folder.query-by-name` / `permission.list` / `tag.list` / `participant.list` / `share.list` / `compare.list-file-objects`），不要陷入 query/write 循环。
4. 最终给用户明确结论、已知状态、成功/失败项和下一步人工确认建议；未生效且需要重做时，从 `prepare` 重新开始并再次取得确认。

**特别注意：** 附件上传与文档创建最容易因重复提交造成重复入库，更要严格执行上述规则。

Windows PowerShell：

```powershell
weaver-work-cli caizhilian schema
weaver-work-cli caizhilian operations
weaver-work-cli --json caizhilian health
weaver-work-cli --json caizhilian search --keywords "季度报告" --folder-id 200 --page-size 10
weaver-work-cli --json caizhilian get --doc-id 1234567890
weaver-work-cli --json caizhilian run caizhilian.folder.query-by-name --input-json '{"folder_name":"财务","kb_type":"company"}'
weaver-work-cli --json caizhilian run caizhilian.org.search-user --input-json '{"keyword":"张三"}'
weaver-work-cli --json caizhilian run caizhilian.document.create-markdown.prepare --input-json '{"name":"周报","folder_id":"456","folder_type":"company","file_path":"C:\\data\\weekly.md"}'
weaver-work-cli --json caizhilian run caizhilian.document.create-markdown.apply --input-json '{"name":"周报","folder_id":"456","folder_type":"company","file_path":"C:\\data\\weekly.md"}' --continuation TOKEN --yes
weaver-work-cli --json caizhilian run caizhilian.attachment.download --input-json '{"doc_id":"1234567890","attachment_id":"888","save_path":"C:\\data\\out"}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli caizhilian schema
weaver-work-cli caizhilian operations
weaver-work-cli --json caizhilian health
weaver-work-cli --json caizhilian search --keywords "季度报告" --folder-id 200 --page-size 10
weaver-work-cli --json caizhilian get --doc-id 1234567890
weaver-work-cli --json caizhilian run caizhilian.folder.query-by-name --input-json '{"folder_name":"财务","kb_type":"company"}'
weaver-work-cli --json caizhilian run caizhilian.org.search-user --input-json '{"keyword":"张三"}'
weaver-work-cli --json caizhilian run caizhilian.document.create-markdown.prepare --input-json '{"name":"周报","folder_id":"456","folder_type":"company","file_path":"/tmp/data/weekly.md"}'
weaver-work-cli --json caizhilian run caizhilian.document.create-markdown.apply --input-json '{"name":"周报","folder_id":"456","folder_type":"company","file_path":"/tmp/data/weekly.md"}' --continuation TOKEN --yes
weaver-work-cli --json caizhilian run caizhilian.attachment.download --input-json '{"doc_id":"1234567890","attachment_id":"888","save_path":"/tmp/data/out/"}'
```

## 不在本 skill 范围

- 禁止加载原始接口文档版 Skill 代替 CLI；本 Skill 通过 `weaver-work-cli` 执行业务操作。
- 禁止 curl、fetch、浏览器自动化或直接访问 E10 `/api/doc/**`、`/api/file/**`、`/api/hrm/**`、`/api/crm/**`。
- 禁止读取或复制 CLI runtime 内部 Token、Cookie、ETEAMSID。
- 禁止读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
- 环境列表、配置快照、配置重载、环境切换（源 `list_systems` / `get_loaded_config` / `reload_config` / `switch_config`）不在本模块，改用 `weaver-work-cli config ...` 与 `weaver-work-cli e10 auth ...`，详见 [`withheld.md`](references/withheld.md)。
- 源 Skill 目录内的系统设置（备份/加密/附件全局设置/存储）、文件收集、文件上传等模块已纳入本 CLI。
- 文档版本回滚、文档审批流转、全文检索高级语法、回收站还原等能力当前未纳入 CLI manifest，需要在 E10 页面操作。
- 非文档中心类 E10 能力（流程、人事、财务、邮件、会议等）不由本 Skill 承载。
