---
name: weaver-e10-shared
display_name: 泛微CLI共享规则
display_name_en: Weaver CLI Shared Rules
description: weaver-work-cli 共享规则：安装检查、E10 认证、JSON 输出、高风险写入、安全边界和引用路由。
description_zh: weaver-work-cli 共享规则：安装检查、E10 认证、JSON 输出、高风险写入、安全边界和引用路由。
description_en: Shared rules for weaver-work-cli, including installation checks, E10 authentication, JSON output, high-risk writes, safety boundaries, and reference routing.
version: 1.2.3
author: 泛微网络科技股份有限公司
agent_created: true
requires:
  bins: ["weaver-work-cli"]
---

# weaver-work-cli 共享规则

所有 `weaver-e10-*` Skill 共享的底座：命令可用性、E10 认证、JSON 输出契约、大结果渲染与高风险操作。

## 连接器模式：--is-connector

同一条 `weaver-work-cli` 命令，认证来源由 `--is-connector true` 区分。**按下面的判据决定传不传，只判断一次，本任务内所有命令沿用同一写法：**

| 本次任务的情形 | 是否传 `--is-connector true` |
| --- | --- |
| 用户明确使用连接器，或上下文中要求使用连接器完成任务 | **传** |
| 本次任务或上下文中直接选择单独技能 | **不传** |
| 用户没有明确说明 | **按正在运行的这份 Skill 是哪一级定**：用户级 → **不传**；连接器级 → **传** |

- **怎么分辨级别**：技能条目标 `(user)`、从 `~/.workbuddy/skills/` 加载的就是**用户级**；标 `(connector)`、从连接器安装目录加载的就是**连接器级**。
- 同名 Skill 只会运行**用户级**那一份（WorkBuddy 按 Skill 名去重、用户级优先），所以这种情况一律按用户级处理。
- 用户级 → CLI 读取**本机活跃 profile**；连接器级 → CLI 读取**连接器托管的 eteams 认证与登录域名**。
- 连接器级**不要再写 `--profile` / `--base-url`**：认证与域名由连接器托管，手工指定会与它冲突。
- 漏传 `--is-connector` **不会报错**，但会退回本机活跃 profile，可能操作到与预期不同的租户。
- **本套 Skill 里的命令示例一律省略 `--is-connector`**（示例只示意参数形态，不代表最终命令）：执行前必须先按上表判定，命中「传」的要在 `weaver-work-cli` 之后补上 `--is-connector true`，且**本任务内每条命令都要带**——包括记忆读取、记忆保存这类无入参的命令。

## 业务 Skill 安装前版本检查

用户要求安装任一业务模块 Skill 时，必须先执行 `weaver-work-cli --version` 与 `npm view weaver-work-cli version` 比对本地版本和 npm 源版本。若本地版本低于 npm 源版本，先询问用户是否使用（推荐）最新版 `weaver-work-cli`；用户确认后先执行 `npm install -g weaver-work-cli` 更新 CLI，再继续安装业务模块 Skill。用户拒绝时，说明将沿用本地版本后再继续。

## 最高优先级：XiaoE 统一登录

只有**当前智能体身份明确是 XiaoE** 时才允许走 XiaoE 统一登录。判定依据只能是宿主/任务上下文明确标识 XiaoE、当前命令进程由 XiaoE 按次注入了成对的平台凭据，或桌面 XiaoE 注入了精确的运行时路径标记。不得把以下任一情况当成 XiaoE 宿主证据：

- 本机存在 `xiaoeUserData/tokens.py` 或 XiaoE 客户端目录；
- 环境中只有 `XIAOE_SKILL_*`、`WEAVER_XIAOE_*` 配置或历史兼容变量；
- 过去在同一台机器上登录或安装过 XiaoE。

WorkBuddy、Codex 及其他智能体一律使用 `weaver-work-cli auth login --base-url "<用户确认的登录域名>" --agent_type "<当前智能体名称>"` 走浏览器 OAuth。`auth login` / `auth oidc` 不会读取或消费 XiaoE 凭据。

