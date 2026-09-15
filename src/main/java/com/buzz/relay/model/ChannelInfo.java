package com.buzz.relay.model;

import java.util.*;

/**
 * ChannelInfo — channel 元信息快照.
 *
 * relay 在 subscribe / membership / channel_info 帧中下发，
 * ACP 缓存后用于 Prompt 中 context 段落显示。
 */
public class ChannelInfo {

    private String channelId = "";
    private String name = "";
    private String channelType = "stream";
    private String visibility = "open";
    private String description = "";
    private List<Map<String, Object>> members = new ArrayList<>();
    private boolean archived;
    private boolean isDm;

    public ChannelInfo() {}

    public ChannelInfo(String channelId, String name) {
        this.channelId = channelId;
        this.name = name;
    }

    // ── Getters / Setters ──

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<Map<String, Object>> getMembers() { return members; }
    public void setMembers(List<Map<String, Object>> members) { this.members = members != null ? members : new ArrayList<>(); }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public boolean isDm() { return isDm; }
    public void setIsDm(boolean isDm) { this.isDm = isDm; }
}
