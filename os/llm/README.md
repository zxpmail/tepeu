# llm.* 组件

派生式断言：`llm.generate` 不接收 messages，恒 `Observation.view(surface)`（observation 组件：derive ∘ normalize ∘ shape）再投影。compose 默认 `ContextShapers.defaults()`（密钥 redact + TOOL_RESULT 截断）。

传输：默认 `FakeLlmTransport`。Anthropic 一族 `AnthropicHttpTransport`（JDK HttpClient，body = prepared.wireJson）。OpenAI 一族 `OpenAiHttpTransport`（`POST {base}/v1/chat/completions`，Bearer；body = prepared.wireJson）。`LlmTransports.fromEnv()` 供调用方注入。compose **不**读密钥。

live 测试：设置 `ANTHROPIC_API_KEY` 或 `OPENAI_API_KEY` 才会跑；CI 默认 skip。cost 空 = n/a。

```bash
mvn -f os/pom.xml -pl llm -am test
```
