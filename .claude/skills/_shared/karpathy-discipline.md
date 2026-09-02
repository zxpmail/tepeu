# Karpathy 四原则（共享指针）

> 各 Skill 不重复全文。**执行前**按需读 [`behavior-rules.md`](../../docs/behavior-rules.md)。

| 原则 | 一句话 |
|------|--------|
| Think Before Coding | 不猜；有歧义先问；有 tradeoff 先摆 |
| Simplicity First | 最少代码；不写未来抽象 |
| Surgical Changes | 只改必须改的；每行变更可追溯 |
| Goal-Driven Execution | 可验证标准；完成须附验证输出 |

**切 vs 填**：抽象与组件边界按 [`cut-before-fill.md`](cut-before-fill.md)。第三次同构必须抽，不算「未来抽象」；清单上的提取不算 drive-by。

**可简化场景**（typo、纯文档、lint 修复等）→ 见 behavior-rules.md §何时可简化。
