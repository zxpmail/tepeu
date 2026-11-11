# spec-sem-package

> 来源：FR-SEM-01、FR-SEM-06；决策 D-006
> 模块 ID：sem-package｜能力域：语义｜保护级别：core｜宿主：daf-sem｜实现状态：可开发

## §1 业务目标

智能体要先读懂应用能做什么，再提议修改。语义包是这份说明的唯一正文：实体、动作、约束、界面提示。词表以外的内容整单拒绝。对应 FR-SEM-01、FR-SEM-06。

## §2 需求映射与选型

| 维度 | 内容 |
|---|---|
| 需求映射 | FR-SEM-01、FR-SEM-06、D-006 |
| 对标借鉴 | Cube 强调口径只有一处。对象换成业务实体而不是分析指标 |
| 技术选型 | 选 JSON 文档加受控词表。备选是 OWL 与推理机。不选的反例：业务开发无法审阅三元组，组装失败时推理机给出的解释不能对应到某个字段。复决：若每月不少于 5 次组装失败是因为需要「子类也算父类」这种判断，再引入显式类型层次，仍不把自由 OWL 当输入 |
| 改造要点 | 未知键失败。约束表达式与行策略同一套 op，避免两套语言 |

## §3 接口契约

### 3.1 提供服务

| 服务键 | 方法签名 | 语义 |
|---|---|---|
| sem.package | `SemanticPackage publish(String appKey, JsonNode body, String version)` | 校验后保存。版本不可覆盖 |
| sem.package | `SemanticPackage current(String appKey)` | 最大已发布版本 |

### 3.2 REST

GET /apps/{appKey}/semantic；PUT 仅经安装或组装调用，不单独对浏览器开放覆盖写。

### 3.3 事件

无。

### 3.4 消费依赖

| 服务键 | 必选/可选 | 用途 | 缺失时降级 |
|---|---|---|---|
| dat.entity | 必选 | 实体键必须已存在或与本次安装草稿同时出现 | 单独发布时缺失即 422 |

## §4 数据模型

`daf_semantic`：tenant_id、app_key、version、body_json、created_at。主键 (tenant_id, app_key, version)。

## §5 实现思路

允许的顶层键仅 entities、actions、constraints、uiHints、packageRef。字段类型和动作使用字面量库。constraints 的 expr 与 daf_row_policy 相同。uiHints 的 block 只能是六种区块。

版本号正则 `^[0-9]+\.[0-9]+\.[0-9]+$`。同一版本重复 publish 返回已存在的记录，不改 body。body 不同则 409 RC-PACKAGE-CONFLICT。

## §6 边界

- ✅ 未知键返回 RC-SEMANTIC-UNKNOWN。
- ❌ 禁止在语义包里写页面像素或 SQL。
- ❌ 解耦红线：语义包不是送给模型的系统提示词，不写入 gnex prompt 服务。模型经工具 biz.describe 读取。

## §7 装配与配置

无。

## §8 验收

| ID | 场景 | Given | When | Then | 强度 | 推导源 | 自动化 | 状态 |
|---|---|---|---|---|---|---|---|---|
| ACC-SEM-01 | 合法包 | 实体已存在 | publish 1.0.0 | GET 返回该版本 | L1 | FR-SEM-01 | JUnit | 待实现 |
| ACC-SEM-02 | 未知键 | body 含 owlClass | publish | RC-SEMANTIC-UNKNOWN 且无新行 | L4 | FR-SEM-06 | JUnit | 待实现 |
| ACC-SEM-03 | 未知动作 | action=approve | publish | RC-SEMANTIC-UNKNOWN | L3 | FR-SEM-06 | JUnit | 待实现 |
| ACC-SEM-04 | 版本冲突 | 1.0.0 已存在且 body 不同 | 再 publish | 409 | L3 | FR-SEM-01 | JUnit | 待实现 |
| ACC-SEM-05 | 幂等 | 相同 body 再 publish | 调用两次 | 仍一行 | L2 | FR-SEM-01 | JUnit | 待实现 |

### 运行命令

```bash
mvn -pl daf-sem test -Dtest=SemanticPackageStoreTest
```

## §9 影响域

坏语义包不会改实体数据。current 只读已成功版本。

## §10 后续

不做自然语言同义词表。
