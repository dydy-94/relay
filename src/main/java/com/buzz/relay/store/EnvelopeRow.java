package com.buzz.relay.store;

/**
 * relay_envelopes 表行映射 POJO（snake_case → camelCase 由 MyBatis 自动转换）.
 */
public class EnvelopeRow {
    private Long id;
    private String envelopeId;
    private String channelId;
    private String projectId;
    private String topicId;
    private String parentEnvelopeId;
    private String rootEnvelopeId;
    private String senderId;
    private String senderRole;
    private String kind;
    private String mentions;      // JSON text
    private String payload;       // JSON text
    private String metadata;      // JSON text
    private String traceId;
    private long createdAtMs;
    private long receivedAtMs;

    // ── Getters / Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEnvelopeId() { return envelopeId; }
    public void setEnvelopeId(String envelopeId) { this.envelopeId = envelopeId; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }
    public String getParentEnvelopeId() { return parentEnvelopeId; }
    public void setParentEnvelopeId(String parentEnvelopeId) { this.parentEnvelopeId = parentEnvelopeId; }
    public String getRootEnvelopeId() { return rootEnvelopeId; }
    public void setRootEnvelopeId(String rootEnvelopeId) { this.rootEnvelopeId = rootEnvelopeId; }
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getMentions() { return mentions; }
    public void setMentions(String mentions) { this.mentions = mentions; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
    public long getReceivedAtMs() { return receivedAtMs; }
    public void setReceivedAtMs(long receivedAtMs) { this.receivedAtMs = receivedAtMs; }
}
