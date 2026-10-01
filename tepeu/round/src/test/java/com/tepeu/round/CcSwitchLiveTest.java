package com.tepeu.round;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用本机 CC Switch 当前供应商跑一轮。
 * 没设置环境变量时不跑。密钥不写入仓库，也不写进断言。
 */
@EnabledIfEnvironmentVariable(named = "TEPEU_CC_KEY", matches = ".+")
class CcSwitchLiveTest {

    @Test
    void 纯聊问模型有返回且不做判定() throws Exception {
        LiveReply replies = new LiveReply();
        Round round = new Round(Set.of(Gate.ASK_MODEL), true, replies);
        replies.attach(round);
        round.run(new Turn(
                Entry.main, true, false, true,
                Field.missing(), Field.missing(), Field.missing(),
                List.of(), ""));
        List<LedgerEntry> rows = round.ledger();
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind())
                && row.writer() == Writer.gate
                && row.goalId() == null
                && row.resultKind() == ResultKind.成功));
        assertFalse(rows.stream().anyMatch(row -> LedgerEntry.DONE.equals(row.kind())));
        assertFalse(rows.stream().anyMatch(row -> LedgerEntry.NOT_DONE.equals(row.kind())));
        assertTrue(round.projection().stream().noneMatch(ProjectionLine::fromPerson));
        String body = rows.stream()
                .filter(row -> LedgerEntry.RETURN.equals(row.kind()))
                .map(LedgerEntry::body)
                .findFirst()
                .orElse("");
        assertTrue(body.contains("OK"));
    }

    @Test
    void 开跑只有问模型成功也不做成() throws Exception {
        LiveReply replies = new LiveReply();
        Round round = new Round(Set.of(Gate.ASK_MODEL), true, replies);
        replies.attach(round);
        round.run(new Turn(
                Entry.main, false, false, false,
                Field.of("问一句今天星期几"),
                Field.of("只回答星期"),
                Field.of("回答里有星期"),
                List.of(Gate.ASK_MODEL),
                "已完成"));
        List<LedgerEntry> rows = round.ledger();
        String goalId = rows.stream()
                .filter(row -> LedgerEntry.PIECE.equals(row.kind()))
                .map(LedgerEntry::goalId)
                .findFirst()
                .orElseThrow();
        assertEquals("只回答星期", rows.stream()
                .filter(row -> LedgerEntry.PIECE.equals(row.kind()))
                .map(LedgerEntry::limit)
                .findFirst()
                .orElseThrow());
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind())
                && goalId.equals(row.goalId())
                && row.resultKind() == ResultKind.成功
                && row.body() != null
                && row.body().contains("OK")));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.NOT_DONE.equals(row.kind()) && row.writer() == Writer.judge));
        assertFalse(rows.stream().anyMatch(row -> LedgerEntry.DONE.equals(row.kind())));
        assertTrue(rows.stream().noneMatch(row -> "已完成".equals(row.body())));
    }

    /** 问模型走 CC Switch 当前供应商。其它能力名不会被调用。 */
    private static final class LiveReply implements ReplySource {
        private Round round;

        /** 跑一轮造好后再接上，这样才能读到已经装好的投影。 */
        void attach(Round round) {
            this.round = round;
        }

        @Override
        public CapabilityReply take(String capabilityName) {
            if (!Gate.ASK_MODEL.equals(capabilityName)) {
                return new CapabilityReply(ResultKind.失败, "UNEXPECTED", "");
            }
            try {
                return ask();
            } catch (Exception ex) {
                return new CapabilityReply(ResultKind.失败, "LLM_IO_ERROR", "");
            }
        }

        /** 把投影当系统提示，向当前供应商发一句很短的问话。 */
        private CapabilityReply ask() throws Exception {
            String system = round.projection().stream()
                    .map(line -> line.text() + (line.fromPerson() ? "" : "（不是人说的）"))
                    .collect(Collectors.joining("\n"));
            String base = System.getenv("TEPEU_CC_BASE").replaceAll("/+$", "");
            String model = System.getenv("TEPEU_CC_MODEL");
            String key = System.getenv("TEPEU_CC_KEY");
            String json = "{"
                    + "\"model\":" + quote(model) + ","
                    + "\"max_tokens\":64,"
                    + "\"thinking\":{\"type\":\"disabled\"},"
                    + "\"system\":" + quote(system) + ","
                    + "\"messages\":[{\"role\":\"user\",\"content\":" + quote("Reply with exactly: OK") + "}]"
                    + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v1/messages"))
                    .timeout(Duration.ofSeconds(50))
                    .header("x-api-key", key)
                    .header("Authorization", "Bearer " + key)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new CapabilityReply(ResultKind.失败, "LLM_HTTP_" + response.statusCode(), "");
            }
            String text = firstText(response.body());
            if (text.isBlank()) {
                return new CapabilityReply(ResultKind.失败, "LLM_EMPTY", "");
            }
            return new CapabilityReply(ResultKind.成功, null, text);
        }

        /** 取出第一个 text 块。不把整段响应留下来。 */
        private static String firstText(String json) {
            int type = json.indexOf("\"text\"");
            if (type < 0) {
                return "";
            }
            int colon = json.indexOf(':', type);
            int start = json.indexOf('"', colon + 1);
            if (start < 0) {
                return "";
            }
            StringBuilder out = new StringBuilder();
            for (int i = start + 1; i < json.length(); i++) {
                char ch = json.charAt(i);
                if (ch == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    out.append(next == 'n' ? '\n' : next);
                    i++;
                    continue;
                }
                if (ch == '"') {
                    break;
                }
                out.append(ch);
            }
            return out.toString();
        }

        /** 做成 JSON 字符串。 */
        private static String quote(String value) {
            return "\"" + value
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    + "\"";
        }
    }
}
