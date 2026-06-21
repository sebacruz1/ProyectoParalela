package protocolo;

import java.io.Serializable;

/**
 * Payload de un Mensaje TX_COMMIT que sincroniza el stock de un ítem entre
 * nodos.
 */
public class StockSync implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int idJuego;
    private final int nuevoStock;

    public StockSync(int idJuego, int nuevoStock) {
        this.idJuego = idJuego;
        this.nuevoStock = nuevoStock;
    }

    public int getIdJuego() {
        return idJuego;
    }

    public int getNuevoStock() {
        return nuevoStock;
    }

    @Override
    public String toString() {
        return "StockSync{juego=" + idJuego + ", stock=" + nuevoStock + "}";
    }
}
