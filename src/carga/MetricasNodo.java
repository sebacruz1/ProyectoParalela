package carga;

import java.io.Serializable;

/** Snapshot de contadores de coordinación de un nodo, pedido por el generador de carga. */
public class MetricasNodo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int nodoId;
    private final long mensajesBully;
    private final long mensajesRA;

    public MetricasNodo(int nodoId, long mensajesBully, long mensajesRA) {
        this.nodoId = nodoId;
        this.mensajesBully = mensajesBully;
        this.mensajesRA = mensajesRA;
    }

    public int getNodoId() {
        return nodoId;
    }

    public long getMensajesBully() {
        return mensajesBully;
    }

    public long getMensajesRA() {
        return mensajesRA;
    }

    public long getTotal() {
        return mensajesBully + mensajesRA;
    }
}
