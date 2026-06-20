package protocolo;

import java.io.Serializable;

public class Mensaje implements Serializable {

    private static final long serialVersionUID = 1L;

    private final TipoMensaje tipo;
    private final int lamport;
    private final int origenNodoId;
    private final Object payload;

    public Mensaje(TipoMensaje tipo, int lamport, int origenNodoId, Object payload) {
        this.tipo = tipo;
        this.lamport = lamport;
        this.origenNodoId = origenNodoId;
        this.payload = payload;
    }

    public TipoMensaje getTipo() {
        return tipo;
    }

    public int getLamport() {
        return lamport;
    }

    public int getOrigenNodoId() {
        return origenNodoId;
    }

    public Object getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return "Mensaje{" + tipo + ", lamport=" + lamport + ", origen=" + origenNodoId +
                (payload != null ? ", payload=" + payload : "") + "}";
    }
}
