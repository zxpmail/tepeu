# v3-spec-plugin-runtime-events

> **来源**：../../04-gnex3.0-plugin-catalog.md §L1 内核层
> **负责人**：<owner>
> **插件 ID**：gnex3-l1-events
> **分层**：L1 内核层
> **受保护级别**：normal（默认；装配声明 mandatory 时实际不可卸载）
> **宿主服务**：全部 gnex 服务
> **实现状态**：规划中
> **审计修订**：v0.2（2026-08-18）· 修订依据：审计修订版/02-设计决策记录.md D-P1-20

---

## §1 业务目标

实现事件总线语义（cordis events + dsh-scope 的 Java 移植）：waterfall（可拦截/可否决/可替换载荷）与 emit（只通知、异常隔离）两种分发，监听器按 priority 排序，事件携带 scope 上下文供 realm 过滤。工具管线（tools/*）、审批链（approval/*）是 waterfall 的关键消费者；事件命名对齐 05 接口关系文档词汇（域/动作）。跨服务透出经 bus 插件映射为 `gnex.<域>.<动作>` topic。

**非目标**：不做跨服务消息（bus 插件承接）；不定义业务事件词汇（见 05 文档 §2）；不做事件持久化（会话日志由 session-store 承接）。

## §2 映射关系

| 维度 | 映射 |
|------|------|
| 对标 dsh 插件 | cordis events + dsh-scope（事件 scope 过滤） |
| 对标 GNEX2.0 模块 | Spring ApplicationEvent + Bus（改造融合） |
| 技术选型 | 🅳 Java 移植 dsh 事件语义 |
| 改造要点 | ① `Events` 接口（waterfall/emit/on + scope 过滤）② waterfall 链按 priority 排序，支持 deny/替换载荷 ③ emit 监听器异常隔离（不炸主流程）④ 事件命名对齐 05 词汇（域/动作）⑤ 跨服务透出经 bus 插件（topic 映射，scope=跨服务事件） |

**派发模型裁定（D-P1-20）**：
- **emit = 异步派发**：事件提交到虚拟线程队列，**每监听器独立执行 + 异常隔离**——emit 调用方立即返回，单个监听器抛错/阻塞不影响其余监听器与主流程；
- **waterfall = 同步**：在调度线程上按序执行整条链（结果需返回调用方），带 **5s 超时**（`waterfall-timeout`）熔断，超时按 deny 处理（fail-closed）。

## §3 插件接口（契约）

### 3.1 提供服务（provide）

| 服务名 | 类型/接口 | 说明 |
|--------|-----------|------|
| `events` | `com.gnex.plugin.runtime.Events` | waterfall/emit/on 分发 |

### 3.2 注入服务（inject）

- `ctx`（必选）：注册 events 服务
- `scope`（可选）：realm 过滤（未装配时不过滤）

### 3.3 事件（emit / listen）

| 事件 | 方向 | 载荷要点 |
|------|------|----------|
| （无自定义事件） | — | 本插件为分发机制；事件词汇见 05 接口关系文档 §2（plugin/* · tools/* · approval/* 等） |

## §4 配置与装配

```yaml
# service-assembly.yml 片段
plugins:
  - id: gnex3-l1-events
    load: mandatory
gnex.events.scope-filter: true       # 事件携带 scope 上下文过滤
gnex.events.waterfall-timeout: 5s    # waterfall 链超时保护
```

## §5 边界（禁止 ❌ / 必须 ✅）

- ✅ waterfall 必须支持监听器可否决（deny 后下游不执行，如工具管线拦截）
- ✅ emit 必须**异步派发**：调用方在入队后立即返回；每个监听器在独立虚拟线程上执行；任一监听器抛错仅记录（含监听器标识与异常栈）并继续其余监听器，不得向调用方传播（D-P1-20 可执行表述）
- ✅ waterfall 必须在调度线程上同步执行整条链，且受 `waterfall-timeout`（默认 5s）约束：超时熔断按 deny 处理（fail-closed），并记录超时链快照（哪个监听器超时）（D-P1-20 可执行表述）
- ✅ 事件载荷必须携带 scope 上下文（租户/会话 realm 供过滤）
- ❌ emit 监听器不得阻塞调度线程（派发线程只在入队后即返回；监听器内同步长耗时操作应自行转后台任务）
- ❌ waterfall 监听器不得自行另起线程执行链环节（必须同步返回结果，否则 deny/替换载荷语义失效）
- ❌ 事件载荷不得携带密钥等敏感明文（脱敏后分发）

## §6 验收

| ID | 场景 | 断言/测试 |
|----|------|-----------|
| V1 | waterfall deny | `EventWaterfallTest`：监听器 deny 后下游不执行 |
| V2 | emit 异常隔离 | `EventEmitTest`：监听器抛错主流程继续 |
| V3 | priority 排序 | `EventPriorityTest`：按 priority 顺序执行 |
| V4 | scope 过滤 | `EventScopeFilterTest`：realm 不匹配的监听器不触发 |
| V5 | 载荷替换 | `EventWaterfallTest`：waterfall 可替换载荷供下游消费 |
| V6 | 异步派发断言 | `EventAsyncDispatchTest`：emit 调用方在监听器完成前返回；单监听器抛错/阻塞（sleep）时其余监听器仍全部执行、主流程无异常；waterfall 超 5s 被熔断为 deny |

### 运行命令

```bash
mvn -pl libs/gnex-plugin-runtime test "-Dtest=Event*Test"
# 目标模块（待 S 阶段新建，D-P1-02）；通配覆盖 V1-V6 全部测试类（D-P0-03）
```

## §7 影响域

- 作用范围：**服务进程级**——全部插件协作的通信机制。
- 被谁消费：工具管线/审批链（waterfall）；会话/可观测（emit）；bus 插件（跨服务透出）。
- 故障影响半径：总线缺陷 = 插件协作中断（工具管线/审批不可用）；emit 异常隔离限制半径。
- 升级影响：分发语义升级 = 全服务回归（waterfall/emit 契约向后兼容）。

## §8 后续（未纳入首版）

- 事件订阅持久化/回放（审计证据链）
- waterfall 链的断点诊断（哪一环否决了事件）
- 跨服务事件订阅管理（bus topic 映射 UI）

---

*GNEX3.0 事件总线 — waterfall/emit + scope 过滤 SDD。*
