package com.buzz.relay.relay;

import com.buzz.relay.config.RedisConfig;
import com.buzz.relay.model.ChatEnvelope;
import com.buzz.relay.store.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RelayState — relay 全局状态管理器.
 *
 * 职责：
 *   1. WebSocket 连接管理（agent_id → sessions 双向索引）
 *   2. 信封存储 + 广播（存入 MySQL + 推给 channel 所有成员的 WS 连接）
 *   3. Channel 管理（创建 / 加成员 / 移成员 + membership 事件推送）
 *   4. Agent 心跳状态追踪
 *   5. 跨实例广播（Redis Pub/Sub）+ Agent 在线状态（Redis Presence）
 *
 * 与 Python mock_relay.py 的 MockRelayState 对齐。
 */
@Component
public class RelayState {

    private static final Logger log = LoggerFactory.getLogger(RelayState.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJ_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, String>> STR_MAP = new TypeReference<>() {};

    // agent_id → WebSocket sessions（一个 agent 可多 WS 连接）
    private final Map<String, Set<WebSocketSession>> agentSockets = new ConcurrentHashMap<>();
    // session → agent_id（反向索引，断线时清理）
    private final Map<String, String> socketAgents = new ConcurrentHashMap<>();

    private final EnvelopeMapper envelopeMapper;
    private final ChannelMapper channelMapper;

    // Redis — 跨实例广播 + presence
    private static final String PRESENCE_KEY_PREFIX = "relay:presence:";
    private static final Duration PRESENCE_TTL = Duration.ofSeconds(60);
    private final StringRedisTemplate redis;
    // 每 JVM 唯一标识，用于跨实例广播回环保护 + presence 归属判断
    private final String instanceId = UUID.randomUUID().toString();

    public RelayState(EnvelopeMapper envelopeMapper, ChannelMapper channelMapper, StringRedisTemplate redis) {
        this.envelopeMapper = envelopeMapper;
        this.channelMapper = channelMapper;
        this.redis = redis;
    }

    // ── 连接管理 ──

    public void enrollSocket(String agentId, WebSocketSession session) {
        agentSockets.computeIfAbsent(agentId, k -> ConcurrentHashMap.newKeySet()).add(session);
        socketAgents.put(session.getId(), agentId);
        touchPresence(agentId);
    }

    public void unenrollSocket(WebSocketSession session) {
        String agentId = socketAgents.remove(session.getId());
        if (agentId != null) {
            Set<WebSocketSession> sessions = agentSockets.get(agentId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    agentSockets.remove(agentId);
                    clearPresence(agentId);
                }
            }
        }
    }

    // ── Presence（Agent 在线状态，跨实例共享）──

    /**
     * 刷新 agent 在线状态（覆盖写并续期 TTL）. 连接建立或收到任何 WS 消息时调用.
     */
    public void touchPresence(String agentId) {
        try {
            redis.opsForValue().set(PRESENCE_KEY_PREFIX + agentId, instanceId, PRESENCE_TTL);
        } catch (Exception e) {
            log.warn("redis presence set failed for {}: {}", agentId, e.getMessage());
        }
    }

    /**
     * 清理 agent 在线状态 — 仅当 Redis 中归属为本实例时才删除，避免误删其他实例上的连接.
     */
    private void clearPresence(String agentId) {
        try {
            String owner = redis.opsForValue().get(PRESENCE_KEY_PREFIX + agentId);
            if (instanceId.equals(owner)) {
                redis.delete(PRESENCE_KEY_PREFIX + agentId);
            }
        } catch (Exception e) {
            log.warn("redis presence clear failed for {}: {}", agentId, e.getMessage());
        }
    }

