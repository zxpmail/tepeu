# GNEX3.0 插件化升级 —— 交付物清单

> **本文件夹**：`dsh研究/gnex3-插件化升级规划/`（与 dsh 分析并列于 dsh研究 根目录，独立于 gnex-core-v2 仓库）
> 关联：gnex 仓库内的原始设计文档仍保留在 `gnex-core-v2/docs/`（v1.0/v2.0/v3.0/运行时 等团队既有文档未动）。

## 目录结构

```
dsh研究/
├── gnex3-插件化升级规划/                 ← 本文件夹（GNEX3.0 全部成果）
│   ├── README.md                         ← 体系索引（入口）
│   ├── 00-PROJECT-TASKS.md               ← 项目任务书
│   ├── 01-gnex2.0-architecture.md        ← GNEX2.0 现状架构 + 模块目录 + 痛点
│   ├── 02-dsh-gnex-comparison.md         ← 框架对比（机制表/映射表/映射图）
│   ├── 03-fusion-design.md               ← 融合设计 + GNEX3.0 六层插件架构总图
│   ├── 04-gnex3.0-plugin-catalog.md      ← 81 个插件定义（映射/选型/保护/宿主）
│   ├── 05-interface-relationships.md     ← 接口关系（服务目录/事件/影响域）
│   ├── 06-upgrade-roadmap.md             ← 升级路线图（S0-S7）
│   ├── 07-dsh-js-bridge.md               ← dsh JS 插件中间件方案论证
│   ├── GNEX3-交付物清单.md                ← 本文件
│   ├── gnex3-architecture.html           ← 交互式架构图页面（单文件，双击打开）
│   ├── specs/                            ← SDD 规格（81 份 + 模板，13 域）
│   │   ├── _TEMPLATE.md
│   │   ├── plugin-runtime/ (14) · model/ (5) · session/ (7) · storage/ (5)
│   │   ├── agent/ (4) · delegation/ (3) · tools/ (3) · tools-model/ (12)
│   │   ├── goals-skills/ (6) · sandbox/ (4) · context/ (3)
│   │   ├── enterprise/ (13) · client/ (2)
│   ├── _work/                            ← 分析过程产物
│   │   ├── gnex-services-analysis.md     （1331 行：9 服务源码深析 + 协作总览）
│   │   ├── gnex-distributed-analysis.md  （9 libs 矩阵 + 分布式设施 + E1-E36）
│   │   ├── gnex-spec-constraints.md      （产品规格与 P0 硬约束）
│   │   ├── gnex-dsh-existing-analysis.md （既有对标结论 + 前端模块）
│   │   ├── plugin-framework-selection.md （插件内核选型论证）
│   │   ├── parts/                        （9 服务逐包分析中间产物）
│   │   └── gnex3-plugins.json / build-gnex3-data.js（页面数据源）
│   └── 项目管理/
│       ├── GOAL-记录.md                  ← 目标/决策/里程碑
│       └── 任务执行日志.md               ← 执行流水/统计/复盘
├── dsh-architecture.html                 ← dsh 分析页（上一任务，并列）
├── packages.json / analysis/             ← dsh 186 插件数据（上一任务，并列）
└── gnex-core-v2/                         ← gnex 2.0 源码仓库（未改动）
```

## 快速导航

| 想了解 | 打开 |
|--------|------|
| 全局概览（图） | `gnex3-architecture.html` |
| 项目是怎么管的 | `00-PROJECT-TASKS.md` + `项目管理/` |
| gnex2.0 现状 | `01-gnex2.0-architecture.md` + `_work/gnex-services-analysis.md` |
| dsh 与 gnex 差在哪 | `02-dsh-gnex-comparison.md` |
| 3.0 怎么融合 | `03-fusion-design.md` |
| 81 个插件是什么 | `04-gnex3.0-plugin-catalog.md` |
| 接口/事件/影响域 | `05-interface-relationships.md` |
| 先做哪步 | `06-upgrade-roadmap.md` |
| dsh 插件能不能在 gnex 用 | `07-dsh-js-bridge.md` |
| 每个插件的详细设计 | `specs/<域>/v3-spec-*.md` |

## 数据一致性

| 项 | 值 |
|----|-----|
| 插件总数 | 81（L0×6 · L1×8 · L3×52 · L4×13 · L5×2） |
| SDD 规格 | 81/81 份，每份 §1-§8 + ✅/❌ 边界 + V1-Vn 断言 + mvn 命令 |
| 受保护插件 | 16 个（15 🔒platform + 1 🔐admin） |
| 企业特性 | E1-E36（保留，见 01 §5 与 _work/gnex-distributed-analysis.md） |
| 对标 dsh | 186 插件 / 15 组分类 |
