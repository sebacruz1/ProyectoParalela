package handlers;

import modelos.Juego;
import modelos.Lobby;
import modelos.Usuario;
import store.Matchmaking;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class HandlerMatchmaking implements Runnable {
    private Socket socket;
    private Matchmaking matchmaking;

    private static final String UNIDO_OK = "UNIDO_OK";
    private static final String UNIDO_ERROR = "UNIDO_ERROR";
    private static final String NO_TIENES_JUEGO = "NO_TIENES_JUEGO";

    public HandlerMatchmaking(Socket socket) {
        this.socket = socket;
        this.matchmaking = Matchmaking.getInstancia();
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Usuario usuario = (Usuario) in.readObject();
            System.out.println("[Matchmaking] Usuario conectado: " + usuario.getUsername());

            boolean activo = true;
            while (activo) {
                int opcion = (int) in.readObject();
                switch (opcion) {

                    case 1 -> {
                        // Ver lobbies
                        List<Lobby> lobbies = matchmaking.getLobbies();
                        out.writeObject(lobbies);
                        out.flush();
                    }

                    case 2 -> {
                        // Crear lobby
                        List<Juego> biblioteca = usuario.getBiblioteca();
                        out.writeObject(biblioteca);
                        out.flush();

                        if (biblioteca.isEmpty()) {
                            break;
                        }

                        int idJuego = (int) in.readObject();
                        Juego juego = biblioteca.stream()
                                .filter(j -> j.getId() == idJuego)
                                .findFirst()
                                .orElse(null);
                        if (juego == null) {
                            out.writeObject(NO_TIENES_JUEGO);
                            out.flush();
                            break;
                        }

                        Lobby lobby = matchmaking.crearLobby(usuario, juego);
                        out.writeObject(lobby);
                        out.flush();
                        iniciarChat(lobby, usuario, in, out);
                    }

                    case 3 -> {
                        List<Lobby> todos = matchmaking.getLobbies();
                        out.writeObject(todos);
                        out.flush();
                        if (todos.isEmpty()) {
                            break;
                        }

                        int idLobby = (int) in.readObject();
                        Lobby lobbyObjetivo = todos.stream()
                                .filter(l -> l.getId() == idLobby)
                                .findFirst().orElse(null);

                        if (lobbyObjetivo == null) {
                            out.writeObject(UNIDO_ERROR);
                            out.flush();
                            break;
                        }

                        int idJuegoRequerido = lobbyObjetivo.getJuego().getId();
                        List<Juego> biblioteca = usuario.getBiblioteca();
                        boolean loTiene = biblioteca.stream()
                                .anyMatch(j -> j.getId() == idJuegoRequerido);

                        if (!loTiene) {
                            out.writeObject(NO_TIENES_JUEGO);
                            out.flush();
                            break;
                        }

                        Lobby lobby = matchmaking.unirse(idLobby, usuario);
                        if (lobby != null) {
                            out.writeObject(UNIDO_OK);
                            out.writeObject(lobby);
                            out.flush();
                            iniciarChat(lobby, usuario, in, out);
                        } else {
                            out.writeObject(UNIDO_ERROR);
                            out.flush();
                        }
                    }

                    case 4 -> {
                        out.writeObject("VOLVER");
                        out.flush();
                        activo = false;
                    }

                    default -> {
                        out.writeObject("ERROR");
                        out.flush();
                    }
                }
            }
            System.out.println("[Matchmaking] Usuario desconectado: " + usuario.getUsername());

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[Matchmaking] Cliente desconectado inesperadamente");
        }
    }

    private void iniciarChat(Lobby lobby, Usuario usuario,
            ObjectInputStream in, ObjectOutputStream out) {
        lobby.agregarConexion(out);
        lobby.broadcast("[" + usuario.getUsername() + " se ha conectado al lobby de "
                + lobby.getJuego().getNombre() + "]");
        System.out.println("[Chat] " + usuario.getUsername() + " entró al lobby " + lobby.getId());
        try {
            while (true) {
                String mensaje = (String) in.readObject();
                if (mensaje.equalsIgnoreCase("/salir")) {
                    lobby.broadcast("[" + usuario.getUsername() + " salió del lobby]");
                    break;
                }
                lobby.broadcast("[" + usuario.getUsername() + "]: " + mensaje);
            }
        } catch (IOException | ClassNotFoundException e) {
            lobby.broadcast("[" + usuario.getUsername() + " se desconectó]");
        } finally {
            lobby.eliminarJugador(usuario.getId());
            lobby.eliminarConexion(out);
            matchmaking.cerrarLobbySiVacio(lobby.getId());
        }
    }
}
