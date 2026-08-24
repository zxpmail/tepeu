# Handoff — Tepeu（develop）

> 到达后阅读序：本文件 → `CONTEXT.md` → `docs/os-baseplate.md` → `docs/os-handbook.md` + `docs/agent-os-gap.md` → `memory/project-memory.md` + `memory/decisions-log.md`（ADR-016）。  
> `Product-Spec.md` / `DEV-PLAN.md` 是 **v1 档案**，不是 develop 规范。

**Last updated**: 2026-08-24

## 当前阶段

- develop：**本机 Agent OS 骨架可演示**（ADR-016 第二十二轮）+ 审查修补（第二十三轮）
- 仍不是企业 Agent OS / 完整 OS；**无完整 UI**（有 ProjectionBus v1）、**无向量记忆**（有 KnowledgeSource 端口）
- 下一刀按痛点：⑤ UI、记忆平面、多副本 fencing

## 口径

- **组件（8）**：session / policy / bus / llm / loop / orchestration / execution / compose
- **不是组件**：identity、syscall（词汇）；conformance（harness）
- Loop **不**依赖 orchestration。CompactionWork 在 loop，经总线 llm.*。
- execution 隔离 = **partial**。spawn：Windows `CREATE_SUSPENDED` 入 Job 再跑；Linux bwrap + ro-bind-try。无 jail 时失败可见。
- 压缩后 `log.surfaceEpoch` 跳过上笔 llm digest 复核。审批绑 `argsDigest`。`/approve` 校验会话。
- compose **不**读 API 密钥。live 测试有 key 才烧。

## 已完成

- 第九–二十一轮：四端口、Loop、llm HTTP、SQLite 发行、Compaction 窗、DefaultRuleMatrix、execution 囚笼
- 第二十二轮：live 测试（opt-in）、turn 内 overflow 压缩、PLAN/FILE 完成门、OS jail、`/approve` + `policy.rules`
- 第二十三轮：surfaceEpoch、Job CREATE_SUSPENDED、env 白名单、stdout 封顶、argsDigest、jail NOFOLLOW、文档口径对齐
- 2026-08-24：外部「Agent 运行时安全」系列开篇+工具 2–4 篇中肯对账落入 `docs/archive/reference/agent-runtime-security-series.md`（九宫格≠九 jar；gate/response 空 jar 不预开）
- 同日补：EnvHarness 机制同构（Gate=边界脚本；观测可改/verifier 不可改；Observation 组件候选）写入安全系列 §6 + 底板 §3.3 / §8.5 挂账；未开 jar、未裁 ADR
- 同日补：腾讯「Harness Engineering」文 → `tencent-harness-engineering.md` 降级为**勿当 os 参照**（与 `os/` 不同线，仅防混谈）；不列入吸收
- 同日补：DEV.to 评测可观测管线 → `ai-eval-observability-pipeline.md`（运维姿态可借脱敏/诚实边界；非内核；勿开 observability jar）
- 同日补：Terax ADE → `terax-ai.md`（⑤ 工作台；与 `os/` 不同线；勿当内核参照）
- 2026-08-24：`ProjectionBus` + `SessionProjections`（UI 投影 v1）；`KnowledgeSource` + `PromptAssembly.memoryHits`

## 待办

- live 往返未在 CI 烧（无 key skip）
- 处置链 / 可配置 deny-sequence 规则面 — 见安全系列；未裁决前不空开 `gate/`·`response/`
- ⑤ UI / 完整 SSE 宿主 / 向量记忆
- 多副本 fencing / timer 轮 / 哈希链 — 远期

## 调试

```bash
mvn -f os/pom.xml test
mvn -f os/pom.xml -pl compose -am test
# live（可选）：ANTHROPIC_API_KEY=... mvn -f os/pom.xml -pl llm -am test
```
