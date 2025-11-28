package edu.eci.arsw.calls.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_deberiaUsarAllowedOriginsDePropiedad_casoFeliz1() {
        CorsConfig cfg = new CorsConfig();
        ReflectionTestUtils.setField(cfg, "allowedOrigins",
                "http://localhost:3000,http://uplearn.test");

        CorsConfigurationSource source = cfg.corsConfigurationSource();
        CorsConfiguration conf = source.getCorsConfiguration(new MockHttpServletRequest());

        assertNotNull(conf);
        assertTrue(conf.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(conf.getAllowedOrigins().contains("http://uplearn.test"));
    }

    @Test
    void corsConfigurationSource_deberiaPermitirCredencialesYMetodos_casoFeliz2() {
        CorsConfig cfg = new CorsConfig();
        ReflectionTestUtils.setField(cfg, "allowedOrigins", "http://localhost:3000");

        CorsConfiguration conf = cfg.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertEquals(Boolean.TRUE, conf.getAllowCredentials());
        assertTrue(conf.getAllowedMethods().contains("GET"));
        assertTrue(conf.getAllowedMethods().contains("POST"));
        assertTrue(conf.getAllowedHeaders().contains("Authorization"));
    }

    @Test
    void corsConfigurationSource_noDeberiaPasar_cuandoAllowedOriginsVacio() {
        CorsConfig cfg = new CorsConfig();
        ReflectionTestUtils.setField(cfg, "allowedOrigins", "");

        CorsConfiguration conf = cfg.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        // Se espera al menos una entrada vacía en la lista
        assertNotNull(conf.getAllowedOrigins());
        assertEquals(1, conf.getAllowedOrigins().size());
    }

    @Test
    void corsConfigurationSource_deberiaExponerContentTypeYAuthorization() {
        CorsConfig cfg = new CorsConfig();
        ReflectionTestUtils.setField(cfg, "allowedOrigins", "http://localhost:3000");

        CorsConfiguration conf = cfg.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest());

        assertTrue(conf.getExposedHeaders().contains("Content-Type"));
        assertTrue(conf.getExposedHeaders().contains("Authorization"));
    }
}
