---
name: octic-cli
description: "在 agent（Claude Code / Codex / OpenClaw 等）内直接调用 Octic `octic` CLI 时使用：查看/搜索录音列表、查看详情与转写、读取摘要与脑图、下载音频/转写/摘要/脑图文件、通过 `OCTIC_SHARE_LINK` 环境变量或独立的 `octic share show`/`octic share download` 命令查看或下载他人分享的录音/文件/报告/项目内容。命中词还包括 octic、OcticCli、录音、转写、摘要、脑图、下载、分享链接。不用于与 Octic 录音无关的任务。"
---

# Octic CLI (`octic`)

`octic` 是 Octic 的官方命令行客户端，用于在终端 / agent 环境里查看、检索、下载录音，无需打开网页。本 skill 教 agent 如何**正确调用**已安装的 `octic`；它不授予任何权限——权限只来自用户已登录的凭证。

前提：已在本机安装并可执行 `octic`（`octic --version` 能正常输出）。安装方式见 `octic skill install --help`，或参见根目录 `README.md` 的安装一节。

## 命令总览

```text
octic auth login|status|logout   # 登录 / 查看登录状态 / 登出（撤销并清除本地凭证）
octic rec list|show|transcript|summary|mindmap|download|rename   # 录音只读操作 + 改名（见下文）；rec summary infographic 列出总结内 infographic 块
octic edit transcript|summary|mindmap   # 编辑转写（乐观锁，旧名 transcription 仍可用）/摘要/脑图；edit summary infographic 编辑单个 infographic 块
octic edit name|keywords         # 编辑名称/关键字（走编辑同步体系，--trigger-scene 必传）
octic collection list|show|recordings|files|download|create|rename|add|remove|upload   # 合集（项目）读写下钻
octic artifact list|formats|show|download|rename   # 产物（收藏夹产出物）读写下钻
octic share show|download        # 凭分享链接（或裸 share ID）查看/下载分享资源
octic skill install|export|list-agents   # 把本 skill 装进本地 agent（仅供本机使用，agent 无需调用）
octic upgrade [--check-only]     # 自更新到最新版本（仅供本机使用，agent 无需调用）
octic completion bash|zsh|fish|powershell  # 生成 shell 自动补全脚本（本机手工使用）
```

`skill export` 直接把二进制内嵌的 SKILL.md 原文打到 stdout（或 `--output <file>` 落盘，注意这是输出文件路径、不是全局 `table|json` 格式参数），`skill list-agents` 列出支持的 agent 与默认安装目录。`completion` 输出 shell 补全脚本、`skill export` 输出 SKILL.md 原文，都不走 `{"ok":...}` JSON 信封。`upgrade` 只对官方 release 构建可用（本地 dev 构建会报用法错误）；`--check-only` 只检查是否有新版本、不安装。**SKILL 与二进制是分开维护的**：本机记录过已装 agent 时 `upgrade` 会自动重装一次 SKILL（即使是「已是最新版」也会补一次兜底同步）；自动同步失败或无记录时输出里的 `skillHint` 会给出手动 `octic skill install --agent <agent> --force` 命令。Windows 上替换与同步由后台分离进程完成，若下次运行提示同步失败，同样手动执行 force 安装即可。

## 何时使用 / 何时不用

**应使用**：
- 用户要求列出/搜索录音、查看某条录音详情、读取转写或摘要或脑图、下载音频/转写/摘要/脑图文件；
- 用户要求修改转写错字、重写摘要/脑图、给录音/合集/产物改名、往合集添加或移除录音、上传文件到合集；
- 用户要求查看或修正总结（摘要）里 infographic 信息图的文字（错字/人名/数字）——先 `rec summary infographic` 列块、再 `edit summary infographic` 改指定块；
- 用户要求查看合集（项目）列表/详情/录音/文件，或产物（收藏夹产出物）列表/详情/下载；
- 用户提供了一个 Octic 分享链接，要求查看或下载其中的内容；
- 需要在脚本/CI 里以 `--output json` 方式拉取录音数据做进一步处理。

**不要使用**：
- 与 Octic 录音无关的任务；
- 删除类操作（删录音/删合集/删报告/回收站）——CLI 未提供，走网页端；
- 需要获取"译文"（非原文转写）——CLI 场景下一律返回原文转写，`octic` 不暴露译文字段。

## 认证：`octic auth`

在调用任何 `rec` 命令前需要先 `login` 一次；`OCTIC_TOKEN` **只有 `octic auth login` 能使用**，其余任何命令（包括 `auth status`、`auth logout`、所有 `rec`/`skill` 子命令）一旦检测到它都会直接报用法错误（退出码 2）并拒绝执行——这是为了杜绝”每次调用都直填 token”的旁路，登录态只能来自本地已持久化的凭证（`--token` 也只是 `auth login` 自己的本地 flag）。

`login` 接收的不是长期凭证本身，而是网页端签发的**一次性登录 token**；CLI 会用它换取长期凭证并自动保存在本机，之后所有命令都读这份本地凭证。

**一次性登录 token 从哪里拿**：在网页端或 App 端打开 **「我的」→「机器人设置」→「Octic CLI」**，点击**「复制指令」**按钮即可获得（按钮名在不同端可能略有差异）。`auth logout` 退出登录后重新登录（或换账号、换机器登录）走同一条路：先去该入口重新点一次「复制指令」拿到新的一次性 token，再执行下面的 `login` 命令即可——**每次登录都需要重新获取一次**，token 用过或过期就作废，不能复用旧的。

```bash
# 方式一（一次性 token）：用网页端/App「我的」→「机器人设置」→「Octic CLI」→「复制指令」拿到的一次性登录 token 登录（凭证会自动落盘，仅需一次）
octic auth login --token <one-time-login-token>

# 方式二（设备码流，适合人类终端用户 / 无法交互输入的环境）：
#   默认模式：打印批准页网址后立即退出；用户浏览器打开网址登录并确认后，
#   `auth status` 会自动完成登录（WorkBuddy 等外部工具的对接方式）
octic auth login --device
#   --wait 模式：打印网址后在本命令内阻塞等待，直到批准成功（退出码 0）、
#   5 分钟内无人批准（自动清理，退出码 3）、或用户按 Ctrl-C 中断（保留批准
#   链接，5 分钟内 `auth status` 仍能完成登录）
octic auth login --device --wait

# 查看当前登录状态（凭证快过期时会自动续期并写回本地；设备码流未完成时会自动探测批准结果）
octic auth status

# 登出：撤销本机凭证并清除本地缓存，幂等
octic auth logout
```

