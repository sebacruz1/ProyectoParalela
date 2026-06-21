package coordinacion;

import cluster.Nodo;
import cluster.PeerClient;
import protocolo.Mensaje;
import protocolo.TipoMensaje;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// Exclusión mutua distribuida (Ricart-Agrawala) sobre el stock de un ítem
public class RicartAgrawala {

    private enum Estado {
        RELEASED, WANTED, HELD
    }

    private final Nodo nodo;
    private final Map<Integer, PeerClient> peerClients;
    private final Object lock = new Object();
    private final Lock entradaLocal = new ReentrantLock();

    private Estado estado = Estado.RELEASED;
    private int miTimestamp = -1;
    private final Set<Integer> respuestasPendientes = new HashSet<>();
    private final Set<Integer> colaDiferida = new HashSet<>();
    private final AtomicLong mensajesEnviados = new AtomicLong();

    public RicartAgrawala(Nodo nodo, Map<Integer, PeerClient> peerClients) {
        this.nodo = nodo;
        this.peerClients = peerClients;
    }

    /**
     * Cantidad de mensajes RA_REQUEST/RA_REPLY enviados por este nodo (métrica de
     * carga).
     */
    public long getMensajesEnviados() {
        return mensajesEnviados.get();
    }

    // Bloquea el hilo llamante hasta obtener la sección crítica
    public void solicitarSeccionCritica() {
        entradaLocal.lock();
        int miId = nodo.getId();
        Set<Integer> destinatarios;
        int ts;
        synchronized (lock) {
            estado = Estado.WANTED;
            miTimestamp = nodo.getClock().tick();
            ts = miTimestamp;
            destinatarios = new HashSet<>(nodo.getMembresia().idsActivos());
            respuestasPendientes.clear();
            respuestasPendientes.addAll(destinatarios);
        }

        nodo.getLogger().log(nodo.getClock().valorActual(), "RA: solicitando sección crítica, ts=" + ts);

        if (!destinatarios.isEmpty()) {
            Mensaje request = new Mensaje(TipoMensaje.RA_REQUEST, ts, miId, null);
            for (int pid : destinatarios) {
                PeerClient pc = peerClients.get(pid);
                if (pc != null) {
                    pc.enviar(request);
                    mensajesEnviados.incrementAndGet();
                }
            }
        }

        synchronized (lock) {
            while (!respuestasPendientes.isEmpty()) {
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                }
            }
            estado = Estado.HELD;
        }
        nodo.getLogger().log(nodo.getClock().valorActual(), "RA: entrando a sección crítica");
    }

    public void liberarSeccionCritica() {
        try {
            Set<Integer> aLiberar;
            synchronized (lock) {
                estado = Estado.RELEASED;
                aLiberar = new HashSet<>(colaDiferida);
                colaDiferida.clear();
            }
            nodo.getLogger().log(nodo.getClock().valorActual(), "RA: saliendo de sección crítica");

            for (int pid : aLiberar) {
                PeerClient pc = peerClients.get(pid);
                if (pc != null) {
                    pc.enviar(new Mensaje(TipoMensaje.RA_REPLY, nodo.getClock().tick(), nodo.getId(), null));
                    mensajesEnviados.incrementAndGet();
                }
            }
        } finally {
            entradaLocal.unlock();
        }
    }

    public void recibirRequest(int fromId, int fromTs, ObjectOutputStream canalRespuesta) throws IOException {
        boolean responderYa;
        synchronized (lock) {
            if (estado == Estado.HELD) {
                responderYa = false;
            } else if (estado == Estado.RELEASED) {
                responderYa = true;
            } else {
                // WANTED: gana la solicitud con menor (timestamp, id) - desempate por ID.
                responderYa = (fromTs < miTimestamp) || (fromTs == miTimestamp && fromId < nodo.getId());
            }
            if (!responderYa) {
                colaDiferida.add(fromId);
            }
        }

        if (responderYa) {
            Mensaje reply = new Mensaje(TipoMensaje.RA_REPLY, nodo.getClock().tick(), nodo.getId(), null);
            synchronized (canalRespuesta) {
                canalRespuesta.writeObject(reply);
                canalRespuesta.flush();
            }
            mensajesEnviados.incrementAndGet();
        }
    }

    public void recibirReply(int fromId) {
        synchronized (lock) {
            if (respuestasPendientes.remove(fromId) && respuestasPendientes.isEmpty()) {
                lock.notifyAll();
            }
        }
    }

    // Llamado por HeartbeatMonitor cuando un peer se marca caído
    public void peerCaido(int peerId) {
        synchronized (lock) {
            colaDiferida.remove(peerId);
            if (respuestasPendientes.remove(peerId) && respuestasPendientes.isEmpty()) {
                lock.notifyAll();
            }
        }
    }
}
