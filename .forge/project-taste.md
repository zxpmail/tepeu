# 项目口味（Project Taste）

> **第三块石碑**：团队偏好陈述，不是 lint 规则。Agent 在模糊空间里向这些偏好靠拢。
> 切口纪律（何时抽、谁批）是硬协议，见 `.claude/skills/_shared/cut-before-fill.md`。本文只写纹理：对着哪块现有代码续写。
> **人在纹理**：人守切法（一件事一个模块、三档角色、新切口谁批）。模型填格子。人不在单文件里改文笔。

---

## 如何使用

- 写码前打开**同一组件**的近邻，按它的切法填，不要发明第二种组织法
- 偏好，不是法律 — 违反应警觉；切口清单上的项按 cut-before-fill 走，不按本文降级
- Spec / ADR-016 写「要什么」；本文写「这块代码长什么样」

---

## 结构纹理（对着写，不要另起炉灶）

结构标本：[spaceXP](https://github.com/zxpmail/spaceXP)（说明见 `docs/archive/reference/spacexp-structure.md`）。学它的**找文件不用猜**，不学 starter / AOP / `*Utils`。结构糊了会变成泥球、日抛；单文件土不是病，角色放错才是。

组件内只认三档角色，新文件先选档再写：

| 档 | 放哪 | 例子 |
|----|------|------|
| 端口 / 聚合口 | 组件根包 | `Session`、`SessionStore`、`Observation`、`SessionLoop`、`CompletionGate` |
| 值对象 / 枚举 | 根包 | `LedgerEntry`、`PolicyVerdict` |
| persist 适配 | `*.persist` | `PersistedSessionStore`、`ApprovalSchema` |
| 本机默认实现 | `*.local` | `LocalProjectionBus`、`LocalCapabilityBus` |
| 测试夹具 | `src/test` | `InMemory*` |

不要发明 `autoconfigure` / `utils` / `support` / `common` 工具箱。`local` 是本机插头档，不是工具袋。根包只放端口和聚合口；实现进 `local` 或 `persist`。`InMemory*` 只许 `src/test`。根包 `interface` / `enum` 不得 import `*.local`（机器门：`os/compose` `PackageRoleTest`）。聚合口类（`Observation`、`SessionLoop`、compose 接线）可以下探本机插头；下一刀不要再往根包加引擎。

- 新持久化适配器跟 `session` / `policy` 现有 persist 适配器走；组件不建连、不关库；JDBC/方言只在 `persist.sqlite`
- 新投影只认 `ProjectionBus` 接口；本机默认 `LocalProjectionBus`，不写第二份契约
- 模型可见管道在 `llm`（`Observation.view`），不另立盒子
- `identity` / `syscall` 是词汇不是组件；边界写在 `package-info`，不新开运行时 logger
- `InMemory*` 只测试夹具，不当发行默认
- 完成态只经 entries + `CompletionGate`；host / 工具 / loop 不自报 `completed`
- 本模块继承不超过两层；更深则拆或组合
- 命名语义化；禁止 `handler2`、`util_new`、`commonV3`
- 两份平行复制先忍；第三份同构必须抽（见 cut-before-fill）
- 不相信「以后可能会用到」的扩展点，除非 ADR 或切口清单已批

---

## 代码风格与评审口味

- API / 组件边界不直连 SQLite；经 `Persist`
- 新依赖须说明用途，默认不引入新框架；`os/` 不上 Spring Boot、不上 ORM
- 人审只审切口。方法级优雅、第二次重复、能写成测试的 import 方向，不要写成 Must-fix

---

## 我们刻意不写成规则的事

- 战略与产品方向：见 ADR-016 / 底板，不在此展开
- v1 工作台肌肉记忆：见 `docs/archive/v1/`，不按那份写 `os/`

---

## 维护说明

- 反复在 CR 里出现的「感觉不对」→ 先写一条纹理指针（指向现有文件），再考虑升格为 cut-before-fill
- 与 `security-guidance.md` 冲突时：**安全红线优先**
- 季度回顾：删掉从未被引用的条目
