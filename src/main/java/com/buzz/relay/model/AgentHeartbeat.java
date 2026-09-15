package com.buzz.relay.model;

/**
 * AgentHeartbeat — ACP 定期向 Relay 发送的心跳载荷.
 *
 * 携带 agent 当前状态（idle/busy）和正在处理的 task 数。
 */
public class AgentHeartbeat {

    private String agentId = "";
    private String status = "idle";
    private int activeTasks;
    private long sentAtMs;

    public AgentHeartbeat() {}

    public AgentHeartbeat(String agentId, String status, int activeTasks) {
        this.agentId = agentId;
        this.status = status;
        this.activeTasks = activeTasks;
        this.sentAtMs = System.currentTimeMillis();
    }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getActiveTasks() { return activeTasks; }
    public void setActiveTasks(int activeTasks) { this.activeTasks = activeTasks; }

    public long getSentAtMs() { return sentAtMs; }
    public void setSentAtMs(long sentAtMs) { this.sentAtMs = sentAtMs; }
}
