package edu.eci.arsw.calls.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pruebas de RedisConfig.
 */
class RedisConfigTest {

    private RedisConfig config;

    @BeforeEach
    void setUp() {
        config = new RedisConfig();
        // Simulamos los @Value
        ReflectionTestUtils.setField(config, "host", "redis-host-test");
        ReflectionTestUtils.setField(config, "port", 1234);
    }

    @Test
    void redisConnectionFactory_deberiaUsarHostYPortConfigurados() {
        LettuceConnectionFactory cf = config.redisConnectionFactory();

        // No inicializamos la conexión real, solo verificamos la config
        assertEquals("redis-host-test", cf.getHostName());
        assertEquals(1234, cf.getPort());
    }

    @Test
    void stringRedisTemplate_deberiaUsarLaConnectionFactoryIndicada() {
        LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);

        StringRedisTemplate template = config.stringRedisTemplate(cf);

        assertSame(cf, template.getConnectionFactory());
    }

    @Test
    void redisContainer_deberiaUsarLaConnectionFactoryIndicada() {
        LettuceConnectionFactory cf = mock(LettuceConnectionFactory.class);

        RedisMessageListenerContainer container = config.redisContainer(cf);

        assertSame(cf, container.getConnectionFactory());
    }

    @Test
    void config_deberiaNoSerNuloDespuesDeSetUp() {
        // Verifica que el setUp inicializó la configuración
        assertNotNull(config);
    }
}
