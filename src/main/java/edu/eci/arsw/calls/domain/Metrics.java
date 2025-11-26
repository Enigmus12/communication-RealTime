package edu.eci.arsw.calls.domain;

/**
 * Métricas relacionadas con una sesión de llamada
 */
public class Metrics {
    private long setupMs;
    private long totalDurationMs;

    public long getSetupMs() { return setupMs; }
    public void setSetupMs(long setupMs) { this.setupMs = setupMs; }
    public long getTotalDurationMs() { return totalDurationMs; }
    public void setTotalDurationMs(long totalDurationMs) { this.totalDurationMs = totalDurationMs; }
}
