package carga;

import cluster.NodoConfig;
import modelos.Juego;
import modelos.Lobby;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Cliente de carga headless (no usa el Cliente.java interactivo): cada hilo
 * mantiene una conexión persistente con UN nodo fijo (asignado por
 * round-robin entre los nodos configurados) y repite acciones aleatorias del
 * protocolo de cliente —catálogo, compra del ítem de oferta flash (el
 * recurso de la exclusión mutua), compra de un juego normal, ver lobbies—
 * hasta que se cumple la duración pedida. Si la conexión se cae (por
 * ejemplo, porque mataron el nodo en plena corrida), el hilo registra el
 * error, espera un poco y reintenta solo, sin tumbar el generador completo.
 *
 * Uso: java -cp out carga.GeneradorCarga [numHilos] [duracionSeg] [peersConfigPath]
 * Por defecto: 50 hilos, 60s, config/peers.conf
 */
public class GeneradorCarga {

    private static final String COMPRA_OK = "COMPRA_OK";
    private static final int ID_JUEGO_FLASH = 8;
    private static final int SALDO_INICIAL = 10_000_000;

    private enum Resultado {
        EXITO, RECHAZO, ERROR
    }

    public static void main(String[] args) throws InterruptedException {
        int numHilos = args.length > 0 ? Integer.parseInt(args[0]) : 50;
        int duracionSeg = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        String peersPath = args.length > 2 ? args[2] : "config/peers.conf";

        List<NodoConfig> peers = NodoConfig.cargarDesde(peersPath);
        if (peers.isEmpty()) {
            System.out.println("No hay nodos configurados en " + peersPath);
            return;
        }

        System.out.println("Generador de carga: " + numHilos + " hilos, " + duracionSeg
                + "s, repartidos entre " + peers.size() + " nodo(s) de " + peersPath);

        Metricas metricas = new Metricas();
        long inicio = System.currentTimeMillis();
        long fin = inicio + duracionSeg * 1000L;

        ExecutorService pool = Executors.newFixedThreadPool(numHilos);
        for (int i = 0; i < numHilos; i++) {
            NodoConfig destino = peers.get(i % peers.size());
            int workerId = i;
            pool.submit(() -> ejecutarWorker(workerId, destino, fin, metricas));
        }

        pool.shutdown();
        pool.awaitTermination(duracionSeg + 30L, TimeUnit.SECONDS);

        long duracionMs = System.currentTimeMillis() - inicio;
        long mensajesCoordinacion = consultarMensajesCoordinacion(peers);
        ReporteCarga.imprimirYExportar(metricas, duracionMs, mensajesCoordinacion, "out/carga_resultados.csv");
    }

