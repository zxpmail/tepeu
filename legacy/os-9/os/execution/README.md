# execution — execution.* 工作区囚笼 + OS jail

一件事：fs 读写限制在装配时绑定的 workspace；`execution.sandbox.probe` 如实报 `isolation=partial`（无 landlock / 无受限令牌，禁止报 FULL）。

`execution.proc.spawn` 只接受工作区相对 `path`。Windows：`CreateProcessW` + `CREATE_SUSPENDED`，入 Job 后再 `ResumeThread`（`KILL_ON_JOB_CLOSE`）。Linux：`bwrap --clearenv` + `--ro-bind-try` `/usr` `/bin` `/lib` `/lib64`。子进程环境白名单，不继承 JVM 密钥。stdout 封顶 8192。无 jail → `SANDBOX_UNAVAILABLE`，禁止静默未沙箱直通。路径解析拒绝绝对路径、符号链接、junction。

Policy=授权，Sandbox=隔离。写盘/进程默认 ASK（`DefaultRuleMatrix`）；批准之后仍须过 jail。

```bash
mvn -f os/pom.xml -pl execution -am test
```