- `login` 的 flag：`--token`（一次性登录 token）、`--device`（设备码流，与 `--token` 互斥）、`--wait`（仅配合 `--device`，前台阻塞轮询；与 `--token` 同用直接退出码 2）、`--server-url`（可选）、`--force`（本地已有凭证时仍重新登录，先清旧凭证再走）。`--token` 的值即上一段「复制指令」拿到的临时 token；登录失败（比如 token 已过期）不会在本机留下任何凭证，换个新 token 重新执行即可。
- **`--server-url` 可不传**：官方构建内置了默认服务器地址，自建/测试环境才需要传（或设 `OCTIC_SERVER_URL` 环境变量）。
- **设备码流（`--device`）**：批准页会在浏览器里打开，用 Octic 账号登录后确认即可；CLI 最终落盘的是一份本机加密的凭证。整个流程有 **5 分钟硬截止**：发起后 5 分钟内没完成批准就自动作废，过期后重新跑一次 `login --device` 即可。
- **默认模式适合外部工具对接**（如 WorkBuddy 等把登录命令拉起来抓走输出的环境）：它只打印批准页网址就退出，用户在浏览器里批准后，之后每次 `auth status` 会自动探测并把凭证落盘；若本地已有凭证则直接跳过（换账号先 `auth logout` 或用 `--force`）。设置了 `OCTIC_TOKEN` 环境变量时 `--device` 会被拒绝（退出码 2，先 unset）。**`--wait` 模式适合人在终端前操作**：不用反复手动跑 `auth status`，一条命令等到底。
- 换到的长期凭证只保存在本机，所有输出里的 token 都经过掩码处理，不会出现在日志里。
- 之后的每个命令都会自动从本地读取这份凭证，无需再传 `--token`；只要设置了 `OCTIC_TOKEN`，无论值是什么，非 `login` 命令都会被直接拒绝（退出码 2）。
- **版本过旧时登录流程也会被拦**：若 `auth login --device` / `auth status` 报退出码 12（服务端版本门槛，业务码 1006），先跑 `octic upgrade` 升级 CLI 再重试登录流程；若批准链接还在 5 分钟窗口内，升级后直接 `auth status` 即可继续完成，不用重新发起。
- `auth logout` 会撤销本机的登录凭证并清掉本地缓存（撤销失败也不影响本地清理，也会清掉未完成的设备码登录状态）。**退出登录后想重新使用 CLI，走本节开头的指引**：重新到「我的」→「机器人设置」→「Octic CLI」点一次「复制指令」拿新的一次性 token，再 `octic auth login` 一次即可。
- 容器 / CI 里可无交互登录：设置 `OCTIC_TOKEN=<一次性登录 token>` 与 `OCTIC_SERVER_URL=<服务器地址>` 两个环境变量、不传任何 flag 直接跑 `octic auth login`；登录成功后 unset `OCTIC_TOKEN`（残留它会让后续所有命令报用法错误）。

## 录音：`octic rec`

`rec` 是录音相关的只读子命令组：

### `octic rec list` — 列出录音

```bash
octic rec list                                    # 默认第 1 页，每页 20 条，按创建时间倒序
octic rec list --status COMPLETED                 # 状态筛选：数字 / 中文名 / 英文名均可（如 5 / 已完成 / COMPLETED）
octic rec list --keyword "评审会"                   # 按录音名搜索
octic rec list --device <device-id>                # 按设备 ID 筛选
octic rec list --project-bound bound               # bound / unbound
octic rec list --recording-id <id>                 # 精确筛选单条
octic rec list --page 2 --page-size 50              # 手动分页（page-size 上限 100）
octic rec list --limit 200                          # 跨页拉取最多 200 条（与 --page 互斥）
```

注意 `--recording-id` 是精确匹配单条的**列表**查询，不是详情查询；返回结构仍是 `items[]` + `pagination`（见「JSON 输出结构」附录）。单条详情用 `rec show <id>`。

### `octic rec show <recording_id>` — 查看详情

```bash
octic rec show <recording_id>                       # 元数据：状态/时长/项目/关键词/创建时间/转写句数
octic rec show <recording_id> --with-transcript      # 人类可读模式下在元数据之后附带转写正文
```

`--with-transcript` 只影响 table 模式的显示；`--output json` 下 `rec show` 的输出**本来就包含完整的 `transcription` 字段**，加不加 `--with-transcript` 结果一致（JSON 模式下无需该 flag）。

### `octic rec transcript <recording_id>` — 打印转写文本

```bash
octic rec transcript <recording_id>                  # 默认带说话人前缀，如 [speaker_1] ...
octic rec transcript <recording_id> --no-speakers     # 不显示说话人前缀
```

转写为空时返回“未就绪”错误（退出码 5），不是空成功输出。`--output json` 下返回结构化 `data.lines[]`（见「JSON 输出结构」附录），人类可读模式仍为逐行纯文本。

### `octic rec summary <recording_id>` — 打印摘要

```bash
octic rec summary <recording_id>                                # 聚合展示口径：主摘要 + 追加内容
octic rec summary <recording_id> --custom <analysis_result_id>  # 精确读单份摘要的原始正文
```

- 不传 `--custom`：主摘要与追加内容（`appendList`，按时间升序）都为空时返回“未就绪”错误（退出码 5）。
- 传 `--custom <analysis_result_id>`：按 `analysis_result_id` 精确读取**那一份**摘要的原始正文（没有 appendList 概念）；`content` 为空时同样返回“未就绪”（退出码 5）；table 模式直接打印 `content` 原文。典型场景是 `edit-sync` skill 在编辑摘要前精确回读当前正文。`analysis_result_id` 无效、已删除或不属于该录音时返回“不存在”（业务码 2207，退出码 4），录音本身不存在时同样退 4（业务码 2001）。

### `octic rec mindmap <recording_id>` — 打印脑图

```bash
octic rec mindmap <recording_id>
```

脑图为空时返回”未就绪”错误（退出码 5）。

### `octic rec summary infographic <recording_id>` — 列出总结里的 infographic 块

```bash
octic rec summary infographic <recording_id>                                # 系统主总结（scheduled）
octic rec summary infographic <recording_id> --custom <custom_summary_id>   # 某份自定义总结（override）
```

列出该总结正文里的 infographic 信息图块，每块给出 `block_id`（后续编辑的定位键）、模板名、DSL 原文、当前图片地址和版本号。**工作流是「先列后改」**：先用这条命令看清每块的当前内容，再用 `edit summary infographic` 改指定块。

- 块列表是 CLI 从总结正文的 `![infographic:{block_id}](url)` 占位符解析出来的，再逐块查询详情，块顺序与正文出现顺序一致。
- `--custom` 的语义与 `rec summary --custom` 一致：不传 = 系统主总结；传 = 那份自定义总结。
- **正文里没有任何占位符（包括只有老格式存量图）时返回空列表（`blocks: []`）、退出码 0，不是错误**——「这份总结没有可编辑的图」本身就是一个合法答案。2026-08-26 之前生成的总结用的是老格式占位符（没有 block_id），无法通过 CLI 编辑；要看/改这类图只能重新生成总结。
- 个别块查询失败（例如块已被软删除）不会让整条命令失败：该块在 `blocks` 里带 `error` 字段标注，其余块正常返回；只有全部块都查询失败才报错。
- 总结正文还没生成时返回“未就绪”（退出码 5）。
- JSON 结构见「JSON 输出结构」附录。

### `octic rec download <recording_id>` — 下载文件

```bash
octic rec download <recording_id>                                        # 默认 --type audio，落盘为 mp3
octic rec download <recording_id> --type transcript --format md          # transcript: md|pdf（默认 md）
octic rec download <recording_id> --type summary --format md             # summary: md|pdf（默认 md）
octic rec download <recording_id> --type mindmap                         # mindmap：固定 md，落盘为 {id}-mindmap.md
octic rec download <recording_id> --type all --destination ./out/        # 四种类型都下载，此时 --destination 必须是目录
octic rec download <recording_id> --destination ./rec.mp3 --overwrite    # 指定文件名并允许覆盖已存在文件
```

