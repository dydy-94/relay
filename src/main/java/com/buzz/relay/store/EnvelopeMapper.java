package com.buzz.relay.store;

import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * MyBatis Mapper — relay_envelopes 表 CRUD.
 */
public interface EnvelopeMapper {

    /** 插入信封（envelope_id 已存在时返回 0）. */
    int insert(@Param("row") EnvelopeRow row);

    /** 按 envelope_id 查找. */
    EnvelopeRow findById(@Param("envelopeId") String envelopeId);

    /**
     * 按 channel_id 分页查询历史信封（dashboard 消息查看用）.
     * 按 created_at_ms 倒序（最新在前），beforeMs 为游标（只取更早的），limit 控制页大小.
     */
    List<EnvelopeRow> findHistoryPage(@Param("channelId") String channelId,
                                       @Param("rootEnvelopeId") String rootEnvelopeId,
                                       @Param("beforeMs") Long beforeMs,
                                       @Param("limit") int limit);

    /**
     * 按 channel_id + since_ms 回放历史信封（created_at_ms > since_ms）.
     */
    List<EnvelopeRow> findReplay(@Param("channelId") String channelId,
                                  @Param("sinceMs") long sinceMs,
                                  @Param("limit") int limit);

    /** 信封总数（dashboard stats 用）. */
    long countEnvelopes();

    /** 某 channel 的信封数（dashboard 用）. */
    long countByChannel(@Param("channelId") String channelId);
}
