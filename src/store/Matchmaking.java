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

    /** El id se prefija con nodoId para que sea único en todo el cluster. */
    public synchronized Lobby crearLobby(Usuario host, Juego juego, int nodoId) {
        int id = nodoId * 1000 + contadorLobbies++;
        Lobby lobby = new Lobby(id, host, juego, nodoId);
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

    /** Devuelve true si el lobby efectivamente se eliminó (estaba vacío). */
    public synchronized boolean cerrarLobbySiVacio(int idLobby) {
        Lobby lobby = getLobby(idLobby);
        if (lobby == null) {
            return false;
        }
        if (!lobby.tieneConexionesActivas()) {
            lobbies.remove(lobby);
            return true;
        }
        return false;
    }

    /** Registra un lobby creado en otro nodo (vía LOBBY_CREADO), si no lo teníamos ya. */
    public synchronized void registrarLobbyRemoto(Lobby lobby) {
        boolean yaExiste = lobbies.stream().anyMatch(l -> l.getId() == lobby.getId());
        if (!yaExiste) {
            lobbies.add(lobby);
        }
    }

    /** Quita un lobby cerrado en su nodo dueño (vía LOBBY_CERRADO). Devuelve true si existía. */
    public synchronized boolean eliminarLobbyRemoto(int idLobby) {
        return lobbies.removeIf(l -> l.getId() == idLobby);
    }

    private Lobby getLobby(int id) {
        return lobbies.stream()
                .filter(l -> l.getId() == id)
                .findFirst()
                .orElse(null);
    }

}
