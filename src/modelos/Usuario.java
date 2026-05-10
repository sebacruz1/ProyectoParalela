package modelos;

import java.io.Serializable;

public class Usuario implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String username;
    private boolean sesionActiva;
    private int saldo;

    public Usuario(int id, String username) {
        this.id = id;
        this.username = username;
        this.sesionActiva = false;
        this.saldo = 0;
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

    @Override
    public String toString() {
        return "Usuario{id=" + id + ", username'" + username + "', Estado = " + sesionActiva + "}";
    }
}
