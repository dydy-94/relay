package com.buzz.relay.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers the ACP WebSocket handler at /ws.
 *
 * ACP relay 使用自定义信封协议（非 Nostr），WebSocket 入口为 /ws。
 * agent_id 通过 query param 或 X-Agent-Id header 传入。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AcpWebSocketHandler handler;

    public WebSocketConfig(AcpWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws")
                .addInterceptors(new AgentIdHandshakeInterceptor())
                .setAllowedOrigins("*");
    }
}
