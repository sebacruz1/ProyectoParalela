package handlers;

import cluster.Nodo;
import coordinacion.RicartAgrawala;
import modelos.Juego;
import modelos.Lobby;
import modelos.Transaccion;
import modelos.Usuario;
import protocolo.Mensaje;
import protocolo.StockSync;
import protocolo.TipoMensaje;
import protocolo.TransaccionReplicada;
import store.Matchmaking;
import store.Tienda;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HandlerCliente implements Runnable {

    private static final String COMPRA_OK = "COMPRA_OK";
    private static final String COMPRA_ERROR = "COMPRA_ERROR";
    private static final String SALDO_OK = "SALDO_OK";
    private static final String UNIDO_OK = "UNIDO_OK";
    private static final String UNIDO_ERROR = "UNIDO_ERROR";
    private static final String NO_TIENES_JUEGO = "NO_TIENES_JUEGO";

    private static final AtomicInteger contadorUsuarios = new AtomicInteger(0);

    private final Socket socket;
    private final Nodo nodo;
    private final Tienda tienda;
    private final Matchmaking matchmaking;

    public HandlerCliente(Socket socket, Nodo nodo) {
        this.socket = socket;
        this.nodo = nodo;
        this.tienda = nodo.getTienda();
        this.matchmaking = nodo.getMatchmaking();
    }

    @Override
    public void run() {
        String username = null;
        try (
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            username = (String) in.readObject();
            Usuario usuario = new Usuario(contadorUsuarios.getAndIncrement(), username);
            usuario.setSesionActiva(true);
            out.writeObject(usuario);
            out.flush();

            nodo.getLogger().log(nodo.getClock().valorActual(), "Usuario conectado: " + username);

            boolean activo = true;
            while (activo) {
                int opcion = (int) in.readObject();
                switch (opcion) {
                    case 1 -> correrTienda(usuario, in, out);
                    case 2 -> correrMatchmaking(usuario, in, out);
                    case 3 -> {
                        out.writeObject("SALIR");
                        out.flush();
                        activo = false;
                    }
                    default -> {
                        out.writeObject("ERROR");
                        out.flush();
                    }
                }
            }
            nodo.getLogger().log(nodo.getClock().valorActual(), "Usuario desconectado: " + username);

        } catch (IOException | ClassNotFoundException e) {
            nodo.getLogger().log(nodo.getClock().valorActual(),
                    "Cliente desconectado inesperadamente" + (username != null ? " (" + username + ")" : ""));
        }
    }

    // TIENDA
    private void correrTienda(Usuario usuario, ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {
        boolean activo = true;
        while (activo) {
            int opcion = (int) in.readObject();
            switch (opcion) {
                case 1 -> {
                    List<Juego> catalogo = tienda.getCatalogo();
                    out.writeObject(catalogo);
                    out.flush();
                }
                case 2 -> {
                    List<Juego> catalogo = tienda.getCatalogo();
                    out.writeObject(catalogo);
                    out.flush();
                    if (catalogo.isEmpty()) {
                        break;
                    }
                    int idJuego = (int) in.readObject();
                    Juego juego = tienda.getJuego(idJuego);
                    boolean exito = (juego != null && juego.isOfertaFlash())
                            ? comprarFlash(usuario, idJuego)
                            : comprarNormal(usuario, idJuego);
                    out.writeObject(exito ? COMPRA_OK : COMPRA_ERROR);
                    out.reset();
                    out.writeObject(usuario);
                    out.flush();
                }
                case 3 -> {
                    List<Juego> biblioteca = usuario.getBiblioteca();
                    out.writeObject(biblioteca);
                    out.flush();
                }
                case 4 -> {
                    int monto = (int) in.readObject();
                    usuario.cargarSaldo(monto);
                    out.writeObject(SALDO_OK);
                    out.reset();
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
    }

    /** Compra de un ítem normal: se replica al log global de transacciones (sin mutex, sin recurso escaso). */
    private boolean comprarNormal(Usuario usuario, int idJuego) {
        Transaccion t = tienda.comprar(usuario, idJuego);
        if (t == null) {
            nodo.getLogger().log(nodo.getClock().valorActual(),
                    "Compra RECHAZADA: juego=" + idJuego + " usuario=" + usuario.getUsername());
            return false;
        }
        difundirTransaccion(t, idJuego, usuario);
        return true;
    }

    /**
     * Compra de un ítem de oferta flash: el stock es un recurso compartido entre
     * nodos, así que solo se puede decrementar dentro de la sección crítica que
     * otorga Ricart-Agrawala. Al salir con éxito, se difunde el nuevo stock y la
     * transacción a los demás nodos para que todos converjan al mismo valor.
     */
    private boolean comprarFlash(Usuario usuario, int idJuego) {
        RicartAgrawala ra = nodo.getRicartAgrawala();
        try {
            ra.solicitarSeccionCritica();
            Transaccion t = tienda.comprarFlash(usuario, idJuego);
            if (t == null) {
                nodo.getLogger().log(nodo.getClock().valorActual(),
                        "Compra flash RECHAZADA: juego=" + idJuego + " usuario=" + usuario.getUsername());
                return false;
            }
            int nuevoStock = tienda.getStock(idJuego);
            Mensaje sync = new Mensaje(TipoMensaje.TX_COMMIT, nodo.getClock().tick(), nodo.getId(),
                    new StockSync(idJuego, nuevoStock));
            nodo.broadcast(sync);
            nodo.getLogger().log(nodo.getClock().valorActual(),
                    "Compra flash OK: juego=" + idJuego + " usuario=" + usuario.getUsername()
                            + " stock_restante=" + nuevoStock);
            difundirTransaccion(t, idJuego, usuario);
            return true;
        } finally {
            ra.liberarSeccionCritica();
        }
    }

    /** Agrega la transacción al log replicado local y la difunde a los demás nodos. */
    private void difundirTransaccion(Transaccion t, int idJuego, Usuario usuario) {
        String txId = nodo.getId() + ":" + t.getId();
        int ts = nodo.getClock().tick();
        TransaccionReplicada registro = new TransaccionReplicada(txId, t, ts, nodo.getId());
        tienda.registrarEnLogGlobal(registro);
        nodo.broadcast(new Mensaje(TipoMensaje.TX_COMMIT, ts, nodo.getId(), registro));
        nodo.getLogger().log(nodo.getClock().valorActual(),
                "TX commit: " + txId + " juego=" + idJuego + " usuario=" + usuario.getUsername());
    }

    // MATCHMAKING
    private void correrMatchmaking(Usuario usuario, ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {
        boolean activo = true;
        while (activo) {
            int opcion = (int) in.readObject();
            switch (opcion) {
                case 1 -> {
                    List<Lobby> lobbies = matchmaking.getLobbies();
                    out.writeObject(lobbies);
                    out.flush();
                }
                case 2 -> {
                    List<Juego> biblioteca = usuario.getBiblioteca();
                    out.writeObject(biblioteca);
                    out.flush();
                    if (biblioteca.isEmpty()) {
                        break;
                    }
                    int idJuego = (int) in.readObject();
                    Juego juego = biblioteca.stream()
                            .filter(j -> j.getId() == idJuego)
                            .findFirst()
                            .orElse(null);
                    if (juego == null) {
                        out.writeObject(NO_TIENES_JUEGO);
                        out.flush();
                        break;
                    }
                    Lobby lobby = matchmaking.crearLobby(usuario, juego);
                    out.writeObject(lobby);
                    out.flush();
                    iniciarChat(lobby, usuario, in, out);
                    // El chat es bloqueante hasta /salir o desconexión
                    activo = false;
                }
                case 3 -> {
                    List<Lobby> todos = matchmaking.getLobbies();
                    out.writeObject(todos);
                    out.flush();
                    if (todos.isEmpty()) {
                        break;
                    }
                    int idLobby = (int) in.readObject();
                    Lobby lobbyObjetivo = todos.stream()
                            .filter(l -> l.getId() == idLobby)
                            .findFirst().orElse(null);

                    if (lobbyObjetivo == null) {
                        out.writeObject(UNIDO_ERROR);
                        out.flush();
                        break;
                    }

                    int idJuegoRequerido = lobbyObjetivo.getJuego().getId();
                    List<Juego> biblioteca = usuario.getBiblioteca();
                    boolean loTiene = biblioteca.stream()
                            .anyMatch(j -> j.getId() == idJuegoRequerido);

                    if (!loTiene) {
                        out.writeObject(NO_TIENES_JUEGO);
                        out.flush();
                        break;
                    }

                    Lobby lobby = matchmaking.unirse(idLobby, usuario);
                    if (lobby != null) {
                        out.writeObject(UNIDO_OK);
                        out.writeObject(lobby);
                        out.flush();
                        iniciarChat(lobby, usuario, in, out);
                        activo = false;
                    } else {
                        out.writeObject(UNIDO_ERROR);
                        out.flush();
                    }
                }
                case 4 -> {
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
    }

    private void iniciarChat(Lobby lobby, Usuario usuario, ObjectInputStream in, ObjectOutputStream out) {
        lobby.agregarConexion(out);
        lobby.broadcast("[" + usuario.getUsername() + " se ha conectado al lobby de "
                + lobby.getJuego().getNombre() + "]");
        nodo.getLogger().log(nodo.getClock().valorActual(),
                "Chat: " + usuario.getUsername() + " entró al lobby " + lobby.getId());
        try {
            while (true) {
                String mensaje = (String) in.readObject();
                if (mensaje.equalsIgnoreCase("/salir")) {
                    lobby.broadcast("[" + usuario.getUsername() + " salió del lobby]");
                    break;
                }
                lobby.broadcast("[" + usuario.getUsername() + "]: " + mensaje);
            }
        } catch (IOException | ClassNotFoundException e) {
            lobby.broadcast("[" + usuario.getUsername() + " se desconectó]");
        } finally {
            lobby.eliminarJugador(usuario.getId());
            lobby.eliminarConexion(out);
            matchmaking.cerrarLobbySiVacio(lobby.getId());
        }
    }
}
