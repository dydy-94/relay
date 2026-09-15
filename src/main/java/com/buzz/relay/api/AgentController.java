package com.buzz.relay.api;

import com.buzz.relay.relay.RelayState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API — Health + Agent 管理.
 *
 * GET  /health               — relay 健康检查（MySQL agents + Redis presence 聚合）
 * POST /agent/register        — agent 注册
 * POST /agent/heartbeat       — agent 心跳（REST 版本，WS 也有 heartbeat 命令）
 */
@RestController
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RelayState state;

    public AgentController(RelayState state) {
        this.state = state;
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
