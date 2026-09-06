# session — 会话三 store + Inbox/claim

一件事：会话聚合口。真相分域 entries / registers / ledger，没有第四个地方。
Inbox/claim = 进场与租约，**不是**调度器。人手审计走 `AuditSink`，不进 entries。
`ProjectionBus` 是通知，完成权读 entries。适配器只认 `Persist`，不建连接、不关库。

```bash
mvn -f os/pom.xml -pl session -am test
```

## 架构

session 只暴露端口。一张会话上：三 store + Inbox + 替换面 + blobs。
`AuditSink` / `ProjectionBus` / `Metering` 挂在旁边，不是第四个真相桶。

```mermaid
flowchart LR
  subgraph callers["调用方 · 不在本组件"]
    host["host / Slash"]
    loop["SessionLoop"]
    obs["Observation.view · llm"]
  end

  subgraph ports["session"]
    store["SessionStore\ncreate get fork"]
    sess["Session"]
    subgraph truth["每个载荷只进其一"]
      log["SessionLog\nevents 全量"]
      led["SessionLedger\n用量"]
      reg["SessionRegisters\nloop.state 等"]
    end
    inbox["SessionInbox\nenqueue claim ack\n不是调度器"]
    replace["LogReplacePort\nreplaceRange\nsurface 给模型"]
    blobs["ContentStore"]
    meter["Metering\n只供数 不裁决"]
    audit["AuditSink\n人手 不进 entries"]
    proj["ProjectionBus\n通知 不是真相"]
  end

  subgraph persistSide["session.persist → Persist"]
    tables["sessions events surface\nregisters ledger inbox\nblobs audit meta"]
  end

  host -->|普通句| inbox
  host -->|/approve| audit
  loop --> store
  loop --> sess
  loop --> meter
  sess --> log
  sess --> led
  sess --> reg
  sess --> inbox
  sess --> replace
  sess --> blobs
  meter --> led
  obs -.->|读 surface| replace
  store --> sess
  log --> tables
  led --> tables
  reg --> tables
  inbox --> tables
  replace --> tables
  blobs --> tables
  audit --> tables
```

`events` 不删。压缩只改 `surface` 序，并插 `COMPACTION_CHECKPOINT`。
`recover()` 补未配对 `TOOL_RESULT`，不截断日志。
依赖：`identity` · `syscall` · `persist/api`。sqlite 仅 test。
不做 Loop / Policy / 总线 / UI / 建连关库。

## 时序

只画 session 自己。调用方是谁（host / Loop）不在这张图里。
`AuditSink` / `ProjectionBus` / `Metering` 不在这条路上。`fork` / `recover` / `replaceRange` 另走。

```mermaid
sequenceDiagram
  autonumber
  participant C as 调用方
  participant Store as SessionStore
  participant Inbox as SessionInbox
  participant Log as SessionLog
  participant Led as SessionLedger
  participant Db as Persist

  C->>Store: create(owner, ns)
  Store->>Db: INSERT sessions
  Store-->>C: Session
  C->>Inbox: enqueue
  Inbox->>Db: INSERT inbox
  C->>Inbox: claimNext
  Inbox->>Db: 租约 + TTL
  Inbox-->>C: ClaimLease
  C->>Log: append
  Log->>Db: INSERT events
  C->>Led: record
  Led->>Db: INSERT ledger
  C->>Inbox: ack
  Inbox->>Db: DELETE inbox
```
