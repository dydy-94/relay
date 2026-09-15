package com.buzz.relay.config;

import com.buzz.relay.relay.RedisEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 配置 — Pub/Sub 跨实例事件分发.
 *
 * 所有 relay 实例订阅同一个 topic {@link #TOPIC}（"relay:events"）。
 * 任一实例收到新信封 / membership 变更时发布到该 topic，
 * 其他实例收到后推送给各自内存中的在线 WS 连接，实现水平扩展。
 */
@Configuration
public class RedisConfig {

    /** 跨实例事件统一 topic. */
    public static final String TOPIC = "relay:events";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory, RedisEventSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(subscriber, new ChannelTopic(TOPIC));
        return container;
    }
}
