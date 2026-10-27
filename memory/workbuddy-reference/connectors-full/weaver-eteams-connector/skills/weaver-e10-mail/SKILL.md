---
name: weaver-e10-mail
display_name: 泛微E10邮件
display_name_en: Weaver E10 Mail
description: "泛微 E10 邮件管理：查询邮件、往来邮件、收件人解析、发送/回复/转发/存草稿、标星/已读/待办/移动/逻辑删除、文件夹、联系人/分组、模板、签名、黑名单与附件上传下载。"
description_zh: "泛微 E10 邮件管理。支持邮件列表/详情/正文/未读统计/配置查询，内部人员/部门/外部邮箱收件人解析，发送/回复/转发/存草稿（prepare→apply 确认链），标星、已读、全部已读、待办、移动、逻辑删除、文件夹管理、撤回，联系人及分组、模板、签名（含二维码名片）、黑名单的增删改，以及附件上传（预检→上传→绑定）与批量下载。"
description_en: "Weaver E10 Mail management: list/detail/content/unread-count/config queries, recipient resolution (internal/department/external), send/reply/forward/draft via prepare→apply confirmation chain, star/read-status/read-all/wait/move/logical-delete/folder/recall, contacts and groups, templates, signatures (with QR card), blacklist, and attachment upload/download."
version: 1.0.0
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
dependencies:
  - weaver-e10-shared
cliHelp: "weaver-work-cli mail --help"
---

**CRITICAL — 开始前 MUST 先用 Read 工具读取 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)，其中包含安装、E10 认证、JSON 输出和高风险写入规则。若共享规则不存在、CLI 未安装或读取失败，说明本技能的必需依赖缺失，必须调用宿主提供的提问/确认弹框，向用户说明必须补齐 `weaver-work-cli` 与共享 skill `weaver-e10-shared`（不补齐本技能无法执行）及补齐计划，并把下面两条待执行命令原样完整展示在确认信息中；Agent 不能在未确认时自行安装。用户明确确认后，Agent 必须直接代为按顺序执行且每条仅执行一次，不得要求或引导用户手动执行：① 缺 CLI 时执行 `npm install -g weaver-work-cli`；② 再执行 `weaver-work-cli skills install shared --target-dir <SKILLS_ROOT>` 装回共享 skill，`<SKILLS_ROOT>` 取本 SKILL.md 所在目录的上一级绝对目录（即与本技能同级的 skills 根目录），不要省略 `--target-dir`——省略会装进默认目录，导致本技能依然读不到共享规则。命令均成功后重新读取共享规则；仍读不到就停止并把结果报告用户，不得凭猜测继续。**
**`--is-connector` 判据**：用户明确使用连接器、或上下文要求用连接器完成任务 → 传 `true`；本次任务或上下文直接选择单独技能 → 不传；用户没有明确说明时，按**正在运行的这份 Skill 是哪一级**定——**用户级（条目标 `(user)`）不传，连接器级（标 `(connector)`）传**（同名 Skill 只会运行用户级那一份）。传 `true` 时不要再写 `--profile` / `--base-url`。完整规则见 [`../weaver-e10-shared/SKILL.md`](../weaver-e10-shared/SKILL.md)。

认证和登录态只能按共享规则通过 `weaver-work-cli auth ...` 命令判断；禁止直接读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
所有命令通过 `weaver-work-cli --json mail run <operation>` 执行：简单 JSON 优先传 `--input-json '<json>'`，复杂或多行 JSON 先写入 UTF-8 文件再传 `--input <path>`，只有确认当前 shell 能稳定传管道时才使用 `--input -`。调用前先按需读取 references 下对应的文件，查参数结构，不要猜字段；**references 是第一信息源**，`weaver-work-cli mail schema` 是 operation、字段和风险等级的合约来源。
命令示例、提示词和临时说明必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、Python 启动器、文件删除、目录查看或文件比对时，同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。
涉及邮件附件、图片、本地文件、远程 URL 文件或邮件正文解析的操作，执行前必须提醒用户：文件内容可能被上传到 E10 邮件系统，并可能进入当前大模型上下文用于理解和处理；必须等待用户明确确认后才继续。

