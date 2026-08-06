# CONTEXT.md
# 项目进度快照（极简）

## 当前在做什么
Phase 18 / v1.0.0 发布收口已完成。Docker 镜像本机实测与 git tag 仍可选待批。

## 上次停在哪
- ✅ Phase 18：`RELEASE_NOTES-v1.0.0.md`、版本号 1.0.0、Spec §10 基线、Docker 定义校验
- ✅ Phase 10–17 主线能力
- ⏳ Docker `docker build` 暂缓（无 CLI）
- ⏳ git tag / GitHub Release（须批准）

## 近期关键决定
- v1.0.0 = Spec Phase 3 单机 Agentic OS；集群/OAuth 仍为远期（Spec §8.2）
- 技能脚本用 GraalJS，原生 WASM 延后（ADR-015）
- tag / Release 不自动打，需明确批准