- `--destination` 缺省时使用**服务端返回的文件名**（`fileName`），落在当前目录；文件名形如 `录音-<录音名>.mp3`、`转写-<录音名>.md`、`总结-<录音名>.md`（带格式后缀，如 `--format pdf` 则是 `转写-<录音名>.pdf`），文件名中的路径分隔符与 `..` 会被清理。`--destination` 指向目录（已存在的目录或以 `/` 结尾）时文件落进该目录、沿用该默认文件名；指向完整文件路径时以该路径为准。
- 下载采用”临时文件 + 原子 rename”落盘，不会留下半截文件；文件已存在且未加 `--overwrite` 会报本地文件冲突错误（退出码 6）。
- `--type all` 时若某一类型失败（如摘要未生成），其余类型仍会继续下载，最终以”N/M 个类型下载失败”的部分失败形式返回，退出码取第一个失败项的分类；非 `all` 模式下任一类型失败会立即返回。
- **`--type mindmap` 走的是读接口**（返回脑图文本，非下载 URL），内容按行拼成 Markdown 落盘为 `{id}-mindmap.md`；脑图未生成时返回“未就绪”（退出码 5）。想直接在终端看而不落盘，用 `octic rec mindmap <recording_id>`。

### `octic rec rename <recording_id> <new_name>` — 录音改名

```bash
octic rec rename <recording_id> "新名字"     # 名称 ≤60 UTF-8 字节（约 20 个中文）
```

本地先做长度校验，超限直接退出码 2，不再发起请求；改名成功返回更新后的 `interviewId`/`interviewName`。

## 编辑命令组：`octic edit`

编辑录音的三类内容，全部走登录态（Bearer），**不支持分享模式**。`rec` 组保持纯只读不动。

### `octic edit transcript <recording_id>` — 读取（取版本号）/编辑转写

编辑转写用"乐观锁"：必须先读一次拿到版本号，提交时带回来；如果别人在你读和写之间改过，提交会返回 409（退出码 11），不会静默覆盖。

```bash
# 第一步：读全文 + 版本号（Version: N 就是下一步要传的 --expected-version）
octic edit transcript <recording_id> --read
octic edit transcript <recording_id> --read --keyword "错字" --context 2   # 过滤+上下文（可选）
octic edit transcript <recording_id> --read --start-seq 1 --end-seq 50     # 行号区间（可选）

# 第二步：提交编辑（三种输入方式互斥）
octic edit transcript <recording_id> --set 12=新文本 --set 13=也改这句 --expected-version 5
octic edit transcript <recording_id> --file edit.json --expected-version 5
cat edit.json | octic edit transcript <recording_id> --stdin --expected-version 5
```

- `edit.json` 形如 `{"sentences":[{"sentence_id":"12","content":"新文本"}]}`。`sentence_id` 的取值是 `--read` 输出里 `sequence_no` 字段的**原始字符串**（纯数字，如 `"12"`），**不带任何前缀**（不是 `"s12"`）——直接原样照抄 `--read` 结果里的 `sequence_no` 值即可，不要自己拼前缀。
- 旧命令名 `octic edit transcription ...` 是 `transcript` 的兼容 alias，行为完全一致；新脚本一律用 `transcript`。
- `--read` 与 `--set`/`--file`/`--stdin`/`--expected-version` 互斥（退出码 2）。
- 提交内容与现状完全相同时提交结果为 null，CLI 输出 `{"changed":false}`、退出码 0。
- 本地校验（全走退出码 2）：sentence_id 只能是字母/数字/下划线/短横线（≤64）、content 非空且 ≤5000 字、无重复 id、单次 ≤10000 条。

### `--trigger-scene`（编辑命令通用）

编辑请求需要带 `edit_trigger_scene`（编辑是谁发起的）。`transcript`/`summary`/`mindmap` 三个命令 CLI 默认 `user`（用户或用户授权的 Agent 主动编辑），不需要显式传；`name`/`keywords` 两个命令**必传**（接口必填，不传本地直接退出码 2）。

**只有一种情况要传非 user 值**：`edit-sync` skill 做级联文件回写时传 `--trigger-scene edit_sync`，服务端据此跳过"再触发一轮编辑同步"（否则死循环）。这是 skill 专用值，普通编辑禁止使用（会跳过记忆沉淀）。值原样透传，非法值会报错。

```bash
octic edit summary <recording_id> --file s.json --trigger-scene edit_sync
```

### `octic edit summary <recording_id>` — 编辑摘要

```bash
echo '{"content":"整篇新摘要"}' | octic edit summary <recording_id> --stdin
octic edit summary <recording_id> --file s.json                     # {"content": "..."}
octic edit summary <recording_id> --file s.json --custom <custom_summary_id>   # 编辑自定义摘要（不传=主摘要）
```

无乐观锁（后写覆盖）；content 为空本地直接拒绝。

### `octic edit summary infographic <recording_id>` — 编辑单个 infographic 块

```bash
octic rec summary infographic <recording_id>                                  # 第一步：列出块，拿 block_id 和当前 DSL
octic edit summary infographic <recording_id> --block <block_id> --file d.json   # 第二步：整块替换 DSL 文本
echo '{"dsl_content":"infographic chart-bar-plain-text\n  title 修正后的标题"}' | octic edit summary infographic <recording_id> --block <block_id> --stdin
octic edit summary infographic <recording_id> --block <block_id> --file d.json --custom <custom_summary_id>   # 编辑自定义总结里的块
```

修改总结里**指定一块**信息图的文字内容（整块 DSL 文本替换，不改版式/模板/配色），成功后回显新版本号（在原版本上 +1）和**新渲染图片地址**——这个地址就是本次编辑的产物，前端/Web 端展示的也是它。

- `--block` 必传，值是列块命令输出的 `block_id`（32 位小写十六进制）；漏传或格式不对本地直接退出码 2。
- `d.json` 形如 `{"dsl_content": "整块新 DSL 文本"}`。**以列块命令读到的 `dsl_content` 为编辑起点**：复制出来、改错字、原样传回。DSL 首行的模板名必须与该块当前模板一致（改模板会被后端拒绝，业务码 2219）。
- **不做本地长度校验，直接交给后端**（业务码 2220）：各字段上限按模板各有不同（compare-swot 等模板有专属上限），报错文案会提示“超出该模板的长度限制”，具体改哪句以列块读到的原文为准。默认上限参考：中文口径 title ≤12 字 / desc ≤24 字 / item.label ≤6 字 / item.desc ≤20 字；英文按词数+字符数双重限制；中英文按文本是否含 CJK 字符自动判定。
- **本期只编辑 DSL 原文，不编辑译文**（命令无 `--lang` 参数；译文编辑需要配套的译文读取入口，后续另立需求）。
- 无乐观锁（后写覆盖），与 `edit summary`/`edit mindmap` 同水位；该编辑不触发记忆沉淀/编辑同步（无 `--trigger-scene` flag）。
- **不要在 `edit summary`（整段编辑）里删掉占位符行**：块编辑成功后后端会回写正文里该块的图片地址，正文里占位符没了的话图更新了但总结里看不到。

