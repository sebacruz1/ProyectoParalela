package store;

import modelos.Juego;
import modelos.Transaccion;
import modelos.Usuario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Tienda {
    private static Tienda instancia;

    private List<Juego> catalogo;
    private Map<Integer, List<Juego>> bibliotecas;
    private Map<Integer, List<Transaccion>> historial;
    private int contadorTransacciones;

    private Tienda() {
        catalogo = new ArrayList<>();
        bibliotecas = new HashMap<>();
        historial = new HashMap<>();
        contadorTransacciones = 0;

        catalogo.add(new Juego(1, "Fortnite", 0, "BattleRoyale"));
        catalogo.add(new Juego(2, "Rocket League", 0, "Futbol con autos"));
        catalogo.add(new Juego(3, "Resident Evil", 50000, "Juego de terror"));
        catalogo.add(new Juego(4, "CyberPunk 2077", 29990, "RPG mundo abierto"));
        catalogo.add(new Juego(5, "Balatro", 10000, "Juego de cartas"));
        catalogo.add(new Juego(6, "Call of Duty", 60000, "Shooter"));
        catalogo.add(new Juego(7, "Arc Raiders", 0, "Juego de supervivencia"));
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

    public synchronized boolean comprar(Usuario usuario, int idJuego) {
        Juego juego = getJuego(idJuego);

        if (juego == null)
            return false;
        List<Juego> biblioteca = bibliotecas.getOrDefault(usuario.getId(), new ArrayList<>());
        boolean yaLoTiene = biblioteca.stream().anyMatch(j -> j.getId() == idJuego);

        if (yaLoTiene)
            return false;

        if (!usuario.comprar(juego.getPrecio()))
            return false;

        bibliotecas.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(juego);
        Transaccion t = new Transaccion(contadorTransacciones++, usuario, juego);
        historial.computeIfAbsent(usuario.getId(), k -> new ArrayList<>()).add(t);

        return true;

    }

    public synchronized List<Juego> getBiblioteca(int userId) {
        return new ArrayList<>(bibliotecas.getOrDefault(userId, new ArrayList<>()));
    }

    public synchronized List<Transaccion> getHistorial(int userId) {
        return new ArrayList<>(historial.getOrDefault(userId, new ArrayList<>()));
    }

}
