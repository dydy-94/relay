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
     * 按 channel_id 查询历史信封，可选按 root_envelope_id 过滤.
     * 按 created_at_ms 升序，取最新 limit 条.
     */
    List<EnvelopeRow> findHistory(@Param("channelId") String channelId,
                                   @Param("rootEnvelopeId") String rootEnvelopeId,
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
