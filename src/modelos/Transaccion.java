package modelos;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Transaccion implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private Usuario usuario;
    private Juego juego;
    private String timestamp;
    private String estado;

    public Transaccion(int id, Usuario usuario, Juego juego) {
        this.id = id;
        this.usuario = usuario;
        this.juego = juego;
        this.timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        this.estado = "COMPLETADA";
    }

    public int getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Juego getJuego() {
        return juego;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getEstado() {
        return estado;
    }

    @Override
    public String toString() {
        return "[" + id + "] " + juego.getNombre() + " - " + timestamp + " - " + estado;
    }
}
