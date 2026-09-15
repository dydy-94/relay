package com.buzz.relay.api;

import com.buzz.relay.model.ChatEnvelope;
import com.buzz.relay.relay.RelayState;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API — 信封注入 + 历史查询.
 *
 * POST /api/envelope   — REST 注入信封（等价于 WS publish 命令）
 * GET  /api/history    — 查询历史信封（Dispatcher.fetch_history 调用）
 */
@RestController
@RequestMapping("/api")
public class EnvelopeController {

    private final RelayState state;

    public EnvelopeController(RelayState state) {
        this.state = state;
    }

    @PostMapping("/envelope")
    public Map<String, Object> inject(@RequestBody Map<String, Object> body) {
        String channelId = (String) body.getOrDefault("channel_id",
                body.getOrDefault("topic_id", "default").toString());

        // 从 body 构建 ChatEnvelope
        ChatEnvelope env = new ChatEnvelope();
        env.setEnvelopeId((String) body.getOrDefault("envelope_id", UUID.randomUUID().toString()));
        env.setChannelId(channelId);
        env.setSenderId((String) body.getOrDefault("sender_id", "rest"));
        env.setSenderRole((String) body.getOrDefault("sender_role", "human"));
        env.setKind((String) body.getOrDefault("kind", "chat"));
        env.setCreatedAtMs(body.get("created_at_ms") != null
                ? ((Number) body.get("created_at_ms")).longValue()
                : System.currentTimeMillis());
        if (body.get("project_id") != null) env.setProjectId((String) body.get("project_id"));
        if (body.get("topic_id") != null) env.setTopicId((String) body.get("topic_id"));
        if (body.get("parent_envelope_id") != null) env.setParentEnvelopeId((String) body.get("parent_envelope_id"));
        if (body.get("root_envelope_id") != null) env.setRootEnvelopeId((String) body.get("root_envelope_id"));
        if (body.get("chat") != null) env.setChat((Map<String, Object>) body.get("chat"));
        if (body.get("task_assign") != null) env.setTaskAssign((Map<String, Object>) body.get("task_assign"));
        if (body.get("task_result") != null) env.setTaskResult((Map<String, Object>) body.get("task_result"));
        if (body.get("command") != null) env.setCommand((Map<String, Object>) body.get("command"));

        ChatEnvelope stored = state.storeAndBroadcast(channelId, env);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("envelope_id", stored.getEnvelopeId());
        return result;
    }

    @GetMapping("/history")
    public Map<String, Object> history(
            @RequestParam String channel_id,
            @RequestParam(required = false) String root_envelope_id,
            @RequestParam(defaultValue = "20") int limit) {
        List<Map<String, Object>> envs = state.getHistory(channel_id, root_envelope_id, limit);
        return Map.of("envelopes", envs);
    }
}
