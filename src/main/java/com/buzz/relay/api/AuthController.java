package com.buzz.relay.api;

import com.buzz.relay.auth.AdminAuthService;
import com.buzz.relay.store.SuperAdminRow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API — Dashboard 登录.
 *
 * POST /api/auth/login — 超管账号登录（username + password，BCrypt 校验）.
 *
 * 登录成功后前端持有账号 id（username），后续调用管理接口时在 header
 * `X-Agent-Id` 带上该值即可（服务端查表校验账号存在且 enabled）.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminAuthService authService;

    public AuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String username = (String) body.getOrDefault("username", "");
        String password = (String) body.getOrDefault("password", "");
        if (username.isBlank() || password == null || password.isBlank()) {
            return badRequest("username and password required");
        }

        SuperAdminRow row = authService.login(username, password);
        if (row == null) {
            return forbidden("invalid username or password");
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("username", row.getUsername());
        res.put("display_name", row.getDisplayName());
        res.put("enabled", row.isEnabled());
        res.put("last_login_at_ms", row.getLastLoginAtMs());
        return ResponseEntity.ok(res);
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
