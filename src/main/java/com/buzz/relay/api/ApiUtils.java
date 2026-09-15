package com.buzz.relay.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * REST 操作者身份解析工具.
 *
 * 身份来源优先级（dashboard 使用 header，兼容旧的 body actor_agent_id）：
 *   1. Header: X-Agent-Id
 *   2. Query:  actor_agent_id
 * POST 操作仍从 body 读取 actor_agent_id（向后兼容）。
 */
public final class ApiUtils {

    private ApiUtils() {}

    public static String resolveActor(HttpServletRequest request) {
        String header = request.getHeader("X-Agent-Id");
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String query = request.getParameter("actor_agent_id");
        return query != null ? query.trim() : "";
    }
}
