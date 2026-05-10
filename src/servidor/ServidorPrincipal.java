package servidor;

import handlers.HandlerPrincipal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorPrincipal {

    private static final int PUERTO = 6000;

    public static void main(String[] args) {
        System.out.println("[Principal] Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Principal] Nueva conexión: " + socket.getInetAddress());
                new Thread(new HandlerPrincipal(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("[Principal] Error al iniciar servidor: " + e.getMessage());
        }
    }
}