`WORKBUDDY_*` / `CODEX_*` 这类通用运行时变量可能也存在于 XiaoE 启动的进程中，不能单独用来否决 XiaoE 身份。当前命令进程中的完整 `XIAOE_USER_TOKEN` + `XIAOE_DOMAIN` 是 Web/沙箱 XiaoE 的强身份信号；`XIAOE_DATA_ROOT`、`XIAOE_ROBOT_DATA_ROOT`、`XIAOE_USER_SETTINGS_PATH`、`XIAOE_ROBOT_ROOT` 是桌面 XiaoE 的精确宿主信号。若同时存在明确的 `WORKBUDDY_AGENT_TYPE` / `CODEX_AGENT_TYPE` 且值不是 XiaoE，则以后者为准并隔离到浏览器 OAuth。

### XiaoE 凭据由 CLI 独占处理（顶层强规则）

Token、Cookie、ETEAMSID 和业务凭证**只能由 `weaver-work-cli` 内部读取和消费**。Agent 禁止：

- 自行执行宿主 `xiaoeUserData/tokens.py`（或任何取 Token 的脚本）并把 stdout 读进上下文——stdout 含 ETEAMSID/Cookie/Token，会进入对话、日志与缓存；
- 执行 `weaver-work-cli auth export-weaver-env` 并 `eval` / `Invoke-Expression` 到当前 shell，或把 `WEAVER_*` 值写进命令、日志、报告；
- 要求用户粘贴、或在回复中展示、转述任何凭证。

下述 `auth xiaoe` 流程仅适用于上述已确认的 XiaoE 智能体环境；其他智能体不执行宿主脚本检测，直接进入浏览器 OAuth 流程。

需要登录态时只运行 `weaver-work-cli auth xiaoe`（业务命令也会自动尝试）。CLI 内部按「平台凭证 Node 换票 → CLI 自带 `xiaoe_env.py` 兼容换票 → 宿主 `tokens.py` → 桌面 XiaoE 客户端会话 → `WEAVER_XIAOE_*` 环境映射」顺序取凭据；其中 `XIAOE_DOMAIN` 是 XiaoE/erpa 换票服务地址，不是 E10 地址。`xiaoe_env.py` 属于 CLI npm 包，不放入 shared Skill，且只能由 CLI 内部执行和解析。失败时 CLI 返回结构化错误，Agent 不要替它执行脚本、安装 Python 依赖、手工取 Token 或自动引导用户在本地终端重复登录。

1. 未登录、登录失效、准备执行业务命令前需要确认 E10 会话，或 `auth status --no-check` 显示未登录时，先运行：
   ```text
   weaver-work-cli auth xiaoe
   ```
