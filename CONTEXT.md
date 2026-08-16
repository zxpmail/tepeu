# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
**develop ① 内核端口 + ② 内存冒烟已落地**；实施以 `docs/os-baseplate.md` + `os/` 为准。

## 上次停在哪
- ✅ 内核 Java 端口：identity / context / session / bus（含 fail-closed + surface 次序修复）
- ✅ 内存适配器 + `KernelPortsSmokeTest`（`mvn -f os/pom.xml test` 通过）
- ✅ CC 源码对账（2026-08-16）：`docs/claude-code-reference.md` + ADR-016 第四轮 12 条裁决 + 底板 §0.5
- ✅ Pi 源码对账（2026-08-16）：`docs/pi-reference.md` + ADR-016 第五轮 4 条裁决 + 底板 §0.6
- ✅ TriniOS 镜鉴（2026-08-16）：`docs/trinios-reference.md`——无新增裁决（守 C3：候选归入 llm.*/Metering 切片顺路兑现）
- ✅ AIOS 对账（2026-08-16）：`docs/aios-reference.md`——同名学术前辈；零新增裁决，产出 conformance 用例弹药（失败必达终态/错误不伪装成功/寄存器键跨重入队稳定等）；C6 barrier 超时语义挂 ledger 切片待裁
- ✅ OpenCode 对账（2026-08-16）：`docs/opencode-reference.md`——工程同代，EventV2 与 tepeu ① 逐条同构（seq 连续/未知 die/幂等重放）；**第六轮已裁**：C2 事件词汇表三件（per-type 版本化+manifest+数量钉死测试，落码=conformance）、C3 A2 备注（解禁唯一形态：工具声明+会话内+expiry）
- 下一步（优先级）：② conformance 套件（KernelPortsSmokeTest 升格 + SessionEventType manifest 钉死测试 = 第六轮落码）→ `llm.*` 断言切片（prepare()+录制回放已定型）→ ③ Loop 端口

## 近期关键决定
- 双真相：会话日志 vs AuditSink；模型须可见的错误=会话事件
- **会话设施三 store**：entries / registers（覆盖写、恢复点查）/ ledger；no third place；配置禁入事件词汇表
- Policy=授权（进程内）vs Execution/Sandbox=隔离（OS 级 spawn 点），分工写明
- Tool/MCP=②；Slash→Command（端口两型）；压缩走总线
- `llm.*` 断言 = `derive(log) ∘ normalize == sent`（normalize 版本化纯函数）
- syscall 注册表确定性规范序（内核不变量）
- 审批界线：事前声明走 Policy 配置面；会话中授予严格单次
- 裁决限期落码：两轮未落码标「悬置」（工程规矩）
- 详见 ADR-016（五轮） / os-baseplate