### `octic edit mindmap <recording_id>` — 编辑脑图

```bash
octic edit mindmap <recording_id> --file m.json   # {"content": "整篇 mermaid 文本"}
```

整篇覆盖（旧版会自动备份到 `-pre-edit`），无乐观锁。

### `octic edit name <recording_id>` — 编辑录音名称（编辑同步体系）

```bash
octic edit name <recording_id> --set "新标题" --trigger-scene user
octic edit name <recording_id> --set "新标题" --trigger-scene edit_sync
```

- 与 `rec rename` 同效：`rec rename` 不显式指定编辑场景，按缺省的用户主动编辑场景（`USER`）处理，同样会触发编辑同步；如果不想触发，目前没有开关。
- 名称 ≤60 UTF-8 字节（约 20 个中文），超限本地退出码 2。
- **`--trigger-scene` 必传**（不传本地直接退出码 2，不发请求）：`user`（用户或用户授权的 Agent 主动改）、`ask_octic`（Octic 侧发起）、`edit_sync`（edit-sync skill 级联回写专用，普通编辑禁止使用）。值原样透传，非法值会报错。

### `octic edit keywords <recording_id>` — 编辑录音关键字（整组覆盖）

```bash
octic edit keywords <recording_id> --set "关键词1,关键词2,关键词3" --trigger-scene user
octic edit keywords <recording_id> --set "" --trigger-scene user   # 清空全部关键字
```

- 整组覆盖（不是追加）。
- **`--set` 必传**：漏传直接退出码 2、不发请求；**只有显式传 `--set ""`（空串）才是清空**——两者刻意区分，防止少打参数误删整组关键字。
- 逗号分隔，逐项去首尾空白，空项丢弃（`"a,,b,"` → `["a","b"]`）。
- `--trigger-scene` 同上必传；当前关键字先用 `octic rec show <id>` 读（`interview.keywords`）。
- 注意两个响应体大小写刻意不同：`name` 返回 camelCase（`interviewName`/`updatedAt`），`keywords` 返回 snake_case（`interview_id`/`updated_at`）。

## 合集命令组：`octic collection`

合集即 Octic 里的"项目"（project）。读写下钻全部走登录态，不支持分享模式。

```bash
octic collection list [--page N --page-size N --keyword 名称]          # 合集列表
octic collection show <collection_id>                                  # 详情 + 录音/文件各前 20 条概览
octic collection recordings <collection_id> [--page/--page-size]       # 合集内录音
octic collection files <collection_id> [--page/--page-size]            # 合集内文件（不含录音音频）
octic collection download <collection_id> <file_id> [--destination 路径] [--overwrite]   # 下载文件到本地（默认当前目录，文件名取服务端 fileName）
octic collection create <name> [--desc 描述]                           # 创建（重名会报错；名称 ≤60 字节）
octic collection rename <collection_id> --name 新名 [--desc-set --desc 新描述]   # 改名/改描述
octic collection add <collection_id> <recording_id>...                 # 添加录音（可批量；绑新=从旧合集移走）
octic collection remove <collection_id> <recording_id>                 # 移除录音（只解绑，不删录音）
octic collection upload <collection_id> --file a.txt --file b.pdf      # 上传文件（可重复 --file）
```

- `rename` 的 `--desc` 空字符串只有配合 `--desc-set` 才会提交（否则视为"不改描述"）。
- `download` 直接落盘（原子写：先写 `.part` 再改名）；目标已存在且未加 `--overwrite` 退出码 6。需要链接而不是文件时没有对应命令——下载语义就是拿文件。
- `add` 语义是"绑定"：一个录音只属一个合集，加进新合集会自动从旧合集移走。
- `upload` 是 CLI 唯一的 multipart 传输；逐文件返回 `success`/`error`，JSON 输出为 `{"results":[...],"uploaded":N,"failed":M}`，部分失败不改变命令退出码 0，看 `failed` 字段。

## 产物命令组：`octic artifact`

产物即 Octic 收藏夹（favorites）里的产出文件；收藏（bookmarks）是产物的子集。

```bash
octic artifact list [--tab all|bookmarks] [--keyword 名称] [--format md,pdf] [--sort latest|earliest] [--page/--page-size]
octic artifact formats [--tab all|bookmarks]        # 可用文件类型清单（配合 --format 筛选）
octic artifact show <file_id>                       # 详情（内嵌 presigned 下载 url）
octic artifact download <file_id> [--destination 路径] [--overwrite]
octic artifact rename <file_id> <new_name>          # 产物改名（统一入口，报告/上传文件都走它）
```

- `--tab research`（深度研究）本期不提供；`--tab` 只接受 `all`/`bookmarks`。
- 改名走统一入口 `artifact rename`（报告/上传文件都走它）。

## 全局选项

对 `octic` 根命令及所有子命令均生效：

| Flag | 环境变量 | 说明 |
|---|---|---|
| `--output table\|json` | `OCTIC_OUTPUT` | 输出格式，默认 `table`（人类可读）。写脚本/agent 解析时一律用 `--output json` |
| `-q, --quiet` | — | 抑制进度输出 |

`-h, --help` 所有命令可用；根命令另有 `-v, --version`（打印版本并做一次轻量更新检查）和 `--no-check-update`（跳过该检查）。**`--no-check-update` 不是全局 flag**：只与 `--version` 配合使用才有意义，单独用在任何子命令上都会报 `unknown flag` 用法错误。

`--token`/`--server-url` **不是全局 flag**，只有 `auth login` 接受它们（见上一节）；其余任何命令一旦检测到 `OCTIC_TOKEN` 都会直接报用法错误。**没有 `--share-link` flag**——分享访问改用 `OCTIC_SHARE_LINK` 环境变量（见下一节）或独立的 `octic share show` 命令。

服务器地址只有 `OCTIC_SERVER_URL`（别名 `OCTIC_API_BASE`）一个环境变量：仅当本地还没有显式落盘的地址时生效——一旦 `auth login --server-url` 已把地址写进配置文件，运行环境里同名的环境变量（例如容器内网 service 地址）不会再顶替它。

## 分享链接访问

拿到一个 Octic 分享链接时，先确保本机已 `octic auth login`（见上一节），配合本地已登录的账号即可直接访问分享内容，不走原分享认证机制；分享模式下同样**不能**再传 `--token`：

```bash
OCTIC_SHARE_LINK="https://octic.example.com/share/<share-id>" octic rec show <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec transcript <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec summary <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec mindmap <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec download <recording_id> --type audio
```

- `OCTIC_SHARE_LINK` 接受完整 URL 或裸 share ID（UUID4），CLI 会自动解析出 ID。
- 分享模式下 `<recording_id>` 仍是必填参数，不会把 share ID 当作录音 ID 使用。
- **`octic rec list` 不支持分享模式**：分享的是单条录音，不是一份可列表的集合，命令内部会拒绝并报用法错误（退出码 2）。分享场景下只能用 `show` / `transcript` / `summary` / `mindmap` / `download` 五个子命令，且必须显式给出 `<recording_id>`。
- 这是**登录用户**访问他人分享的方式（需要本机先 `octic auth login`）。若拿到的是一个独立分享链接、且不知道对应的真实 `recording_id`，改用下一节的 `octic share` 命令组——同样需要本机已 `octic auth login`，区别只是直接凭分享链接/裸 share ID 定位资源，不需要提前知道真实 ID。

