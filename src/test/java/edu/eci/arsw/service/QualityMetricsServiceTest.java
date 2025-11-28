package edu.eci.arsw.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import edu.eci.arsw.calls.service.QualityMetricsService;

import static org.junit.jupiter.api.Assertions.*;

class QualityMetricsServiceTest {

    private QualityMetricsService service;

    @BeforeEach
    void setUp() {
        service = new QualityMetricsService(new SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // recordSuccess(long setupMs)
    // -------------------------------------------------------------------------

    @Test
    void recordSuccess_deberiaAgregarMuestra_casoFeliz1() {
        service.recordSuccess(100);

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(1, s.samples());
        assertEquals(100L, s.p95ms());
        assertEquals(100L, s.p99ms());
    }

    @Test
    void recordSuccess_deberiaCalcularPercentiles_casoFeliz2() {
        service.recordSuccess(100);
        service.recordSuccess(200);
        service.recordSuccess(300);

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(3, s.samples());
        assertTrue(s.p95ms() >= 200 && s.p95ms() <= 300);
        assertTrue(s.p99ms() >= 200 && s.p99ms() <= 300);
    }

    @Test
    void recordSuccess_noDeberiaRomper_cuandoSetupEsCero() {
        service.recordSuccess(0);

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(1, s.samples());
        assertEquals(0L, s.p95ms());
        assertEquals(0L, s.p99ms());
    }

    @Test
    void recordSuccess_noDeberiaRomper_cuandoSetupEsNegativo() {
        service.recordSuccess(-50);

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(1, s.samples());
        // el percentil será -50, pero lo importante es que no reviente
        assertEquals(-50L, s.p95ms());
    }

    // -------------------------------------------------------------------------
    // recordFailure()
    // -------------------------------------------------------------------------

    @Test
    void recordFailure_deberiaImpactarSuccessRate_casoFeliz1() {
        service.recordSuccess(100);
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(1, s.samples());
        assertEquals(0.5, s.successRate(), 0.0001);
    }

    @Test
    void recordFailure_deberiaPermitirMultiplesFallos_casoFeliz2() {
        service.recordFailure();
        service.recordFailure();
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(0, s.samples());
        assertEquals(0.0, s.successRate(), 0.0001);
    }

    @Test
    void recordFailure_noDeberiaCambiarSamples_cuandoSoloFalla() {
        service.recordFailure();
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();
        assertEquals(0, s.samples());
    }

    @Test
    void recordFailure_noDeberiaRomper_cuandoNoHayExitosPrevios() {
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(0, s.samples());
        assertEquals(0.0, s.successRate(), 0.0001);
    }

    // -------------------------------------------------------------------------
    // snapshot()
    // -------------------------------------------------------------------------

    @Test
    void snapshot_deberiaSerExitoso_conSinMuestras_casoFeliz1() {
        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(0L, s.p95ms());
        assertEquals(0L, s.p99ms());
        assertEquals(1.0, s.successRate(), 0.0001);
        assertEquals(0, s.samples());
    }

    @Test
    void snapshot_deberiaActualizarseDespuesDeExitosYFallos_casoFeliz2() {
        service.recordSuccess(100);
        service.recordSuccess(200);
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(2, s.samples());
        assertTrue(s.successRate() > 0 && s.successRate() < 1);
    }

    @Test
    void snapshot_noDeberiaPasar_cuandoMuchosExitosYSinFallos() {
        service.recordSuccess(10);
        service.recordSuccess(20);
        service.recordSuccess(30);

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(3, s.samples());
        assertEquals(1.0, s.successRate(), 0.0001);
    }

    @Test
    void snapshot_noDeberiaPasar_cuandoSoloFallos() {
        service.recordFailure();
        service.recordFailure();

        QualityMetricsService.Snapshot s = service.snapshot();

        assertEquals(0, s.samples());
        assertEquals(0.0, s.successRate(), 0.0001);
    }
}
