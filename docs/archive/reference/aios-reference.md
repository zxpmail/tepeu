# AIOS 对 Tepeu OS 的启示（对账与镜鉴）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../../../memory/decisions-log.md) + [os-baseplate.md](../../os-baseplate.md) 为准。
> **来源**：`E:\work\AIOS-main`（AIOS: AI Agent Operating System，Rutgers agiresearch，COLM 2025 论文实现，Python + 261 行 Rust 脚手架；本快照为 v0.2.2 后 hooks 化重构版）。2026-08-16 四路源码探查（syscall 层 / 调度器 / 上下文与记忆存储 / LLM·工具·hooks·Rust）。非 git 仓库，日期弱钉。
> **四参照定位**：CC（agent 巨石）· Pi（agent 极简）· TriniOS（真 OS 镜鉴）· **AIOS（同名学术前辈）**——「LLM as OS, Agents as Apps」的原命题者，tepeu 概念最直接的先行实现。

---

## 1. 总判断

1. **AIOS 是四参照里与 tepeu 命题重合度最高的**（syscall 表 / 调度器 / 上下文管理 / 记忆 / 存储 / 工具全都有对应模块），也因此它的**实现现状对 tepeu 最有信息量**：论文概念与可用代码的落差是四个参照里最大的。
2. **核心贡献的真实成色**：论文卖点「Agent 上下文切换」（时间片打断生成 + KV-cache 检查点恢复）——实际 `use_context_manager` **默认关闭**、只对本地 HF 模型真成立；API 模型路径「恢复」= 丢弃部分输出整请求重发（重复计费）；且 syscall 重建时 **pid 漂移**（新对象领新 pid），旧检查点成孤儿，续算实际接不上（`syscall.py:110-127` vs `simple_context.py:186`）。RR 调度器的 LLM 路径在本快照**首个请求即 TypeError**（`rr_scheduler.py:178-181` 把单个 syscall 当 batch 迭代）。
3. **对 tepeu 的最大价值是双面的**：一边是**唯一真做过 Agent 调度/挂起恢复的先行者**（有可直接借鉴的原语与教训）；一边是**最大的 fail-open 反面标本**（与 tepeu 全部 fail-closed 裁决逐一相反）——公开发表、被广泛引用的系统尚且如此，反证 tepeu 把 fail-closed 当纪律而不是口号的必要性。

## 2. 对账表：tepeu 冻结的规矩 × AIOS 现状

| # | tepeu 概念 | AIOS 现状 | 判定 |
|---|-----------|-----------|------|
| 1 | 能力总线统一入口 + Policy + 卫兵 | `/query` → isinstance 硬编码分发 + 字符串 switch；**入口零策略**（无超时/取消/配额/身份） | ⚠️ 反面：无 chokepoint 策略层的「syscall 层」只是命名 |
| 2 | fail-closed（异常规范化为拒绝） | **多数 fail-open**：storage 错误转成功字符串返回（`lsfs.py:267`）、写屏障超时放行（`write_barrier.py:341`）、LLM JSON 解析失败返回编造默认值（`syscall.py:482`）、调度器吞异常致 `join()` 永久挂起（`fifo_scheduler.py:149-152`） | ⚠️ 最强反证：tepeu d7e053f 的异常规范化路线正确 |
| 3 | 错误封闭 union | 无 errno 体系；开放 int 伪 HTTP 码 + 自由字符串三套并存 | ⚠️ 反面（与 TriniOS 同判） |
| 4 | 身份随消息（TurnContext） | `agent_name` 裸字符串无认证；`user_id` 靠私有属性 hack 传递；访问控制沉到最底层 provider 过滤 | ⚠️ 反面 |
| 5 | 取消传播拓扑（第四轮 B6） | **取消结构性缺席**：Future 不可 cancel、队列不可清单项、无 kill | ⚠️ 反面：没有取消的调度器只是排队器 |
| 6 | 配额限流入口卫兵 | 无界队列 + 每批 `max_workers=len(tasks)` 线程池 + 零重试零退避（429 直接终态错误） | ⚠️ 反面 |
| 7 | Metering / usage ledger | **零记账**：usage 打一行日志就丢；SmartRouting 查单价却不算账 | ⚠️ 反面 |
| 8 | 三 store（entries/registers/ledger） | context_dict 是**寄存器**（KV-cache 断点，键=pid）但**键不稳定**（pid 漂移）；entries 无对应物（对话历史在 agent 侧 SDK 手里，内核无状态）；ledger 无对应物 | ⚠️ 半面镜：寄存器形态印证，键稳定性是生死线 |
| 9 | surface 替换压缩 | **完全没有**——内核对 messages 零处理；哲学是「少注入」而非「压缩已有」 | 空白：tepeu 差异化点 |
| 10 | LoopRuntime 三态 / maintenance 窗口 | 「两次 syscall 之间」归 agent 线程，内核被动（RPC 进微内核模型）；无维护窗口概念 | ⚠️ AIOS 回答更弱；tepeu 三态仍是更强答案 |
| 11 | 调度公平（§8 显式债务） | FIFO + 挂起者**排队尾**（饿死）；`priority` 是死字段无人读；论文版优先级队列已拆除 | ⚠️ 双面：AIOS 证明实现公平是真工作；tepeu 挂账不假装是对的 |
| 12 | 隔离（SandboxPolicy/命名空间） | Storage **零隔离**：绝对路径直接写宿主任意位置、`share` 把文件上传公网 transfer.sh、Redis 无认证 | ⚠️ 最重反面教材 |

