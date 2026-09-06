package com.tepeu.os.llm;

import java.util.Map;

/** HTTP 往返缝 — 生产走 JDK，测试可替换。 */
@FunctionalInterface
public interface HttpRoundTrip {

    record Exchange(int status, String body) {
        public Exchange {
            body = body == null ? "" : body;
        }
    }

    Exchange post(String url, Map<String, String> headers, String json);
}
