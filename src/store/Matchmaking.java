package store;

import modelos.Lobby;
import modelos.Usuario;
import modelos.Juego;

import java.util.ArrayList;
import java.util.List;

public class Matchmaking {
    private static Matchmaking instancia;

    private List<Lobby> lobbies;
    private int contadorLobbies;

    private Matchmaking() {
        lobbies = new ArrayList<>();
        contadorLobbies = 1;
    }

    public static synchronized Matchmaking getInstancia() {
        if (instancia == null)
            instancia = new Matchmaking();
        return instancia;

    }

    public synchronized List<Lobby> getLobbies() {
        return new ArrayList<>(lobbies);
    }

    public synchronized Lobby crearLobby(Usuario host, Juego juego) {
        Lobby lobby = new Lobby(contadorLobbies++, host, juego);
        lobbies.add(lobby);
        return lobby;
    }

    public synchronized Lobby unirse(int idLobby, Usuario usuario) {
        Lobby lobby = getLobby(idLobby);
        if (lobby == null)
            return null;
        boolean agregado = lobby.agregarJugador(usuario);
        if (!agregado)
            return null;
        return lobby;
    }

    public synchronized void cerrarLobbySiVacio(int idLobby) {
        Lobby lobby = getLobby(idLobby);
        if (lobby == null) {
            return;
        }
        if (!lobby.tieneConexionesActivas()) {
            lobbies.remove(lobby);
        }
    }

    private Lobby getLobby(int id) {
        return lobbies.stream()
                .filter(l -> l.getId() == id)
                .findFirst()
                .orElse(null);
    }

}
