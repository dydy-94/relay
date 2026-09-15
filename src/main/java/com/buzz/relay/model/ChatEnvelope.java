package com.buzz.relay.model;

import java.util.*;

/**
 * ChatEnvelope — ACP 自定义信封格式（非 NIP-01 Nostr Event）.
 *
 * 字段分三组：
 *   路由元数据 — channel_id / root_envelope_id / parent_envelope_id / mentions
 *   发送者     — sender_id / sender_role / created_at_ms
 *   Payload    — chat / task_assign / task_result / command 四选一
 *
 * 无签名验证（可信 relay 内部），序列化往返通过 Jackson。
 */
public class ChatEnvelope {

    private String envelopeId = "";
    private String projectId = "";
    private String topicId = "";
    private String channelId = "";
    private String parentEnvelopeId = "";
    private String rootEnvelopeId = "";
    private List<String> mentions = new ArrayList<>();
    private long createdAtMs;
    private String senderRole = "expert";
    private String senderId = "";
    private String kind = "chat";
    private Map<String, Object> chat;
    private Map<String, Object> taskAssign;
    private Map<String, Object> taskResult;
    private Map<String, Object> command;
    private String traceId = "";
    private Map<String, String> metadata = new HashMap<>();

    // ── Jackson 需要无参构造 ──

    public ChatEnvelope() {}

    // ── 便捷工厂方法 ──

    public static ChatEnvelope newEnvelope(String kind, String senderId, String senderRole, String channelId) {
        ChatEnvelope env = new ChatEnvelope();
        env.envelopeId = UUID.randomUUID().toString();
        env.createdAtMs = System.currentTimeMillis();
        env.kind = kind;
        env.senderId = senderId;
        env.senderRole = senderRole;
        env.channelId = channelId;
        return env;
    }

    // ── Getters / Setters ──

    public String getEnvelopeId() { return envelopeId; }
    public void setEnvelopeId(String envelopeId) { this.envelopeId = envelopeId; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getParentEnvelopeId() { return parentEnvelopeId; }
    public void setParentEnvelopeId(String parentEnvelopeId) { this.parentEnvelopeId = parentEnvelopeId; }

    public String getRootEnvelopeId() { return rootEnvelopeId; }
    public void setRootEnvelopeId(String rootEnvelopeId) { this.rootEnvelopeId = rootEnvelopeId; }

    public List<String> getMentions() { return mentions; }
    public void setMentions(List<String> mentions) { this.mentions = mentions != null ? mentions : new ArrayList<>(); }

    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }

    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public Map<String, Object> getChat() { return chat; }
    public void setChat(Map<String, Object> chat) { this.chat = chat; }

    public Map<String, Object> getTaskAssign() { return taskAssign; }
    public void setTaskAssign(Map<String, Object> taskAssign) { this.taskAssign = taskAssign; }

    public Map<String, Object> getTaskResult() { return taskResult; }
    public void setTaskResult(Map<String, Object> taskResult) { this.taskResult = taskResult; }

    public Map<String, Object> getCommand() { return command; }
    public void setCommand(Map<String, Object> command) { this.command = command; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata != null ? metadata : new HashMap<>(); }

    @Override
    public String toString() {
        return "ChatEnvelope{id=" + envelopeId.substring(0, Math.min(8, envelopeId.length()))
                + ", channel=" + channelId + ", kind=" + kind + ", sender=" + senderId + "}";
    }
}
