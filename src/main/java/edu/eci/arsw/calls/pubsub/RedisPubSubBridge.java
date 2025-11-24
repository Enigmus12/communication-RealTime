package edu.eci.arsw.calls.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
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

    // Suscriptores locales (fallback sin Redis o si Redis se cae)
    private final Map<String, CopyOnWriteArrayList<Consumer<String>>> localSubs = new ConcurrentHashMap<>();
    private final AtomicBoolean redisOk = new AtomicBoolean(true);

    // Para evitar múltiples addMessageListener() por el mismo canal
    private final Set<String> redisSubscribed = ConcurrentHashMap.newKeySet();

    /**
     * Constructor del puente Pub/Sub con Redis
     */
    public RedisPubSubBridge(
            @Autowired(required = false) StringRedisTemplate template,
            @Autowired(required = false) RedisMessageListenerContainer container) {
        this.template = template;
        this.container = container;

        if (this.container != null) {
            this.container.setErrorHandler(ex -> {
                redisOk.set(false);
                log.warn("Redis listener error: {}. Usando fallback local.", ex.toString());
            });
        } else {
            redisOk.set(false);
        }
    }

    /**
     * Suscribirse a un canal Pub/Sub
     */
    public void subscribe(String channel, Consumer<String> consumer) {
        // Siempre suscripción local
        localSubs.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(consumer);

        // Si Redis está OK y no nos hemos suscrito aún a este canal, suscribir
        if (redisOk.get() && container != null && redisSubscribed.add(channel)) {
            try {
                container.addMessageListener((Message m, byte[] pattern) -> {
                    String payload = new String(m.getBody(), StandardCharsets.UTF_8);
                    // Fanout local (este nodo) para todos los suscriptores locales
                    fanoutLocal(channel, payload);
                }, new PatternTopic(channel));
            } catch (Exception e) {
                redisOk.set(false);
                log.warn("No se pudo suscribir en Redis ({}). Fallback local activo.", e.toString());
            }
        }
    }

    /**
     * Publicar un mensaje en un canal Pub/Sub
     */
    public void publish(String channel, String payload) {
        boolean publishedToRedis = false;
        if (redisOk.get() && template != null) {
            try {
                template.convertAndSend(channel, payload);
                publishedToRedis = true;
            } catch (Exception e) {
                redisOk.set(false);
                log.warn("Fallo publicando en Redis ({}). Fallback local activo.", e.toString());
            }
        }
        // Siempre fanout local para garantizar entrega en este proceso
        fanoutLocal(channel, payload);

        if (!publishedToRedis && template == null) {
            log.debug("Publicación local (sin Redis) en {}", channel);
        }
    }

    /**
     * Realiza el fanout local a los suscriptores locales de un canal
     */
    private void fanoutLocal(String channel, String payload) {
        var list = localSubs.getOrDefault(channel, new CopyOnWriteArrayList<>());
        for (var c : list) {
            try { c.accept(payload); } catch (Exception ignore) {}
        }
    }
}
