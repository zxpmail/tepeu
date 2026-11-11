# v3-spec-tool-ask-user

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.6 模型工具
> **审计修订：v0.2（2026-08-18）· 依据 D-P1-09 / P2-01 / P2-04**
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-tool-ask-user
> **分层**：L3 能力层 · 3.6 模型工具
> **受保护级别**：normal（默认，管理员可装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

Agent 循环中需要澄清/选择/征询时，模型不再自问自答，而是挂起当前回合、向用户提问并等待回答。提问/回答全部事件化：提问 emit `question/requested`，UI 回答经 `question/resolved` 回注后恢复回合（事件词表已回填 05 §2 `question/*` 命名空间，P2-04；本 §3.3 引用 05，05 为全量权威）；支持选项、自由文本、超时回退默认答案，避免死等。gnex2.0 的 AskUserQuestionTool 为同步阻塞式工具调用，本插件将其改造为事件化原语，挂起状态与会话 checkpoint 对齐，断线可恢复。**非目标**：不做对话式多轮澄清（首版单问题挂起）与提问优先级/审批联动（留 §8）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | `dsh-tool-ask-user` |
| 代码实证来源 | agent-service `com.gnex.agent.tools.AskUserQuestionTool` `[spring-ai 依赖]`（P2-01）+ QuestionController（POST /api/questions/{id}/answer）`[源码实证]` |
| 技术选型 | 🅶 gnex2.0 模块改造 |
| 改造要点 | ① 同步阻塞式 AskUserQuestionTool 改造为事件化：提问 emit `question/requested`，UI 回答经 `question/resolved` 恢复回合 ② 挂起状态写入 session-store/checkpoint，断线可恢复 ③ 超时回退 `default-answer-on-timeout`，禁止死等 ④ 经 `tools` 注册表注册 ask_user 工具定义 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `userQuestions` | `UserQuestionsService` | 提问入队/回答回注/超时回退/按会话查询 |

### 3.2 注入服务（inject）

- `tools`（必选）：注册 ask_user 工具回调；`sessions`（必选）：回合挂起/恢复与 checkpoint 对齐
- `events`（必选）：question/requested · question/resolved 收发；`scope`（必选）：会话 realm 隔离

### 3.3 事件（emit / listen）

词表权威为 05 §2 `question/*` 命名空间（P2-04 已回填），本表为核心子集：

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `question/requested` | emit | {questionId, sessionId, prompt, options[], timeoutMs} |
| `question/resolved` | listen | {questionId, sessionId, answer} → 校验归属后恢复回合 |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-cap-tool-ask-user
    load: mandatory
gnex.tool.ask-user:
  timeout-ms: 120000            # 提问挂起上限（D-P1-09）
  max-options: 10               # 选项上限
  default-answer-on-timeout: "" # 空 = 超时放弃提问继续
```

**超时语义（D-P1-09）**：ask_user 为挂起类工具，自身挂起上限 **120s**（timeout-ms），并向 gnex3-cap-tools-timeout **注册 override**（豁免 30s 默认工具超时）；超时仅触发本插件自身的 default-answer-on-timeout 回退，而非管线默认超时中止。

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 提问必须挂起当前模型回合，未收到 question/resolved 不得继续生成；必须支持超时回退（default-answer-on-timeout）恢复回合，禁止死等
- ✅ **挂起类工具不得被默认超时中止（D-P1-09）**：tools-timeout 默认豁免表中含 tool_ask_user；本插件超时以自身 120s 挂起上限 + override 为准
- ✅ 回答必须校验 questionId 与 sessionId 归属，跨会话串答必须拒绝
- ✅ 提问/回答必须经事件总线（question/requested · question/resolved，词表见 05 §2），禁止直连 UI
- ❌ 不得把「UI 未应答」当作「用户拒绝/取消」硬编码为业务结论；不得在非会话 scope 广播提问（租户/会话隔离）
- ❌ 提问载荷不得携带会话历史全文（上下文裁剪）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 提问挂起 | `AskUserQuestionFlowTest`：emit question/requested 后回合挂起，无回答不继续生成 |
| V2 | 回答恢复 | `AskUserQuestionFlowTest`：question/resolved 回注后按答案继续回合 |
| V3 | 超时回退 | `AskUserQuestionTimeoutTest`：120s 挂起上限到点后按 default-answer-on-timeout 恢复 |
| V4 | 会话隔离 | `AskUserQuestionScopeTest`：会话 B 的回答不恢复会话 A 的挂起问题 |
| V5 | 工具注册 | `AskUserQuestionRegistryTest`：tools 注册表含 ask_user 定义（name/desc/JSON schema） |
| V6 | 默认超时豁免（D-P1-09） | `AskUserSuspendExemptTest`：挂起期间 tools-timeout 30s 默认超时不得中止回合；120s 挂起上限 + override 生效 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service -am test "-Dtest=AskUserQuestion*Test,AskUserSuspendExemptTest"
```

## §7 影响域

- 作用范围：**会话级**——单会话回合挂起/恢复。
- 被谁消费：UI（回答入口）、agentLoop（回合恢复）、tools-timeout（豁免表 + override，D-P1-09）；故障影响半径：单次提问失败可超时兜底，不影响其他会话与主进程。
- 升级影响：工具装卸无感，其余工具不受影响（05 §3 L3.6）。

## §8 后续（未纳入首版）

- 富交互选项（文件选择/表格/级联）
- 提问优先级与 approval 审批协同（高风险提问入队审批）
- 对话式多轮澄清提问

---

*GNEX3.0 用户提问工具插件 — AskUserQuestionTool 事件化改造 SDD（审计修订版：挂起类超时语义）。*
