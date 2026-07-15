# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
缺陷项 2–6 已推进：T1 注入重复、Docker 定义、Experimental 合入 main、文章补业务 Agent、幂等/记忆双写落地。

## 上次停在哪
- 文章：`docs/essays/from-pithtrain-to-enterprise-agent.md`
- T1 注入×3：avg≈12.3（9/17/11）→ `results/T1-inject-repeat-20260716-063441/`
- 主线已含 `Tools.java` / `ToolRegistry` / `IdempotencyService` / `MemoryFileMirror`
- Docker：`experiments/ate-bench/Dockerfile`（本机无 docker 守护进程，未实测 build）

## 近期关键决定
- 显式工具注册合入 main，不再只放实验补丁
- 幂等为进程内 TTL；记忆双写 DB 权威、MD 可读
- 仍未做：20 任务扩面、第二模型对照
