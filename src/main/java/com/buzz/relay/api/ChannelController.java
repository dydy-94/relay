package com.buzz.relay.api;

import com.buzz.relay.relay.RelayState;
import com.buzz.relay.store.ChannelRow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API — Channel 管理.
 *
 * POST /api/channels                 — 创建 channel（creator_agent_id 成为首个 admin）
 * POST /api/channels/add_member      — 添加成员（需 admin，body 携带 actor_agent_id）
 * POST /api/channels/remove_member    — 移除成员（需 admin，body 携带 actor_agent_id）
 *
 * 权限模型：管理操作（增删成员 / 更新频道元信息）仅 admin 可执行。
 * REST 无独立认证，操作者身份通过 body 中的 actor_agent_id 标识
 * （可信内部网络，与 WS agent_id 同一信任模型）。
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final RelayState state;

    public ChannelController(RelayState state) {
        this.state = state;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createChannel(@RequestBody Map<String, Object> body) {
        String channelId = (String) body.getOrDefault("channel_id", UUID.randomUUID().toString());
        String name = (String) body.getOrDefault("name", "");
        String channelType = (String) body.getOrDefault("channel_type", "stream");
        String visibility = (String) body.getOrDefault("visibility", "open");
        String description = (String) body.getOrDefault("description", "");

        ChannelRow existing = state.getChannel(channelId);
        if (existing != null) {
            // 已存在 = 更新元信息，需 admin
            String actor = (String) body.getOrDefault("actor_agent_id", "");
            if (!state.isAdmin(channelId, actor)) {
                return forbidden("admin required to update channel");
            }
        }

        ChannelRow ch = state.updateChannelInfo(channelId, name, channelType, visibility, description);

        // 新频道：创建者成为首个 admin
        if (existing == null) {
            String creator = (String) body.getOrDefault("creator_agent_id", "");
            if (!creator.isBlank()) {
                state.addMember(channelId, creator, creator, "admin");
            }
        }

        return ResponseEntity.ok(state.channelInfoMap(ch));
    }

    @PostMapping("/add_member")
    public ResponseEntity<Map<String, Object>> addMember(@RequestBody Map<String, Object> body) {
        String channelId = (String) body.getOrDefault("channel_id", "");
        String agentId = (String) body.getOrDefault("agent_id", "");
        String actor = (String) body.getOrDefault("actor_agent_id", "");
        String name = (String) body.getOrDefault("name", "");
        String role = (String) body.getOrDefault("role", "member");

        if (channelId.isBlank() || agentId.isBlank()) {
            return badRequest("channel_id and agent_id required");
        }
        if (!state.isAdmin(channelId, actor)) {
            return forbidden("admin required to add member");
        }

        state.addMember(channelId, agentId, name, role);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/remove_member")
    public ResponseEntity<Map<String, Object>> removeMember(@RequestBody Map<String, Object> body) {
        String channelId = (String) body.getOrDefault("channel_id", "");
        String agentId = (String) body.getOrDefault("agent_id", "");
        String actor = (String) body.getOrDefault("actor_agent_id", "");

        if (channelId.isBlank() || agentId.isBlank()) {
            return badRequest("channel_id and agent_id required");
        }
        if (!state.isAdmin(channelId, actor)) {
            return forbidden("admin required to remove member");
        }

        state.removeMember(channelId, agentId);
        return ResponseEntity.ok(Map.of("ok", true));
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
