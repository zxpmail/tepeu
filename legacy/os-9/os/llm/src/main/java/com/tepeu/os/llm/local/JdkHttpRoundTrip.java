package com.tepeu.os.llm.local;

import com.tepeu.os.llm.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** JDK HttpClient 实现。不引入官方 SDK / Spring AI。 */
public final class JdkHttpRoundTrip implements HttpRoundTrip {

    private final HttpClient client;
    private final Duration requestTimeout;

    public JdkHttpRoundTrip() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), Duration.ofSeconds(60));
    }

    public JdkHttpRoundTrip(HttpClient client, Duration requestTimeout) {
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Exchange post(String url, Map<String, String> headers, String json) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Exchange(response.statusCode(), response.body() == null ? "" : response.body());
        } catch (IOException e) {
            throw new LlmTransportException("TRANSPORT", "http i/o: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmTransportException("TRANSPORT", "http interrupted", e);
        }
    }
}