# 泛微E10邮件

## 认证与请求头契约

登录与会话由 `weaver-e10-shared` 共享规则和 `weaver-work-cli auth ...` 命令体系统一托管。业务 Skill 不自建登录流程，不索取、打印或转存任何凭证。

## 路由优先级（先判断是不是 E10 邮件，再选 operation）

只要用户的核心对象是泛微 E10 邮件（收件箱/发件箱/草稿/已删除/垃圾箱/待办邮件、邮件正文、附件、签名、模板、联系人、黑名单、往来邮件），就优先使用 `weaver-e10-mail`。

### 明确归 `weaver-e10-mail` 的高优先级语义

出现以下任一语义时，优先走本 Skill：

- 邮件 / 收件箱 / 发件箱 / 草稿箱 / 已删除 / 垃圾箱 / 待办邮件
- 查看邮件、看正文、看附件、未读统计、邮件配置
- 发邮件 / 回复 / 转发 / 存草稿 / 定时发送
- 标星、标记已读/未读、全部已读、待办提醒、移动邮件、删除邮件、撤回邮件
- 文件夹新建/删除
- 联系人、联系人分组、邮件模板、签名、二维码名片、黑名单
- 附件上传、附件下载、附件中心

**判定规则：** 只要最终动作是对 E10 邮件做查询、查看、发送、解析收件人、标记、移动、删除、文件夹、联系人、模板、签名、黑名单或附件处理，就归 `weaver-e10-mail`。只有当用户处理的是非邮件类 E10 业务（如招聘、薪酬、业票通）或尚未封装的邮件能力时，才不使用本 Skill。

## 选哪个命令
| 想做什么 | 命令 | 按需读取 reference |
|---|---|---|
| 查看可用 operation、字段和风险等级 | `mail schema` | [`mail-agent-entry.md`](references/mail-agent-entry.md) |
| 查询邮件列表 | `mail run mail.list` | [`mail-read.md`](references/mail-read.md) |
| 往来邮件 | `mail run mail.contact.list` | [`mail-read.md`](references/mail-read.md) |
| 查看邮件详情/正文 | `mail run mail.view.info` / `mail run mail.view.content` | [`mail-read.md`](references/mail-read.md) |
| 未读统计 / 邮件配置 | `mail run mail.unread.count` / `mail run mail.config.get` | [`mail-read.md`](references/mail-read.md) |
| 收件人解析（内部/部门/外部） | `mail run mail.person.search` / `mail run mail.dep.search` / `mail run mail.address.search` | [`mail-read.md`](references/mail-read.md) |
| 发送/回复/转发/存草稿 | `mail run mail.send.prepare/apply` | [`mail-send.md`](references/mail-send.md) |
| 标星 / 已读 / 全部已读 / 待办 / 移动 / 逻辑删除 / 文件夹 / 撤回 | `mail run mail.star` / `mail run mail.read.status` / `mail run mail.read.all` / `mail run mail.wait.add` / `mail run mail.move.folder` / `mail run mail.delete.move` / `mail run mail.folder.create` / `mail run mail.folder.delete` / `mail run mail.recall` | [`mail-manage.md`](references/mail-manage.md) |
| 联系人 / 分组 | `mail run mail.contacts.*` | [`mail-contacts.md`](references/mail-contacts.md) |
| 模板 | `mail run mail.template.*` | [`mail-template.md`](references/mail-template.md) |
| 签名 / 二维码名片 | `mail run mail.sign.*` | [`mail-sign.md`](references/mail-sign.md) |
| 黑名单 | `mail run mail.blacklist.*` | [`mail-blacklist.md`](references/mail-blacklist.md) |
| 附件上传 / 下载 / 附件中心 | `mail run mail.attachment.upload` / `mail run mail.attachment.download` / `mail run mail.attachment.center` | [`mail-attachment.md`](references/mail-attachment.md) |
| 破坏性/缺口操作（已暂缓） | 无可用命令 | [`safety-boundaries.md`](references/safety-boundaries.md) |

处理链：

