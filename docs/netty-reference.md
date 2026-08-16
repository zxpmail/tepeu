# Netty 4.2 对 Tepeu OS 的启示（工程理念参照）

> **地位**：参照材料，不是规范；tepeu **不把 Netty 作为运行时依赖**（不写网络层），吸收的是理念与算法。裁决以 [ADR-016](../memory/decisions-log.md) + [os-baseplate.md](../os-baseplate.md) 为准。
> **来源**：`E:\work\netty-4.2`（Netty 4.2.18.Final-SNAPSHOT 源码）。2026-08-16 单路深查（时间轮 / 泄漏检测 / 背压 / 传输抽象 / 管线 / 执行模型 + 杂项）。非 git 仓库，版本弱钉。
> **六参照定位**：CC 巨石 · Pi 极简 · TriniOS 真 OS · AIOS 学术同名 · OpenCode 工程同代 · **Netty 工程理念源**——第一个非 agent 域参照，价值在「带卫兵的事件驱动内核」怎么做工业级。

---

## 1. 总判断

1. **Netty 4.2 自身的演化方向恰是 tepeu 架构的镜像**：4.2 头号重构 = IoHandler 五件套把 EventLoop 与传输解耦、`NioEventLoop` 弃用——「内核管执行面，IO/能力全部变成可插拔 handler」。tepeu 的 ①/② 缝切分拿到了工业级先例的同构印证。
2. **三个直接解现有挂账**：时间轮 → 「租约 TTL 记而不执」；双水位滞回 → ProjectionBus SSE 慢消费者；Ticker/MockTicker → TTL/超时的 conformance 可测试性。
3. Netty 的**元模式三条**比任何数据结构都值钱：**显式契约**（线程约束写进接口 javadoc，不靠文档习惯）、**惰性清理**（cancel/close 都 O(1)，真实回收推迟到下一 tick）、**采样观测**（一切观测可采样降级：leak level、recycler ratio）。

## 2. 吸收清单（按对 tepeu 的紧迫度排序）

### 2.1 → 解挂账：租约 TTL 定时器（时间轮 / 优先队列）

`HashedWheelTimer`（`common/.../HashedWheelTimer.java`）：插入/取消均 O(1)（cancel = 一次 CAS 状态位 + 延迟一 tick 回收，:656-666）、单 worker 批量到期、每 tick 限转 10 万个防提交风暴（:522-525）、`maxPendingTimeouts` 自带配额、**实例数守卫**（超 64 个实例即 WARN「必须全 JVM 共享」）。要点：**到期 ≠ 执行**——expire 经 taskExecutor 分发，计时面与动作面分离（TTL 到期的回收动作应投回 turn 执行面，不在 timer 线程改 registers）。
**选型判据**（严苛节 C2）：租约量 < 万级 → 先用 `AbstractScheduledEventExecutor` 式 per-domain 优先队列起步；量级上来再换轮。**判据进切片设计，不预设时间轮**。

### 2.2 → 解挂账：死租约/资源泄漏的采样检测

`ResourceLeakDetector`：四级 severity（DISABLED/SIMPLE/ADVANCED/PARANOID）、**弱引用 + GC 当探测器**（资源被 GC 而 tracker 未 close = 泄漏实锤，ReferenceQueue 轮询零后台线程，:311-342）、`1/N` 采样（默认 128）、报告按内容去重、LeakListener 可挂账本。
tepeu 落法：claim 租约时按 1/N 采样包 WeakReference tracker；会话对象被 GC 而 tracker 未 close → WARN + 创建点（记录 syscall id/turn id，比 30 帧栈更有用）；测试期 PARANOID、生产 SIMPLE。

### 2.3 → 解挂账：ProjectionBus SSE 背压 = 双水位滞回闭环

Netty 的答案是**闭环控制**而非一次性三选（断开/丢弃/降级）：出口积压超 high → `channelWritabilityChanged`（**位掩码 CAS 只在 0↔非0 跳变时发一次**，防通知风暴）→ 业务关入口（autoRead=false，背压传到 TCP 对端）→ 积压跌破 **low**（不是 high——滞回防抖）→ 重开。
tepeu 落法：每 SSE 订阅者一个 outbox + 双阈值滞回；超 high 后按订阅语义分级——entries 投影**降级**（折叠/只发指针），ledger 类不可丢流**断开**（客户端带游标重连）。

### 2.4 → conformance 基建：Ticker/MockTicker + EmbeddedChannel

- **`Ticker` 可注入时钟**（4.2 把 `System.nanoTime()` 抽象出来，MockTicker 可编程推进时间）——TTL/超时测试**拨时钟而非真 sleep**。tepeu conformance 的基础设施前提：自写 `Clock` 接口进 kernel。
- **EmbeddedChannel**：无网络传输让同一套 pipeline 在测试里跑通全部事件语义——即 conformance「假实现跑契约」模式（InMemoryLlmProvider / ImmediateExecution 同构物）。与 rg 差分 oracle 互补。

### 2.5 → 总线卫兵链三件

1. **显式传播**：handler 不调 `ctx.fireChannelRead` 链就断——tepeu 卫兵的「显式中断（deny）」与「卫兵自身异常」是两条通道，异常转成事件继续走链（fail-closed 视作 deny + 异常入 entries），不炸整条管线。
2. **链尾兜底清资源**：Netty tail 不只 log，还 `release` 事件载荷——tepeu 分发到无 handler 的 syscall，兜底要做资源清理 + WARN。
3. **@Skip 兴趣掩码**：卫兵声明关心的 syscall 类别，总线预计算位掩码跳过无关卫兵——卫兵链开销从「每 syscall 走全链」降为「只走声明者」。**@Sharable** = 无状态卫兵可共享的文档注解约定（有状态如配额余额者 per-session 实例）。