    /**
     * 查询所有在线 agent（跨实例聚合）.
     */
    public List<Map<String, Object>> getOnlineAgents() {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            Set<String> keys = redis.keys(PRESENCE_KEY_PREFIX + "*");
            if (keys == null) return result;
            for (String key : keys) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("agent_id", key.substring(PRESENCE_KEY_PREFIX.length()));
                m.put("instance_id", redis.opsForValue().get(key));
                result.add(m);
            }
        } catch (Exception e) {
            log.warn("redis presence scan failed: {}", e.getMessage());
        }
        return result;
    }

    public String getInstanceId() {
        return instanceId;
    }

    // ── 信封存储 + 广播 ──

    /**
     * 存储信封到 MySQL 并广播给 channel 所有在线成员.
     *
     * @return 存储后的 ChatEnvelope（补全了 envelope_id + created_at_ms + channel_id）
     */
    public ChatEnvelope storeAndBroadcast(String channelId, ChatEnvelope envelope) {
        // 补全字段
        if (envelope.getEnvelopeId() == null || envelope.getEnvelopeId().isBlank()) {
            envelope.setEnvelopeId(UUID.randomUUID().toString());
        }
        if (envelope.getCreatedAtMs() == 0) {
            envelope.setCreatedAtMs(System.currentTimeMillis());
        }
        envelope.setChannelId(channelId);

        // 存入 MySQL
        EnvelopeRow row = toRow(envelope);
        try {
            envelopeMapper.insert(row);
        } catch (Exception e) {
            // duplicate envelope_id → ignore
            log.debug("duplicate envelope_id={}", envelope.getEnvelopeId());
        }

        // 广播（本实例 + 其他实例）
        Map<String, Object> broadcast = envelopeToMap(envelope);
        broadcast.put("type", "envelope");
        publishToChannel(channelId, broadcast);

        return envelope;
    }

    // ── 历史回放 ──

    /**
     * 回放 channel 历史信封（created_at_ms > sinceMs）.
     */
    public List<Map<String, Object>> replayEnvelopes(String channelId, long sinceMs, int limit) {
        List<EnvelopeRow> rows = envelopeMapper.findReplay(channelId, sinceMs, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (EnvelopeRow row : rows) {
            result.add(rowToEnvelopeMap(row));
        }
        return result;
    }

    /**
     * 查询历史信封（REST /api/history 用）.
     */
    public List<Map<String, Object>> getHistory(String channelId, String rootEnvelopeId, int limit) {
        List<EnvelopeRow> rows = envelopeMapper.findHistory(channelId, rootEnvelopeId, limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (EnvelopeRow row : rows) {
            result.add(rowToEnvelopeMap(row));
        }
        return result;
    }

    // ── Channel 管理 ──

    /**
     * 获取或创建 channel（幂等）.
     */
    public ChannelRow getOrCreateChannel(String channelId) {
        ChannelRow row = channelMapper.findChannelById(channelId);
        if (row != null) return row;

        row = new ChannelRow();
        row.setChannelId(channelId);
        row.setChannelType("stream");
        row.setVisibility("open");
        row.setCreatedAtMs(System.currentTimeMillis());
        try {
            channelMapper.insertChannel(row);
        } catch (Exception e) {
            // 可能被其他线程创建了，再查一次
            row = channelMapper.findChannelById(channelId);
        }
        return row;
    }

    /**
     * 更新 channel 元信息.
     */
    public ChannelRow updateChannelInfo(String channelId, String name, String channelType,
                                         String visibility, String description) {
        ChannelRow row = getOrCreateChannel(channelId);
        if (name != null) row.setName(name);
        if (channelType != null) row.setChannelType(channelType);
        if (visibility != null) row.setVisibility(visibility);
        if (description != null) row.setDescription(description);
        channelMapper.updateChannel(row);
        return row;
    }

    /**
     * 添加成员到 channel（幂等），并广播 membership + channel_info 事件.
     * 频道首个成员自动成为 admin.
     */
    public void addMember(String channelId, String agentId, String name, String role) {
        getOrCreateChannel(channelId);

        boolean firstMember = channelMapper.findMembers(channelId).isEmpty();
        if (firstMember) {
            role = "admin"; // 首个成员固定 admin
        } else if (role == null || role.isBlank()) {
            role = "member";
        }

        ChannelMemberRow member = new ChannelMemberRow();
        member.setChannelId(channelId);
        member.setAgentId(agentId);
        member.setRole(role != null ? role : "member");
        member.setName(name != null ? name : agentId);
        member.setAddedAtMs(System.currentTimeMillis());
        channelMapper.insertMember(member);

        // 广播 membership(joined)
        Map<String, Object> memEvt = new LinkedHashMap<>();
        memEvt.put("type", "membership");
        memEvt.put("channel_id", channelId);
        memEvt.put("action", "joined");
        memEvt.put("agent_id", agentId);
        memEvt.put("created_at_ms", System.currentTimeMillis());
        publishToChannel(channelId, memEvt);
        publishToAgent(agentId, memEvt);

        // 推 channel_info 给新成员
        ChannelRow ch = channelMapper.findChannelById(channelId);
        if (ch != null) {
            publishToAgent(agentId, channelInfoMap(ch));
        }
    }

    /**
     * 从 channel 移除成员，并广播 membership(left) 事件.
     */
    public void removeMember(String channelId, String agentId) {
        channelMapper.deleteMember(channelId, agentId);

        Map<String, Object> memEvt = new LinkedHashMap<>();
        memEvt.put("type", "membership");
        memEvt.put("channel_id", channelId);
        memEvt.put("action", "left");
        memEvt.put("agent_id", agentId);
        memEvt.put("created_at_ms", System.currentTimeMillis());
        publishToChannel(channelId, memEvt);
        publishToAgent(agentId, memEvt);
    }

    /**
     * 查询 channel 所有成员.
     */
    public List<ChannelMemberRow> getMembers(String channelId) {
        return channelMapper.findMembers(channelId);
    }

    // ── 权限检查 ──

    /**
     * 获取 channel（不存在返回 null）.
     */
    public ChannelRow getChannel(String channelId) {
        return channelMapper.findChannelById(channelId);
    }

    /**
     * 是否 channel 成员.
     */
    public boolean isMember(String channelId, String agentId) {
        return channelMapper.findMember(channelId, agentId) != null;
    }

    /**
     * 是否 channel 管理员.
     */
    public boolean isAdmin(String channelId, String agentId) {
        ChannelMemberRow m = channelMapper.findMember(channelId, agentId);
        return m != null && "admin".equals(m.getRole());
    }

    /**
     * 读权限：open 频道任何人可读；private 频道仅成员可读.
     * 不存在的频道按 open 处理（订阅即创建）.
     */
    public boolean canRead(String channelId, String agentId) {
        ChannelRow ch = channelMapper.findChannelById(channelId);
        if (ch == null) return true;
        if ("private".equals(ch.getVisibility())) {
            return isMember(channelId, agentId);
        }
        return true;
    }

    /**
     * 写权限：仅成员可发布（订阅 open 频道即自动加入为成员）.
     */
    public boolean canWrite(String channelId, String agentId) {
        return isMember(channelId, agentId);
    }

    /**
     * 查询 agent 加入的所有 channel.
     */
    public List<ChannelRow> getChannelsByAgent(String agentId) {
        return channelMapper.findChannelsByAgent(agentId);
    }

    /**
     * 构建 channel_info 消息帧.
     */
    public Map<String, Object> channelInfoMap(ChannelRow ch) {
        List<ChannelMemberRow> members = channelMapper.findMembers(ch.getChannelId());
        List<Map<String, Object>> memberList = new ArrayList<>();
        for (ChannelMemberRow m : members) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("pubkey", m.getAgentId());
            mm.put("name", m.getName());
            mm.put("role", m.getRole());
            memberList.add(mm);
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("type", "channel_info");
        info.put("channel_id", ch.getChannelId());
        info.put("name", ch.getName());
        info.put("channel_type", ch.getChannelType());
        info.put("visibility", ch.getVisibility());
        info.put("description", ch.getDescription() != null ? ch.getDescription() : "");
        info.put("members", memberList);
        info.put("archived", ch.isArchived());
        info.put("is_dm", "dm".equals(ch.getChannelType()));
        return info;
    }

    // ── Agent 心跳 ──

    public void registerAgent(String agentId, String sandboxId, String capabilitiesJson, String rulesJson) {
        channelMapper.insertAgent(agentId, sandboxId, capabilitiesJson, rulesJson, System.currentTimeMillis());
    }

    /**
     * 更新 agent 心跳（如果 agent 不存在则先自动注册）.
     */
    public void updateHeartbeat(String agentId, String status, int activeTasks) {
        // 先确保 agent 存在（INSERT IGNORE 不会覆盖已有记录）
        channelMapper.insertAgent(agentId, null, null, null, System.currentTimeMillis());
        channelMapper.updateHeartbeat(agentId, status, activeTasks, System.currentTimeMillis());
    }

    public List<Map<String, Object>> getAllAgents() {
        return channelMapper.findAllAgents();
    }

    // ── 广播工具 ──

    /**
     * 广播到 channel 所有成员的在线连接：本实例内存推送 + 发布到 Redis 供其他实例推送.
     */
    public void publishToChannel(String channelId, Map<String, Object> msg) {
        broadcastToChannel(channelId, msg);
        publishRemote("channel", channelId, null, msg);
    }

    /**
     * 广播给某个 agent 的所有在线连接：本实例内存推送 + 发布到 Redis 供其他实例推送.
     */
    public void publishToAgent(String agentId, Map<String, Object> msg) {
        broadcastToAgent(agentId, msg);
        publishRemote("agent", null, agentId, msg);
    }

    /**
     * 发布跨实例事件到 Redis（携带源 instance_id，供回环保护）.
     */
    private void publishRemote(String target, String channelId, String agentId, Map<String, Object> payload) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("instance_id", instanceId);
            event.put("target", target);
            if (channelId != null) event.put("channel_id", channelId);
            if (agentId != null) event.put("agent_id", agentId);
            event.put("payload", payload);
            redis.convertAndSend(RedisConfig.TOPIC, toJson(event));
        } catch (Exception e) {
            log.warn("redis publish failed: {}", e.getMessage());
        }
    }

    /**
     * Redis 订阅回调 — 处理其他 relay 实例发布的事件（跳过自己发布的消息）.
     */
    public void onRemoteEvent(String json) {
        try {
            Map<String, Object> event = fromJson(json, OBJ_MAP, Collections.emptyMap());
            if (event.isEmpty()) return;
            String srcInstance = (String) event.get("instance_id");
            if (instanceId.equals(srcInstance)) return; // 回环保护

            String target = (String) event.get("target");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.get("payload");
            if (payload == null) return;

            if ("channel".equals(target)) {
                broadcastToChannel((String) event.get("channel_id"), payload);
            } else if ("agent".equals(target)) {
                broadcastToAgent((String) event.get("agent_id"), payload);
            }
        } catch (Exception e) {
            log.warn("onRemoteEvent parse failed: {}", e.getMessage());
        }
    }

    /**
     * 给 channel 所有成员的在线 WS 连接广播消息.
     */
    public void broadcastToChannel(String channelId, Map<String, Object> msg) {
        List<ChannelMemberRow> members = channelMapper.findMembers(channelId);
        Set<WebSocketSession> targets = new HashSet<>();
        for (ChannelMemberRow m : members) {
            Set<WebSocketSession> sessions = agentSockets.get(m.getAgentId());
            if (sessions != null) targets.addAll(sessions);
        }
        sendToSessions(targets, msg);
    }

    /**
     * 给某个 agent 的所有 WS 连接发消息.
     */
    public void broadcastToAgent(String agentId, Map<String, Object> msg) {
        Set<WebSocketSession> sessions = agentSockets.get(agentId);
        if (sessions != null) {
            sendToSessions(new HashSet<>(sessions), msg);
        }
    }

    /**
     * 给单个 session 发消息.
     */
    public void sendToSession(WebSocketSession session, Map<String, Object> msg) {
        try {
            session.sendMessage(new TextMessage(MAPPER.writeValueAsString(msg)));
        } catch (IOException e) {
            log.warn("send failed, closing session: {}", e.getMessage());
            unenrollSocket(session);
        }
    }

    private void sendToSessions(Set<WebSocketSession> sessions, Map<String, Object> msg) {
        String json;
        try {
            json = MAPPER.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            log.error("failed to serialize message", e);
            return;
        }
        TextMessage textMsg = new TextMessage(json);
        List<WebSocketSession> dead = new ArrayList<>();
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) { dead.add(s); continue; }
            try {
                s.sendMessage(textMsg);
            } catch (IOException e) {
                log.warn("send to {} failed: {}", s.getId(), e.getMessage());
                dead.add(s);
            }
        }
        for (WebSocketSession s : dead) unenrollSocket(s);
    }

    // ── 序列化工具 ──

    private EnvelopeRow toRow(ChatEnvelope env) {
        EnvelopeRow row = new EnvelopeRow();
        row.setEnvelopeId(env.getEnvelopeId());
        row.setChannelId(env.getChannelId());
        row.setProjectId(env.getProjectId());
        row.setTopicId(env.getTopicId());
        row.setParentEnvelopeId(env.getParentEnvelopeId());
        row.setRootEnvelopeId(env.getRootEnvelopeId());
        row.setSenderId(env.getSenderId());
        row.setSenderRole(env.getSenderRole());
        row.setKind(env.getKind());
        row.setMentions(toJson(env.getMentions()));
        row.setPayload(buildPayload(env));
        row.setMetadata(toJson(env.getMetadata()));
        row.setTraceId(env.getTraceId());
        row.setCreatedAtMs(env.getCreatedAtMs());
        row.setReceivedAtMs(System.currentTimeMillis());
        return row;
    }

    private Map<String, Object> envelopeToMap(ChatEnvelope env) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("envelope_id", env.getEnvelopeId());
        m.put("channel_id", env.getChannelId());
        if (env.getProjectId() != null && !env.getProjectId().isBlank()) m.put("project_id", env.getProjectId());
        if (env.getTopicId() != null && !env.getTopicId().isBlank()) m.put("topic_id", env.getTopicId());
        if (env.getParentEnvelopeId() != null && !env.getParentEnvelopeId().isBlank()) m.put("parent_envelope_id", env.getParentEnvelopeId());
        if (env.getRootEnvelopeId() != null && !env.getRootEnvelopeId().isBlank()) m.put("root_envelope_id", env.getRootEnvelopeId());
        m.put("sender_id", env.getSenderId());
        m.put("sender_role", env.getSenderRole());
        m.put("kind", env.getKind());
        m.put("mentions", env.getMentions());
        m.put("created_at_ms", env.getCreatedAtMs());
        if (env.getChat() != null) m.put("chat", env.getChat());
        if (env.getTaskAssign() != null) m.put("task_assign", env.getTaskAssign());
        if (env.getTaskResult() != null) m.put("task_result", env.getTaskResult());
        if (env.getCommand() != null) m.put("command", env.getCommand());
        if (env.getTraceId() != null && !env.getTraceId().isBlank()) m.put("trace_id", env.getTraceId());
        if (env.getMetadata() != null && !env.getMetadata().isEmpty()) m.put("metadata", env.getMetadata());
        return m;
    }

    private Map<String, Object> rowToEnvelopeMap(EnvelopeRow row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("envelope_id", row.getEnvelopeId());
        m.put("channel_id", row.getChannelId());
        if (row.getProjectId() != null) m.put("project_id", row.getProjectId());
        if (row.getTopicId() != null) m.put("topic_id", row.getTopicId());
        if (row.getParentEnvelopeId() != null) m.put("parent_envelope_id", row.getParentEnvelopeId());
        if (row.getRootEnvelopeId() != null) m.put("root_envelope_id", row.getRootEnvelopeId());
        m.put("sender_id", row.getSenderId());
        m.put("sender_role", row.getSenderRole());
        m.put("kind", row.getKind());
        m.put("mentions", fromJson(row.getMentions(), STR_LIST, Collections.emptyList()));
        m.put("created_at_ms", row.getCreatedAtMs());
        // payload 字段根据 kind 还原
        String payloadJson = row.getPayload();
        if (payloadJson != null && !payloadJson.isBlank()) {
            Map<String, Object> payload = fromJson(payloadJson, OBJ_MAP, Collections.emptyMap());
            String kind = row.getKind();
            if ("chat".equals(kind)) m.put("chat", payload);
            else if ("task_assign".equals(kind)) m.put("task_assign", payload);
            else if ("task_result".equals(kind)) m.put("task_result", payload);
            else if ("command".equals(kind)) m.put("command", payload);
        }
        if (row.getTraceId() != null) m.put("trace_id", row.getTraceId());
        if (row.getMetadata() != null) {
            Map<String, String> meta = fromJson(row.getMetadata(), STR_MAP, Collections.emptyMap());
            if (!meta.isEmpty()) m.put("metadata", meta);
        }
        return m;
    }

    /** 根据 kind 构建对应 payload JSON. */
    private String buildPayload(ChatEnvelope env) {
        Map<String, Object> payload = null;
        switch (env.getKind()) {
            case "chat" -> payload = env.getChat();
            case "task_assign" -> payload = env.getTaskAssign();
            case "task_result" -> payload = env.getTaskResult();
            case "command" -> payload = env.getCommand();
        }
        return payload != null ? toJson(payload) : null;
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            return fallback;
        }
    }
}
