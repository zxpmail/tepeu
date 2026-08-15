package com.tepeu.config;

import com.tepeu.controller.TerminalWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TerminalWebSocketHandler terminalHandler;

    public WebSocketConfig(TerminalWebSocketHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Terminal WebSocket — origin locked to localhost (app URL + Vite dev URL).
        // Defence in depth: the handler also checks the remote address per-session.
        registry.addHandler(terminalHandler, "/api/terminal/ws")
                .setAllowedOrigins(
                        "http://localhost:30141",
                        "http://localhost:5173",
                        "http://127.0.0.1:30141",
                        "http://127.0.0.1:5173");
    }
}
