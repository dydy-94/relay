package com.buzz.relay.ws;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * 从 WS 握手 URL 的 query param 中提取 agent_id，存入 session attributes.
 * 这解决 Spring WebSocketSession.getUri() 在某些实现中不暴露 query 的问题.
 */
public class AgentIdHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                     WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String agentId = extractAgentId(request.getURI());
        if (agentId != null) {
            attributes.put("agentId", agentId);
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractAgentId(URI uri) {
        if (uri == null || uri.getQuery() == null) return null;
        for (String pair : uri.getQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "agent_id".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
