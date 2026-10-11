package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.ToolAsk;

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
 * 读、写、跑在请求里交给模型，从回答旁边的工具块收回。正文里的字不拿去执行。
 */
final class MimoAsker implements AskModel.Asker {

    /** 和以前的 Anthropic 调用一样，最多要这么多字。 */
    static final int MAX_TOKENS = 4096;

    /** 这一次问答最多等这么久。 */
    static final Duration WAIT = Duration.ofSeconds(60);

    private static final String API_VERSION = "2023-06-01";

    /** 模型认得的名字。Read、Write 到检查口再对上读东西、写东西。 */
    private static final String TOOLS = "["
            + "{\"name\":\"Read\",\"description\":\"读取限制里的文件或目录。目录返回里面的名字。要看代码就用它，不要叫人把代码贴过来。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}},"
            + "{\"name\":\"Grep\",\"description\":\"在限制里的文件中查找。pattern 是正则。path 可空，空则从限制目录找。不经过外壳。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"pattern\"]}},"
            + "{\"name\":\"Glob\",\"description\":\"按名字在限制里找文件。pattern 如 *.java。path 可空。不经过外壳。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"}},\"required\":[\"pattern\"]}},"
            + "{\"name\":\"Edit\",\"description\":\"把限制里某个文件中唯一的一段旧字换成新字。要人点头。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"old_string\":{\"type\":\"string\"},\"new_string\":{\"type\":\"string\"}},\"required\":[\"path\",\"old_string\",\"new_string\"]}},"
            + "{\"name\":\"Write\",\"description\":\"把字整篇写进限制里的一个文件。要人点头。\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"contents\":{\"type\":\"string\"}},\"required\":[\"path\",\"contents\"]}},"
            + "{\"name\":\"跑命令\",\"description\":\"启动一个程序。参数每行一个，不经过外壳\","
            + "\"input_schema\":{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"},\"args\":{\"type\":\"string\"}},\"required\":[\"target\"]}}"
            + "]";

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
            return Reply.success(textOf(response.body()), asksOf(response.body()));
        } catch (HttpTimeoutException ex) {
            return new Reply(ResultKind.超时, null, "");
        } catch (IOException ex) {
            return new Reply(ResultKind.失败, "IO", "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Reply(ResultKind.超时, null, "");
        }
    }

    /** 请求体带模型、这几行，以及旁边的三样工具。 */
    static String requestJson(String model, String content) {
        return "{\"model\":" + quote(model)
                + ",\"max_tokens\":" + MAX_TOKENS
                + ",\"tools\":" + TOOLS
                + ",\"messages\":[{\"role\":\"user\",\"content\":" + quote(content) + "}]}";
    }

    /** 把回答旁边的工具块收成名单。正文里的字不在这里。 */
    static List<ToolAsk> asksOf(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<ToolAsk> asks = new ArrayList<>();
        int cursor = 0;
        while (cursor < json.length()) {
            int mark = indexOfType(json, cursor, "tool_use");
            if (mark < 0) {
                break;
            }
            int start = json.lastIndexOf('{', mark);
            int end = start < 0 ? -1 : endOfObject(json, start);
            if (end < 0) {
                break;
            }
            asks.add(toolAsk(json.substring(start, end)));
            cursor = end;
        }
        return List.copyOf(asks);
    }

    /** 从一块工具里取出名字、目标和附带的字。 */
    private static ToolAsk toolAsk(String object) {
        String name = field(object, "name");
        String path = first(object, "path", "file_path", "target");
        if (Ability.GREP.equals(name) || Ability.GLOB.equals(name)) {
            return new ToolAsk(name, path, first(object, "pattern", "glob", "query"));
        }
        if ("Write".equals(name)) {
            return new ToolAsk(name, path, first(object, "contents", "content", "text"));
        }
        if (Ability.EDIT.equals(name)) {
            return new ToolAsk(name, path, first(object, "old_string"), first(object, "new_string"));
        }
        if (Ability.RUN.equals(name)) {
            return new ToolAsk(name, path, field(object, "args"));
        }
        return new ToolAsk(name, path, "");
    }

    /** 这几个键里，用先写到的那个。 */
    private static String first(String json, String... keys) {
        for (String key : keys) {
            String value = field(json, key);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    /** 读这个键后面的字符串。没有就是空。 */
    private static String field(String json, String key) {
        int keyAt = json.indexOf("\"" + key + "\"");
        if (keyAt < 0) {
            return "";
        }
        StringCursor value = readString(json, keyAt + key.length() + 2);
        return value == null ? "" : value.text();
    }

    /** 从这里的左花括号配到右花括号。配不上就是 -1。 */
    private static int endOfObject(String json, int start) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (ch == '\\') {
                    i = i + 1;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
            } else if (ch == '{') {
                depth = depth + 1;
            } else if (ch == '}') {
                depth = depth - 1;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return -1;
    }

    /** 找到下一个 type 标记的结尾。没有就是 -1。 */
    private static int indexOfType(String json, int from, String type) {
        int tight = json.indexOf("\"type\":\"" + type + "\"", from);
        int spaced = json.indexOf("\"type\": \"" + type + "\"", from);
        if (tight < 0 && spaced < 0) {
            return -1;
        }
        if (tight < 0 || (spaced >= 0 && spaced < tight)) {
            return spaced + ("\"type\": \"" + type + "\"").length();
        }
        return tight + ("\"type\":\"" + type + "\"").length();
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
