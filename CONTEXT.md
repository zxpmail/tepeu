# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**② conformance 切片已落码（2026-08-16，22 用例全绿）——对账冻结解除**；下一刀 = `llm.*` 断言切片（LlmProvider 双协议族选型同刀裁）。实施以 `docs/os-baseplate.md` + `os/` 为准。

## 上次停在哪
- ✅ 内核 Java 端口：identity / context / session / bus（含 fail-closed + surface 次序修复）
- ✅ 内存适配器 + `KernelPortsSmokeTest`（`mvn -f os/pom.xml test` 通过）
- ✅ 五参照对账 + ADR-016 四~六轮（CC/Pi/TriniOS/AIOS/OpenCode，2026-08-16，见 docs/*-reference.md）
- ✅ 第七轮设计审计（2026-08-16）：文档级缺陷已修（表格损坏/抢占边界/§5 滞后等）；四裁决：压缩双轨（触发式内联 turn + 后台走 maintenance，无无门 llm.*）、fork 后 seq 续接同一空间、C3 轮=落码切片轮+**对账冻结**、租约 TTL/fencing；新增 §8.5 挂账清单
- ✅ v1.0 吸收清单（2026-08-16）：`docs/legacy-absorption.md`——A16 条行为规格 + 代码迁移候选（ScriptSandbox 最直接可搬）+ 反模式不吸收（上帝编排器/装饰器链/内存审批）；切片规划输入，不开新裁决轮
- ✅ 代码审计二（2026-08-16）：立即修四件（§2 Metering 正典措辞投影、冻结词可操作定义、§8.5 增 drift 表 + 三挂账【审批端口形态 / 未装配默认 / 失败双通道】、身份双枚举注记）；drift 五项入 §8.5（死租约 / 卫兵无 verdict / priority / fork / 计量槽）——全部待 conformance 切片消化
- ✅ Netty 理念对账（2026-08-16）：`docs/netty-reference.md`——IoHandler 解耦与 ①② 缝同构印证；三解挂账（时间轮判据→死租约、双水位滞回→SSE 背压、Ticker→可测试时钟）；元模式三条（显式契约/惰性清理/采样观测）；零新裁决，§8.5 增 timer 与泄漏检测两行
- ✅ **② conformance 切片落码**（2026-08-16）：kernel 新增 `conformance` 包（SessionConformance 12 + BusConformance 13 + MutableClock，随包发布、runner 无关、纯 JDK）；`KernelPortsSmokeTest` 改 @TestFactory 桥；**第六轮 manifest 钉死 7 类落码（SYSTEM_NOTE 已删）**；**第七轮死租约可回收落码**（时钟注入 + 惰性回收）；mvn 22 用例全绿。**对账冻结解除**
- ⏭️ **下一刀：`llm.*` 断言切片**（canonical 类型 + normalize 版本化 + prepare 可观测 + 双协议族选型同刀裁【LlmProvider 挂账】）→ kernel 端口演化刀（C1 审批端口/C2 未装配默认/C3 失败双通道/SessionLoop/③ Loop 选型）→ ③ Loop 端口

## 近期关键决定
- 双真相：entries（对话事实）vs ledger（用量真相）vs AuditSink（人手审计）vs ProjectionBus（非真相）
- **会话设施三 store**：entries / registers（权威清单已列）/ ledger；no third place；配置与编排**控制状态**禁入词汇表（PLAN_STEP 等模型可见事实不在此列）
- 压缩双轨：触发式内联 turn（占该 turn 预算）/ 后台走 maintenance（独立预算条目）；一切 llm.* 必过 Metering 门
- seq：append 点分配、本日志单调连续；fork 种子保留原 seq、自写续接同一空间
- Policy=授权（进程内）vs Execution/Sandbox=隔离（OS 级）；配额=卫兵、预算门=Metering 供数+Policy 协作
- `llm.*` 断言 = `derive(log) ∘ normalize == sent`（normalize 版本化纯函数）；syscall 注册表确定性规范序
- 审批界线：事前声明走 Policy 配置面；会话中授予严格单次（解禁唯一形态见第六轮备注）
- 租约必带 TTL；多副本升级 fencing token
- now 级抢占只切流式 chunk 边界；结构化输出与非幂等工具不可无损切
- 裁决限期落码：轮=落码切片轮；挂账见底板 §8.5
- 详见 ADR-016（七轮） / os-baseplate
