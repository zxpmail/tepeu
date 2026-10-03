package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 把已经准备好的几行发给 Xiaomi MiMo，把回答正文交回来。
 * 地址、模型和密钥来自 cc-switch。几行不改写，也不从回答里再找能力。
 */
final class MimoAsker implements AskModel.Asker {

    /** 和以前的 Anthropic 调用一样，最多要这么多字。 */
    static final int MAX_TOKENS = 4096;

    /** 这一次问答最多等这么久。 */
    static final Duration WAIT = Duration.ofSeconds(60);

    private static final String API_VERSION = "2023-06-01";

    private final String baseUrl;
    private final String model;
    private final String token;
    private final HttpClient http;
    private final Duration wait;

    /** 用这一家供应商。 */
    MimoAsker(CcSwitch.Supplier supplier) {
        this(supplier.baseUrl(), supplier.model(), supplier.token(), HttpClient.newHttpClient(), WAIT);
    }

    /** 测试可以换掉发送和时限。 */
    MimoAsker(String baseUrl, String model, String token, HttpClient http, Duration wait) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.token = token;
        this.http = http;
        this.wait = wait;
    }

    /** 几行原样拼成一条，发给模型。失败不带上游正文，避免把密钥写进记录。 */
    @Override
    public Reply ask(List<String> lines) {
        String content = String.join("\n", lines == null ? List.of() : lines);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .timeout(wait)
                .header("x-api-key", token)
                .header("Authorization", "Bearer " + token)
                .header("anthropic-version", API_VERSION)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson(model, content)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                return new Reply(ResultKind.失败, "HTTP_" + status, "");
            }
            return Reply.success(textOf(response.body()));
        } catch (HttpTimeoutException ex) {
            return new Reply(ResultKind.超时, null, "");
        } catch (IOException ex) {
            return new Reply(ResultKind.失败, "IO", "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Reply(ResultKind.超时, null, "");
        }
    }

    /** 请求体只带模型和这几行，不另加说明。 */
    static String requestJson(String model, String content) {
        return "{\"model\":" + quote(model)
                + ",\"max_tokens\":" + MAX_TOKENS
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + quote(content) + "}]}";
    }

    /** 把回答里的文字块接起来。没有文字就是空。 */
    static String textOf(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        int cursor = 0;
        while (cursor < json.length()) {
            int mark = indexOfTypeText(json, cursor);
            if (mark < 0) {
                break;
            }
            int textKey = json.indexOf("\"text\"", mark);
            if (textKey < 0) {
                break;
            }
            StringCursor value = readString(json, textKey + 6);
            if (value == null) {
                cursor = textKey + 6;
                continue;
            }
            parts.add(value.text());
            cursor = value.next();
        }
        return String.join("\n", parts);
    }

    /** 找到下一个文字块 type 标记的结尾。没有就是 -1。 */
    private static int indexOfTypeText(String json, int from) {
        int tight = json.indexOf("\"type\":\"text\"", from);
        int spaced = json.indexOf("\"type\": \"text\"", from);
        if (tight < 0 && spaced < 0) {
            return -1;
        }
        if (tight < 0 || (spaced >= 0 && spaced < tight)) {
            return spaced + "\"type\": \"text\"".length();
        }
        return tight + "\"type\":\"text\"".length();
    }

    /** 从冒号后面读一个 JSON 字符串。 */
    private static StringCursor readString(String json, int from) {
        int quote = json.indexOf('"', from);
        if (quote < 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '"') {
                return new StringCursor(text.toString(), i + 1);
            }
            if (ch != '\\' || i + 1 >= json.length()) {
                text.append(ch);
                continue;
            }
            char escaped = json.charAt(++i);
            if (escaped == 'u' && i + 4 < json.length()) {
                text.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                i += 4;
                continue;
            }
            text.append(switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case '"' -> '"';
                case '\\' -> '\\';
                default -> escaped;
            });
        }
        return null;
    }

    /** 把一段字收成 JSON 字符串。 */
    private static String quote(String raw) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    /** 读到的字符串，以及下一处继续看的位置。 */
    private record StringCursor(String text, int next) {
    }
}
