package handlers;

import modelos.Lobby;
import modelos.Usuario;
import store.Matchmaking;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class HandlerMatchmaking implements Runnable {
    private Socket socket;
    private Matchmaking matchmaking;

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
                        // Crear Lobby

                        Lobby lobby = matchmaking.crearLobby(usuario);
                        out.writeObject(lobby);
                        out.flush();
                        iniciarChat(lobby, usuario, in, out);
                    }
                    case 3 -> {
                        int idLobby = (int) in.readObject();
                        Lobby lobby = matchmaking.unirse(idLobby, usuario);
                        if (lobby != null) {
                            out.writeObject("CONECTADO");
                            out.writeObject(lobby);
                            out.flush();
                            iniciarChat(lobby, usuario, in, out);
                        } else {
                            out.writeObject("ERROR AL CONECTARSE");
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

    private void iniciarChat(Lobby lobby, Usuario usuario, ObjectInputStream in, ObjectOutputStream out) {
        lobby.agregarConexion(out);
        lobby.broadcast("[" + usuario.getUsername() + " se ha conectado]");
        System.out.println("[Chat]" + usuario.getUsername() + " entró al lobby " + lobby.getId());

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
            lobby.broadcast("[" + usuario.getUsername() + "se desconectó]");
        } finally {
            lobby.eliminarConexion(out);
        }
    }
}
