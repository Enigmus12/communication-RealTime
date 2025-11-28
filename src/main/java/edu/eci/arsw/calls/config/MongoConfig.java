package edu.eci.arsw.calls.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Configuración de MongoDB para la aplicación.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
