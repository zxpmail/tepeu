# llm.* 组件

派生式断言：`llm.generate` 不接收 messages，恒 `derive(surface) ∘ normalize` 再投影。

传输：默认 `FakeLlmTransport`。Anthropic 一族 `AnthropicHttpTransport`（JDK HttpClient，body = prepared.wireJson）。OpenAI 一族 `OpenAiHttpTransport`（`POST {base}/v1/chat/completions`，Bearer；body = prepared.wireJson）。compose 不读密钥。

```bash
mvn -f os/pom.xml -pl llm test
```
