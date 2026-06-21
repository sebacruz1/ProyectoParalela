package cluster;

import protocolo.Mensaje;
import protocolo.TipoMensaje;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envía PING periódicos a cada peer y vigila los heartbeats recibidos para
 * detectar caídas: sospecha a los 5s sin respuesta, caída confirmada a los 8s.
 */
public class HeartbeatMonitor {

    private static final long INTERVALO_PING_MS = 2000;
    private static final long TIMEOUT_SOSPECHA_MS = 5000;
    private static final long TIMEOUT_CAIDA_MS = 8000;

    private final Nodo nodo;
    private final Map<Integer, PeerClient> peerClients;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public HeartbeatMonitor(Nodo nodo, Map<Integer, PeerClient> peerClients) {
        this.nodo = nodo;
        this.peerClients = peerClients;
    }

    public void iniciar() {
        scheduler.scheduleAtFixedRate(this::enviarPings, 0, INTERVALO_PING_MS, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::vigilarCaidas, 1000, 1000, TimeUnit.MILLISECONDS);
    }

    private void enviarPings() {
        Mensaje ping = new Mensaje(TipoMensaje.PING, nodo.getClock().tick(), nodo.getId(), null);
        for (PeerClient peerClient : peerClients.values()) {
            peerClient.enviar(ping);
        }
    }

    private void vigilarCaidas() {
        long ahora = System.currentTimeMillis();
        Membresia membresia = nodo.getMembresia();
        for (Integer peerId : membresia.idsTodos()) {
            if (!membresia.estaActivo(peerId)) {
                continue;
            }
            long inactividad = ahora - membresia.ultimoHeartbeat(peerId);
            if (inactividad >= TIMEOUT_CAIDA_MS) {
                membresia.marcarCaido(peerId);
                nodo.getLogger().log(nodo.getClock().valorActual(), "NODO CAIDO: " + peerId);
                if (nodo.getBully() != null && peerId == nodo.getBully().getCoordinadorId()) {
                    nodo.getLogger().log(nodo.getClock().valorActual(),
                            "Coordinador caído (" + peerId + "), disparando reelección");
                    nodo.getBully().iniciarEleccion();
                }
                if (nodo.getRicartAgrawala() != null) {
                    nodo.getRicartAgrawala().peerCaido(peerId);
                }
            } else if (inactividad >= TIMEOUT_SOSPECHA_MS) {
                if (membresia.marcarSospechosoSiCorresponde(peerId)) {
                    nodo.getLogger().log(nodo.getClock().valorActual(), "SOSPECHA caida nodo " + peerId);
                }
            }
        }
    }
}