### 2.6 → turn 执行面：串行域 + 禁令显性化

- **串行域线程封闭**仍然成立：一个会话的所有 syscall 在同一串行执行域 → 三 store 免大部分锁。虚线程让「每会话一个串行域」更便宜，不是取消它。
- **`BlockingOperationException`**：在执行域线程上 await 直接抛——把「你在不该阻塞的地方阻塞了」从隐性死锁变成显性异常。
- **ThreadExecutorMap 回调重映射**：provider 异步回调统一 remap 回会话串行域，禁止回调在 provider 线程直接改 registers。
- **被虚线程取代**：手工事件循环/MPSC 唤醒/wakeup-task 整套不需要（JVM 调度器管）；JCTools MPSC 若需要可直接依赖。

**SessionLoop 提案（用户提出，仿 EventLoop 会话粒度，2026-08-16 记入待裁；同日严苛复核修正过卖）**：每会话一个单线程串行执行域（虚线程），`execute / assertInLoop / schedule(delay)` 三件接口；会话一切**状态变更**（entries/registers/ledger/claim/定时回收）pin 域内——此为**无条件成立**的部分（三 store 唯一写者、可断言纪律、TTL per-loop PQ 回投、回调 remap、跨域禁同步等待）。**条件成立（取决于 ③ Loop 形态，裁决时必须摆上台面的真权衡）**：「maintenance 独占=物理保证」「抢占=队列插队」**仅当 ③ Loop 写成事件驱动状态机**（step 是域任务、LLM/工具在域外工作线程、完成回投）时成立；若 ③ 保持阻塞式循环（CC/Pi/OpenCode 全是此款），turn 跑独立虚线程、经 `loop.execute` 回写状态——独占与抢占仍由已裁 LoopRuntime 协议承担，SessionLoop 降为状态面串行化基底。**当前倾向（待裁）**：阻塞式 + 显式门（简单、全参照同款、已裁协议覆盖），SessionLoop 先只做状态面。**四坑**：① 命名消歧「SessionLoop(①执行域) ≠ LoopRuntime(③编排态)」；② 域线程禁跑长阻塞——工作丢虚线程、完成回投；③ 跨会话=跨域，他域 store 必须 `targetLoop.execute()` 转投，域上同步等跨域 future 直接抛；④ **域任务队列不得成为第二个 Inbox**——顺序真相在 Inbox/LoopRuntime，域队列只执行状态机跃迁、不自长业务语义。**勿以性能卖**（tepeu 量级下锁不是痛点，价值在正确性结构化）；Java 21 虚线程在 `synchronized` 内阻塞会 pin carrier（JDK24/JEP491 修），域任务调用旧同步代码需留意。量级合身（数十会话×虚线程）。归「kernel 端口演化」切片随 C1/C2/C3 一并裁（冻结中）。

### 2.7 杂项速记

DefaultPromise 单 listener 快路径 + **listener 栈深守卫**（防递归换栈）；Signal 类型化控制流常量异常（TurnAborted 同构）；「自举检测」（HashedWheelTimer 用 leak detector 检测自己）；FastThreadLocal 的卫生纪律（thread local 必须 removeAll）保留、实现不吸收。

## 3. 明确不吸收（虚线程时代过时）

`Recycler` 对象池（ZGC 分代下负收益）；`FastThreadLocal` 实现（虚线程无快路径，上下文用 ScopedValue）；手工 EventLoop 全套实现形态；pipeline 双向出入站模型（tepeu 总线单向，组合代数已裁且更严）。

## 4. 严苛节（对 tepeu 自身）

**C1 量级错配是最大风险**。Netty 为百万连接设计；tepeu 单机会话/租约/订阅者量级小几个数量级。时间轮、位掩码、MPSC 这些优化在 tepeu 量级下可能是**过度工程**——吸收判据必须写进切片设计（如 2.1 的选型判据），每项先问「tepeu 的量级下这笔复杂度买回什么」。这与 CC 参照 §5-C3 的规模警告同族。

**C2 别被管线隐喻带偏**。Netty 的链式责任模型（任一 handler 可改写/吞事件/双向传播）与 tepeu 已裁的组合代数（deny>ask>allow 格、封闭 union、allow 压不过 deny）是**两种模型**——Netty 灵活、tepeu 严格。吸收其「显式中断 vs 异常」「掩码跳过」「兜底清资源」三件机制，**不**吸收「handler 可改写消息继续传」的形态（tepeu 卫兵不是 transformer）。

**C3 串行域 ≠ 事件循环**。吸收「每会话串行执行域」的**不变量**（线程封闭→免锁），实现用虚线程 executor 而非手写 loop。若发现自己在写 selector/wakeup 代码，就是走岔了。

**证据链**：单路报告，行号未复核；关键算法（时间轮 CAS cancel、水位边沿触发、弱引用泄漏判定）机制自洽且为公认实现，风险低。

## 5. 对切片的影响（零新裁决，守冻结）

- **② conformance 切片**直接吃三样：`Clock`/MockTicker 接口（拨时钟）、EmbeddedChannel 假实现模式、（与 rg 差分互补的）契约测试思路。
- **挂账新增两行**（进 §8.5）：timer 基础设施（PQ 起步、判据升级时间轮——解「死租约可回收」drift）；采样泄漏检测（租约/spill 生命周期审计）。
- ProjectionBus 背压（双水位滞回）记入缝的行为规格，随 ProjectionBus 落码兑现。
