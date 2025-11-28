package edu.eci.arsw.calls.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prueba mínima para ejecutar el constructor de MongoConfig.
 */
class MongoConfigTest {

    @Test
    void constructor_deberiaCrearInstancia() {
        MongoConfig config = new MongoConfig();
        assertNotNull(config);
    }
}