## 分享命令组：`octic share`

`octic share show` / `octic share download` 同样需要本机先 `octic auth login`：分享 ID 只是随已登录请求一起发出的 `X-Share-Id` 头，用来限定可访问的资源范围，不能替代本机凭证。与上一节 `OCTIC_SHARE_LINK` 的区别在于：`octic share` 可以直接凭分享链接/裸 share ID 定位资源，不需要提前知道对应的真实 `recording_id`/`project_id`/`file_id`。两个子命令的第一个位置参数都接受完整 URL 或裸 share ID（UUID4）。

分享有三种类型：单条录音分享、单个报告分享、项目分享（多条录音/文件/报告的集合）。**类型是自动识别的**，不需要用户指定：

```bash
octic share show "https://octic.example.com/share/<share-id>"              # 录音分享：直接打印录音详情
octic share show "<share-id>" --transcript                                 # 录音分享：打印转写（未就绪报退出码 5）
octic share show "<share-id>" --summary                                    # 录音分享：打印摘要（未就绪报退出码 5）
octic share show "<share-id>" --mindmap                                    # 录音分享：打印脑图（未就绪报退出码 5）
octic share show "<share-id>"                                              # 报告分享：打印报告详情
```

项目分享是一份集合，先看概览（录音/用户上传文件/报告各前 20 条；文件列表不包含录音音频，那些只出现在"recordings"里），再用 `--recording` 下钻到具体某条录音：

```bash
octic share show "<project-share-id>"                                      # 项目分享：概览
octic share show "<project-share-id>" --recording <recording_id>           # 下钻到某条录音，等价于录音分享的详情视图
octic share show "<project-share-id>" --recording <recording_id> --transcript
```

- `--recording` 仅在项目分享下有效，否则报用法错误（退出码 2）。`show` 没有 `--report`/`--file`：报告分享本身就是一份报告，直接 `share show "<report-share-id>"` 即可看到详情；项目分享的报告/文件条目只展示于概览列表，没有独立下钻查看命令。
- `--transcript`/`--summary`/`--mindmap` 仅在"录音分享"或项目分享的 `--recording` 下钻下有效，未就绪时的退出码行为与 `rec transcript`/`rec summary`/`rec mindmap` 一致（退出码 5）。

`share download` 复用与 `rec download` 相同的 `--type`/`--format`/`--destination`/`--overwrite` 语义，并额外支持 `--recording`/`--report`/`--file`（三者互斥，仅项目分享下有效）下钻到具体条目；`--type`/`--format` 只对录音目标有意义，下载报告/项目文件时传了这两个 flag 会报用法错误（退出码 2）：

```bash
octic share download "<share-id>"                                          # 录音分享：默认 --type audio
octic share download "<share-id>" --type transcript --format md            # 录音分享：下载转写
octic share download "<report-share-id>"                                   # 报告分享：直接下载对应文件，不能加 --type/--format
octic share download "<project-share-id>" --recording <recording_id> --type all --destination ./out/   # 下钻到录音后按 all 下载四种类型
octic share download "<project-share-id>" --report <report_id> --destination ./report.pdf              # 下钻到项目自身的报告 PDF
octic share download "<project-share-id>" --file <file_id> --destination ./out/                         # 下钻到项目文件
```

- **项目分享本身是一份集合，不能直接下载**：不带 `--recording`/`--report`/`--file` 直接 `share download <project-share-id>` 会报用法错误（退出码 2），必须先指定下钻到具体条目。
- `--recording`/`--report`/`--file` 仅在项目分享下有效，且只能三选一，否则报用法错误（退出码 2）。
- **`--report` 接受两种报告 ID 格式**：UUID 格式（如 `2dc8a389-b66b-433d-96b9-3acc99834bb1`）与 32 位 hex 格式（无连字符，如 `740f67d887d94d2db2e79f36e2107700`）。两种格式都能直接下载，CLI 会自动识别，无需关心区别。
- `--type all` 时 `--destination` 必须是目录，行为与 `rec download --type all` 一致（部分失败时其余类型仍继续下载）。
- `--type mindmap` 与 `rec download` 一致（走读接口，落盘 `{id}-mindmap.md`）；`--recording` 下钻场景下想在终端直接看脑图，用 `share show ... --recording <id> --mindmap`。
- **下载报告分享、`--report`、`--file` 时不能传 `--type`/`--format`**（含 `--type all`）：这两个 flag 只对录音下载有意义，非录音目标传了会直接报用法错误（退出码 2），不会静默忽略。

## 退出码与错误处理

失败时进程以非零码退出，人类可读的原因写到 stderr（`--output json` 时错误也是结构化 JSON）：

| 退出码 | 含义 | 常见触发场景 |
|---|---|---|
| 0 | 成功 | — |
| 1 | 未分类错误 | — |
| 2 | 用法错误 | 非法 flag、缺参数、`--limit` 与 `--page` 同时给出、本地输入校验失败（编辑 JSON 非法、名称超 60 字节、`edit summary infographic` 的 `--block` 缺失/格式不对/`dsl_content` 为空）、infographic DSL 首行模板名与块当前模板不一致（业务码 2219，不能通过编辑改模板）、infographic 文本超出该模板的长度限制（业务码 2220，上限按模板各有不同，精简文本后重试）、服务端返回 HTTP 400（`sentence_id` 不存在这一种 400 改判退出码 4）|
| 3 | 认证错误 | 未登录、凭证失效/过期、本地 dev 构建缺编译期认证配置 |
| 4 | 资源不存在 | 录音 ID 不存在或无权访问、分享不存在；`edit transcript --set/--file` 提交了当前转写里不存在的 `sentence_id`（不是 `--read` 返回的 `sequence_no` 原始值），错误信息会列出具体哪些 id 没匹配到；infographic 块不存在或已删除（业务码 2217，`edit summary infographic` 用了列块结果里没有的 `--block`，或该块已被软删除——重跑 `rec summary infographic` 拿最新块列表） |
| 5 | 资源未就绪 | 转写尚未生成、摘要尚未生成、脑图尚未生成、总结正文尚未生成（`rec summary infographic` 也走这条） |
| 6 | 本地文件冲突 | `skill install --target-dir` 指向的路径已存在且非空，未加 `--force`；`rec download`/`share download` 目标文件已存在且未加 `--overwrite` |
| 7 | 无权限 | 无权访问该录音、分享已设为私有/无权访问该资源、无权下载该文件 |
| 8 | 网络错误 | 请求服务端失败（连接失败、超时、5xx 重试仍失败等）；`rec download`/`share download` 下载传输中途失败 |
| 9 | 被限流 | 触发接口层防爬/防爆破限流 |
| 10 | 服务端错误 | 服务端 5xx 或内部错误；infographic 渲染/翻译失败（业务码 2221/2222，稍后重试） |
| 11 | 版本冲突 | `edit transcript` 提交时返回 409（转写已被他人修改，乐观锁版本号过期）；错误信息会带当前最新版本号 |
| 12 | 版本过旧 | 服务端版本门槛拦截（业务码 1006）：当前 octic 版本低于服务端要求的最低版本，stderr 会给出形如 `octic >= 1.3.0 required (current 1.2.0). Run: octic upgrade` 的提示 |

