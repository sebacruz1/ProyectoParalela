package cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
    Vista local de la membresía del cluster: qué peers están activos y cuándo
 */
public class Membresia {

    private static class EstadoPeer {
        final NodoConfig config;
        volatile long ultimoHeartbeat = System.currentTimeMillis();
        volatile boolean activo = true;
        volatile boolean sospechoso = false;

        EstadoPeer(NodoConfig config) {
            this.config = config;
        }
    }

    private final Map<Integer, EstadoPeer> estados = new ConcurrentHashMap<>();

    public Membresia(List<NodoConfig> peers, int selfId) {
        for (NodoConfig p : peers) {
            if (p.getId() != selfId) {
                estados.put(p.getId(), new EstadoPeer(p));
            }
        }
    }

    public void marcarVivo(int peerId) {
        EstadoPeer e = estados.get(peerId);
        if (e != null) {
            e.ultimoHeartbeat = System.currentTimeMillis();
            e.activo = true;
            e.sospechoso = false;
        }
    }

    public boolean marcarSospechosoSiCorresponde(int peerId) {
        EstadoPeer e = estados.get(peerId);
        if (e == null || e.sospechoso) {
            return false;
        }
        e.sospechoso = true;
        return true;
    }

    public void marcarCaido(int peerId) {
        EstadoPeer e = estados.get(peerId);
        if (e != null) {
            e.activo = false;
        }
    }

    public boolean estaActivo(int peerId) {
        EstadoPeer e = estados.get(peerId);
        return e != null && e.activo;
    }

    public long ultimoHeartbeat(int peerId) {
        EstadoPeer e = estados.get(peerId);
        return e != null ? e.ultimoHeartbeat : 0;
    }

    public List<Integer> idsActivos() {
        return estados.values().stream()
                .filter(e -> e.activo)
                .map(e -> e.config.getId())
                .collect(Collectors.toList());
    }

    public List<Integer> idsTodos() {
        return new ArrayList<>(estados.keySet());
    }

    public NodoConfig configDe(int peerId) {
        EstadoPeer e = estados.get(peerId);
        return e != null ? e.config : null;
    }
}
