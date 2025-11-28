package edu.eci.arsw.calls.pubsub;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas de RedisPubSubBridge con y sin Redis real (modo fallback local).
 */
class RedisPubSubBridgeTest {

    @Test
    void publishYSubscribe_deberianFuncionarEnModoLocal_sinRedis_casoFeliz1() {
        RedisPubSubBridge bridge = new RedisPubSubBridge(null, null);

        List<String> recibidos = new ArrayList<>();
        Consumer<String> consumer = recibidos::add;

        bridge.subscribe("ch1", consumer);
        bridge.publish("ch1", "hola");

        assertEquals(List.of("hola"), recibidos);
    }

    @Test
    void publish_noDeberiaFallar_sinSuscriptores_casoFeliz2() {
        RedisPubSubBridge bridge = new RedisPubSubBridge(null, null);

        // No hay suscriptores, simplemente no debe lanzar excepción
        assertDoesNotThrow(() -> bridge.publish("ch2", "mensaje"));
    }

    @Test
    void publish_deberiaUsarRedisCuandoEstaDisponible_casoFeliz3() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);

        RedisPubSubBridge bridge = new RedisPubSubBridge(template, container);

        // Suscripción, verificamos que se registre el listener en el container
        bridge.subscribe("ch-redis", payload -> {});

        verify(container).addMessageListener(
                any(MessageListener.class),
                any(Topic.class)
        );

        bridge.publish("ch-redis", "hola");

        verify(template).convertAndSend("ch-redis", "hola");
    }

    @Test
    void publish_deberiaHacerFallbackLocal_siRedisFalla_casoNoFeliz() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);

        RedisPubSubBridge bridge = new RedisPubSubBridge(template, container);

        List<String> recibidos = new ArrayList<>();
        bridge.subscribe("ch-fail", recibidos::add);

        doThrow(new RuntimeException("redis down"))
                .when(template).convertAndSend("ch-fail", "msg");

        // Debe seguir entregando localmente aunque Redis falle
        bridge.publish("ch-fail", "msg");

        assertEquals(List.of("msg"), recibidos);
    }
}
