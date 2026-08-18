# TriniOS 对 Tepeu OS 的启示（对账与镜鉴）

> **地位**：参照材料，不是规范。裁决仍以 [ADR-016](../../../memory/decisions-log.md) + [os-baseplate.md](../../os-baseplate.md) 为准。
> **来源**：`E:\work\TriniOS-master`（AI-First Operating System，Mach 微内核方向，GNU Mach 基座，BSD 3-Clause，Phase 0–2）。2026-08-16 直接源码阅读（仓库 932K / kernel 侧 ~6000 行 C，无需 fan-out）。
> **与前两参照的关系**：CC（agent 巨石）与 Pi（agent 极简）都是 tepeu 的**同域**参照；TriniOS 是**异域同题**——真 OS 里把 LLM 操作做成一等公民（`servers/ai` LLM 推理服务器 + 内核 ASI 接口）。方向相反的同一命题：tepeu 借 OS 隐喻建 agent 内核，TriniOS 在真 OS 上加 AI syscall。

---

## 1. 成色核验（先说清它是什么）

- **Phase 0–2 骨架项目**：全仓 52 个 C/H 文件、~6K 行 kernel 代码、一份 ADR（001 选型 GNU Mach，质量合格：选项表+理由+验证（QEMU 启动 PASS）+负面风险+开放问题——与 tepeu ADR 纪律同型）。
- **硬事实**：`kernel/gnumach/` submodule **为空**；kernel 测试靠 `mach_stubs.h` + `libc_stubs.c` 打桩，未在真 Mach 上跑过；`asi.c`（334 行）中 list_models / stats / config get/set / verify 四组 API 是 `/* TODO: Implement */` 桩；`servers/ai/README.md` 全文一行标题。
- kernel README 的 Phase 2 表格给五个子系统打 ✅ Complete——**实际语义是「文件已写」，不是「可运行」**。全仓最诚实的文档是 shell README（自称「AI-naive」，AI 功能明确推迟 Phase 2）。

## 2. 对 tepeu 有用的东西（按价值排序）

### 2.1 ASI 消息设计 = `llm.*` syscall 的真 OS 对照

`kernel/asi/asi.h` 把「推理作为 IPC 服务」落成了消息协议，几处与 tepeu 已冻决定**正面对上**：

| ASI 设计 | tepeu 对应 | 判定 |
|---|---|---|
| 请求头携带 `sender_task` + `request_id`（身份与关联在消息上，非 ambient） | TurnContext 显式传递、请求跟踪 | ✅ 再证：身份随消息走 |
| **reply 基座头**携带 `token_count` + `latency_us`（计量是每个回复的必选字段，不是可选附加） | Metering | 💡 值得吸收：tepeu `SyscallResult` 基座可带 usage/latency 槽位，让计量结构性存在而非旁路 |
| 流式 = 三消息型（`STREAM_INFER / STREAM_CHUNK / STREAM_END`） | Loop 事件流 | ✅ 同型 |
| 模型无关接口（换模型=配置变化） | ModelRouter 只选型 | ✅ 同型 |
| `seed` 字段进请求（可复现性意图） | — | 💡 小启发：可复现请求的意图位 |
| 心跳/统计作为消息（`HEARTBEAT` / `GET_STATS`） | Metering/健康 | 同型 |

### 2.2 反面教材一：审计是 caller 的可选位

`ASI_FLAG_AUDIT_TRAIL`（0x2）与 `ASI_FLAG_SIGN_OUTPUT`（0x1）**由调用方在 flags 里置位**——调用方清位即可绕过审计与签名。审计若是 opt-in，就不是审计。tepeu 的对照面：**卫兵与审计挂在总线入口、不可协商、异常规范化为拒绝**（d7e053f）——正反对照极鲜明，可作为 Policy/卫兵设计的反例测试用例（「调用方声明跳过审计」必须被结构性地拒绝）。

### 2.3 反面教材二：开放魔数枚举 vs 封闭 union

`finish_reason` 是 `uint32_t` + 注释（`0=stop, 1=length, 2=error`）——开放整型+口头约定。tepeu 的 `PolicyVerdict`/`LoopOutcome`/`StopReason` 全走封闭 union + 词汇外规范化，Pi/CC 同款。三种参照三个证据：封闭 union 是对的。

### 2.4 反面教材三：大负载内联定长缓冲

`prompt[32KB]` / `response[64KB]` 直接内联在 IPC 消息结构体里（Mach 明明有 out-of-line memory 机制却没用）。tepeu §9 已立规：**大结果 spill 落盘 + locator + retrievalHint**，事件里只放引用。CC（tool-results 文件）与 Pi 同。三参照一致，此题已无悬念。

### 2.5 IPC server registry 的粗对照

`ipc_port.h`：封闭 server 类型枚举、注册带 `notify_port`（崩溃通知）、`crashed` 标志 + 依赖方通知、引用计数、pending 请求表（256 上限）带超时清理。与 tepeu syscall 注册表 + 卫兵（超时/取消）同构的**雏形**；固定容量 64/256 是真 OS 早期阶段的朴素选择。无新增可裁项。

## 3. 核心镜鉴：OS 类比诚实度的活标本

TriniOS 是 tepeu §8「OS 类比诚实度」债务表的**终局形态实例**：

- 叙事（「AI-First Operating System」「五子系统 Complete」）跑赢现实（空 submodule、桩测试、半数 TODO）——**靠命名与勾选框暗示能力**。
- tepeu 的对应防线已是制度：§8 显式债务表（调度公平/tamper-evidence/资源隔离列明欠账）+ 第五轮「裁决限期落码、两轮未落码标悬置」。
- **但 tepeu 不许嘲笑它**：底板五轮裁决 ~200 行规范 vs 已落码 ①②，是同一形状的小号版。TriniOS 的价值恰是当镜子——C3 规矩立得正是时候。每轮对账后问一句：这轮的裁决，落码切片指认了吗？

## 4. 严苛节（对 tepeu 自身）

1. **tepeu 实际进度在自己的目标域领先**：可运行的总线 + 内存会话 + 7 绿测试 + 失败路径覆盖，而 TriniOS 的同类物（ASI）停在消息格式 + client 桩。但 tepeu 尚未受过的考验是 TriniOS 已开始付的：**真资源约束**（GPU 内存类别、页钉住、跨特权级 IPC）——tepeu 的「OS」全部跑在一个 JVM 里，类比税还没缴。§8 债务表继续挂着是对的。
2. **本轮不新增裁决**（遵守 C3：新裁决须指认落码切片）。唯一候选——「`SyscallResult` 基座携带 usage/latency 槽位」——归入 `llm.*` 断言/Metering 切片时一并落码裁决，不悬空立项。
3. 证据链：仓库非 git、无版本号可钉；本文件论断全部来自 2026-08-16 直接读码（asi.h 全文 / ipc_port.h 全文 / asi.c TODO 扫描 / submodule 空目录核验），行号引用前文已内联。

## 5. 不照搬清单

- 全部实现（异域早期骨架：桩测试、空 submodule、定长缓冲、caller 可选审计）
- GPU 内存类别 / 页钉住（真硬件域，tepeu 无此层）
- BSD 兼容服务器 / 窗口 / 音频服务器（真 OS 外设域）

## 6. 对下一刀的直接影响

- 无新增裁决、无切片变更；② conformance 套件 → `llm.*` 断言 → ③ Loop 端口的优先级不变。
- §2.1 的「计量进 reply 基座」与 §2.2 的「审计不可协商」两条，分别在 `llm.*`/Metering 切片与 Policy conformance 用例里**顺路兑现**即可，不单独立项。
