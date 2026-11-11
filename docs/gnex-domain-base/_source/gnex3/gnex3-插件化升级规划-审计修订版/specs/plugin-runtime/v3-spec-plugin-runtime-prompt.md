# v3-spec-plugin-runtime-prompt

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-prompt
> **分层**：L1 内核层
> **受保护级别**：platform（🔒 平台持有，禁租户/Agent 装卸）
> **宿主服务**：agent-service
> **实现状态**：规划中

---

## §1 业务目标

系统提示组装服务：按"章宪（不可变）→ 人设（平台 preset）→ 配方序（Agent 配置）→ 会话上下文"分层组装系统提示（dsh-system-prompt + dsh-persona + dsh-agent-instructions 语义）；gnex2.0 的 PromptAssemblyLayers / BASE_AGENT_CHARTER 改造为插件化 prompt 服务。🔒 平台持有：章宪/人设层不可被租户/Agent 篡改，组装结果分层可审计，输入经注入防护（不拼接未校验的租户文本为系统指令）。

**非目标**：不做提示词生成策略（compaction/上下文由对应插件承接）；不做模板渲染引擎选型（后续）；不做跨服务组装（agent-service 内）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | dsh-system-prompt + dsh-persona + dsh-agent-instructions |
| 对标 GNEX2.0 模块 | agent-service PromptAssemblyLayers / BASE_AGENT_CHARTER（改造） |
| 技术选型 | 🅶 GNEX2.0 模块改造（分层组装插件化） |
| 改造要点 | ① prompt 服务（assemble(context) 返回分层系统提示 + layers() 来源清单）② 分层：章宪（内置只读）→ 人设（平台 preset）→ 配方序（Agent 配置/行为契约）→ 会话上下文 ③ 模板与 preset 来自 settings 插件段 + 行为契约 ④ 🔒 平台层不可被插件/租户覆盖 ⑤ 注入防护 + 长度预算（与 compaction 协同） |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `prompt` | `com.gnex.plugin.runtime.PromptAssembler` | assemble(context)/layers()（分层来源可审计） |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 prompt 服务
- `settings`（必选）：人设/配方序模板数据源
- `logger`（可选）：组装诊断
- `scope`（可选）：租户/项目 realm 上下文

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `agent/pre-step` | listen（waterfall） | {agentName, sessionId, layers} → 注入/校验系统提示层 |

## §4 配置与装配

```yaml
# service-assembly.yml（agent-service 声明，保护级别 platform）
plugins:
  - id: gnex3-l1-prompt
    load: mandatory
    protection: platform        # 🔒 平台持有，禁租户/Agent 装卸
gnex3-l1-prompt:
  charter: builtin:BASE_AGENT_CHARTER   # 章宪内置只读
  persona-presets: [default, researcher]  # 平台人设预设
  max-prompt-tokens: 32000       # 长度预算（与 compaction 协同）
  injection-scan: true           # 租户输入注入防护扫描
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 章宪层必须为平台只读（租户/Agent 不可修改，🔒）
- ✅ 组装结果必须分层可审计（layers() 返回每层来源/版本）
- ✅ 租户输入必须经注入防护后再进入提示层（不拼接未校验文本为系统指令）
- ❌ 人设/章宪不得被插件覆盖（platform 层优先，覆盖被拒）
- ❌ 组装结果不得超长度预算（超限截断/压缩，与 compaction 协同）
- ✅ 每层必须带来源标识（prompt 溯源：哪个人设/哪份配方）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 分层组装 | `PromptAssemblyTest`：章宪→人设→配方序→会话上下文顺序正确 |
| V2 | 章宪只读 | `PromptCharterTest`：修改章宪层被拒 |
| V3 | 注入防护 | `PromptInjectionTest`：恶意租户输入被清洗/拒绝 |
| V4 | 来源审计 | `PromptLayersTest`：layers() 返回每层来源 |
| V5 | 长度预算 | `PromptBudgetTest`：超限截断/压缩生效 |
| V6 | pre-step 注入 | `PromptPreStepTest`：agent/pre-step 注入系统提示层 |

### 运行命令

```bash
mvn -pl services/gnex-agent-service test "-Dtest=PromptAssemblyTest"
```

## §7 影响域

- 作用范围：**会话级（每轮组装）**——agent-service 内系统提示生成。
- 被谁消费：agentLoop（step 组装）；会话标题/压缩（同源消费提示策略）。
- 故障影响半径：组装缺陷 = 该会话提示异常（模型行为漂移）；章宪泄露/篡改 = 安全事件（🔒 发布门）。
- 升级影响：🔒 平台级升级 + 灰度（章宪/人设变更影响全部 Agent 行为）。

## §8 后续（未纳入首版）

- 提示模板渲染引擎选型（dsh 模板语法迁移）
- 提示 A/B 与效果评估（quality 联动）
- 跨会话提示缓存（相同上下文复用组装结果）

---

*GNEX3.0 系统提示组装 — 人设/章宪/配方序分层 SDD。*
