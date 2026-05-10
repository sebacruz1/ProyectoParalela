package modelos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Lobby implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Usuario host;
    private List<Usuario> jugadores;
    private String estado;

    public Lobby(int id, Usuario host) {
        this.id = id;
        this.host = host;
        this.jugadores = new ArrayList<>();
        this.jugadores.add(host);
        this.estado = "ESPERANDO";
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

    public String getEstado() {
        return estado;
    }

    public void agregarJugador(Usuario usuario) {
        jugadores.add(usuario);
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        String nombres = jugadores.stream()
                .map(Usuario::getUsername)
                .reduce((a, b) -> a + ", " + b)
                .orElse("vacío");
        return "[" + id + "] Host: " + host.getUsername() +
                " | Jugadores: " + jugadores.size() +
                " | Estado: " + estado +
                " | (" + nombres + ")";
    }
}
