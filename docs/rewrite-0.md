# Tepeu 从零重写 — 规划

**状态**：标本已迁 `legacy/os-9/`。第一刀进行中：`tepeu/types/identity` 待人审。  
**标本**：`legacy/os-9/os/`、`legacy/os-9/host/`。新库根 `tepeu/`。  
**本文用词**：模块、接口、注册表、分发、授权、持久化、事件日志、控制循环。

2026-09-06：从零规划。产品行为可以参考现行实现。

---

## 1. 产品

单用户、单机、单进程 CLI。

1. 对话：控制循环调用模型与工具，结果写入会话状态。
2. 斜杠命令：走 `commands` 命令表。host 把 `/` 开头的行交给表。第一刀：`/help` `/approve` `/status` `/btw`。`/compact` 以后加。闲着时 loop 自己压缩（已有）。
3. 授权询问：判定为需批准时，由操作者确认后单次生效。
4. 旁问 `/btw`：看当前对话，问一句，印出答复。不写事件日志，不调工具。用量进用量流水。第一刀只在 loop 空闲时可用。主任务还在跑时也能问，以后做。
5. 第一刀一个默认对话。一个 `SessionId`，进程退出后记录仍在。会话列表与切换以后做。
6. 默认授权：问模型、读文件 → `allow`。写文件、创建进程 → `require_approval`。
7. 第一刀 llm 挂 `fake`，回固定文本。结构可跑通。真模型：host POM 另依赖一份实现，不改层。

---

## 2. 运行时

| 项 | 规定 |
|----|------|
| 进程 | 一个 JVM |
| 入口 | Spring Boot（无 Web）。解析命令行、按 POM 加载 persist / llm 实现、交给 load 装配、读取密钥 |
| 持久化 | 见 §3.1。实现由 host POM 选定 |
| 模型适配 | 见 §6。实现由 host POM 选定 |
| 工作区 | 宿主指定的目录。文件与子进程限制在该目录内。隔离程度如实报告 |

host 的 POM 依赖哪个实现，就加载哪个、用哪个。斜杠 `/approve` 是操作者对审批记录的写入。

---

## 3. 会话状态

`session` 是一次对话存下来的状态，用 `SessionId` 标识。进程退出后记录仍在库里。第一刀一个默认对话。会话列表与切换以后做。

每个会话标识下三类存储，互斥：一条数据只进其中一类。

| 存储 | 写入语义 | 内容 |
|------|----------|------|
| 事件日志 | 只追加 | 用户输入、助手输出、工具请求与结果、推理/计划等须可复核的事实 |
| 寄存器 | 覆盖写 | 控制状态（例如循环是否空闲）。进程重启后按键读取 |
| 用量流水 | 只追加 | token / 费用 |

另外两个，与事件日志分离：

| 存储 | 作用 |
|------|------|
| 收件箱 | 输入队列。入队、按优先级领取（租约）、确认或退回。供控制循环取下一条输入 |
| 操作审计 | 操作者动作（批准、拒绝等） |

通知向界面推送。权威状态是上表。

完成判定：只读事件日志，单一判定点。终态以该判定为准。

### 3.1 持久化（`persist`：父 POM + 多实现）

`persist` 是父 POM：上面是接口，下面是实现。

```
persist/                 packaging=pom
  api/                   读写接口
  sqlite/                第一刀实现。host POM 依赖这份
```

以后再加实现模块，仍只改 host 的 POM 依赖。session、policy **只依赖 `persist-api`**。host 的 POM 依赖哪个实现，就加载哪个、用哪个。

JDBC、连接池、MyBatis、SQL 方言、对象存储 SDK 写在对应实现模块里。

`persist` 的接口服务 Tepeu 自己的状态（会话记录、审批记录等）。工作区读文件、写文件、创建进程是 `execution` 的接口，经 `dispatch.invoke` 调用。两套接口、两套类型。

方法按语义命名：

