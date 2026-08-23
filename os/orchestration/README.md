# orchestration — ③ PromptAssembly + CommandDispatcher

一件事：按有序 Section 组装进模文本；Slash 命令不经模型。Team / Subagent / LongTask / 路由三决策仍未落。

Loop **不**依赖本模块。`LoopConfig.system` 仍只转发；调用方 assemble 后再传入。红线「须经 PromptAssembly」是调用方/compose 纪律。

PromptAssembly：STATIC → system；DYNAMIC → `dynamicBodies`（user-role 快照，不混进 system）。超预算出账单（先丢宽泛 DYNAMIC，后截最具体）。weight = 字符数。收据 = includedIds + bill。不新开事件类型。技能目录仅 name/description/digest。

CommandDispatcher：只见 local / prompt。未知 `/name` 失败，不进 Inbox。prompt 型 `inbox.enqueue`，不跑 Loop。compose 预注册 `/help`。

```bash
mvn -f os/pom.xml -pl orchestration test
```
