# observation — 模型可见管道

会话上下文入口：`Observation.view` = derive ∘ normalize ∘ shape。

- 读 `session.surface`，不持久化，不是第四 store
- 压缩仍在 loop（经日志替换端口）
- PromptAssembly（system 段）仍独立
- llm 只投影 + 传输，依赖本组件

compose 发行默认 `Observation.install(ContextShapers.defaults())`（密钥 redact + TOOL_RESULT 截断）。conformance 默认 shaper = none。

```bash
mvn -f os/pom.xml -pl observation -am test
```
