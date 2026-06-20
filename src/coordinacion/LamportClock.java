package coordinacion;

/*
    Reloj lógico de Lamport. tick() marca un evento local/saliente;
 */
public class LamportClock {

    private int reloj = 0;

    public synchronized int tick() {
        reloj++;
        return reloj;
    }

    public synchronized int observe(int recibido) {
        reloj = Math.max(reloj, recibido) + 1;
        return reloj;
    }

    public synchronized int valorActual() {
        return reloj;
    }
}