| 口 | 方法名（提案） |
|----|----------------|
| persist 结构化记录 | `append`、`get`、`put`（覆盖） |
| persist 字节对象（若有） | `putObject`、`getObject` |
| execution 工作区 | `readFile`、`writeFile`、`spawn`（或 syscall 名 `fs.read` / `fs.write` / `proc.spawn`） |

---

## 4. 具名操作与授权

**具名操作**：用字符串名称调用的能力，例如模型生成、读文件、写文件、创建进程。

**调用分发**（模块名 `dispatch`）：名称 → 处理函数 的注册表。方法名 `invoke(context, name, args)`：取消检查 → 授权 → 调用处理函数。未注册则失败。未装配授权则拒绝。

一次调用对应一个处理函数。目录名是 `dispatch`，方法名是 `invoke`。调用信封的类型在 `syscall`。

**授权**：三值：`allow` | `deny` | `require_approval`。需批准时登记审批（会话、名称、参数摘要），操作者决定后单次消费。多条规则聚合：`deny` 优先于 `require_approval`，优先于 `allow`。

第一刀默认矩阵：`llm.generate`、工作区读文件 → `allow`；工作区写文件、创建进程 → `require_approval`。其余未登记 → `deny`。

控制循环经调用分发使用工具。

---

## 5. 控制循环

从收件箱领取 → 读事件日志 → 构造模型请求 → `invoke` 模型生成 → 若返回工具请求则再 `invoke` → 追加事件 → 完成判定。

空闲时可做日志压缩（仍经调用分发，仍写事件日志）。

轮次是循环自己的控制流。寄存器保存循环状态（空闲/运行），供查询。

---

## 6. 模型适配与工作区

**模型适配**（`llm`：父 POM）：网关统一调用。从事件日志派生模型可见序列（纯函数、可测试相等），再交给后面的实现做协议与传输。

```
llm/                     packaging=pom
  gateway/               统一调用口。调用方只进这里
  fake/                  第一刀：挂在网关后。离线，不发 HTTP，返回固定文本
```

loop / dispatch 只调网关。load 把实现注入网关。host 的 POM 依赖哪个实现，就加载哪个，交给 load。

系统提示由 `load` 装配，交给 loop / gateway 使用。

**工作区执行**：该目录内的读/写/创建进程。授权在调用分发处完成。操作系统级隔离在创建进程时实施；隔离失败时失败可见。

---

## 7. 模块划分

一个职责一个 Maven 模块。共享类型是编译期依赖。

| 模块 | 职责 |
|------|------|
| persist | 父 POM：`api` + 实现。第一刀实现 `sqlite`。host POM 依赖这份 |
| session | 一次对话的持久状态：事件日志、寄存器、用量流水、收件箱、操作审计 |
| policy | 授权判定、审批存储 |
| dispatch | 名称注册表与 `invoke` |
| llm | 父 POM：`gateway` 统一调用；实现挂在网关后。第一刀 `fake` |
| execution | 工作区文件与进程 |
| loop | 控制循环、完成判定、压缩触发 |
| identity | 共用类型：谁、哪个工作区、哪一次对话、调用上下文 |
| syscall | 共用类型：一次具名操作的名称、参数、结果、用量 |
| conformance | 测试套件：对各模块接口的契约测试 |
| commands | 斜杠命令表。名称 → 处理函数。第一刀：`/help` `/approve` `/status` `/btw`。`/btw` 经 dispatch 调 llm 网关，不调工具、不写事件日志 |
| load | 装配：注入实现、拼系统提示、登记斜杠命令。看见四层与 commands |
| host | Spring Boot（无 Web）。进程入口、CLI。`/` 行交给 commands。第一刀 POM 依赖 persist-sqlite、llm-fake、load |

### 7.1 分层目录（提案）

库根 `tepeu/`。其下四层 + `commands` + `load`。下层在上。层与层之间依赖只朝下。`commands` 看见 kernel。`load` 看见四层与 commands。四层不知道 load / commands。

persist：父 POM，`api/` + 实现。llm：父 POM，`gateway/` 统一调用，实现挂在网关后。第一刀每个父 POM 只落一份实现。host 的 POM 依赖哪个实现，就加载哪个，交给 load。

