package modelos;

import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Lobby implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Usuario host;
    private Juego juego;
    private int nodoDueno;
    private final List<Usuario> jugadores;
    private transient List<ObjectOutputStream> conexiones = new ArrayList<>();

    public Lobby(int id, Usuario host, Juego juego, int nodoDueno) {
        this.id = id;
        this.host = host;
        this.juego = juego;
        this.nodoDueno = nodoDueno;
        this.jugadores = new ArrayList<>();
        this.jugadores.add(host);
    }

    public int getId() {
        return id;
    }

    public Usuario getHost() {
        return host;
    }

    public Juego getJuego() {
        return juego;
    }

    public int getNodoDueno() {
        return nodoDueno;
    }

    public synchronized List<Usuario> getJugadores() {
        return new ArrayList<>(jugadores);
    }

    public synchronized boolean agregarJugador(Usuario usuario) {
        boolean yaEsta = jugadores.stream().anyMatch(j -> j.getId() == usuario.getId());
        if (yaEsta) {
            return false;
        }
        jugadores.add(usuario);
        return true;
    }

    public synchronized void eliminarJugador(int userId) {
        jugadores.removeIf(j -> j.getId() == userId);
    }

    public synchronized void agregarConexion(ObjectOutputStream out) {
        if (conexiones == null)
            conexiones = new ArrayList<>();
        conexiones.add(out);
    }

    public synchronized void eliminarConexion(ObjectOutputStream out) {
        if (conexiones != null)
            conexiones.remove(out);
    }

    public synchronized boolean tieneConexionesActivas() {
        return conexiones != null && !conexiones.isEmpty();
    }

    public synchronized void broadcast(String mensaje) {
        if (conexiones == null)
            return;
        for (ObjectOutputStream out : conexiones) {
            try {

                out.writeObject(mensaje);
                out.flush();
            } catch (Exception e) {

            }
        }
    }

    @Override
    public synchronized String toString() {
        String nombres = jugadores.stream()
                .map(Usuario::getUsername)
                .reduce((a, b) -> a + ", " + b)
                .orElse("vacío");
        return "[" + id + "] Juego: " + juego.getNombre() +
                " | Host: " + host.getUsername() +
                " | Jugadores: " + jugadores.size() +
                " | Nodo: " + nodoDueno +
                " | (" + nombres + ")";
    }
}
