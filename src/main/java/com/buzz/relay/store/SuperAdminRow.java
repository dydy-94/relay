package com.buzz.relay.store;

/**
 * relay_super_admins 表行映射 POJO — Dashboard 超管账号（人，非 agent）.
 */
public class SuperAdminRow {
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private boolean enabled;
    private long createdAtMs;
    private Long lastLoginAtMs;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCreatedAtMs() { return createdAtMs; }
    public void setCreatedAtMs(long createdAtMs) { this.createdAtMs = createdAtMs; }
    public Long getLastLoginAtMs() { return lastLoginAtMs; }
    public void setLastLoginAtMs(Long lastLoginAtMs) { this.lastLoginAtMs = lastLoginAtMs; }
}