**agent 调用约束（重要）**：
- 命中退出码 9（限流）**禁止**立即重试或加大请求频率——这会被判定为刷接口攻击，只应提示用户稍后再试。
- 命中退出码 5（未就绪）通常代表异步处理还没完成（转写/摘要生成中），可提示用户稍后重试，不要在短时间内密集轮询。
- 命中退出码 11（版本冲突）**禁止自动重试**——重试前必须先 `octic edit transcript <id> --read` 重读最新内容，确认要改的句子没有被别人的修改覆盖后，再用新的版本号提交；直接拿新版本号盲发会静默覆盖他人的修改。
- 命中退出码 12（版本过旧）**先升级再重试**：跑 `octic upgrade`，成功后重试原命令；升级前直接重试原命令只会再次被拦（服务端按版本号确定性拒绝，重试无意义）。升级通道本身不受版本门槛影响。
- 业务失败（4/5/7 等）不会自动重试；只有幂等的网络层错误（如 502/503/504）内部才会有限次重试。

## JSON 输出结构（`--output json`）

**结构化业务命令**（`auth` / `rec` / `edit` / `collection` / `artifact` / `share` / `skill install` / `skill list-agents` / `upgrade`）的成功输出都是同一个信封：stdout 一行 `{"ok": true, "data": ...}`；失败时 stderr 一行 `{"ok": false, "error": {"kind", "message", "businessCode", "exitCode"}}`（`businessCode` 无业务码时为 `null`，部分失败时另有 `details[]` 逐项列出；版本过旧/退出码 12 时另有 `upgradeRequired: {"min_version": "...", "command": "octic upgrade"}` 字段）。解析时**先看 `ok`，再按命令取 `data` 里的字段**——各命令的 `data` 结构互不相同。

**两个本机命令不走这个信封**，不要按 JSON 解析：`completion` 直接输出 shell 补全脚本文本；`skill export` 直接输出内嵌 SKILL.md 原文（它的 `--output` 是**输出文件路径**，`"-"` 表示 stdout，与全局 `--output table|json` 格式参数同名但完全无关）。

各命令 `data` 结构：

| 命令 | `data` 结构 |
|---|---|
| `rec list` | `{"items": [...], "pagination": {...}}` |
| `rec show` | `{"interview": {...}, "transcription": {...}, "analysis": {...}, "transcriptCount": n}` |
| `rec transcript` | `{"lines": [{"speaker": "...", "text": "..."}, ...]}`（`--no-speakers` 时各项省略 `speaker`） |
| `rec summary` | `{"summary": "...", "summarySource": "...", "appendList": [...]}`（服务端原样返回；`--custom <id>` 时为 `{"interview_id", "analysis_result_id", "content"}`） |
| `rec summary infographic` | `{"summary_id": "...", "type": "scheduled\|override", "blocks": [...]}`（`blocks[]` 每项含 `block_id`/`template_key`/`dsl_content`/`asset_url`/`version`/`updated_at`；查询失败的块只含 `block_id` + `error`；正文无占位符时 `blocks` 为空数组） |
| `rec mindmap` | `{"interviewId": "...", "mindMap": [...], "aiGenerated": bool}`（服务端原样返回） |
| `rec download` | `{"files": [{"type", "ok", "path", "size", "format"}, ...]}` |
| `rec rename` | `{"interviewId": "...", "interviewName": "..."}` |
| `edit transcript --read` | 服务端原样返回：`{"interview_id", "version_no", "total_count", "sentences": [{"sequence_no", "text", ...}, ...]}`（`sequence_no` 即编辑要传的 `sentence_id`） |
| `edit transcript` | `{"changed": bool}`（有变化时另有 `"result": {"change_id", "interview_id", "version_no", "updated_at"}`） |
| `edit summary` / `edit mindmap` | `{"interview_id", "analysis_result_id", "updated_at"}`（服务端原样返回） |
| `edit summary infographic` | `{"block_id": "...", "version": n, "asset_url": "https://..."}`（服务端原样返回；`version` 是编辑后的新版本号，`asset_url` 是新渲染图片地址） |
| `edit name` | `{"interviewId", "interviewName", "updatedAt"}`（camelCase，服务端原样返回） |
| `edit keywords` | `{"interview_id", "keywords": [...], "updated_at"}`（snake_case，服务端原样返回） |
| `collection list` / `collection recordings` / `collection files` | `{"items": [...], "pagination": {...}}` |
| `collection show` | `{"collection": {...}, "recordings": [...], "files": [...]}`（各取前 20 条；`files` 已剔除录音音频） |
| `collection download` | `{"files": [{"type": "file", "ok": true, "path", "size"}]}`（与 rec/artifact download 同构） |
| `collection create` / `collection rename` | 服务端项目对象原样返回（`projectId`/`projectName`/...） |
| `collection add` / `collection remove` | `{"count": n}` |
| `collection upload` | `{"results": [{"fileName", "fileId", "success", "error"}, ...], "uploaded": n, "failed": m}` |
| `artifact list` | `{"items": [...], "pagination": {...}}`（字段 snake_case：`file_id`/`file_name`/`file_format`/`is_bookmarked`/`created_at`） |
| `artifact formats` | `{"formats": ["md", "pdf", ...]}`（服务端原样返回） |
| `artifact show` / `artifact download` | 收藏详情原样返回（snake_case；download 包成 `{"files": [{...}]}`） |
| `artifact rename` | `{"file_id": "...", "new_name": "..."}` |
| `share show`（录音分享） | `{"interview": {...}, "transcriptCount": n}`（带 `--transcript` 时另有 `"transcript": [...]`，`--summary`/`--mindmap` 时另有 `summary`/`mindMap`） |
| `share show`（合集分享） | `{"project": {...}, "interviews": [...], "files": [...], "reports": [...]}`（各取前 20 条；`files` 已剔除录音音频） |
| `share show`（产物分享） | `{"file_id", "file_name", "file_format", "url", "is_bookmarked", "source_session_title", "created_at", ...}`（收藏详情原样返回，snake_case） |
| `auth login` / `auth status` | `{"credentialType", "credentialName", "subject", "apiBase", "expiresAt"}`（status 另有 `probeResult`） |
| `auth logout` | `{"deleted": bool}` |
| `skill install` | `[{"agent", "skill", "target", "dryRun", "installed", "status", "skip_reason"}, ...]`（数组，每个 (agent, skill) 组合一项；`status` 为 `installed`/`skipped`/`dry-run`，跳过时另带 `skip_reason`=`platform`/`agent`） |
| `skill list-agents` | `[{"agent", "skills": [...], "targets": [...]}, ...]`（每 agent 一项，摊开本平台会装的 skill 及各自目标目录） |
| `upgrade` | `{"current", "latest", "updated", "skillHint"}` |

各命令要点：

### `rec list --output json`