2. 不得因为本地存在旧 profile、`auth profile current` 指向旧环境，或 `auth profile list` 里只有旧环境，就跳过 XiaoE 统一登录；也不得先要求用户选择旧 profile 或输入域名。
3. XiaoE 统一登录由 CLI 按序取凭证：① 平台注入的 `XIAOE_USER_TOKEN` + `XIAOE_DOMAIN`，先走 Node 换票，再由 CLI npm 包自带的标准库 `xiaoe_env.py` 作兼容回退；② 宿主 `xiaoeUserData/tokens.py`（仅桌面宿主，CLI 在 macOS/Windows 都会先自动发现 XiaoE 客户端内置 Python，再回退到系统 Python）；③ 内置原生实现读取桌面 XiaoE 客户端会话换取 `eteamId`，依次查找 `XIAOE_USER_SETTINGS_PATH`、`XIAOE_ROBOT_DATA_ROOT` / `XIAOE_ROBOT_ROOT` 和默认 `XiaoE/Robots/<id>/user_settings.json`，因此会话有效时不要求存在 `tokens.py`；④ `WEAVER_XIAOE_*` 环境映射。宿主只注入 `XIAOE_DOMAIN`、`XIAOE_USER_TOKEN` 等平台变量，不会注入 ETEAMSID 环境变量，CLI 也不读取 `XIAOE_ETEAMSID`。Agent 不要打印、转述、索取或要求用户手动设置这些凭证，也不要直接执行任一脚本或因为脚本报缺依赖就让用户安装 Python 包——降级由 CLI 内部完成。
4. `auth xiaoe` 和未显式指定 `--auth` / `--profile` 的业务命令都会先尝试上述统一登录。E10 目标地址按「显式 `--base-url` → 专用 `WEAVER_XIAOE_*` E10 配置/映射 → 当前 profile → CLI 内置默认值」解析，绝不能把 `XIAOE_DOMAIN` 当作 E10 地址。Token、Cookie、ETEAMSID 仍属于凭证，禁止在回复中展示。
5. XiaoE 统一登录走不通时，CLI 必须反馈未拿到登录信息的原因。Web/Linux 环境的 `auth xiaoe` 返回 `authentication/xiaoe_web_credential_unavailable`，不会自动打开 localhost 浏览器 OAuth；macOS/Windows 桌面宿主才允许自动降级。用户明确要求网页登录、且 CLI 与浏览器同机时，再显式运行 `weaver-work-cli auth login`。
6. `auth status --no-check` 只是本地诊断，不会主动触发 XiaoE 统一登录；业务命令和 `auth xiaoe` 才会尝试写入统一登录态。具体读取 [`e10-auth-and-session.md`](references/e10-auth-and-session.md)。

`auth xiaoe` 的 preflight 会输出不含凭证的 `hostDetected`、`attempted`、`tokenScriptDetected`、`clientSettingsDetected`、`baseUrl`；命中脚本分支时会出现 `Trying XiaoE token script credential...`，命中内置 Node 会话分支时会出现 `Trying built-in XiaoE client session credential...`。成功后登录结果和 `auth status` 中的 `credentialSourceStep`、`credentialSourceType`、`credentialSourceLabel` 用于确认实际来源；`2 / token_script / xiaoeUserData/tokens.py` 表示宿主脚本换票成功，`3 / xiaoe_client / XiaoE client settings` 表示不依赖 `tokens.py`、由 Node 直接换票成功。

`auth login` / `auth oidc` 是严格的浏览器 OAuth 入口，即使同一台机器上存在 XiaoE 凭据或客户端文件，也不得先尝试 XiaoE 统一登录。未显式指定 `--auth` / `--profile` 的业务命令也只能在当前进程身份明确为 XiaoE 时自动尝试统一登录。

### 浏览器 OAuth 防挂起（顶层强规则）

`auth login`（以及 macOS/Windows 桌面宿主上的 `auth xiaoe` 降级）会在本机 `127.0.0.1:19800` 监听浏览器回调，最长等待 5 分钟。在 Agent 沙箱/托管环境中，外部浏览器无法回调容器内 `127.0.0.1:19800`，因此：

1. Web/Linux 中运行 `auth xiaoe` 时，收到 `xiaoe_web_credential_unavailable` 即反馈宿主登录态/凭据注入异常，不要自动切换 `auth login`，也不要要求用户去本地终端重复执行命令。
2. 只有用户明确要求浏览器授权且确认 CLI 与浏览器同机时，才运行 `auth login`；仍须限时等待，超时未完成即终止并反馈。
3. 用户无法本地执行浏览器登录时，经用户明确同意后改用保底方式 `weaver-work-cli auth set --eteamsid "<ETEAMSID>" --base-url "<用户确认的登录域名>"`，由用户从已登录浏览器主动提供 ETEAMSID；Agent 不得索取、展示或转述该值。
4. 严禁不加限制地前台运行 `auth xiaoe` / `auth login` 并等待 OAuth 回调。

## 通用准则

1. 执行业务命令前先确认用法：优先读当前业务 Skill 和被路由到的 reference，必要时跑对应命令的 `--help` 或 `schema`，不要猜参数。

2. 首次使用先确认 CLI 可用：

