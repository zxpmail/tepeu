# GenericAgent × Tepeu

> **地位**：存档备查。**与 `os/` 不同线**，不进底板。  
> **来源**：`C:\GenericAgent-main`（[lsdefine/GenericAgent](https://github.com/lsdefine/GenericAgent)，~3K 行个人电脑 Agent）。2026-08-31 抽检 `agent_loop.py` / `ga.py`。未跑其测试。

个人 Computer-Use + 技能自结晶。口号「不要预装技能，用一次长一次」。9 个原子工具（`code_run` / 浏览器 / 文件 / 问人…）+ ~100 行 `agent_runner_loop`。不是 Java Agent OS。

**能吸收的：没有。**

| 它怎么做 | 为什么不进 Tepeu |
|----------|------------------|
| `messages` 每轮重拼，历史在 Session 对象 | 与 `derive(log)` 反着 |
| `next_prompt` 空 = `CURRENT_TASK_DONE`；`should_exit` 由 handler 自决 | 工具/循环自报完成。完成权在门 |
| `max_turns=40` → `MAX_TURNS_EXCEEDED` | 触顶是停，不是他们这种「转数用尽当结果」当产品默认 |
| `code_run` 任意 Python/PowerShell | 无 Policy。execution 已是 ASK + partial jail |
| 每 10 轮扔掉工具描述 | 上下文省钱 hack，不是 offered 纪律 |
| 子代理靠 `output.txt` / 主 agent 空闲去读 | Inbox ≠ 调度器。禁止「监察升循环」 |
| 任务结晶成 skill 写进 memory | 记忆平面未做；不是内核 |

反面：全能句、预算不准停、用 prompt SOP 当完成门。GNEX 蒸馏过同一句，这里不重复挂。

不新开 ADR / jar / §8.5。
