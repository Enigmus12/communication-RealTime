package edu.eci.arsw.calls.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Configuración de Redis para la aplicación
 */
@Configuration
public class RedisConfig {
    @Value("${REDIS_HOST:localhost}")
    private String host;
    @Value("${REDIS_PORT:6379}")
    private int port;

    /**
     * Configurar la fábrica de conexiones Redis
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration conf = new RedisStandaloneConfiguration(host, port);
        return new LettuceConnectionFactory(conf);
    }

    /**
     * Configurar el template de Redis para operaciones con cadenas
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory cf) {
        return new StringRedisTemplate(cf);
    }

    /**
     * Configurar el contenedor de escucha de mensajes Redis
     */
    @Bean
    public RedisMessageListenerContainer redisContainer(LettuceConnectionFactory cf) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(cf);
        return container;
    }
}