```text
weaver-work-cli --version
weaver-work-cli doctor --e10
```

如果命令不可用（尤其是 `command not found` 或退出码 127），先读 [`weaver-e10-installation.md`](references/weaver-e10-installation.md)：先区分「已安装但不在 PATH」与「未安装」（前者只需把全局 bin 目录前置到 PATH，不要重装），确认未安装后再让用户选择 npm 源安装或本地代码安装；本地代码必须提供仓库目录。用户明确同意后才执行 `npm install -g weaver-work-cli`、`npm install`、`npm run build` 或 `npm link`。

3. 业务输入只描述业务对象和意图。认证、Cookie、ETEAMSID、业务 Token、接口地址和回查细节都由 CLI 托管。调用业务 operation 时，简单 JSON 优先使用 `--input-json '<json>'`；多步传递优先用 shell 变量，超大结果用 `--input-ref <refId>`，不要把上一步 stdout 写入临时文件再作为下一步输入。`--input <path>` 只用于用户提供的 JSON 文件或明确的文件业务输入；复杂 JSON 可在确有需要时使用，不能用它中转 CLI 上一步的输出。只有确认当前 shell 能稳定传管道时才使用 `--input -`。生成或改写 docs、reference、提示词和命令示例时，必须同时兼容 Windows 和 macOS/Linux；涉及 JSON 输入、用户目录、路径分隔符、Python 启动器、文件删除、目录查看或文件比对时，同时给 Windows PowerShell 与 macOS/Linux（bash/zsh）两套示例。Agent 先根据当前系统和 shell 选择对应示例，不确定时用 `node -p "process.platform"` 判断。Windows/PowerShell 下不要使用 `printf`、`$HOME/...`、`~/...`、bash 反斜杠续行、`rm/ls/diff/python3` 等 Unix-only 写法。

   CLI 对 `--input <path>` 默认只告警；需要拒绝文件 JSON 输入时使用 `--no-file-input`，或设置 `WEAVER_WORK_CLI_FILE_INPUT=deny` / config `io.fileInput=deny`。一次性允许时可用 `--allow-file-input`。`--input -` 和业务附件参数（如 `--file`）不受这条策略影响。

4. 认证诊断只能通过 `weaver-work-cli auth root/status/profile list/profile current`、`weaver-work-cli doctor --e10` 和业务命令 JSON 错误完成。禁止直接列目录、打印、复制或解析用户态认证目录和认证文件。

5. 涉及附件、图片、本地文件、远程 URL 文件或文件内容解析的操作，执行前必须先向用户说明：文件内容可能会被上传到业务系统、OCR/解析服务，并可能进入当前大模型上下文用于理解和处理。必须等待用户明确确认后，才继续上传、读取、解析、OCR、导入或复用已上传文件对象；用户未确认时只说明风险和所需确认，不执行相关命令。

6. **同名不同包不要混用**：环境中可能并存另一个独立的 E10 登录 CLI（例如 `e10-login`，版本号可能带 `xiaoe` 后缀），它提供登录与状态查询，但其登录态**不会**被 `weaver-work-cli` 复用。可用性一律以 `weaver-work-cli --version` 为凭，登录态一律以 `weaver-work-cli auth status --no-check` / `doctor --e10` 为凭；不得因为另一个 CLI 显示已登录就判定本 CLI 可用，也不要用另一个 CLI 的输出替代本 CLI 的认证结论。

## 超时与重试预算

命令执行必须有界。默认按下表 timeout（超时）预算约束；超时即终止并反馈，不允许无界等待或无界重试。

