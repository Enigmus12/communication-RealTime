package edu.eci.arsw.calls.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de métricas para la aplicación
 */
@Configuration
public class MetricsConfig {
    public MetricsConfig(MeterRegistry registry) {
        registry.config().commonTags("application", "call-service");
    }
}