## 3. 值得借鉴（真金）

1. **挂起信号的最小协议**：`LLMResponse.finished` 布尔位 + `status("suspend")` 一个字段把「生成中断」从执行层传到调度层；重试循环放执行器里（同 id 重建重入队），**挂起对 agent 透明**——公平策略可独立演进不污染 agent API。
2. **「哪里能切」的诚实判断**：时间片只施加于流式生成（有天然无损停点）；**带工具调用/JSON 结构化输出的请求一律不可挂起**（一口气跑完，`simple_context.py:359-378`）。tepeu 的 now 级抢占（第四轮 B4）应同款：只切流式生成边界，工具执行中途不无损切——与 pi C5（steering 打不断流是缺陷）合起来是完整边界：**能切流式的 chunk 边界，不能切结构化输出与非幂等工具**。
3. **调度指标随响应返回**：每个 syscall 返回 `start/end/waiting/turnaround_times`（`syscall.py:208-214`）——OS 作业调度指标直接给调用方。与 TriniOS「reply 基座带 token_count+latency」**独立收敛**，两票加持 tepeu `SyscallResult` 基座带计量槽位（该候选已在 TriniOS 轮挂账）。
4. **写屏障三件套**：入队 stamp 全局单调 seq → 读前 snapshot 高水位 → 只等 ≤snapshot 的写、失败也 release、`stats()` 诊断（`write_barrier.py` 全文件）。「全局单调序号而非 per-user 锁，保证晚写不阻塞早读」（:115-118 注释）——tepeu ledger 的 read-your-writes 直接适用；**但其超时 fail-open 语义须再裁**（见 §5-C6）。
5. **记忆注入管线的三个好件**：缺 `user_id` 即 fail-closed 拒绝的分区过滤（`context_injector.py:347-367`）；top-k 后过滤前的 **4× 超取**；每次注入返回 diagnostics 字典（可观测做成 API 契约）。
6. **动态 provider 注册闭环**：探测端点 → 锁内幂等注册 → 刷新路由可见性（`adapter.py:315-376`）——tepeu LlmProvider 热注册的最小可行形态。
7. **KV-cache 检查点**（概念）：执行态保存/恢复的第二层（语义层之下）；对 tepeu 是远期参照（先决条件：寄存器键稳定）。
8. `/proc` 目录 + `ps` 端点：进程表落 JSON 文件，审计/调试零成本。

## 4. 反面教材速查（tepeu conformance 用例的弹药）

| AIOS 之坑 | 落到 tepeu 哪个测试 |
|---|---|
| 调度器吞异常 → 调用方永久挂起（不 event.set()） | 总线/卫兵 conformance：**每条失败路径必须到达终态**（已有，加用例名） |
| status="error" ≠ "done" → 无限重投活锁 | Loop conformance：转移表封闭 + 重试上限/熔断 |
| storage 错误当成功字符串返回 | 失败可见 conformance：错误不得伪装成功 |
| 工具 schema 宣称 delete_file 但不实现（落到 "not supported"） | 工具注册 conformance：**宣告的能力必须可调用**（schema 与实现一致） |
| 每 syscall 只执行第一条 tool_call 其余静默丢弃 | 工具批执行 conformance：全批结果一一对应 |
| 上下文恢复存而不读（API 路径死数据） | 寄存器 conformance：写入的恢复状态必须被消费或显式淘汰 |
| 重入队领新 pid → 旧检查点孤儿 | **寄存器键跨重入队稳定**（预留 id 的价值，见 §5-C2） |
| hooks 目录名是 DI 布线而非拦截点 | 命名诚实：tepeu「卫兵」必须有真实拦截面 |

## 5. 严苛节（对 tepeu 自身）

