package carga;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

/** Contadores thread-safe acumulados por todos los hilos del generador de carga. */
public class Metricas {

    private final LongAdder exitosas = new LongAdder();
    private final LongAdder rechazadas = new LongAdder();
    private final LongAdder errores = new LongAdder();
    private final ConcurrentLinkedQueue<Long> latenciasMs = new ConcurrentLinkedQueue<>();

    public void registrarExito(long latenciaMs) {
        exitosas.increment();
        latenciasMs.add(latenciaMs);
    }

    public void registrarRechazo(long latenciaMs) {
        rechazadas.increment();
        latenciasMs.add(latenciaMs);
    }

    public void registrarError() {
        errores.increment();
    }

    public long getExitosas() {
        return exitosas.sum();
    }

    public long getRechazadas() {
        return rechazadas.sum();
    }

    public long getErrores() {
        return errores.sum();
    }

    public ConcurrentLinkedQueue<Long> getLatenciasMs() {
        return latenciasMs;
    }
}
