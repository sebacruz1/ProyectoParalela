package modelos;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private boolean sesionActiva;
    private int saldo;
    private List<Juego> biblioteca;

    public Usuario(int id, String username) {
        this.id = id;
        this.username = username;
        this.sesionActiva = false;
        this.saldo = 0;
        this.biblioteca = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isSesionActiva() {
        return sesionActiva;
    }

    public int getSaldo() {
        return saldo;
    }

    public List<Juego> getBiblioteca() {
        return new ArrayList<>(biblioteca);
    }

    public void setSesionActiva(boolean sesionActiva) {
        this.sesionActiva = true;
    }

    public void cargarSaldo(int monto) {
        this.saldo += monto;
    }

    public boolean comprar(int precio) {
        if (saldo >= precio) {
            saldo -= precio;
            return true;
        }

        return false;
    }

    public void agregarJuego(Juego juego) {
        biblioteca.add(juego);
    }

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", username'" + username + "', Estado = " + sesionActiva + "}";
    }
}
