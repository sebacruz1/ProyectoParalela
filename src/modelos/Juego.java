package modelos;

import java.io.Serializable;

public class Juego implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String nombre;
    private int precio;
    private String descripcion;
    private boolean ofertaFlash;

    public Juego(int id, String nombre, int precio, String descripcion) {
        this(id, nombre, precio, descripcion, false);
    }

    public Juego(int id, String nombre, int precio, String descripcion, boolean ofertaFlash) {
        this.id = id;
        this.nombre = nombre;
        this.precio = precio;
        this.descripcion = descripcion;
        this.ofertaFlash = ofertaFlash;
    }

    public int getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public int getPrecio() {
        return precio;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public boolean isOfertaFlash() {
        return ofertaFlash;
    }

    @Override
    public String toString() {
        String precioStr = (precio == 0) ? "GRATIS" : "$" + String.format("%,d", precio);
        String flashStr = ofertaFlash ? " [OFERTA FLASH - stock limitado]" : "";
        return "[" + id + "] " + nombre + " - " + precioStr + flashStr;
    }

}
