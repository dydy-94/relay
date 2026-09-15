package com.buzz.relay.api;

import com.buzz.relay.auth.AdminAuthService;
import com.buzz.relay.relay.RelayState;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API — Health + Agent 管理.
 *
 * GET  /health               — relay 健康检查（MySQL agents + Redis presence 聚合）
 * GET  /api/agents           — 全部 agent 列表（含在线状态；超管账号，header X-Agent-Id）
 * POST /agent/register       — agent 注册
 * POST /agent/heartbeat      — agent 心跳（REST 版本，WS 也有 heartbeat 命令）
 */
@RestController
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RelayState state;
    private final AdminAuthService authService;

    public AgentController(RelayState state, AdminAuthService authService) {
        this.state = state;
        this.authService = authService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("ok", true);
        info.put("instance_id", state.getInstanceId());
        info.put("agents", state.getAllAgents());
        info.put("online_agents", state.getOnlineAgents());
        return info;
    }

    /**
     * Dashboard：全部 agent 列表，合并 Redis presence 在线状态.
     */
    @GetMapping("/api/agents")
    public ResponseEntity<Map<String, Object>> listAgents(HttpServletRequest request) {
        String actor = ApiUtils.resolveActor(request);
        if (!authService.isValidAdmin(actor)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", "FORBIDDEN");
            err.put("message", "super admin account required (X-Agent-Id header)");
            return ResponseEntity.status(403).body(err);
        }
        Set<String> online = new HashSet<>();
        for (Object o : state.getOnlineAgents()) {
            if (o instanceof Map<?, ?> m) {
                Object id = m.get("agent_id");
                if (id != null) online.add(id.toString());
            }
        }
        List<Map<String, Object>> agents = new ArrayList<>();
        for (Map<String, Object> a : state.getAllAgents()) {
            Map<String, Object> row = new LinkedHashMap<>(a);
            row.put("online", online.contains(a.get("agentId")));
            agents.add(row);
        }
        return ResponseEntity.ok(Map.of("agents", agents));
    }

    @PostMapping("/agent/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        try {
            String agentId = (String) body.getOrDefault("agent_id", "");
            String sandboxId = (String) body.getOrDefault("sandbox_id", "");
            String capabilitiesJson = toJson(body.get("capabilities"));
            String rulesJson = toJson(body.get("rules"));
            state.registerAgent(agentId, sandboxId, capabilitiesJson, rulesJson);
            return Map.of("ok", true);
        } catch (Exception e) {
            log.error("register failed", e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage());
            return err;
        }
    }

    @PostMapping("/agent/heartbeat")
    public Map<String, Object> heartbeat(@RequestBody Map<String, Object> body) {
        String agentId = (String) body.getOrDefault("agent_id", "");
        String status = (String) body.getOrDefault("status", "idle");
        int activeTasks = body.get("active_tasks") != null ? ((Number) body.get("active_tasks")).intValue() : 0;
        state.updateHeartbeat(agentId, status, activeTasks);
        return Map.of("ok", true);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
