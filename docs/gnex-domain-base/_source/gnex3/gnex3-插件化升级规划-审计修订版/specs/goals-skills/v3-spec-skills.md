# v3-spec-skills

> **来源**：../../04-gnex3.0-plugin-catalog.md §3.8
> **负责人**：<owner>
> **插件 ID**：gnex3-cap-skills
> **分层**：能力层（L3）
> **受保护级别**：normal（管理员可装卸）
> **宿主服务**：registry-service（注册控制面）
> **实现状态**：规划中

---

## §1 业务目标

技能注册表（对齐 dsh-skill + dsh-skill-filesystem）：把 GNEX2.0 registry-service 已有的 SkillRegistryService（skill_registry 持久化 + 磁盘目录扫描 + 自动蒸馏摘要）与 HookExecutor/HookSubscription（PRE/POST hook，priority 0-1000）升级为事件化技能域。提供 skills 服务：技能注册/查询/卸载/导入、hook 注册与执行、目录变更事件 skills/change。技能发布/导入必须挂质量门禁（对齐 platform-service skill.quality：覆盖率/冒烟/隔离区），不合格进隔离区 quarantine。

**非目标**：不做技能运行时执行（执行经 agent-service 工具管线）；不做技能市场。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | @deepseek-ai/dsh-skill + @deepseek-ai/dsh-skill-filesystem |
| 对标 GNEX2.0 模块 | registry-service SkillRegistryService/HookExecutor/HookSubscription + platform-service skill.quality（QualityGateService） |
| 技术选型 | 🅶 GNEX2.0 模块改造 |
| 改造要点 | ① 技能注册/导入/卸载 emit skills/change（UI/缓存/观测同步）；② 质量门禁挂载到导入与发布路径（覆盖率+冒烟+隔离区）；③ HookExecutor 保留并接入事件总线（PRE/POST 按 priority 排序）；④ 技能文件经 MinIO 多副本同步（skill 定义可被任意副本拉取）；⑤ scope 先过滤再返回技能目录 |

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `skills` | com.gnex.cap.skill.SkillRegistryService | 技能注册/查询/卸载/导入 + hook 注册执行（HookExecutor/HookSubscription） |

### 3.2 注入服务（inject）

- `audit`（必选）— 导入/安装/卸载审计
- `permission`（可选）— 技能安装/发布权限校验
- `storage`（可选）— 技能文件存储（MinIO 同步）
- `goals`（可选）— 目标 round 引用技能目录

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| `skills/change` | emit | {skillName, tenantId, action(install/update/uninstall/import), quality} |
| `command/run` | listen | /skill 命令入口（可选） |

## §4 配置与装配

```yaml
plugins:
  - id: gnex3-cap-skills
    load: mandatory
    config:
      skills:
        quality-gate-enabled: true    # 覆盖率+冒烟+隔离区
        quarantine-on-fail: true
        scan-dir: ~/.gnex/skills
        hook-max-priority: 1000
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ 技能导入/发布必须过质量门禁，不合格进隔离区（对齐 skill.quality）
- ✅ 目录变更必须 emit skills/change（UI/缓存/观测统一消费）
- ✅ hook 执行失败不阻断主流程（对齐 ToolHookPort 语义）
- ❌ 技能目录不得跨租户可见（scope 先过滤）
- ❌ hook 不得执行危险命令（保留危险命令拦截）
- ❌ 技能定义不得绕过质量门禁直接发布（含自动导入路径）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | 技能注册与事件 | SkillRegistryTest：install/update/uninstall 各 emit skills/change |
| V2 | 质量门禁 | QualityGateTest：覆盖率不足/冒烟失败 → 进隔离区，不入目录 |
| V3 | hook 执行序 | HookExecutorTest：PRE/POST 按 priority 排序执行，失败不阻断 |
| V4 | 目录 scope 过滤 | SkillScopeTest：跨租户技能查询为空 |
| V5 | 文件同步 | SkillSyncTest：导入后技能文件推送 MinIO（多副本可拉取） |

### 运行命令

```bash
mvn -pl services/gnex-registry-service test "-Dtest=SkillRegistryTest,QualityGateTest,HookExecutorTest,SkillScopeTest,SkillSyncTest"
```

## §7 影响域

- 作用范围：**服务级**（registry-service 单例注册表，禁会话级复制）；故障影响半径：技能目录不可用=技能装卸/查询失败，执行侧不受影响。
- 被谁消费：tool-skill、agent 装配（preset）、goal round、UI 技能页。
- 升级影响：skill schema/hook 接口变更=全技能消费方回归。

## §8 后续（未纳入首版）

- 技能市场/外部格式导入扩展（gnex-skill-adapter-* 接入）
- hook 链可视化（观测）
- 技能版本化与回滚

---

*GNEX3.0 技能注册表 — gnex3-cap-skills SDD。*
