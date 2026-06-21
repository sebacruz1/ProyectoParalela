package store;

import modelos.Juego;
import modelos.Transaccion;
import modelos.Usuario;
import protocolo.TransaccionReplicada;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Tienda {
    private static Tienda instancia;

    public static final int ID_JUEGO_FLASH = 8;
    private static final int STOCK_FLASH_INICIAL = 8;

    private List<Juego> catalogo;
    private Map<Integer, List<Juego>> bibliotecas;
    private Map<Integer, List<Transaccion>> historial;
    private Map<Integer, Integer> stockFlash;
    private int contadorTransacciones;

    // Log replicado entre nodos (independiente de bibliotecas/historial, que son
    // por usuario y solo consistentes en el nodo donde ese usuario tiene sesión).
    // txIdsAplicados garantiza aplicación idempotente de TX_COMMIT replicados.
    private final List<TransaccionReplicada> logGlobal = new ArrayList<>();
    private final Set<String> txIdsAplicados = new HashSet<>();

    private Tienda() {
        catalogo = new ArrayList<>();
        bibliotecas = new HashMap<>();
        historial = new HashMap<>();
        stockFlash = new HashMap<>();
        contadorTransacciones = 0;

        catalogo.add(new Juego(1, "Fortnite", 0, "BattleRoyale"));
        catalogo.add(new Juego(2, "Rocket League", 0, "Futbol con autos"));
        catalogo.add(new Juego(3, "Resident Evil", 50000, "Juego de terror"));
        catalogo.add(new Juego(4, "CyberPunk 2077", 29990, "RPG mundo abierto"));
        catalogo.add(new Juego(5, "Balatro", 10000, "Juego de cartas"));
        catalogo.add(new Juego(6, "Call of Duty", 60000, "Shooter"));
        catalogo.add(new Juego(7, "Arc Raiders", 0, "Juego de supervivencia"));
        catalogo.add(new Juego(ID_JUEGO_FLASH, "Stock Limitado Edition", 39990,
                "Edicion especial con unidades limitadas", true));
        stockFlash.put(ID_JUEGO_FLASH, STOCK_FLASH_INICIAL);
    }

    public static synchronized Tienda getInstancia() {
        if (instancia == null)
            instancia = new Tienda();
        return instancia;

    }

    public synchronized List<Juego> getCatalogo() {
        return new ArrayList<>(catalogo);
    }

    public synchronized Juego getJuego(int id) {
        return catalogo.stream()
                .filter(j -> j.getId() == id)
                .findFirst()
                .orElse(null);
    }

    /** Devuelve la Transaccion creada, o null si la compra no procede. */
    public synchronized Transaccion comprar(Usuario usuario, int idJuego) {
        Juego juego = getJuego(idJuego);

        if (juego == null)
            return null;
        List<Juego> biblioteca = bibliotecas.getOrDefault(usuario.getId(), new ArrayList<>());
        boolean yaLoTiene = biblioteca.stream().anyMatch(j -> j.getId() == idJuego);

        if (yaLoTiene)
            return null;

        if (!usuario.comprar(juego.getPrecio()))
            return null;

        bibliotecas.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(juego);
        usuario.agregarJuego(juego);
        Transaccion t = new Transaccion(contadorTransacciones++, usuario, juego);
        historial.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(t);

        return t;

    }

    public synchronized int getStock(int idJuego) {
        return stockFlash.getOrDefault(idJuego, 0);
    }

    // Usado al recibir un StockSync de otro nodo.
    public synchronized void fijarStock(int idJuego, int valor) {
        stockFlash.put(idJuego, valor);
    }

    public synchronized Map<Integer, Integer> getStockSnapshot() {
        return new HashMap<>(stockFlash);
    }

    /**
     * Compra de un ítem de oferta flash. Debe llamarse solo dentro de la sección
     * crítica que otorga RicartAgrawala.solicitarSeccionCritica(), ya que el stock
     * es un recurso compartido entre nodos protegido por exclusión mutua
     * distribuida. Devuelve la Transaccion creada, o null si la compra no procede.
     */
    public synchronized Transaccion comprarFlash(Usuario usuario, int idJuego) {
        Juego juego = getJuego(idJuego);
        if (juego == null || !juego.isOfertaFlash()) {
            return null;
        }

        List<Juego> biblioteca = bibliotecas.getOrDefault(usuario.getId(), new ArrayList<>());
        boolean yaLoTiene = biblioteca.stream().anyMatch(j -> j.getId() == idJuego);
        if (yaLoTiene) {
            return null;
        }

        int stockActual = stockFlash.getOrDefault(idJuego, 0);
        if (stockActual <= 0) {
            return null;
        }

        if (!usuario.comprar(juego.getPrecio())) {
            return null;
        }

        stockFlash.put(idJuego, stockActual - 1);
        bibliotecas.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(juego);
        usuario.agregarJuego(juego);
        Transaccion t = new Transaccion(contadorTransacciones++, usuario, juego);
        historial.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(t);

        return t;
    }

    /**
     * Agrega un registro al log replicado de transacciones, ya sea de origen
     * local o recibido por TX_COMMIT de otro nodo. Devuelve false si el txId
     * ya estaba aplicado
     */
    public synchronized boolean registrarEnLogGlobal(TransaccionReplicada registro) {
        if (!txIdsAplicados.add(registro.getTxId())) {
            return false;
        }
        logGlobal.add(registro);
        return true;
    }

    public synchronized List<TransaccionReplicada> getLogGlobal() {
        return new ArrayList<>(logGlobal);
    }

    /** Aplica el snapshot recibido de un peer al unirse al cluster. */
    public synchronized void aplicarSnapshot(Map<Integer, Integer> stockRemoto, List<TransaccionReplicada> logRemoto) {
        stockFlash.putAll(stockRemoto);
        for (TransaccionReplicada registro : logRemoto) {
            registrarEnLogGlobal(registro);
        }
    }

    public synchronized List<Juego> getBiblioteca(int userId) {
        return new ArrayList<>(bibliotecas.getOrDefault(userId, new ArrayList<>()));
    }

    public synchronized List<Transaccion> getHistorial(int userId) {
        return new ArrayList<>(historial.getOrDefault(userId, new ArrayList<>()));
    }

}
