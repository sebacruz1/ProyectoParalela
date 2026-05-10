package servidor;

import handlers.HandlerTienda;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorTienda {

    private static final int PUERTO = 6001;

    public static void main(String[] args) {
        System.out.println("[Tienda] Servidor iniciado en puerto " + PUERTO);

        try (ServerSocket serverSocket = new ServerSocket(PUERTO)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Tienda] Nueva conexión: " + socket.getInetAddress());
                new Thread(new HandlerTienda(socket)).start();
            }
        } catch (IOException e) {
            System.out.println("[Tienda] Error al iniciar servidor: " + e.getMessage());
        }
    }
}
