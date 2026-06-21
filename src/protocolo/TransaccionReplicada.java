package protocolo;

import modelos.Transaccion;

import java.io.Serializable;

public class TransaccionReplicada implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String txId;
    private final Transaccion transaccion;
    private final int lamport;
    private final int nodoOrigen;

    public TransaccionReplicada(String txId, Transaccion transaccion, int lamport, int nodoOrigen) {
        this.txId = txId;
        this.transaccion = transaccion;
        this.lamport = lamport;
        this.nodoOrigen = nodoOrigen;
    }

    public String getTxId() {
        return txId;
    }

    public Transaccion getTransaccion() {
        return transaccion;
    }

    public int getLamport() {
        return lamport;
    }

    public int getNodoOrigen() {
        return nodoOrigen;
    }

    @Override
    public String toString() {
        return "TransaccionReplicada{" + txId + ", lamport=" + lamport + ", origen=" + nodoOrigen
                + ", " + transaccion + "}";
    }
}
