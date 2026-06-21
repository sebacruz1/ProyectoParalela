package handlers;

import carga.MetricasNodo;
import cluster.Nodo;
import cluster.NodoConfig;
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

            // Login normal: llega un username (String) y se crea un Usuario nuevo.
            // Sesión redirigida desde otro nodo (ver "unirse a lobby"): llega el
            // Usuario ya autenticado tal cual lo tiene el cliente, para no perder
            // su biblioteca/saldo de sesión al cambiar de nodo solo para el chat.
            Object primerMensaje = in.readObject();
            Usuario usuario;
            if (primerMensaje instanceof Usuario u) {
                usuario = u;
                username = usuario.getUsername();
                nodo.getLogger().log(nodo.getClock().valorActual(), "Sesión retomada (redirect): " + username);
            } else {
                username = (String) primerMensaje;
                int idGlobal = nodo.getId() * 100_000 + contadorUsuarios.getAndIncrement();
                usuario = new Usuario(idGlobal, username);
                usuario.setSesionActiva(true);
                nodo.getLogger().log(nodo.getClock().valorActual(), "Usuario conectado: " + username);
            }
            out.writeObject(usuario);
            out.flush();

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
                    case 9 -> {
                        long bullyMsgs = nodo.getBully() != null ? nodo.getBully().getMensajesEnviados() : 0;
                        long raMsgs = nodo.getRicartAgrawala() != null ? nodo.getRicartAgrawala().getMensajesEnviados()
                                : 0;
                        out.writeObject(new MetricasNodo(nodo.getId(), bullyMsgs, raMsgs));
                        out.flush();
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

    /**
     * Compra de un ítem normal: se replica al log global de transacciones (sin
     * mutex, sin recurso escaso).
     */
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
                    Lobby lobby = matchmaking.crearLobby(usuario, juego, nodo.getId());
                    out.writeObject(lobby);
                    out.flush();
                    nodo.broadcast(new Mensaje(TipoMensaje.LOBBY_CREADO, nodo.getClock().tick(), nodo.getId(), lobby));
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

                    // El lobby vive (con conexiones reales) solo en su nodo dueño
                    if (lobbyObjetivo.getNodoDueno() != nodo.getId()) {
                        NodoConfig destino = nodo.configDePeer(lobbyObjetivo.getNodoDueno());
                        if (destino != null) {
                            out.writeObject("REDIRECT:" + destino.getHost() + ":" + destino.getPuertoCliente());
                        } else {
                            out.writeObject(UNIDO_ERROR);
                        }
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
            boolean cerrado = matchmaking.cerrarLobbySiVacio(lobby.getId());
            if (cerrado) {
                nodo.broadcast(
                        new Mensaje(TipoMensaje.LOBBY_CERRADO, nodo.getClock().tick(), nodo.getId(), lobby.getId()));
                nodo.getLogger().log(nodo.getClock().valorActual(), "Lobby cerrado y difundido: " + lobby.getId());
            }
        }
    }
}
