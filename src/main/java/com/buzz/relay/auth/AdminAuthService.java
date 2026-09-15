package com.buzz.relay.auth;

import com.buzz.relay.store.SuperAdminMapper;
import com.buzz.relay.store.SuperAdminRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 超管账号认证服务 — Dashboard 登录 + 管理接口校验.
 *
 * 超管是"人"（非 agent），通过账号密码登录 dashboard，账号存于
 * relay_super_admins 表（运维可直接配置 / 通过 /api/admin/accounts 管理）.
 *
 * 校验模型（按用户要求保持简单）：
 *   - 登录：POST /api/auth/login 校验 username + BCrypt(password)
 *   - 管理接口：前端在 header 带登录账号 id，服务端查表校验账号存在且 enabled
 *   - 不做 token / session / filter
 */
@Component
public class AdminAuthService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    /** 表为空时自动创建的 bootstrap 账号（首次启动即可用） */
    private static final String BOOTSTRAP_USERNAME = "admin";
    private static final String BOOTSTRAP_PASSWORD = "admin123";
    private static final String BOOTSTRAP_DISPLAY_NAME = "Administrator";

    private final SuperAdminMapper superAdminMapper;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminAuthService(SuperAdminMapper superAdminMapper) {
        this.superAdminMapper = superAdminMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (superAdminMapper.countAll() == 0) {
            SuperAdminRow row = new SuperAdminRow();
            row.setUsername(BOOTSTRAP_USERNAME);
            row.setPasswordHash(encoder.encode(BOOTSTRAP_PASSWORD));
            row.setDisplayName(BOOTSTRAP_DISPLAY_NAME);
            row.setEnabled(true);
            row.setCreatedAtMs(System.currentTimeMillis());
            try {
                superAdminMapper.insert(row);
                log.info("bootstrap super admin account created: {} / {}", BOOTSTRAP_USERNAME, BOOTSTRAP_PASSWORD);
            } catch (Exception e) {
                log.warn("bootstrap super admin account failed: {}", e.getMessage());
            }
        }
    }

    /**
     * 校验账号密码，成功则更新 last_login_at_ms 并返回账号行.
     */
    public SuperAdminRow login(String username, String password) {
        if (username == null || password == null) return null;
        SuperAdminRow row = superAdminMapper.findByUsername(username.trim());
        if (row == null || !row.isEnabled()) return null;
        if (!encoder.matches(password, row.getPasswordHash())) return null;
        try {
            superAdminMapper.updateLastLogin(row.getUsername(), System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("update last login failed for {}: {}", username, e.getMessage());
        }
        return row;
    }

    /**
     * 管理接口校验 — 前端 header 携带的账号 id（username）是否合法超管.
     */
    public boolean isValidAdmin(String accountId) {
        if (accountId == null || accountId.isBlank()) return false;
        SuperAdminRow row = superAdminMapper.findByUsername(accountId.trim());
        return row != null && row.isEnabled();
    }

    public List<SuperAdminRow> findAll() {
        return superAdminMapper.findAll();
    }

    public SuperAdminRow findByUsername(String username) {
        return superAdminMapper.findByUsername(username);
    }

    /** 创建账号（返回 null 表示 username 已存在）. */
    public boolean create(String username, String password, String displayName) {
        SuperAdminRow row = new SuperAdminRow();
        row.setUsername(username.trim());
        row.setPasswordHash(encoder.encode(password));
        row.setDisplayName(displayName != null && !displayName.isBlank() ? displayName : username.trim());
        row.setEnabled(true);
        row.setCreatedAtMs(System.currentTimeMillis());
        try {
            return superAdminMapper.insert(row) > 0;
        } catch (Exception e) {
            return false; // 唯一键冲突
        }
    }

    public boolean delete(String username) {
        return superAdminMapper.deleteByUsername(username) > 0;
    }

    /** 重置密码. */
    public boolean resetPassword(String username, String newPassword) {
        SuperAdminRow row = superAdminMapper.findByUsername(username);
        if (row == null) return false;
        row.setPasswordHash(encoder.encode(newPassword));
        try {
            return superAdminMapper.updatePassword(row.getUsername(), row.getPasswordHash()) > 0;
        } catch (Exception e) {
            log.warn("reset password failed for {}: {}", username, e.getMessage());
            return false;
        }
    }
}
