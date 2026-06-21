package protocolo;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Snapshot de estado de Tienda enviado a un nodo que recién se une al cluster.
 */
public class EstadoSync implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Map<Integer, Integer> stockFlash;
    private final List<TransaccionReplicada> logGlobal;

    public EstadoSync(Map<Integer, Integer> stockFlash, List<TransaccionReplicada> logGlobal) {
        this.stockFlash = stockFlash;
        this.logGlobal = logGlobal;
    }

    public Map<Integer, Integer> getStockFlash() {
        return stockFlash;
    }

    public List<TransaccionReplicada> getLogGlobal() {
        return logGlobal;
    }
}
