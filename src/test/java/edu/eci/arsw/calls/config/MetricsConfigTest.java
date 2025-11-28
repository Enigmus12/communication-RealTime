package edu.eci.arsw.calls.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Pruebas de MetricsConfig.
 */
class MetricsConfigTest {

    @Test
    void constructor_deberiaRegistrarCommonTags() {
        MeterRegistry registry = mock(MeterRegistry.class);
        MeterRegistry.Config config = mock(MeterRegistry.Config.class);

        when(registry.config()).thenReturn(config);
        when(config.commonTags("application", "call-service")).thenReturn(config);

        MetricsConfig metricsConfig = new MetricsConfig(registry);

        assertNotNull(metricsConfig);
        verify(registry).config();
        verify(config).commonTags("application", "call-service");
        verifyNoMoreInteractions(config);
    }
}
