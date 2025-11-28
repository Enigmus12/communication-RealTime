package edu.eci.arsw.calls.ws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleRateLimiterTest {

    // -------------------------------------------------------------------------
    // tryAcquire()
    // -------------------------------------------------------------------------

    @Test
    void tryAcquire_deberiaPermitirHastaElLimite_casoFeliz1() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(3);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    void tryAcquire_deberiaPermitirExactamenteLimite_casoFeliz2() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1);

        assertTrue(limiter.tryAcquire());
    }

    @Test
    void tryAcquire_noDeberiaPasar_cuandoSeSuperaElLimite() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(2);

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire()); // tercer intento ya debe bloquear
    }

    @Test
    void tryAcquire_noDeberiaPasar_cuandoLimiteEsCero() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(0);

        assertFalse(limiter.tryAcquire());
    }

    @Test
    void tryAcquire_deberiaReiniciarContador_enNuevaVentana_casoFeliz2() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(1);

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire()); // mismo segundo

        // Espera hasta que vuelva a devolver true (nueva ventana)
        org.awaitility.Awaitility.await()
                .atMost(java.time.Duration.ofSeconds(2))
                .until(limiter::tryAcquire);
    }

    @Test
    void tryAcquire_noDeberiaPasar_cuandoSeLlamaMasivamente() {
        SimpleRateLimiter limiter = new SimpleRateLimiter(5);
        boolean last = true;
        for (int i = 0; i < 10; i++) {
            last = limiter.tryAcquire();
        }
        assertFalse(last); // en algún punto se sobrepasa el límite
    }
}