    private static void ejecutarWorker(int workerId, NodoConfig destino, long fin, Metricas metricas) {
        Random random = new Random();
        while (System.currentTimeMillis() < fin) {
            try (
                    Socket socket = new Socket(destino.getHost(), destino.getPuertoCliente());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

                out.writeObject("carga-" + workerId + "-" + System.nanoTime());
                out.flush();
                in.readObject(); // Usuario creado

                cargarSaldoInicial(in, out);

                while (System.currentTimeMillis() < fin) {
                    long t0 = System.nanoTime();
                    Resultado r = elegirYEjecutarAccion(random, in, out);
                    long latenciaMs = (System.nanoTime() - t0) / 1_000_000;

                    switch (r) {
                        case EXITO -> metricas.registrarExito(latenciaMs);
                        case RECHAZO -> metricas.registrarRechazo(latenciaMs);
                        case ERROR -> metricas.registrarError();
                    }
                    if (r == Resultado.ERROR) {
                        break; // conexión probablemente caída: reconectar desde afuera
                    }
                }

                out.writeObject(3); // salir prolijo si la conexión sigue viva
                out.flush();

            } catch (IOException | ClassNotFoundException e) {
                metricas.registrarError();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    private static Resultado elegirYEjecutarAccion(Random random, ObjectInputStream in, ObjectOutputStream out) {
        int accion = random.nextInt(100);
        if (accion < 40) {
            return verCatalogo(in, out);
        } else if (accion < 70) {
            return comprar(in, out, ID_JUEGO_FLASH); // alta contención: el recurso de la exclusión mutua
        } else if (accion < 90) {
            return comprar(in, out, 1 + random.nextInt(7)); // juego normal cualquiera del catálogo base
        } else {
            return verLobbies(in, out);
        }
    }

    private static void cargarSaldoInicial(ObjectInputStream in, ObjectOutputStream out)
            throws IOException, ClassNotFoundException {
        out.writeObject(1); // Tienda
        out.flush();
        out.writeObject(4); // Cargar saldo
        out.flush();
        out.writeObject(SALDO_INICIAL);
        out.flush();
        in.readObject(); // "SALDO_OK"
        in.readObject(); // Usuario actualizado
        out.writeObject(5); // Volver
        out.flush();
        in.readObject(); // "VOLVER"
    }

    private static Resultado verCatalogo(ObjectInputStream in, ObjectOutputStream out) {
        try {
            out.writeObject(1); // Tienda
            out.flush();
            out.writeObject(1); // Ver catalogo
            out.flush();
            @SuppressWarnings("unchecked")
            List<Juego> catalogo = (List<Juego>) in.readObject();
            out.writeObject(5); // Volver
            out.flush();
            in.readObject(); // "VOLVER"
            return catalogo != null ? Resultado.EXITO : Resultado.ERROR;
        } catch (IOException | ClassNotFoundException e) {
            return Resultado.ERROR;
        }
    }

    private static Resultado comprar(ObjectInputStream in, ObjectOutputStream out, int idJuego) {
        try {
            out.writeObject(1); // Tienda
            out.flush();
            out.writeObject(2); // Comprar juego
            out.flush();
            in.readObject(); // catalogo (no se necesita, ya sabemos el id)
            out.writeObject(idJuego);
            out.flush();
            String resultado = (String) in.readObject();
            in.readObject(); // Usuario actualizado
            out.writeObject(5); // Volver
            out.flush();
            in.readObject(); // "VOLVER"
            return COMPRA_OK.equals(resultado) ? Resultado.EXITO : Resultado.RECHAZO;
        } catch (IOException | ClassNotFoundException e) {
            return Resultado.ERROR;
        }
    }

    private static Resultado verLobbies(ObjectInputStream in, ObjectOutputStream out) {
        try {
            out.writeObject(2); // Matchmaking
            out.flush();
            out.writeObject(1); // Ver lobbies
            out.flush();
            @SuppressWarnings("unchecked")
            List<Lobby> lobbies = (List<Lobby>) in.readObject();
            out.writeObject(4); // Volver
            out.flush();
            in.readObject(); // "VOLVER"
            return lobbies != null ? Resultado.EXITO : Resultado.ERROR;
        } catch (IOException | ClassNotFoundException e) {
            return Resultado.ERROR;
        }
    }

    /** Suma los contadores de mensajes de coordinación (Bully + RA) de cada nodo vivo al final de la corrida. */
    private static long consultarMensajesCoordinacion(List<NodoConfig> peers) {
        long total = 0;
        for (NodoConfig peer : peers) {
            try (
                    Socket socket = new Socket(peer.getHost(), peer.getPuertoCliente());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                out.writeObject("metricas-" + System.nanoTime());
                out.flush();
                in.readObject(); // Usuario

                out.writeObject(9); // métricas (no es parte del menú visible)
                out.flush();
                MetricasNodo m = (MetricasNodo) in.readObject();
                total += m.getTotal();

                out.writeObject(3);
                out.flush();
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("No se pudieron leer métricas del nodo " + peer.getId() + " (¿está caído?): "
                        + e.getMessage());
            }
        }
        return total;
    }

    private GeneradorCarga() {
    }
}
