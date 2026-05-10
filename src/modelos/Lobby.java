package modelos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Lobby implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Usuario host;
    private List<Usuario> jugadores;

    public Lobby(int id, Usuario host) {
        this.id = id;
        this.host = host;
        this.jugadores = new ArrayList<>();
        this.jugadores.add(host);
    }

    public int getId() {
        return id;
    }

    public Usuario getHost() {
        return host;
    }

    public List<Usuario> getJugadores() {
        return jugadores;
    }

    public void agregarJugador(Usuario usuario) {
        jugadores.add(usuario);
    }

    @Override
    public String toString() {
        String nombres = jugadores.stream()
                .map(Usuario::getUsername)
                .reduce((a, b) -> a + ", " + b)
                .orElse("vacío");
        return "[" + id + "] Host: " + host.getUsername() +
                " | Jugadores: " + jugadores.size() +
                " | (" + nombres + ")";
    }
}
