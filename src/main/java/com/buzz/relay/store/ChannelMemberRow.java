package com.buzz.relay.store;

/**
 * relay_channel_members 表行映射 POJO.
 */
public class ChannelMemberRow {
    private Long id;
    private String channelId;
    private String agentId;
    private String role;
    private String name;
    private long addedAtMs;

    // ── Getters / Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getAddedAtMs() { return addedAtMs; }
    public void setAddedAtMs(long addedAtMs) { this.addedAtMs = addedAtMs; }
}
