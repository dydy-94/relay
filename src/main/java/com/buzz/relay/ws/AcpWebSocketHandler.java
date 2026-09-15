package com.buzz.relay.ws;

import com.buzz.relay.config.RelayProperties;
import com.buzz.relay.model.ChatEnvelope;
import com.buzz.relay.relay.RelayState;
import com.buzz.relay.store.ChannelRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACP WebSocket 处理器 — 自定义信封协议（非 Nostr）.
 *
 * 协议帧格式（JSON over WS）：
 *   Client → Server:
 *     subscribe / unsubscribe / subscribe_membership / publish / heartbeat / ping
 *
 *   Server → Client:
 *     envelope / membership / channel_info / eose / pong / error
 *
 * agent_id 从 query param 或 X-Agent-Id header 获取。
 */
@Component
public class AcpWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AcpWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE);

    private final RelayState state;
    private final RelayProperties props;

    // 每连接的订阅状态：session → {channelId → sinceMs}
    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    public AcpWebSocketHandler(RelayState state, RelayProperties props) {
        this.state = state;
        this.props = props;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String agentId = resolveAgentId(session);
        if (agentId == null || agentId.isBlank()) {
            sendJson(session, Map.of("type", "error", "code", "BAD_REQUEST", "message", "agent_id required"));
            try { session.close(); } catch (Exception ignored) {}
            return;
        }
        state.enrollSocket(agentId, session);
        sessionSubscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        log.info("[ws] agent={} connected", agentId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String agentId = resolveAgentId(session);
        if (agentId == null) return;

        // 任何 WS 活动都续期 Redis presence
        state.touchPresence(agentId);

        try {
            JsonNode root = MAPPER.readTree(message.getPayload());
            String cmd = root.path("type").asText("");
            switch (cmd) {
                case "subscribe" -> handleSubscribe(session, agentId, root);
                case "unsubscribe" -> handleUnsubscribe(session, agentId, root);
                case "subscribe_membership" -> handleSubscribeMembership(session, agentId, root);
                case "publish" -> handlePublish(session, agentId, root);
                case "heartbeat" -> handleHeartbeat(session, agentId, root);
                case "ping" -> sendJson(session, Map.of("type", "pong"));
                default -> log.warn("[ws] agent={} unknown cmd={}", agentId, cmd);
            }
        } catch (Exception e) {
            log.error("[ws] agent={} parse error: {}", agentId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String agentId = resolveAgentId(session);
        state.unenrollSocket(session);
        sessionSubscriptions.remove(session.getId());
        if (agentId != null) {
            log.info("[ws] agent={} disconnected", agentId);
        }
    }

    // ── 命令处理 ──

    private void handleSubscribe(WebSocketSession session, String agentId, JsonNode root) {
        String channelId = root.path("channel_id").asText("");
        if (channelId.isBlank()) {
            sendJson(session, Map.of("type", "error", "code", "BAD_REQUEST", "message", "channel_id required"));
            return;
        }

        long sinceMs = root.path("since_ms").asLong(0);
        log.info("[ws] agent={} subscribe channel={} since_ms={}", agentId, channelId, sinceMs);

        // 权限：private 频道仅成员可订阅
        if (!state.canRead(channelId, agentId)) {
            sendJson(session, Map.of("type", "error", "code", "FORBIDDEN",
                    "message", "not a member of private channel"));
            return;
        }

        // 确保 channel 存在；open 频道自动加入成员，private 频道成员需预先添加（不自动加入）
        ChannelRow ch = state.getChannel(channelId);
        if (ch == null || !"private".equals(ch.getVisibility())) {
            state.getOrCreateChannel(channelId);
            state.addMember(channelId, agentId, agentId, "member");
        }

        // 回放历史信封
        int limit = root.path("limit").asInt(200);
        for (Map<String, Object> env : state.replayEnvelopes(channelId, sinceMs, limit)) {
            env.put("type", "envelope");
            sendJson(session, env);
        }

        // 推 channel_info
        ch = state.getOrCreateChannel(channelId);
        sendJson(session, state.channelInfoMap(ch));

        // 推 eose 标记
        sendJson(session, Map.of("type", "eose", "channel_id", channelId));

        // 记录订阅
        sessionSubscriptions.getOrDefault(session.getId(), ConcurrentHashMap.newKeySet()).add(channelId);
    }

    private void handleUnsubscribe(WebSocketSession session, String agentId, JsonNode root) {
        String channelId = root.path("channel_id").asText("");
        log.info("[ws] agent={} unsubscribe channel={}", agentId, channelId);
        Set<String> subs = sessionSubscriptions.get(session.getId());
        if (subs != null) subs.remove(channelId);
    }

    private void handleSubscribeMembership(WebSocketSession session, String agentId, JsonNode root) {
        long sinceMs = root.path("since_ms").asLong(0);
        log.info("[ws] agent={} subscribe_membership since_ms={}", agentId, sinceMs);

        // 推送 agent 已加入的所有 channel 的 channel_info
        for (ChannelRow ch : state.getChannelsByAgent(agentId)) {
            sendJson(session, state.channelInfoMap(ch));
        }

        // 推 eose 标记
        sendJson(session, Map.of("type", "eose", "channel_id", "__membership__"));
    }

    private void handlePublish(WebSocketSession session, String agentId, JsonNode root) {
        JsonNode envNode = root.path("envelope");
        if (envNode.isMissingNode() || envNode.isNull()) {
            sendJson(session, Map.of("type", "error", "code", "BAD_REQUEST", "message", "envelope required"));
            return;
        }

        // 从外层 channel_id 或信封内取 channel_id
        String channelId = envNode.path("channel_id").asText(root.path("channel_id").asText(""));
        if (channelId.isBlank()) {
            sendJson(session, Map.of("type", "error", "code", "BAD_REQUEST", "message", "channel_id required"));
            return;
        }

        // 解析信封
        ChatEnvelope envelope;
        try {
            envelope = MAPPER.treeToValue(envNode, ChatEnvelope.class);
        } catch (Exception e) {
            sendJson(session, Map.of("type", "error", "code", "BAD_REQUEST", "message", "invalid envelope: " + e.getMessage()));
            return;
        }

        // 补全 sender_id
        if (envelope.getSenderId() == null || envelope.getSenderId().isBlank()) {
            envelope.setSenderId(agentId);
        }

        // 权限：仅成员可发布
        if (!state.canWrite(channelId, agentId)) {
            sendJson(session, Map.of("type", "error", "code", "FORBIDDEN", "message", "not a member"));
            return;
        }

        // 存储 + 广播
        ChatEnvelope stored = state.storeAndBroadcast(channelId, envelope);
        log.info("[ws] agent={} publish channel={} env={}", agentId, channelId, stored.getEnvelopeId().substring(0, 8));

        // 回复 pong（带 echo）
        sendJson(session, Map.of("type", "pong", "echo", stored.getEnvelopeId()));
    }

    private void handleHeartbeat(WebSocketSession session, String agentId, JsonNode root) {
        String status = root.path("status").asText("idle");
        int activeTasks = root.path("active_tasks").asInt(0);
        state.updateHeartbeat(agentId, status, activeTasks);
        log.info("[ws] agent={} heartbeat status={} active={}", agentId, status, activeTasks);
        sendJson(session, Map.of("type", "pong", "echo", "heartbeat"));
    }

    // ── 工具 ──

    private String resolveAgentId(WebSocketSession session) {
        // 从 HandshakeInterceptor 设置的 attributes 取
        return (String) session.getAttributes().get("agentId");
    }

    private void sendJson(WebSocketSession session, Map<String, Object> msg) {
        try {
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
        } catch (Exception e) {
            log.warn("send to {} failed: {}", session.getId(), e.getMessage());
            state.unenrollSocket(session);
        }
    }
}
