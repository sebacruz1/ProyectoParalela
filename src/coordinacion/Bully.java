package coordinacion;

import cluster.Membresia;
import cluster.Nodo;
import cluster.PeerClient;
import protocolo.Mensaje;
import protocolo.TipoMensaje;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Elección de coordinador por el algoritmo Bully: gana el ID más alto entre
 * los nodos activos.
 */
public class Bully {

    private static final long TIMEOUT_OK_MS = 3000;
    private static final long TIMEOUT_COORDINATOR_MS = 5000;

    private final Nodo nodo;
    private final Map<Integer, PeerClient> peerClients;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Object lock = new Object();

    private volatile int coordinadorId = -1;
    private volatile boolean eleccionEnCurso = false;
    private volatile boolean okRecibido = false;
    private final AtomicLong mensajesEnviados = new AtomicLong();

    public Bully(Nodo nodo, Map<Integer, PeerClient> peerClients) {
        this.nodo = nodo;
        this.peerClients = peerClients;
    }

    public int getCoordinadorId() {
        return coordinadorId;
    }

    public long getMensajesEnviados() {
        return mensajesEnviados.get();
    }

    public void iniciarEleccion() {
        synchronized (lock) {
            if (eleccionEnCurso) {
                return;
            }
            eleccionEnCurso = true;
            okRecibido = false;
        }

        int miId = nodo.getId();
        Membresia membresia = nodo.getMembresia();
        List<Integer> superiores = membresia.idsActivos().stream()
                .filter(pid -> pid > miId)
                .collect(Collectors.toList());

        nodo.getLogger().log(nodo.getClock().valorActual(), "Iniciando elección (Bully)");

        if (superiores.isEmpty()) {
            declararseCoordinador();
            return;
        }

        Mensaje election = new Mensaje(TipoMensaje.ELECTION, nodo.getClock().tick(), miId, null);
        for (int pid : superiores) {
            PeerClient pc = peerClients.get(pid);
            if (pc != null) {
                pc.enviar(election);
                mensajesEnviados.incrementAndGet();
            }
        }

        scheduler.schedule(this::evaluarTimeoutOk, TIMEOUT_OK_MS, TimeUnit.MILLISECONDS);
    }

    private void evaluarTimeoutOk() {
        synchronized (lock) {
            if (!eleccionEnCurso) {
                return;
            }
            if (!okRecibido) {
                declararseCoordinador();
            } else {
                scheduler.schedule(this::evaluarTimeoutCoordinator, TIMEOUT_COORDINATOR_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void evaluarTimeoutCoordinator() {
        synchronized (lock) {
            if (eleccionEnCurso) {
                // Alguien superior dijo OK pero nunca anunció ser coordinador: reintentar.
                eleccionEnCurso = false;
                iniciarEleccion();
            }
        }
    }

    private void declararseCoordinador() {
        synchronized (lock) {
            coordinadorId = nodo.getId();
            eleccionEnCurso = false;
        }
        nodo.getLogger().log(nodo.getClock().valorActual(), "SOY COORDINADOR (" + nodo.getId() + ")");

        Mensaje anuncio = new Mensaje(TipoMensaje.COORDINATOR, nodo.getClock().tick(), nodo.getId(), null);
        for (int pid : nodo.getMembresia().idsActivos()) {
            PeerClient pc = peerClients.get(pid);
            if (pc != null) {
                pc.enviar(anuncio);
                mensajesEnviados.incrementAndGet();
            }
        }
    }

    public void recibirElection(int fromId, ObjectOutputStream canalRespuesta) throws IOException {
        int miId = nodo.getId();
        if (miId <= fromId) {
            return;
        }
        Mensaje ok = new Mensaje(TipoMensaje.OK, nodo.getClock().tick(), miId, null);
        synchronized (canalRespuesta) {
            canalRespuesta.writeObject(ok);
            canalRespuesta.flush();
        }
        mensajesEnviados.incrementAndGet();
        iniciarEleccion();
    }

    public void recibirOk(int fromId) {
        synchronized (lock) {
            okRecibido = true;
        }
    }

    public void recibirCoordinator(int leaderId) {
        synchronized (lock) {
            coordinadorId = leaderId;
            eleccionEnCurso = false;
        }
        nodo.getLogger().log(nodo.getClock().valorActual(), "NUEVO COORDINADOR: " + leaderId);
    }
}