| 命令类别 | 单次超时预算 | 重试策略 |
| --- | --- | --- |
| 登录类（`auth xiaoe` / `auth login` / `auth set`） | 300 秒（5 分钟，后台运行 + 限时观察） | **禁止自动重试**；出现降级信号立即终止并移交用户 |
| 只读业务查询（list / get / schema / preview） | 120 秒 | 最多 1 次；仅在网络类错误且未产生副作用时 |
| 写入类（prepare / apply / import / update / delete） | 600 秒（10 分钟） | **禁止自动重试**（存在重复入账/重复提交风险）；结果不确定时只补一次只读回查 |
| 安装与构建（`npm install` / `npm run build` / `npm link`） | 1800 秒（30 分钟） | 禁止自动重试；失败即停止并报告，不加 `sudo` |
| 诊断类（`--version` / `doctor` / `auth status --no-check` / `skills list`） | 60 秒 | 最多 1 次 |

预算是**上限**而不是必须等满：登录类一旦出现降级信号（`Open browser login` / `Waiting for E10 authorization callback` / `Could not open browser automatically`）就立即终止，不必等满 300 秒；写入类一旦拿到确定结果或返回 `partial` / `write_uncertain` 也立即停止。慢环境（容器冷启动、大依赖安装、批量导入）可以撑到上限，快环境不要人为等待。

补充约束：

1. 登录类命令超时后，**不得**换域名或换命令反复重试；先按 [`non-interactive-environment.md`](references/non-interactive-environment.md) 判定环境并反馈原因。
2. 写入类命令返回 `partial` / `write_uncertain` / 网络中断 / 回查失败时，立即停止写流程，只做一次只读回查确认。
3. 任何命令被外部超时掐断（退出码 124）都视为超时失败，按上表处理，不得静默重跑。

## 输出契约

Agent 调用业务能力时优先加 `--json`。成功看进程退出码 0 和 JSON envelope 的 `ok=true`；失败看非 0 退出码和 stderr JSON 的 `error.type/subtype/message`。不要用老式 `code == 0` 判断成功。

### 请求头契约（E10 接口调用强制）

**本段是请求头三件套的唯一权威声明**：业务 Skill 与 reference 一律引用本段，不再各自复制完整代码块；引用处只需一行紧凑指针指向本节（发布门禁 B10c 已停用，不再按技能目录逐个校验这三项参数名）。

`weaver-work-cli` 发往 E10 的每个请求都由 CLI 统一携带以下三项用户信息参数，缺一不可（业务入参里不要传这些字段，也不要手工拼装请求头）：

```text
Cookie: <weaver-work-cli 托管登录态的完整原始 Cookie 串，原样透传，禁止裁剪/去重/改写>
eteamsid: <weaver-work-cli 托管登录态的 ETEAMSID>
User-Agent: AgentType=<agentType>,IsAgent=true
```

登录与会话统一由 `weaver-e10-shared` 共享规则和 `weaver-work-cli auth ...` 命令体系托管；业务 Skill 不得自建登录流程，也不得读取、打印、转存任何认证目录或凭证。

返回 `partial`、`write_uncertain`、登录失效、回查失败或网络中断时，立即停止当前写流程，不自动重试写入或删除。

当前 CLI 的普通大小 JSON 输出是完整 envelope；超过预算时会暂存完整结果，并在 stdout 返回精简摘要和 `refId`。CLI 没有飞书 CLI 那种通用 `--jq`、`--format table/ndjson` 或 `--page-all`。列表、详情、OCR、导出等可能产生大响应时，必须先读 [`json-output-contract.md`](references/json-output-contract.md) 的“大结果渲染与提效规则”：默认小页读取、只保留任务所需字段、分页有预算，回复用户时给摘要，不要原样粘贴超长 JSON。

## Reference 强触发索引

命中任一触发条件时，执行下一步前读取对应 reference。命中多条时按表中顺序读取，同一 reference 只读取一次。

