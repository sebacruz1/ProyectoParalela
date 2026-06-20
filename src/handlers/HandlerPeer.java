package handlers;

import cluster.Nodo;
import protocolo.Mensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class HandlerPeer implements Runnable {

    private final Socket socket;
    private final Nodo nodo;

    public HandlerPeer(Socket socket, Nodo nodo) {
        this.socket = socket;
        this.nodo = nodo;
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensaje mensaje = (Mensaje) in.readObject();
            nodo.getClock().observe(mensaje.getLamport());
            nodo.getLogger().log(nodo.getClock().valorActual(), "Peer recibido (sin despachar aún): " + mensaje);
        } catch (IOException | ClassNotFoundException e) {
            nodo.getLogger().log(nodo.getClock().valorActual(), "Conexión de peer cerrada: " + e.getMessage());
        }
    }
}
