# os/ — Tepeu 重写骨架（develop）

> **身份陈述（目标态——实现进度见下表，未实现者不以现状宣称）**：CC 一切皆内置 · Pi 一切皆安装件 · dsh 一切皆插件 · OpenCode 一切皆契约客户端 · AIOS 一切皆资源队列——**tepeu：不变量进内核，能力全在缝上**。一切都是 syscall，syscall 只有一扇门；门上焊死四样不可插拔（Policy 封闭 union + 卫兵 fail-closed、双真相 journal-first、封闭词汇表、单次许可），门外的 LLM/工具/存储/编排皆是必须过 conformance 的可换实现。别的系统回答「能做什么」，本内核回答「不允许发生什么」。

实施底板：[docs/os-baseplate.md](../docs/os-baseplate.md)  
规范：[memory/decisions-log.md](../memory/decisions-log.md) ADR-016  
外部参照：[claude-code](../docs/claude-code-reference.md)（巨石）· [pi](../docs/pi-reference.md)（极简）· [trinios](../docs/trinios-reference.md)（真 OS）· [aios](../docs/aios-reference.md)（学术同名）· [opencode](../docs/opencode-reference.md)（工程同代），均非规范  
v1 标本：[legacy/](../legacy/README.md)（只读）

| 目录 | 环 | 状态 |
|------|----|------|
| `kernel/` | ① | 端口已落地（identity/context/session/bus） |
| `adaptors/` | ② | 内存 Session + Bus 冒烟通过 |
| `orchestration/` | ③ | Loop/Command/Prompt…，待实现 |
| `routing/` | ④ | 三 Router，待实现 |
| `compose/` | 接线 | 开机组装，待实现 |

规则：新代码只进 `os/`；禁止在 `legacy/` 加功能。

```bash
mvn -f os/pom.xml test
```
