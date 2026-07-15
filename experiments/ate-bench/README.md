# ATE-Bench（Tepeu / Spring AI）最小对照实验

固定同一编码 Agent（Claude Code CLI）与同一任务集，只改变代码库形态（Baseline vs Experimental），测量 Agent 修改系统的成本。

配套文章：[`docs/essays/from-pithtrain-to-enterprise-agent.md`](../../docs/essays/from-pithtrain-to-enterprise-agent.md)  
合并结果：[`results/MERGED-20260715.md`](results/MERGED-20260715.md)

**定位：** 初步验证（5 任务 × 1 模型 × 1 仓库），不是普遍结论。

## 变量

| 角色 | 内容 |
|------|------|
| 自变量 | 代码库：`baseline`（当前主线） / `experimental`（显式工具注册 + 调用路径文档） |
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

## 快速跑（PowerShell）

```powershell
pwsh -File experiments/ate-bench/scripts/setup-fixtures.ps1
pwsh -File experiments/ate-bench/scripts/run-ate.ps1
# 汇总：对最近一次 results/<stamp>/summary.json
python experiments/ate-bench/scripts/summarize.py --summary experiments/ate-bench/results/<stamp>/summary.json
```

### T1 注入消融（主动把调用路径写入 system prompt）

```powershell
pwsh -File experiments/ate-bench/scripts/run-t1-inject.ps1
```

结果写入 `experiments/ate-bench/results/`。

## 任务一览

| ID | 类型 | 测什么 |
|----|------|--------|
| T1 | Q&A | 理解调用链 |
| T2 | Q&A | 新增工具要改哪些文件 |
| T3 | Feature | 新增 WeatherTool |
| T4 | Feature | 调整历史消息上限常量 |
| T5 | Debug | 解释 FileTools NPE/空绑定风险 |
