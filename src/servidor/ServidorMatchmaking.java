package servidor;

import handlers.HandlerMatchmaking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorMatchmaking {

    private static final int PUERTO = 6002;

    public static void main(String[] args) {
        System.out.println("[Matchmaking] Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Matchmaking] Nueva conexión: " + socket.getInetAddress());
                new Thread(new HandlerMatchmaking(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("[Matchmaking] Error al iniciar servidor: " + e.getMessage());
        }
    }
}
