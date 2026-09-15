package com.buzz.relay.store;

import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * MyBatis Mapper — relay_channels + relay_channel_members + relay_agents 表 CRUD.
 */
public interface ChannelMapper {

    // ── Channel ──

    /** 插入 channel（channel_id 已存在时返回 0）. */
    int insertChannel(@Param("row") ChannelRow row);

    /** 按 channel_id 查找. */
    ChannelRow findChannelById(@Param("channelId") String channelId);

    /** 更新 channel 元信息. */
    int updateChannel(@Param("row") ChannelRow row);

    /** 查询 agent 加入的所有 channel. */
    List<ChannelRow> findChannelsByAgent(@Param("agentId") String agentId);

    /** 查询所有 channel（含 archived，dashboard 用）. */
    List<ChannelRow> findAllChannels();

    /** channel 总数. */
    long countChannels();

    // ── Member ──

    /** 插入成员关系（UNIQUE 约束防重复）. */
    int insertMember(@Param("row") ChannelMemberRow row);

    /** 删除成员. */
    int deleteMember(@Param("channelId") String channelId, @Param("agentId") String agentId);

    /** 查询 channel 所有成员. */
    List<ChannelMemberRow> findMembers(@Param("channelId") String channelId);

    /** 检查 agent 是否是 channel 成员. */
    ChannelMemberRow findMember(@Param("channelId") String channelId, @Param("agentId") String agentId);

    // ── Agent ──

    /** 插入 agent 注册信息（agent_id 已存在时返回 0）. */
    int insertAgent(@Param("agentId") String agentId,
                    @Param("sandboxId") String sandboxId,
                    @Param("capabilities") String capabilitiesJson,
                    @Param("rules") String rulesJson,
                    @Param("registeredAtMs") long registeredAtMs);

    /** 更新 agent 心跳. */
    int updateHeartbeat(@Param("agentId") String agentId,
                         @Param("status") String status,
                         @Param("activeTasks") int activeTasks,
                         @Param("lastHeartbeatMs") long lastHeartbeatMs);

    /** 查询所有已注册 agent. */
    List<java.util.Map<String, Object>> findAllAgents();

    // ── Stats ──

    /** agent 总数. */
    long countAgents();
}
