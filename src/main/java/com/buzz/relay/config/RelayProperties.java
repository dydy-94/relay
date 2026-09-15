package com.buzz.relay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ACP relay 配置属性.
 */
@Configuration
@ConfigurationProperties(prefix = "relay")
public class RelayProperties {
    private String wsPath = "/ws";
    private int replayLimit = 200;
    private int historyLimit = 20;
    private int heartbeatIntervalSeconds = 5;

    public String getWsPath() { return wsPath; }
    public void setWsPath(String v) { this.wsPath = v; }
    public int getReplayLimit() { return replayLimit; }
    public void setReplayLimit(int v) { this.replayLimit = v; }
    public int getHistoryLimit() { return historyLimit; }
    public void setHistoryLimit(int v) { this.historyLimit = v; }
    public int getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public void setHeartbeatIntervalSeconds(int v) { this.heartbeatIntervalSeconds = v; }
}
