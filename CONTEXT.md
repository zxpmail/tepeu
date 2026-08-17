# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**第十轮 gnex3 对账 + LlmProvider 选型 + 派生式断言形态已裁决落文档（2026-08-18，纯文档轮——用户指令「只写文档不写代码」）**；`E:\work\docs` 有利项已收口为 `docs/work-docs-absorption.md`。下一刀 = `llm.*` 断言切片**落码**（裁决已备：`os/llm` 新模块 + kernel 四处小改，fake 传输先行）。实施以 `docs/os-baseplate.md` + `os/` 为准。

## 上次停在哪
- ✅ **`E:\work\docs` 吸收清单**（2026-08-18）：`docs/work-docs-absorption.md`——**只吸收有利**；纪律八条（完成=证据 / 未知即未知 / 授权只收紧 / SoR 三分 / 有界续跑 / 失败重评估 / 单栈 / 学习≠立项）+ 按刀映射；底板头注挂链
- ✅ **第十轮对账落文档**（2026-08-18）：`docs/gnex3-reference.md`（第七参照，影子时间线定位，**只吸取有利**：吸收 8 条各指认归属切片 + 反模式不吸收 7 条）；ADR-016 第十轮三裁决——①LlmProvider=自研双协议族（Anthropic Messages + OpenAI Chat Completions 投影自研；Spring AI ChatModel 不进 `llm.*` 路径，黑盒与 §6-6 结构冲突；缝可逆）②断言形态升格「派生式」（`llm.*` 不收调用方拼装 messages，恒 `derive(log)`；红线 §6-1 与 §6-6 合一为机制；digest+版本号落 ledger attrs，下次调用前复核上笔）③gnex3 吸收/不吸收清单；§8.5 新增 4 行挂账 + 3 行并入备注 + LlmProvider 行 ◐ 已裁决；底板 §7 骨架加 `os/llm`（规划）
- ✅ **第九轮 kernel 端口演化刀落码**（2026-08-17）：C1 审批端口（`ApprovalStore` 同步重试式 ask + 严格单次 consume，`ApprovalRecord` 证据持久）、C2 fail-closed（未装配 Policy/审批通道即拒，废默认 ALLOW）、C3 失败双通道（拦截三异常→调用方；执行失败→ok=false+errorCode）；计量槽位入 `SyscallResult` 基座 + `Usage` inclusive 双轨；`SessionLedger`/`LedgerEntry` + `Metering` 端口定形；`Priority` 入 `InboxMessage` 签名（NOW>NEXT>LATER 同级 FIFO）；`SessionRegistry`→`SessionStore` 改名；conformance 重写（Session 12 + Bus 18 + Store 1，adaptors 重建）；`mvn -f os/pom.xml test` 31/31 全绿；§8.5 销账 C1/C2/计量槽/priority/ledger 零代码五项，drift 表销三行
- ✅ ② conformance 切片已落码（2026-08-16）——对账冻结解除
- ✅ 内核 Java 端口：identity / context / session / bus（含 fail-closed + surface 次序修复）
- ✅ 内存适配器 + `KernelPortsSmokeTest`（`mvn -f os/pom.xml test` 通过）
- ✅ 五参照对账 + ADR-016 四~六轮（CC/Pi/TriniOS/AIOS/OpenCode，2026-08-16，见 docs/*-reference.md）
- ✅ 第七轮设计审计（2026-08-16）：文档级缺陷已修（表格损坏/抢占边界/§5 滞后等）；四裁决：压缩双轨（触发式内联 turn + 后台走 maintenance，无无门 llm.*）、fork 后 seq 续接同一空间、C3 轮=落码切片轮+**对账冻结**、租约 TTL/fencing；新增 §8.5 挂账清单
- ✅ v1.0 吸收清单（2026-08-16）：`docs/legacy-absorption.md`——A16 条行为规格 + 代码迁移候选（ScriptSandbox 最直接可搬）+ 反模式不吸收（上帝编排器/装饰器链/内存审批）；切片规划输入，不开新裁决轮
- ✅ 代码审计二（2026-08-16）：立即修四件（§2 Metering 正典措辞投影、冻结词可操作定义、§8.5 增 drift 表 + 三挂账【审批端口形态 / 未装配默认 / 失败双通道】、身份双枚举注记）；drift 五项入 §8.5（死租约 / 卫兵无 verdict / priority / fork / 计量槽）——全部待 conformance 切片消化
- ✅ Netty 理念对账（2026-08-16）：`docs/netty-reference.md`——IoHandler 解耦与 ①② 缝同构印证；三解挂账（时间轮判据→死租约、双水位滞回→SSE 背压、Ticker→可测试时钟）；元模式三条（显式契约/惰性清理/采样观测）；零新裁决，§8.5 增 timer 与泄漏检测两行
- ✅ **② conformance 切片落码**（2026-08-16）：kernel 新增 `conformance` 包（SessionConformance + MutableClock，随包发布、runner 无关、纯 JDK）；`KernelPortsSmokeTest` 改 @TestFactory 桥；**第六轮 manifest 钉死 7 类落码（SYSTEM_NOTE 已删）**；**第七轮死租约可回收落码**（时钟注入 + 惰性回收）。**对账冻结解除**
- ⏭️ **下一刀：`llm.*` 断言切片落码**（裁决已备：`os/llm` 新模块——canonical/LogDeriver/SharedNormalizer/双协议族投影/CanonicalJson+digest/LlmGenerateHandler/LlmConformance；kernel 四处小改——`Usage` cost 槽、`LedgerEntry` attrs、`record` attrs 重载、`registeredSyscalls()` 规范序销 drift）→ ③ Loop 端口（SessionLoop/RegisterStore/总线落事件三项同裁，见 §8.5）

## 近期关键决定
- **LlmProvider = 自研双协议族**（第十轮）；Spring AI ChatModel 不进 `llm.*` 路径；工具/MCP 面随 O5 另裁
- **派生式断言**（第十轮）：`llm.*` messages 恒 `derive(log)`，非日志可派生者结构性发不出去；prepared digest + derive/normalize/project 版本号落 ledger attrs，下次调用前复核上笔；断言 v1 范围 = messages + tools 序（总线规范序子列）+ cache 布点确定性
- **gnex3 姿态 = 只吸取有利**（第七参照 docs/gnex3-reference.md）：8 条吸收各指认切片；cordis-jvm/接口森林/开放词汇表/审批无单次消费/日志背压 DROP/Spring AI 黑盒不吸收
- 双真相：entries（对话事实）vs ledger（用量真相）vs AuditSink（人手审计）vs ProjectionBus（非真相）
- **会话设施三 store**：entries / registers（权威清单已列）/ ledger；no third place；配置与编排**控制状态**禁入词汇表（PLAN_STEP 等模型可见事实不在此列）
- 压缩双轨：触发式内联 turn（占该 turn 预算）/ 后台走 maintenance（独立预算条目）；一切 llm.* 必过 Metering 门
- seq：append 点分配、本日志单调连续；fork 种子保留原 seq、自写续接同一空间
- Policy=授权（进程内）vs Execution/Sandbox=隔离（OS 级）；配额=卫兵、预算门=Metering 供数+Policy 协作
- `llm.*` 断言 = `derive(log) ∘ normalize == sent`（normalize 版本化纯函数）；syscall 注册表确定性规范序
- 审批界线：事前声明走 Policy 配置面；会话中授予严格单次（解禁唯一形态见第六轮备注）；C1 形态=同步重试式 ask（第九轮：ask 登记抛出→decide→重试 consume 即消费）
- C2 fail-closed：未装配 Policy/审批通道即拒，无默认 ALLOW；C3 失败双通道：拦截三异常→调用方（③ Loop），执行失败→ok=false+errorCode（第九轮）
- 租约必带 TTL；多副本升级 fencing token
- now 级抢占只切流式 chunk 边界；结构化输出与非幂等工具不可无损切
- 裁决限期落码：轮=落码切片轮；挂账见底板 §8.5
- `E:\work\docs`：**只吸收有利**（闸门与诚实八条）；见 `docs/work-docs-absorption.md`
- 详见 ADR-016（十轮） / os-baseplate
