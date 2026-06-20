package cluster;

import coordinacion.Bully;
import coordinacion.LamportClock;
import coordinacion.NodoLogger;
import handlers.HandlerCliente;
import handlers.HandlerPeer;
import protocolo.Mensaje;
import protocolo.TipoMensaje;
import store.Matchmaking;
import store.Tienda;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Un nodo del cluster EpicGames: sirve clientes (login + Tienda + Matchmaking)
 */
public class Nodo {

    private final int id;
    private final List<NodoConfig> peers;
    private final NodoConfig self;
    private final LamportClock clock = new LamportClock();
    private final NodoLogger logger;
    private final Tienda tienda = Tienda.getInstancia();
    private final Matchmaking matchmaking = Matchmaking.getInstancia();
    private final Membresia membresia;
    private final Map<Integer, PeerClient> peerClients = new HashMap<>();
    private Bully bully;

    public Nodo(int id, List<NodoConfig> peers) {
        this.id = id;
        this.peers = peers;
        this.self = peers.stream()
                .filter(p -> p.getId() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "El nodo " + id + " no está en la configuración de peers"));
        this.logger = new NodoLogger(id);
        this.membresia = new Membresia(peers, id);
    }

    public int getId() {
        return id;
    }

    public LamportClock getClock() {
        return clock;
    }

    public NodoLogger getLogger() {
        return logger;
    }

    public Tienda getTienda() {
        return tienda;
    }

    public Matchmaking getMatchmaking() {
        return matchmaking;
    }

    public List<NodoConfig> getPeers() {
        return peers;
    }

    public Membresia getMembresia() {
        return membresia;
    }

    public Bully getBully() {
        return bully;
    }

    public void iniciar() throws IOException {
        logger.log(clock.valorActual(), "Iniciando nodo " + id
                + " (cliente=" + self.getPuertoCliente() + ", peer=" + self.getPuertoPeer() + ")");

        ServerSocket serverCliente = new ServerSocket(self.getPuertoCliente());
        ServerSocket serverPeer = new ServerSocket(self.getPuertoPeer());

        new Thread(() -> aceptarClientes(serverCliente)).start();
        new Thread(() -> aceptarPeers(serverPeer)).start();

        for (NodoConfig peer : peers) {
            if (peer.getId() != id) {
                peerClients.put(peer.getId(), new PeerClient(peer, this));
            }
        }

        this.bully = new Bully(this, peerClients);
        new HeartbeatMonitor(this, peerClients).iniciar();

        // Dar un margen para que los PeerClient terminen de conectar
        Thread bootstrapEleccion = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException ignored) {
            }
            bully.iniciarEleccion();
        }, "bully-bootstrap");
        bootstrapEleccion.setDaemon(true);
        bootstrapEleccion.start();
    }

    public void procesarMensaje(Mensaje mensaje, ObjectOutputStream canalRespuesta) throws IOException {
        clock.observe(mensaje.getLamport());
        switch (mensaje.getTipo()) {
            case PING -> {
                membresia.marcarVivo(mensaje.getOrigenNodoId());
                Mensaje pong = new Mensaje(TipoMensaje.PONG, clock.tick(), id, null);
                synchronized (canalRespuesta) {
                    canalRespuesta.writeObject(pong);
                    canalRespuesta.flush();
                }
            }
            case PONG -> membresia.marcarVivo(mensaje.getOrigenNodoId());
            case ELECTION -> bully.recibirElection(mensaje.getOrigenNodoId(), canalRespuesta);
            case OK -> bully.recibirOk(mensaje.getOrigenNodoId());
            case COORDINATOR -> bully.recibirCoordinator(mensaje.getOrigenNodoId());
            default -> logger.log(clock.valorActual(), "Mensaje sin manejar aún: " + mensaje);
        }
    }

    private void aceptarClientes(ServerSocket serverSocket) {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new HandlerCliente(socket, this)).start();
            } catch (IOException e) {
                logger.log(clock.valorActual(), "Error aceptando cliente: " + e.getMessage());
            }
        }
    }

    private void aceptarPeers(ServerSocket serverSocket) {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                new Thread(new HandlerPeer(socket, this)).start();
            } catch (IOException e) {
                logger.log(clock.valorActual(), "Error aceptando peer: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: java cluster.Nodo <id> <peersConfigPath>");
            return;
        }
        int id = Integer.parseInt(args[0]);
        String peersPath = args[1];
        List<NodoConfig> peers = NodoConfig.cargarDesde(peersPath);
        Nodo nodo = new Nodo(id, peers);
        try {
            nodo.iniciar();
        } catch (IOException e) {
            System.out.println("[Nodo " + id + "] Error al iniciar: " + e.getMessage());
        }
    }
}
