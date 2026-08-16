# os/ — Tepeu 重写骨架（develop）

实施底板：[docs/os-baseplate.md](../docs/os-baseplate.md)  
规范：[memory/decisions-log.md](../memory/decisions-log.md) ADR-016  
外部参照：[claude-code-reference.md](../docs/claude-code-reference.md)（CC 巨石）· [pi-reference.md](../docs/pi-reference.md)（Pi 极简）· [trinios-reference.md](../docs/trinios-reference.md)（真 OS 镜鉴）· [aios-reference.md](../docs/aios-reference.md)（同名学术前辈，均非规范）  
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
