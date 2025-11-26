package edu.eci.arsw.calls.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;              // 👈 IMPORT CORRECTO
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;        // 👈 IMPORT EXPLÍCITO
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Component
public class RedisPubSubBridge {
    private static final Logger log = LoggerFactory.getLogger(RedisPubSubBridge.class);

    private final @Nullable StringRedisTemplate template;
    private final @Nullable RedisMessageListenerContainer container;

    private final Map<String, CopyOnWriteArrayList<Consumer<String>>> localSubs = new ConcurrentHashMap<>();
    private final AtomicBoolean redisOk = new AtomicBoolean(true);
    private final Set<String> redisSubscribed = ConcurrentHashMap.newKeySet();

    public RedisPubSubBridge(@Autowired(required = false) StringRedisTemplate template,
                             @Autowired(required = false) RedisMessageListenerContainer container) {
        this.template = template;
        this.container = container;
        if (this.container != null) {
            this.container.setErrorHandler(ex -> {
                redisOk.set(false);
                log.warn("Redis listener error: {}. Fallback local.", ex.toString());
            });
        } else {
            redisOk.set(false);
        }
    }

    public void subscribe(String channel, Consumer<String> consumer) {
        localSubs.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(consumer);
        if (redisOk.get() && container != null && redisSubscribed.add(channel)) {
            try {
                container.addMessageListener((Message m, byte[] pattern) -> {   // 👈 Message correcto
                    String payload = new String(m.getBody(), StandardCharsets.UTF_8);
                    fanoutLocal(channel, payload);
                }, new PatternTopic(channel));
            } catch (Exception e) {
                redisOk.set(false);
                log.warn("No se pudo suscribir en Redis. Fallback local. {}", e.toString());
            }
        }
    }

    public void publish(String channel, String payload) {
        boolean okRedis = false;
        if (redisOk.get() && template != null) {
            try {
                template.convertAndSend(channel, payload);
                okRedis = true;
            } catch (Exception e) {
                redisOk.set(false);
                log.warn("Publish Redis falló. Fallback local. {}", e.toString());
            }
        }
        fanoutLocal(channel, payload);
        if (!okRedis && template == null) log.debug("Publicación local (sin Redis) en {}", channel);
    }

    private void fanoutLocal(String channel, String payload) {
        var list = localSubs.getOrDefault(channel, new CopyOnWriteArrayList<>());
        for (var c : list) {
            try { c.accept(payload); } catch (Exception ignore) {}
        }
    }
}