```
tepeu/
  types/                    层0  共用类型
    identity/                    谁、工作区、哪一次对话
    syscall/                     一次调用的信封

  persist/                  层1  父 POM
    api/                         读写接口
    sqlite/                      第一刀实现

  kernel/                   层2  账 + 门
    session/                     一次对话的持久状态  → persist-api
    policy/                      授权                  → persist-api
    dispatch/                    调用分发              → policy

  run/                      层3  干活与循环
    llm/                         父 POM
      gateway/                   统一调用口  → session
      fake/                      挂在网关后。第一刀：离线，不发 HTTP，返回固定文本
    execution/                   工作区文件与进程
    loop/                        控制循环  → session, policy, dispatch

  commands/                 斜杠命令表  → session, policy, dispatch
                            第一刀：/help /approve /status /btw

  load/                     装配：注入实现、拼系统提示、登记斜杠命令

  conformance/              测试套件

host/                       Spring Boot（无 Web）。CLI。`/` 行交给 commands
                            第一刀 POM 依赖 persist-sqlite、llm-fake、load
```

`execution` 现规划是单模块。以后有第二份实现，再按 persist 切，第一刀仍只落一份。

跨对话记忆（memory）以后进 `kernel/memory/`，与 session 同层：依赖 `persist-api`。session 是一次对话；memory 是跨对话的记下与召回。调用方是 `run/loop`、`llm-gateway`。persist 不知道 memory。第一刀不建此模块。

仓库根目录的 `memory/` 是交接文档，与这个模块无关。

---

## 8. 依赖方向（编译期与运行时）

依赖只朝下。被调用方不知道调用方：Maven 依赖、import、运行时回调都朝下。

例：`persist` 不知道 `session`。`session` 是调用方，依赖 `persist-api`。`persist` 的接口与实现里没有 session / policy / loop / host 的类型。

同一条：

| 被调用方 | 调用方 |
|----------|--------|
| persist/api | session、policy（load 注入） |
| llm/gateway | 经 dispatch 登记的处理函数。网关后的实现由 load 注入 |
| policy | dispatch |
| session | loop、llm-gateway、commands |
| dispatch | loop |
| policy、session、dispatch | commands |
| 四层与 commands | load（装配、登记斜杠） |
| load | host |

```
identity, syscall          值对象
persist/api                读写接口
persist/*-impl             只由 host 依赖，交给 load
llm/gateway                → identity, syscall, session
llm/*-impl                 只由 host 依赖，交给 load 注入网关
session, policy            → identity, syscall, persist-api
dispatch                   → identity, syscall, policy
execution                  → identity, syscall
loop                       → identity, syscall, session, policy, dispatch
commands                   → identity, syscall, session, policy, dispatch
load                       → 四层 + commands（装配、拼系统提示、登记斜杠）
host                       → load + 选定的实现。Spring Boot（无 Web）
```

loop 经 dispatch 调用处理函数。斜杠经 commands。host 的 POM 依赖哪个 persist / llm 实现，就加载哪个，交给 load。

---

## 9. 标本

标本在 `legacy/os-9/`。新库根是 `tepeu/`。本规划是条文来源。

---

无对话直接 `invoke`：以后做。第一刀不做。

定时入队：以后做。第一刀不做。若做，状态先放 session 寄存器（每个对话一个闹钟）。新表 / `/tasks` 另批。

## 10. 未决

第一刀无未决。以后：`/compact`、invoke、定时（寄存器）、`/btw` 与主任务并发、会话列表、`kernel/memory/`、llm / persist 的下一份实现。

---

## 11. 规划阶段工作方式

- 一次只定一层：模块表 → 接口 → 一个模块的实现。
- 父 POM 第一刀只落一份实现。以后再加实现模块。
- 新模块、新公共类型、schema、第二处完成判定：先写入本文未决表并批准。
- 新依赖只许朝下。被调用方不知道调用方。
- 对照现行代码只为确认行为。