- 查邮件：`mail.list`（folder/keyword/unread/star/todo/hasFile/date 过滤）-> 必要时 `mail.view.info`（含附件列表）或 `mail.view.content`（正文）
- 收件人解析：先用 `mail.person.search` / `mail.dep.search` / `mail.address.search` 拿到 id/邮箱；发送时可直接在 `mail.send.prepare` 传 `--to`/`--to-dep`/`--to-id`/`--external` 让 CLI 解析，重复姓名会要求确认
- 发送：`mail.send.prepare`（flag: -1 新建 / 1 回复 / 3 转发 / 4 编辑草稿）-> 展示摘要 -> 用户确认 -> `mail.send.apply`（传 continuation + confirm）-> 按返回回执报告
- 标记/移动/删除：`mail.star` / `mail.read.status` / `mail.read.all` / `mail.move.folder` / `mail.delete.move` / `mail.wait.add` / `mail.recall` / `mail.folder.create` / `mail.folder.delete`
- 联系人：先 `mail.contacts.list`/`mail.contacts.group.list` 定位 -> `mail.contacts.save.prepare`（查全量表单 + 输出摘要）-> 用户确认 -> `mail.contacts.save.apply`；删除用 `mail.contacts.delete`
- 模板：`mail.template.list` -> 新增 `mail.template.save.prepare/apply`；编辑 `mail.template.update`（GET 全量 + 保存）；删除 `mail.template.delete`；默认 `mail.template.default`
- 签名：`mail.sign.list` -> 新增 `mail.sign.save.prepare/apply`；编辑 `mail.sign.update`；删除 `mail.sign.delete`；默认 `mail.sign.default`；二维码名片 `mail.sign.qrcode`
- 黑名单：`mail.blacklist.operate.prepare`（operateType: add/delete）-> 用户确认 -> `mail.blacklist.operate.apply`
- 附件：上传 `mail.attachment.upload`（预检→上传→绑定，返回 fileid）；下载 `mail.attachment.download`（ids + output 目录）；附件中心 `mail.attachment.center`

## 执行原则（减少误路由、误重试和无效消耗）

### 1) 先拿最小必要信息，再执行

- 只是查邮件列表时，优先直接用 `mail.list`
- 列表默认先取小页，优先 `page=1`、`size=10`；用户没有要求全量时不要自动翻完整文件夹
- 用户已经给出邮件 id 时，不要先查列表再过滤，直接用 `mail.view.info` / `mail.view.content` / 对应写操作
- 只有需要正文、附件列表或完整字段时，才补 `mail.view.info` / `mail.view.content`

### 1.5) 大结果只摘要给用户

- 当前 CLI 会把完整 JSON envelope 写 stdout；Agent 回复时不要原样贴完整 JSON
- 列表最多先展示最相关的前 10 条，包含 id、主题、收发件人、日期、已读/星标/附件状态
- 需要全量统计时，按 `page`/`size` 分页读取并累计必要字段；说明已读取页数、命中数和是否还有更多
- 详情、附件或调试用完整响应过长时，优先落本地文件并向用户提供摘要和文件路径

### 2) 已知对象时直达动作

- 已拿到邮件 id、联系人 id、模板 id、签名 id、附件 id 或本地文件路径时，优先调用对应 operation
- 同一轮里如果已有足够的新鲜查询结果，不要重复查询同一列表或同一详情
- 不要默认走 `list -> filter -> view -> prepare -> apply` 全链路；对象已明确时应压缩步骤

### 3) 错误语义驱动，而不是盲目重试

- 失败后先看进程退出码、`error.type`、`error.subtype` 和 `error.message`
- **除非错误明确提示可恢复或需要补充参数，否则不要重复刷同一个 operation**
- 写请求已经发出后，遇到结果不确定必须停止；不要把 `.apply` 当成可重试读操作
- **错误为 `authentication`（未登录，如 `E10 auth not found`）或 `session_expired`（登录态失效）时：立即停止当前操作，不要用示例占位域名或自行猜域名登录，也不要盲目重试**。先读取共享规则 `../weaver-e10-shared/references/e10-auth-and-session.md`，按其中流程用宿主提问/弹框让用户输入 E10 登录域名或选择已有 profile，完成登录后再继续原操作。

