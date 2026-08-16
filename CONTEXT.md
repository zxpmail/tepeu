# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**对账轮已冻结（ADR-016 第七轮）——下一动作 = ② conformance 切片落码**；实施以 `docs/os-baseplate.md` + `os/` 为准。

## 上次停在哪
- ✅ 内核 Java 端口：identity / context / session / bus（含 fail-closed + surface 次序修复）
- ✅ 内存适配器 + `KernelPortsSmokeTest`（`mvn -f os/pom.xml test` 通过）
- ✅ 五参照对账 + ADR-016 四~六轮（CC/Pi/TriniOS/AIOS/OpenCode，2026-08-16，见 docs/*-reference.md）
- ✅ 第七轮设计审计（2026-08-16）：文档级缺陷已修（表格损坏/抢占边界/§5 滞后等）；四裁决：压缩双轨（触发式内联 turn + 后台走 maintenance，无无门 llm.*）、fork 后 seq 续接同一空间、C3 轮=落码切片轮+**对账冻结**、租约 TTL/fencing；新增 §8.5 挂账清单
- ✅ v1.0 吸收清单（2026-08-16）：`docs/legacy-absorption.md`——A16 条行为规格 + 代码迁移候选（ScriptSandbox 最直接可搬）+ 反模式不吸收（上帝编排器/装饰器链/内存审批）；切片规划输入，不开新裁决轮
- ✅ 代码审计二（2026-08-16）：立即修四件（§2 Metering 正典措辞投影、冻结词可操作定义、§8.5 增 drift 表 + 三挂账【审批端口形态 / 未装配默认 / 失败双通道】、身份双枚举注记）；drift 五项入 §8.5（死租约 / 卫兵无 verdict / priority / fork / 计量槽）——全部待 conformance 切片消化
- ⏭️ **下一步（冻结解除条件）：② conformance 套件落码**（discharge 第六轮 manifest 测试 + 第五轮三 store 用例 + 五参照弹药 + §8.5 drift 五项）→ `llm.*` 断言切片 → ③ Loop 端口

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