**C1 论文级系统也会 fail-open——兑现率教训。** AIOS 公开发表（COLM 2025）、被广泛引用，但核心特性默认关闭、调度器路径损坏、死代码成片（`syscall/types/`、`schema.py`、`factory.py` 引用不存在的模块）、热路径残留 `/tmp` 调试写盘。**这不证明他们差，证明「论文概念→可用内核」的距离本身就是产品**。tepeu 的优势恰恰是不背论文叙事债：底板债务表 + 悬置规矩是主动管理这层距离的制度。同时自警：tepeu 五轮对账 vs 只落码 ①②，距离同样存在——**AIOS 是「概念跑赢实现」的学术版，TriniOS 是宣言版，tepeu 别做工程版**（C3 纪律继续执行）。

**C2 寄存器键稳定性是生死线。** AIOS 的 `context_dict`（KV-cache 断点）就是第五轮裁的「寄存器」，但因 syscall 重建时 pid 漂移而失效——**寄存器的键必须跨挂起/重入队/重启稳定**。tepeu 的预留 id（pi 借鉴）+ 单调 seq + delegationDepth 单调下界同属一族：**可变状态的键是一等设计对象**。落码 ② conformance 时应有「重入队后寄存器键不变」用例。

**C3 调度公平债务的 AIOS 实证。** AIOS 实现了调度（FIFO/RR+时间片）但公平性退化（挂起排队尾饿死、priority 死字段）。tepeu §8 把「调度公平/优先级队列」挂显式债务是对的——**挂账不假装，比实现了但坏掉诚实**。将来实现时的最小公平件：挂起者重入队不排尾（aging）+ CircuitBreaker 作用域（第四轮 B7 已裁）。

**C4 抢占边界收敛。** AIOS（只切流式）+ pi（切不了流式是缺陷）+ tepeu 第四轮（now 级抢占）三方合流成完整规则：**now 级抢占切流式 chunk 边界；结构化输出与非幂等工具不可无损切，只能等完成或整体取消**。此规则无需新裁决（第四轮 B4+B6 已覆盖），写入 Loop 端口设计说明即可。

**C5 计量槽位两票收敛。** TriniOS（reply 基座带 token/latency）+ AIOS（调度指标随响应）独立收敛。维持 TriniOS 轮的挂账：归 `llm.*`/Metering 切片顺路兑现，不悬空立项。

**C6 写屏障超时语义待裁（唯一新候选）。** AIOS barrier 超时 fail-open（读方拿到可能不含刚写的数据）。tepeu 若为 ledger 引入 read-your-writes barrier，**超时语义必须显式裁决**：fail-open（可用性优先，带降级标记）vs fail-closed（一致性优先，拒绝读）。倾向后者（与总线 fail-closed 同族），但归 ledger/Metering 切片时裁，现在不裁。

**证据链**：四路报告行号未逐一复核（抽查以机制自洽交叉验证为准）；快照无版本钉；`use_context_manager` 默认值、RR 损坏路径、pid 漂移三处关键论断在两路报告中独立出现，互证成立。

## 6. 不照搬清单

- 全部实现（fail-open 语义、零隔离 storage、无界线程池、死代码）
- 「内核无状态、对话历史在 agent 侧」的哲学（tepeu 双真相/journal-first 是反方向的有意选择）
- SmartRouting 的 k-NN+ILP 选型（tepeu ModelRouter 明裁「默认透传，不智能分类」）
- aios-rs（0% 脚手架；路线图表述可作未来 Rust 化的计划模板）
- React 风格 `useXxx()` 布线层（tepeu 有 compose 接线层，职责相同形态各异）

## 7. 对下一刀的直接影响

1. **② conformance 套件新增一批用例名**（§4 表右列）：失败必达终态、错误不伪装成功、宣告能力必须可调用、工具批一一对应、**寄存器键跨重入队稳定**（C2）——全部落进套件第一版。
2. 抢占边界（C4）与计量槽位（C5）分别写入 Loop 端口设计说明与 `llm.*` 切片，无需新裁决。
3. C6（barrier 超时语义）挂在 ledger/Metering 切片待裁，记入悬置清单。
4. 切片优先级不变：② conformance → `llm.*` 断言 → ③ Loop 端口。

---

## 8. GitHub HEAD 复核（2026-08-31）

来源：https://github.com/agiresearch/AIOS（README + `aios/{scheduler,syscall,context,memory,storage,tool}` 目录仍在）。未再 clone、未重跑 08-16 行号。

模块还是那一套：FIFO/RR 调度器、syscall 链、上下文/记忆/存储/工具当资源管理器。Cerebrum 仍是 SDK。Computer-use 走 VM+MCP。`aios-rs` 仍自称脚手架。

**不新吸收、不新挂 §8.5。** C4 抢占边界、C5 计量槽、C6 写屏障已在底板落地或挂过多副本。内核无调度器——不为了更像这篇论文开调度切片。fail-open / 零隔离 / 对话历史在 SDK 侧，仍是反面。
