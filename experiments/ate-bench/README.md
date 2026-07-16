# ATE-Bench（Tepeu / Spring AI）最小对照实验

固定同一编码 Agent（Claude Code CLI）与同一任务集，只改变代码库形态（Baseline vs Experimental），测量 Agent 修改系统的成本。

配套文章：[`docs/essays/from-pithtrain-to-spring-ai.md`](../../docs/essays/from-pithtrain-to-spring-ai.md)  
合并结果：[`results/MERGED-20260715.md`](results/MERGED-20260715.md)

**定位：** 初步验证（5 任务 × 1 模型 × 1 仓库），不是普遍结论。

## 变量

| 角色 | 内容 |
|------|------|
| 自变量 | `baseline`（7980e6d，工具硬编码） / `experimental`（注册 + 被动路径文档 A+B） / `registry-only`（仅注册，条件 C） |
| 控制变量 | 同一 `claude` CLI、同一 system prompt、同一任务 JSON |
| 因变量 | `num_turns`、`total_cost_usd`、`duration_ms`、任务成功率 |

## 跨平台说明

| 组件 | 依赖 |
|------|------|
| `scripts/judge.py` / `run_one.py` / `parse_agent_json.py` / `summarize.py` | Python 3.10+，Linux / macOS / Windows 均可 |
| `setup-fixtures.ps1` / `run-ate.ps1` / `run-t1-inject.ps1` | PowerShell 7+（`pwsh`）；Windows 自带或另装；Linux/macOS 可装 [PowerShell](https://github.com/PowerShell/PowerShell) |
| 编码 Agent | 已登录的 Claude Code CLI（`claude`） |
| T3 裁判 | `PATH` 中有 `mvn` |

无 PowerShell 时，可直接：

```bash
# 需已自行准备好两个 git worktree，并对 experimental 应用 patches/experimental/
python experiments/ate-bench/scripts/run_one.py \
  --repo /path/to/tepeu-ate-experimental \
  --task experiments/ate-bench/tasks/T1-qa-call-chain.json \
  --system-prompt-file experiments/ate-bench/prompt/system.txt \
  --agent-log /tmp/t1.agent.json
```

### Docker（冒烟，默认不调用 LLM）

```bash
docker compose -f experiments/ate-bench/docker-compose.yml run --rm ate
# ATE_MODE=smoke：mvn compile + 关键单测 + judge 自检
```

全量 Agent 跑仍建议本机 `run-ate.ps1`（需 CLI 登录或 `ANTHROPIC_API_KEY`）。

## 快速跑（Windows PowerShell）

```powershell
powershell -File experiments/ate-bench/scripts/setup-fixtures.ps1
powershell -File experiments/ate-bench/scripts/run-ate.ps1
# 条件 C：只注册、无被动 md（T2/T3）
powershell -File experiments/ate-bench/scripts/run-registry-ablation.ps1
# 同步债：从 Tools.java + AGENT_CALL_PATH.md 生成 / 校验 call-path inject
python experiments/ate-bench/scripts/generate_call_path_inject.py
python experiments/ate-bench/scripts/check_inject_sync.py --mode fail   # CI 门禁
python experiments/ate-bench/scripts/check_inject_sync.py --mode degrade # 过期则 DEGRADE=1
# T1 主动注入重复（默认 7 次；跑前会自动 regenerate inject）
powershell -File experiments/ate-bench/scripts/run-t1-inject-repeat.ps1
# 定向补方差：同场 Baseline vs A+B，仅 T2+T3 ×N（默认 3）
powershell -File experiments/ate-bench/scripts/run-ab-variance-repeat.ps1 -Times 3
```

## 任务一览

| ID | 类型 | 测什么 |
|----|------|--------|
| T1 | Q&A | 理解调用链 |
| T2 | Q&A | 新增工具要改哪些文件 |
| T3 | Feature | 新增 WeatherTool |
| T4 | Feature | 调整历史消息上限常量 |
| T5 | Debug* | 解释 FileTools NPE/空绑定风险（偏理解） |
| T6 | Debug | 修路径穿越（`tasks/dim/`，seed） |
| T7 | Refactor | 抽出 PromptAssembler（`tasks/dim/`） |
| T8 | Debug | 历史截断保旧不保新（`tasks/dim/`，按 variant seed） |

\*T5 裁判是解释题。真修 bug 用 T6。默认套件只扫 `tasks/*.json`；扩维：

```powershell
powershell -File experiments/ate-bench/scripts/run-dim-expand.ps1
powershell -File experiments/ate-bench/scripts/run-t6-repeat.ps1              # T6 ×3
powershell -File experiments/ate-bench/scripts/run-dim-repeat.ps1 -Task T7    # T7 ×3
powershell -File experiments/ate-bench/scripts/run-second-model.ps1 -Model deepseek-v4-pro
powershell -File experiments/ate-bench/scripts/run-second-model.ps1 -Model glm-5.2 -FromTepeuProvider anthropic
```

