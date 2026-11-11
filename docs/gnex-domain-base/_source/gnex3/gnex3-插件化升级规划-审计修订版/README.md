# GNEX3.0 插件化升级改造 —— 规划成果索引

> **状态**：规划进行中（Goal 模式）· 入口文档
> **任务书**：[00-PROJECT-TASKS.md](./00-PROJECT-TASKS.md)
> **用户指令**：学习 dsh「一切皆插件」重构 GNEX；服务化与企业特性保留；服务内部插件组合；产出架构图/映射/融合设计/插件 SDD/Spec 体系，并论证 dsh JS 插件桥。

---

## 成果物一览

| # | 成果物 | 文档 | 状态 |
|---|--------|------|------|
| D0 | 项目任务书 | [00-PROJECT-TASKS.md](./00-PROJECT-TASKS.md) | ✅ |
| D1 | GNEX 2.0 架构图与模块目录 | [01-gnex2.0-architecture.md](./01-gnex2.0-architecture.md) | ✅（含 1331 行服务深析） |
| D2 | 框架对比映射图与映射表 | [02-dsh-gnex-comparison.md](./02-dsh-gnex-comparison.md) | ✅ |
| D3 | 框架融合设计方案 | [03-fusion-design.md](./03-fusion-design.md) | ✅ |
| D4 | GNEX3.0 插件化架构图与插件定义 | [03-fusion-design.md](./03-fusion-design.md) + [04-gnex3.0-plugin-catalog.md](./04-gnex3.0-plugin-catalog.md) | ✅ |
| D5 | 接口关系（服务目录/事件/影响域） | [05-interface-relationships.md](./05-interface-relationships.md) | ✅ |
| D6 | 每插件 SDD 规格 | [specs/](./specs/) | ✅ 81/81（每份含 §1-§8） |
| D7 | 升级改造 Spec 体系 + 路线图 | [06-upgrade-roadmap.md](./06-upgrade-roadmap.md) | ✅ |
| D8 | dsh JS 插件中间件方案论证 | [07-dsh-js-bridge.md](./07-dsh-js-bridge.md) | ✅ |
| D9 | 交互式架构图页面 | [gnex3-architecture.html](./gnex3-architecture.html) | ✅ |
| - | 分析过程产物（子代理输出） | [_work/](./_work/) | ✅ |

---

## 一页读懂：GNEX3.0 = 企业骨架 + dsh 组合语义

```
GNEX 2.0（现状）                     GNEX 3.0（目标）
┌──────────────────────────┐      ┌────────────────────────────────────┐
│ 9 个 Spring 微服务        │      │ L0 组合与装载（装配清单/cordis-jvm） │
│ 能力 = 代码模块（改代码/    │ ───▶ │ L1 内核（ctx/事件/fiber/scope）     │
│ 构建/重启 = 加能力）        │      │ L2 服务层（9 服务保留·副本·弹性）    │
│ 企业特性（租户/多副本/      │      │ L3 能力层（52 插件·服务内组合）      │
│ 记忆SoR/审批/完成门）       │      │ L4 企业层（13 插件·🔒受保护）        │
│ d1-web React 前端         │      │ L5 客户端层（前端模块化）             │
└──────────────────────────┘      └────────────────────────────────────┘
 痛点：改动不插件化、不解耦       处方：服务是部署单元，插件是组合单元；
                                 dsh 已验证规制（ctx/inject/provide/
                                 waterfall/scope/能力缝）Java 移植；
                                 企业红线（P0-a/P0-b/E/租户）为受保护插件
```

## 关键决策速览

1. **插件内核**：自研 cordis-jvm（Java 移植 cordis 语义），不用 OSGi/PF4J 做内核（PF4J 留作 v3.1 热装类加载后端）——见 `_work/plugin-framework-selection.md`
2. **插件数量**：81 个（L0×6 · L1×8 · L3×52 · L4×13 · L5×2），16 个受保护（15 🔒platform + 1 🔐admin）
3. **继承红线**：完成证据门（P0-a）、企业记忆 SoR（P0-b）、审批原语（E）、租户边界 —— 均为受保护插件，**插件化不放松任何一条**
4. **JS 桥**：GraalJS 嵌入为主 + MCP 为辅，仅桥工具/逻辑类插件，企业红线在桥边界强制

## 建议阅读顺序

1. [00-PROJECT-TASKS.md](./00-PROJECT-TASKS.md)（任务书）
2. [01-gnex2.0-architecture.md](./01-gnex2.0-architecture.md)（现状）
3. [02-dsh-gnex-comparison.md](./02-dsh-gnex-comparison.md)（对比）
4. [03-fusion-design.md](./03-fusion-design.md)（融合）
5. [04-gnex3.0-plugin-catalog.md](./04-gnex3.0-plugin-catalog.md)（插件目录）
6. [05-interface-relationships.md](./05-interface-relationships.md)（接口关系）
7. [06-upgrade-roadmap.md](./06-upgrade-roadmap.md)（路线图）
8. [07-dsh-js-bridge.md](./07-dsh-js-bridge.md)（JS 桥）
9. [specs/](./specs/)（SDD 规格）

---

*变更记录：2026-08-17 v0.1 建立。*
