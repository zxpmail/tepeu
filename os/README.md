# os/ — Tepeu 重写骨架（develop）

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