```json
{
  "ok": true,
  "data": {
    "items": [
      {"interviewId": "i-1", "interviewName": "季度复盘", "status": 5,
       "statusName": "COMPLETED", "duration": 3661, "createdAt": 1700000000000}
    ],
    "pagination": {"total": 1, "page": 1, "pageSize": 20, "totalPages": 1, "returned": 1}
  }
}
```

- `items[]` 即返回的 `list`，每项额外补了 CLI 生成的 `statusName`（英文状态名）。
- `pagination` 的 `total`/`page`/`pageSize`/`totalPages` 来自服务端；`returned` 只在用 `--limit` 跨页拉取时由 CLI 写入（实际拉到的条数）。
- `--recording-id <id>` 精确匹配单条时，`items` 里仍是 0 或 1 个元素的数组，`pagination` 以服务端返回为准（精确匹配场景下可能没有分页字段，此时 `pagination` 为空对象 `{}`，不要假设 `total` 恒有值）。

### `rec show --output json`

```json
{
  "ok": true,
  "data": {
    "interview": {"interviewId": "i-1", "interviewName": "示例录音", "status": 5,
                   "statusName": "COMPLETED", "duration": 120, "createdAt": 1700000000000},
    "transcription": {"data": [{"speaker_id": "张三", "text": "你好"}]},
    "analysis": {"summary": "示例分析"},
    "transcriptCount": 2
  }
}
```

- 转写在 `data.transcription.data[]`（每项含 `speaker_id`、`text`），不在顶层；`transcriptCount` 是转写句数。
- `interview.statusName` 是 CLI 生成的英文状态名，`status` 是数字状态码。
- **`--with-transcript` 在 JSON 模式下无行为差异**：`transcription` 字段本来就在输出里。

### `rec summary --output json`

```json
{"ok": true, "data": {"summary": "主摘要", "summarySource": "template", "appendList": []}}
```

- 主摘要在 `data.summary`（字符串）；追加内容在 `data.appendList[]`（每项含 `content`、`createdAt` 等）。两者都为空时命令报“未就绪”（退出码 5），不会输出空成功 JSON。
- `--custom <analysis_result_id>` 时结构变为单份摘要口径：`{"ok": true, "data": {"interview_id": "...", "analysis_result_id": "...", "content": "..."}}`，没有 `summarySource`/`appendList`；`content` 为空同样报“未就绪”（退出码 5）。

### `rec summary infographic --output json`

```json
{"ok": true, "data": {
  "summary_id": "i-1", "type": "scheduled",
  "blocks": [
    {"block_id": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "template_key": "chart-bar-plain-text",
     "dsl_content": "infographic chart-bar-plain-text\n  title 修复后的标题", "asset_url": "https://.../x.svg",
     "version": 3, "updated_at": "2026-09-08 12:00:00"},
    {"block_id": "0123456789abcdef0123456789abcdef",
     "error": {"businessCode": 2217, "message": "..."}}
  ]
}}
```

- `blocks[]` 顺序与正文出现顺序一致；`dsl_content` 就是编辑该块时要传回去的 DSL 原文（编辑起点）。
- 个别块查询失败（如已被软删除）时该块只含 `block_id` + `error`，命令整体仍成功（`ok: true`）；**全部块都失败才报错**。
- 正文里没有任何占位符时 `blocks` 是空数组 `[]`，命令成功（退出码 0）——包括只有 2026-08-26 之前老格式存量图的情况。
- `--custom` 时 `summary_id` 是那份自定义总结的 ID、`type` 为 `override`。

### `edit summary infographic --output json`

```json
{"ok": true, "data": {"block_id": "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6", "version": 4, "asset_url": "https://.../new.svg"}}
```

- `version` 是编辑后的新版本号（在编辑前列块读到的版本上 +1）；`asset_url` 是本次新渲染的图片地址，即为编辑回执。
- 编辑后想验证：再跑一次 `rec summary infographic`，该块的 `dsl_content`/`version`/`asset_url` 应与编辑结果一致（编辑后内容成为再次查询/编辑的基准）。

### `rec transcript --output json`

```json
{"ok": true, "data": {"lines": [{"speaker": "张三", "text": "你好"}, {"speaker": "李四", "text": "喂"}]}}
```

- `data.lines[]` 是**对象数组**，每项默认含 `speaker`（说话人 ID）与 `text`；加 `--no-speakers` 时各项**省略 `speaker` 字段**（不是空字符串）。
- 空文本行会跳过；转写整体为空时命令报“未就绪”（退出码 5）。
- 人类可读模式不变：逐行纯文本，默认带 `[speaker] ` 前缀。

### `rec mindmap --output json`

```json
{"ok": true, "data": {"interviewId": "i-1", "mindMap": ["mindmap", "  (录音)季度复盘"], "aiGenerated": true}}
```

- **字段名是 `mindMap`（驼峰，M 大写）**，不是 `mindmap`。
- **值是字符串数组**（Mermaid 文本按行拆分），不是单个字符串；人类可读模式下按行原样打印。数组为空/缺失时命令报“未就绪”（退出码 5）。

### `rec download` / `share download --output json`

```json
{"ok": true, "data": {"files": [{"type": "audio", "ok": true, "path": "/cwd/录音-季度复盘.mp3", "size": 1024, "format": "mp3"}]}}
```

- `--type all` 时 `files[]` 有三项；部分失败时整体是错误信封（`ok:false`），`error.details[]` 逐项给出每个类型的成败与原因，但已成功的文件已落盘。
- `path` 是实际落盘路径；`size` 是字节数。

### `share show --output json`

`share show` 的 `data` 结构**随分享类型变化**，解析前先判断类型（录音分享有 `interview`，合集分享有 `project`，产物分享有 `file_id`）：

**录音分享**：

```json
{
  "ok": true,
  "data": {
    "interview": {"interviewId": "i-1", "interviewName": "示例录音", "status": 5,
                   "statusName": "COMPLETED", "duration": 120, "createdAt": 1700000000000},
    "transcriptCount": 2
  }
}
```

- 结构同 `rec show` 的精简版：`interview`（含 CLI 生成的 `statusName`）+ `transcriptCount`（转写句数）。
- 带 `--transcript` 时增加 `"transcript": ["[张三] 你好", ...]`（**字符串数组**，与 `rec show` 的对象数组不同）；带 `--summary` 时增加 `summary` 对象、带 `--mindmap` 时增加 `mindMap` 对象（均为服务端原样返回）。

**合集分享**：

```json
{
  "ok": true,
  "data": {
    "project": {"projectId": "p-1", "projectName": "调研合集", "projectDesc": "desc",
                 "interviewCount": 30, "createdAt": "2026-01-01T00:00:00Z"},
    "interviews": [{"interviewId": "i-1", "interviewName": "录音一", "status": 5, "duration": 3661}],
    "files": [{"fileId": "f-1", "fileName": "笔记.docx", "createdAt": "2026-01-02T00:00:00Z"}],
    "reports": [{"reportId": "r-1", "reportName": "分析报告", "status": "completed",
                  "createdAt": "2026-01-03T00:00:00Z", "fileId": "f-9"}]
  }
}
```

