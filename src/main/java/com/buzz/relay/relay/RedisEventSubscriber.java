package com.buzz.relay.relay;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Redis Pub/Sub 订阅者 — 接收其他 relay 实例发布的事件并交给 RelayState 处理.
 */
@Component
public class RedisEventSubscriber implements MessageListener {

    private final RelayState state;

    public RedisEventSubscriber(RelayState state) {
        this.state = state;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        state.onRemoteEvent(body);
    }
}
