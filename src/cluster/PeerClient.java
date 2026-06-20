package cluster;

import protocolo.Mensaje;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class PeerClient {

    private static final long ESPERA_INICIAL_MS = 500;
    private static final long ESPERA_MAXIMA_MS = 5000;

    private final NodoConfig destino;
    private final Nodo nodo;
    private volatile ObjectOutputStream out;
    private volatile boolean conectado = false;

    public PeerClient(NodoConfig destino, Nodo nodo) {
        this.destino = destino;
        this.nodo = nodo;
        Thread hilo = new Thread(this::mantenerConexion, "peer-client-" + destino.getId());
        hilo.setDaemon(true);
        hilo.start();
    }

    public boolean isConectado() {
        return conectado;
    }

    public synchronized void enviar(Mensaje mensaje) {
        if (!conectado || out == null) {
            return;
        }
        try {
            out.writeObject(mensaje);
            out.flush();
        } catch (IOException e) {
            conectado = false;
        }
    }

    private void mantenerConexion() {
        long esperaMs = ESPERA_INICIAL_MS;
        while (true) {
            try (Socket socket = new Socket(destino.getHost(), destino.getPuertoPeer());
                    ObjectOutputStream salida = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream entrada = new ObjectInputStream(socket.getInputStream())) {

                this.out = salida;
                this.conectado = true;
                esperaMs = ESPERA_INICIAL_MS;
                nodo.getLogger().log(nodo.getClock().valorActual(), "Conectado al peer " + destino.getId());

                while (true) {
                    Mensaje mensaje = (Mensaje) entrada.readObject();
                    nodo.procesarMensaje(mensaje, salida);
                }
            } catch (IOException | ClassNotFoundException e) {
                conectado = false;
                out = null;
            }
            try {
                Thread.sleep(esperaMs);
            } catch (InterruptedException ignored) {
            }
            esperaMs = Math.min(esperaMs * 2, ESPERA_MAXIMA_MS);
        }
    }
}
