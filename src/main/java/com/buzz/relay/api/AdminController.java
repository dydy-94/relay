package com.buzz.relay.api;

import com.buzz.relay.auth.AdminAuthService;
import com.buzz.relay.relay.RelayState;
import com.buzz.relay.store.SuperAdminRow;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API — 超管 / Dashboard 管理.
 *
 * 超管 = 人（非 agent），通过账号密码登录 dashboard（POST /api/auth/login）.
 * 所有管理接口的鉴权：前端在 header `X-Agent-Id` 带上登录账号 id，
 * 服务端查 relay_super_admins 表校验账号存在且 enabled（AdminAuthService.isValidAdmin）.
 *
 * GET    /api/admin/stats                      — 全局统计总览
 * GET    /api/admin/accounts                   — 账号列表
 * POST   /api/admin/accounts                   — 创建账号 {username, password, display_name}
 * DELETE /api/admin/accounts/{username}        — 删除账号
 * POST   /api/admin/accounts/{username}/password — 重置密码 {password}
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RelayState state;
    private final AdminAuthService authService;

    public AdminController(RelayState state, AdminAuthService authService) {
        this.state = state;
        this.authService = authService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(HttpServletRequest request) {
        if (!requireAdmin(request)) return forbidden("super admin account required (X-Agent-Id header)");
        return ResponseEntity.ok(state.getStats());
    }

    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> listAccounts(HttpServletRequest request) {
        if (!requireAdmin(request)) return forbidden("super admin account required (X-Agent-Id header)");
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (SuperAdminRow a : authService.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", a.getUsername());
            m.put("display_name", a.getDisplayName());
            m.put("enabled", a.isEnabled());
            m.put("created_at_ms", a.getCreatedAtMs());
            m.put("last_login_at_ms", a.getLastLoginAtMs());
            accounts.add(m);
        }
        return ResponseEntity.ok(Map.of("accounts", accounts));
    }

    @PostMapping("/accounts")
    public ResponseEntity<Map<String, Object>> createAccount(@RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        if (!requireAdmin(request)) return forbidden("super admin account required (X-Agent-Id header)");
        String username = (String) body.getOrDefault("username", "");
        String password = (String) body.getOrDefault("password", "");
        String displayName = (String) body.getOrDefault("display_name", "");
        if (username.isBlank() || password == null || password.isBlank()) {
            return badRequest("username and password required");
        }
        if (!authService.create(username, password, displayName)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", "CONFLICT");
            err.put("message", "username already exists");
            return ResponseEntity.status(409).body(err);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("username", username.trim());
        return ResponseEntity.ok(res);
    }

    @DeleteMapping("/accounts/{username}")
    public ResponseEntity<Map<String, Object>> deleteAccount(@PathVariable String username,
                                                             HttpServletRequest request) {
        if (!requireAdmin(request)) return forbidden("super admin account required (X-Agent-Id header)");
        // 防止误删自身
        String actor = ApiUtils.resolveActor(request);
        if (username.equals(actor)) {
            return badRequest("cannot delete the account you are logged in with");
        }
        if (!authService.delete(username)) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "NOT_FOUND",
                    "message", "account not found"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "username", username));
    }

    @PostMapping("/accounts/{username}/password")
    public ResponseEntity<Map<String, Object>> resetPassword(@PathVariable String username,
                                                             @RequestBody Map<String, Object> body,
                                                             HttpServletRequest request) {
        if (!requireAdmin(request)) return forbidden("super admin account required (X-Agent-Id header)");
        String password = (String) body.getOrDefault("password", "");
        if (password.isBlank()) return badRequest("password required");
        if (!authService.resetPassword(username, password)) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "error", "NOT_FOUND",
                    "message", "account not found"));
        }
        return ResponseEntity.ok(Map.of("ok", true, "username", username));
    }

    private boolean requireAdmin(HttpServletRequest request) {
        return authService.isValidAdmin(ApiUtils.resolveActor(request));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String msg) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", false);
        err.put("error", "BAD_REQUEST");
        err.put("message", msg);
        return ResponseEntity.badRequest().body(err);
    }

    private ResponseEntity<Map<String, Object>> forbidden(String msg) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", false);
        err.put("error", "FORBIDDEN");
        err.put("message", msg);
        return ResponseEntity.status(403).body(err);
    }
}
