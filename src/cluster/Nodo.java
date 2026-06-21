package cluster;

import coordinacion.Bully;
import coordinacion.LamportClock;
import coordinacion.NodoLogger;
import coordinacion.RicartAgrawala;
import handlers.HandlerCliente;
import handlers.HandlerPeer;
import modelos.Lobby;
import protocolo.EstadoSync;
import protocolo.Mensaje;
import protocolo.StockSync;
import protocolo.TipoMensaje;
import protocolo.TransaccionReplicada;
import store.Matchmaking;
import store.Tienda;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    private RicartAgrawala ricartAgrawala;
    private final CountDownLatch syncLatch = new CountDownLatch(1);

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

    public RicartAgrawala getRicartAgrawala() {
        return ricartAgrawala;
    }

    /**
     * Config de un peer por id (host/puertos), usado para armar la dirección de un
     * redirect.
     */
    public NodoConfig configDePeer(int peerId) {
        return peers.stream().filter(p -> p.getId() == peerId).findFirst().orElse(null);
    }

    // Envía un mensaje a todos los peers actualmente activos.
    public void broadcast(Mensaje mensaje) {
        for (int pid : membresia.idsActivos()) {
            PeerClient pc = peerClients.get(pid);
            if (pc != null) {
                pc.enviar(mensaje);
            }
        }
    }

    public void iniciar() throws IOException {
        logger.log(clock.valorActual(), "Iniciando nodo " + id
                + " (cliente=" + self.getPuertoCliente() + ", peer=" + self.getPuertoPeer() + ")");

        // Usado por scripts/matar_coordinador.sh para encontrar el PID de este nodo
        // a partir de su id, sin tener que adivinarlo entre los procesos java.
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("logs"));
            java.nio.file.Files.writeString(java.nio.file.Paths.get("logs/node" + id + ".pid"),
                    String.valueOf(ProcessHandle.current().pid()));
        } catch (IOException e) {
            logger.log(clock.valorActual(), "No se pudo escribir el archivo de PID: " + e.getMessage());
        }

        ServerSocket serverPeer = new ServerSocket(self.getPuertoPeer());
        new Thread(() -> aceptarPeers(serverPeer)).start();

        for (NodoConfig peer : peers) {
            if (peer.getId() != id) {
                peerClients.put(peer.getId(), new PeerClient(peer, this));
            }
        }

        this.bully = new Bully(this, peerClients);
        this.ricartAgrawala = new RicartAgrawala(this, peerClients);
        new HeartbeatMonitor(this, peerClients).iniciar();

        sincronizarEstadoBloqueante();

        ServerSocket serverCliente = new ServerSocket(self.getPuertoCliente());
        new Thread(() -> aceptarClientes(serverCliente)).start();

        Thread bootstrapEleccion = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
            }
            bully.iniciarEleccion();
        }, "bully-bootstrap");
        bootstrapEleccion.setDaemon(true);
        bootstrapEleccion.start();
    }

    private void sincronizarEstadoBloqueante() {
        long limite = System.currentTimeMillis() + 2000;
        int intentos = 0;
        while (System.currentTimeMillis() < limite && syncLatch.getCount() > 0) {
            for (int peerId : membresia.idsActivos()) {
                PeerClient pc = peerClients.get(peerId);
                if (pc != null && pc.isConectado()) {
                    intentos++;
                    logger.log(clock.valorActual(), "Pidiendo sincronización de estado a nodo " + peerId);
                    pc.enviar(new Mensaje(TipoMensaje.JOIN, clock.tick(), id, null));
                    break;
                }
            }
            try {
                syncLatch.await(400, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
        }
        if (syncLatch.getCount() > 0) {
            logger.log(clock.valorActual(), intentos == 0
                    ? "Sin peers activos al arrancar; se omite sincronización inicial"
                    : "Sincronización inicial agotó el tiempo de espera; se arranca con estado local");
        }
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
            case RA_REQUEST ->
                ricartAgrawala.recibirRequest(mensaje.getOrigenNodoId(), mensaje.getLamport(), canalRespuesta);
            case RA_REPLY -> ricartAgrawala.recibirReply(mensaje.getOrigenNodoId());
            case TX_COMMIT -> {
                if (mensaje.getPayload() instanceof StockSync sync) {
                    tienda.fijarStock(sync.getIdJuego(), sync.getNuevoStock());
                    logger.log(clock.valorActual(),
                            "Stock sincronizado: juego=" + sync.getIdJuego() + " stock=" + sync.getNuevoStock());
                } else if (mensaje.getPayload() instanceof TransaccionReplicada registro) {
                    boolean nueva = tienda.registrarEnLogGlobal(registro);
                    if (nueva) {
                        logger.log(clock.valorActual(), "TX replicada: " + registro.getTxId());
                    }
                }
            }
            case JOIN -> {
                EstadoSync snapshot = new EstadoSync(tienda.getStockSnapshot(), tienda.getLogGlobal());
                Mensaje respuesta = new Mensaje(TipoMensaje.SYNC_RESPONSE, clock.tick(), id, snapshot);
                synchronized (canalRespuesta) {
                    canalRespuesta.writeObject(respuesta);
                    canalRespuesta.flush();
                }
            }
            case SYNC_RESPONSE -> {
                if (mensaje.getPayload() instanceof EstadoSync snapshot) {
                    tienda.aplicarSnapshot(snapshot.getStockFlash(), snapshot.getLogGlobal());
                    logger.log(clock.valorActual(), "Estado sincronizado desde nodo " + mensaje.getOrigenNodoId()
                            + " (" + snapshot.getLogGlobal().size() + " tx, stock=" + snapshot.getStockFlash() + ")");
                }
                syncLatch.countDown();
            }
            case LOBBY_CREADO -> {
                if (mensaje.getPayload() instanceof Lobby lobby) {
                    matchmaking.registrarLobbyRemoto(lobby);
                    logger.log(clock.valorActual(),
                            "Lobby remoto registrado: " + lobby.getId() + " (nodo " + lobby.getNodoDueno() + ")");
                }
            }
            case LOBBY_CERRADO -> {
                if (mensaje.getPayload() instanceof Integer idLobby) {
                    boolean eliminado = matchmaking.eliminarLobbyRemoto(idLobby);
                    if (eliminado) {
                        logger.log(clock.valorActual(), "Lobby remoto eliminado: " + idLobby);
                    }
                }
            }
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
