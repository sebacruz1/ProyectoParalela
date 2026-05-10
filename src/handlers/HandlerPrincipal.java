package handlers;

import modelos.Usuario;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

public class HandlerPrincipal implements Runnable {
    private Socket socket;
    private static final AtomicInteger contadorUsuarios = new AtomicInteger(0);

    public HandlerPrincipal(Socket socket) {
        this.socket = socket;

    }

    @Override
    public void run() {
        try (
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());) {
            String username = (String) in.readObject();
            Usuario usuario = new Usuario(contadorUsuarios.getAndIncrement(), username);
            usuario.setSesionActiva(true);

            out.writeObject(usuario);
            out.flush();

            System.out.println("[Principal] Usuario conectado: " + username);

            boolean activo = true;
            while (activo) {
                int opcion = (int) in.readObject();
                switch (opcion) {
                    case 1 -> out.writeObject("TIENDA");
                    case 2 -> out.writeObject("MATCHMAKING");
                    case 3 -> {
                        out.writeObject("SALIR");
                        activo = false;
                    }
                    default -> out.writeObject("ERROR");
                }
                out.flush();
            }
            System.out.println("[Principal] Usuario desconectado " + username);

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("[Principal] Cliente desconectado inesperadamente");
        }
    }

}
