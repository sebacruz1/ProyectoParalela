package handlers;

import modelos.Juego;
import modelos.Usuario;
import store.Tienda;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class HandlerTienda implements Runnable {
    private Socket socket;
    private Tienda tienda;

    public HandlerTienda(Socket socket) {
        this.socket = socket;
        this.tienda = Tienda.getInstancia();
    }

    @Override
    public void run() {
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            Usuario usuario = (Usuario) in.readObject();
            System.out.println("[Tienda] Usuario conectado: " usuario.getUsername());

            boolean activo = true;

            while (activo){
                int opcion = ( int ) in.readObject();
                switch (opcion) {
                    case 1 -> {
                        // Ver catalogo
                        List<Juego> catalogo = tienda.getCatalogo();
                        out.writeObject(catalogo);
                        out.flush();
                    }
                    case 2 -> {
                        // COmprar juego
                        int idJuego = (int) in.readObject();
                        boolean exito = tienda.comprar(usuario, idJuego);
                        out.writeObject(exito ? "COMPRA REALIZADA" : "ERROR EN LA COMPRA");
                        out.flush();
                    }
                    case 3 -> {
                        List<Juego> biblioteca = tienda.getBiblioteca(usuario.getId());
                        out.writeObject(biblioteca);
                        out.flush();

                    }
                    case 4 -> {
                        // Cargar saldo
                        int monto = (int) in.readObject();
                        usuario.cargarSaldo(monto);
                        out.writeObject("SALDO CARGADO");
                        out.writeObject(usuario);
                        out.flush();
                    }
                    case 5 -> {
                        out.writeObject("VOLVER");
                        out.flush();
                        activo = false;
                    }
                    default -> {
                        out.writeObject("ERROR");
                        out.flush();
                    }
                }
            }
            System.out.println("[TIENDA] Usuario desconectado: " + usuario.getUsername());
        } catch (IOException | ClassNotFoundException e){
            System.out.println("[Tienda] Cliente desconectado inesperadamente");
        }

    }
}