### 4) 附件、图片和文件解析确认

- 执行 `mail.attachment.upload`、`mail.attachment.download`，或任何会读取本地文件、上传远程 URL 文件、解析图片/PDF 的步骤前，必须先提醒用户文件内容可能进入大模型上下文，也可能发送到 E10 邮件系统。
- 用户未明确确认前，不要运行上传、解析或下载命令。
- 下载附件会落本地 `output` 目录，执行前确认目录与文件名，避免覆盖。

## 写操作失败处理：`partial/write_uncertain` 决策树

当发送、联系人/分组/模板/签名保存、黑名单操作等写操作返回 `partial/write_uncertain`，或提示网络中断、部分成功、回查失败、回查字段不一致时，按下面规则处理：

1. **先停止盲目重试**，不要连续重复提交相同 `.apply`
2. 优先从以下角度解释：
   - 写请求可能已经到达服务端，但连接在结果确认前中断
   - 服务端可能只处理了部分数据
   - 写接口已返回成功，但详情回查失败或与预期不一致
   - continuation、目标对象或登录上下文可能已经变化
3. 如需确认，只补 **一次** 只读查询（例如 `mail.view.info`、列表查询或 `mail.list`），不要陷入 query/write 循环
4. 最终给用户明确结论、已知状态和下一步人工确认建议，而不是继续无意义重试

**特别注意：** 对发送（尤其是回复/转发带原文的）、联系人删除、模板/签名覆盖和黑名单删除场景更要严格执行上述规则；这些场景最容易因重复提交造成重复发送、误删或覆盖用户刚修改的数据。

Windows PowerShell：

```powershell
weaver-work-cli mail schema
weaver-work-cli --json mail run mail.list --input-json '{"folder":"0","page":1,"size":10}'
weaver-work-cli --json mail run mail.view.info --input-json '{"id":"12345"}'
weaver-work-cli --json mail run mail.send.prepare --input-json '{"flag":"-1","to":"张三","subject":"季度汇报","content":"您好，附件为季度汇报。"}'
weaver-work-cli --json mail run mail.send.apply --input-json '{"continuation":"PREPARE_CONTINUATION","confirm":true}'
weaver-work-cli --json mail run mail.star --input-json '{"id":"12345","star":"1"}'
weaver-work-cli --json mail run mail.attachment.upload --input-json '{"file":"./quarterly.pdf","name":"quarterly.pdf"}'
```

macOS/Linux（bash/zsh）：

```bash
weaver-work-cli mail schema
weaver-work-cli --json mail run mail.list --input-json '{"folder":"0","page":1,"size":10}'
weaver-work-cli --json mail run mail.view.info --input-json '{"id":"12345"}'
weaver-work-cli --json mail run mail.send.prepare --input-json '{"flag":"-1","to":"张三","subject":"季度汇报","content":"您好，附件为季度汇报。"}'
weaver-work-cli --json mail run mail.send.apply --input-json '{"continuation":"PREPARE_CONTINUATION","confirm":true}'
weaver-work-cli --json mail run mail.star --input-json '{"id":"12345","star":"1"}'
weaver-work-cli --json mail run mail.attachment.upload --input-json '{"file":"./quarterly.pdf","name":"quarterly.pdf"}'
```

## 不在本 skill 范围

- 禁止加载原始 `weaver-e10-mail` Skill 代替 CLI。
- 禁止 curl、fetch、浏览器自动化或直接访问 E10 `/api/inc/*` 或邮件接口。
- 禁止读取或复制 CLI runtime 内部 Token、Cookie、ETEAMSID。
- 禁止读取、列出、打印或解析用户主目录下的旧 `.e10-cli`、auth、config 或 Keychain 数据。
- 永久删除（`mail.delete.batch`）与重发（`mail.resend`）当前未纳入 CLI manifest，已暂缓实现；如确需执行，请在确认风险后以单独授权方式处理，详见 [`safety-boundaries.md`](references/safety-boundaries.md)。
- 非邮件类 E10 能力（招聘、薪酬、业票通、通用流程编排）不由本 Skill 承载。