| 强触发条件 | Reference |
| --- | --- |
| 命令不可用、Agent 自动安装 CLI、安装、构建、全局 link、检查可用 skill | [`weaver-e10-installation.md`](references/weaver-e10-installation.md) |
| 登录、profile、E10 会话、baseUrl、auth 文件、`WEAVER_*` / `XIAOE_*` 环境变量；**未登录或登录失效（仅 XiaoE 智能体环境先自动尝试，其它场景需要用户提供登录域名）** | [`e10-auth-and-session.md`](references/e10-auth-and-session.md) |
| 容器/沙箱/CI/无 GUI 环境、无人值守、登录类命令超过 180 秒未结束、出现 `Open browser login` / `Waiting for E10 authorization callback` / `Could not open browser automatically`、显式传域名后仍降级、认证目录 `EACCES` | [`non-interactive-environment.md`](references/non-interactive-environment.md) |
| 判断 stdout/stderr、JSON envelope、退出码、脚本封装、自动化调用、大结果渲染/分页控量 | [`json-output-contract.md`](references/json-output-contract.md) |
| 准备执行写入/删除/导入/更新、遇到 `confirmation.required`、`partial.write_uncertain`、网络中断或回查失败 | [`high-risk-write.md`](references/high-risk-write.md) |

## 安全规则

1. 禁止输出或索取 Cookie、ETEAMSID、业务 Token、access token、app key、app secret。

2. 禁止读取、列出、打印、复制或解析任何 `e10-login` / `e10-cli` 历史认证目录，例如用户主目录下的旧 `.e10-cli`；也禁止直接读取 `auth`、`config.json` 或 Keychain 项来推断登录态。

3. 禁止绕过 `weaver-work-cli` 直接 `curl`、`fetch`、浏览器自动化或访问 E10 业务接口。

4. 写入、删除、导入、更新等高风险操作必须使用 CLI 提供的确认流程；业务 Skill 要求 `prepare -> apply` 时，必须把 prepare 返回的 continuation 原样传给 apply，并在 apply 前取得用户明确确认。

5. 不能猜测 CLI 未暴露的内部字段、接口路径、ID 解析逻辑或回查语义。

6. 未登录或登录失效（`authentication` / `session_expired` / `auth status` 未登录）时，XiaoE 宿主环境必须先运行 `weaver-work-cli auth xiaoe` 尝试统一登录；统一登录不可用时必须反馈 CLI 原因。Web/Linux 环境不得把 `xiaoe_web_credential_unavailable` 自动改成浏览器 OAuth 或本地终端操作；只有用户明确要求且 CLI 与浏览器同机时才运行 `auth login`。执行浏览器登录时必须传入 `--agent_type "<当前智能体名称>"`，让授权确认页展示发起登录验证的智能体；**禁止**使用示例占位域名登录，也禁止在拿到域名前反复重试业务命令。具体流程读取 [`e10-auth-and-session.md`](references/e10-auth-and-session.md)。

7. 附件、图片上传或文件解析属于敏感数据处理。即便 operation 风险等级不是高风险写入，也必须先取得用户对“文件内容可能进入大模型/外部解析服务”的明确确认；不能把用户仅提供文件路径或文件名视为同意解析或上传。

8. 在 XiaoE 宿主环境中，CLI 按顺序从平台注入凭证（Node 换票后由 CLI npm 包自带 `xiaoe_env.py` 兼容回退）、宿主 `xiaoeUserData/tokens.py`、内置原生实现读取的桌面 XiaoE 客户端会话（宿主注入路径优先、默认 `XiaoE/Robots/<id>/user_settings.json` 回退）、`WEAVER_XIAOE_*` 环境映射读取支持统一登录的凭证（宿主不会注入 ETEAMSID 环境变量）。桌面会话有效时 Node 可直接换票，不要求存在 `tokens.py`；shared Skill 也不携带任何 Token 脚本。`XIAOE_DOMAIN` 仅是 XiaoE/erpa 换票服务地址；E10 目标按显式参数、专用 E10 配置/映射、当前 profile、CLI 默认值的顺序解析。业务命令会在未显式指定 `--auth` / `--profile` 时自动优先尝试 XiaoE 统一登录；Agent 也可以显式调用 `weaver-work-cli auth xiaoe`。不要要求用户提供、展示或手动设置这些凭证。Web/Linux 失败时反馈结构化错误，不自动降级浏览器；macOS/Windows 桌面宿主可由 CLI 自动降级。