- `interviews[]`/`files[]`/`reports[]` 各取前 20 条（更多内容当前无 CLI 分页入口）；`files[]` 已剔除录音音频（.m4a 只出现在 `interviews` 里）。
- `reports[]` 里的 `reportId` 就是 `share download --report <id>` 要用的 ID；`files[]` 里的 `fileId` 对应 `--file <id>`。
- `--recording <id>` 下钻时结构与录音分享完全一致（`interview` + `transcriptCount`）。

**产物分享**：

```json
{
  "ok": true,
  "data": {
    "file_id": "f-1", "file_name": "洞察报告.html", "file_format": "html",
    "url": "https://cos.../presigned", "is_bookmarked": true,
    "source_session_title": "会话A", "created_at": "2026-01-05T00:00:00Z"
  }
}
```

- 产物详情**原样返回**，字段是 snake_case（与前两类不同）；`url` 是预签名下载地址（可直接 GET，1 小时有效）。

## 使用场景示例

### 场景 1：查看列表并按状态筛选（机器可读）

```bash
# 一次性登录 token 先从网页端/App「我的」→「机器人设置」→「Octic CLI」点「复制指令」获取
octic auth login --token "$OCTIC_LOGIN_TOKEN" --server-url https://octic.example.com
octic --output json rec list --status COMPLETED --limit 50
```

### 场景 2：查看某条录音的转写与摘要，并下载留存

```bash
octic rec list --keyword "评审会" --output json
octic rec transcript <recording_id>
octic rec summary <recording_id>
octic rec download <recording_id> --type all --destination ./out/
```

### 场景 3：通过分享链接查看并下载录音内容

```bash
OCTIC_SHARE_LINK="https://octic.example.com/share/<share-id>" octic rec show <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec summary <recording_id>
OCTIC_SHARE_LINK="<share-id>" octic rec download <recording_id> --type transcript --format md

# 或直接用分享链接/裸 share ID 定位资源，不需要提前知道 recording_id（仍需已 `octic auth login`）
octic share show "<share-id>" --summary
octic share download "<share-id>" --type transcript --format md
```

### 场景 4：CI / 容器内无交互登录

```bash
export OCTIC_TOKEN=<一次性登录 token>   # 从「我的」→「机器人设置」→「Octic CLI」点「复制指令」获取；仅用于本次 login，登录成功后应 unset
export OCTIC_SERVER_URL=https://octic.example.com
octic auth login   # 首次登录：token 与服务器地址从环境变量读取，无需传任何 flag；登录通过后凭证落盘缓存
unset OCTIC_TOKEN   # 之后的命令一旦仍检测到 OCTIC_TOKEN 会直接报用法错误（退出码 2）
octic auth status   # 之后 status/rec 命令读取本地已缓存的凭证，不必再设 OCTIC_TOKEN 或重新 login
```

`octic auth status` 会先检查本地是否已有缓存凭证（`octic auth login` 落盘的那份），**没有缓存凭证时 status/rec 会直接报“未登录”**（退出码 3）——环境变量只是免去交互输入，登录动作本身仍必须执行一次；而登录完成后若仍残留 `OCTIC_TOKEN` 环境变量，任何非 `login` 命令都会因“检测到 token 直填”而报用法错误（退出码 2），而不是复用旧凭证。

## 把本 Skill 安装到 agent

`octic` 二进制内置了本 skill 的内容，装好 `octic` 之后不需要克隆仓库，直接用子命令安装：

**注意**：`octic upgrade` 只替换二进制，agent 目录里的 SKILL 不会随之自动落到新版本（本机记录过已装 agent 时会自动重装一次；没记录过或重装失败时要手动执行下面的 `--force` 安装来同步）。

```bash
# 安装到单个 agent
octic skill install --agent claude
octic skill install --agent codex
octic skill install --agent openclaw
octic skill install --agent hermes
octic skill install --agent workbuddy

# 一次装到多个 agent
octic skill install --agent claude,codex,openclaw,hermes,workbuddy

# 覆盖已存在的同名 skill 目录
octic skill install --agent claude --force

# 只演练，不实际写文件（先看看会装到哪、装什么）
octic skill install --agent claude --dry-run

# 自定义安装基目录（仅限单个 --agent）：每套 skill 落 <base>/<skill> 子目录
octic skill install --agent codex --target-dir /custom/base

# 查看当前 octic 版本支持哪些 agent、各自会装哪些 skill 及目标目录
octic skill list-agents

# 把内嵌的 SKILL.md 内容打到 stdout（或 --output <file> 落盘），不写任何 agent 目录
octic skill export
```

**不用自己选 skill**：安装集合按「平台 × agent」自动推导——`octic-cli` 对每个受支持 agent 都装；`octic-daemon` 只对支持 daemon 的 agent（openclaw / codex / claude / hermes）、且只在能跑 daemon 常驻服务的平台（Linux / macOS）上装。推导不出的组合（`workbuddy` agent、或任何 agent 在 Windows 上）会在 stderr 打一条跳过说明、退出码仍为 0，不是失败。

默认目标目录（`octic-cli` skill）：

| Agent | 目标目录 |
|---|---|
| `claude` | `~/.claude/skills/octic-cli` |
| `codex` | `${CODEX_HOME:-~/.codex}/skills/octic-cli` |
| `openclaw` | `~/.openclaw/skills/octic-cli` |
| `hermes` | `${HERMES_HOME:-~/.hermes}/skills/octic-cli` |
| `workbuddy` | `${WORKBUDDY_HOME:-~/.workbuddy}/skills/octic-cli` |

## 故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| 退出码 3，提示“未登录” | 未 `login` 过（或已 `auth logout`），本地无缓存凭证 | 到网页端/App「我的」→「机器人设置」→「Octic CLI」点「复制指令」拿一次性登录 token，执行 `octic auth login --token <one-time-login-token> --server-url <url>` |
| 退出码 2，提示 token 直填被拒绝 | 非 `login` 命令设置了 `--token`/`OCTIC_TOKEN` | 去掉该 flag/环境变量；先 `octic auth login` 落盘凭证，其余命令自动读取本地凭证 |
| 退出码 3，提示凭证无效/已过期 | 一次性登录 token 已过期，或本地凭证续期失败 | 到网页端/App「我的」→「机器人设置」→「Octic CLI」重新点「复制指令」生成新的一次性登录 token 后重新 `login` |
| 退出码 5，转写/摘要未就绪 | 转写/摘要仍在异步处理中 | 稍后重试，不要密集轮询 |
| 退出码 6，文件已存在/目标目录冲突 | `skill install --target-dir` 指向的路径已存在且非空；或 `rec download`/`share download` 的目标文件已存在 | 加 `--force`（skill install）或 `--overwrite`（download）覆盖，或改目标路径 |
| 退出码 9，被限流 | 触发接口层防爬/防爆破 | 停止重试，等待冷却后再执行 |
| 退出码 12，提示版本过旧（`octic >= X.Y.Z required`） | 当前 octic 版本低于服务端最低要求（服务端版本门槛，业务码 1006） | 跑 `octic upgrade` 升级后重试原命令；升级通道本身不受门槛影响，一定能装上 |
| `--type all` 下载报"N/M 个类型下载失败" | 部分类型未就绪（如摘要未生成）或下载中途网络失败，其余类型已下载成功 | 检查已落盘的文件，对失败类型单独重跑 `rec download <id> --type <失败的类型>` |
