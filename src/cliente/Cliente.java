package cliente;

import modelos.Juego;
import modelos.Lobby;
import modelos.Usuario;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.Scanner;

public class Cliente {

    private static final String HOST_DEFAULT = "localhost";
    private static final int PUERTO_CLIENTE_DEFAULT = 7001;
    private static final String COMPRA_OK = "COMPRA_OK";
    private static final String SALDO_OK = "SALDO_OK";
    private static final String UNIDO_OK = "UNIDO_OK";

    private static Scanner scanner = new Scanner(System.in);
    private static Usuario usuario;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : HOST_DEFAULT;
        int puerto = args.length > 1 ? Integer.parseInt(args[1]) : PUERTO_CLIENTE_DEFAULT;

        System.out.println("=================================");
        System.out.println("     EPIC GAMES - BIENVENIDO     ");
        System.out.println("=================================");
        System.out.println("Conectando a " + host + ":" + puerto);
        System.out.print("Ingresa tu nombre de usuario: ");
        String username = scanner.nextLine();

        try (
                Socket socket = new Socket(host, puerto);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            // Enviar nombre y recibir usuario creado
            out.writeObject(username);
            out.flush();
            usuario = (Usuario) in.readObject();
            System.out.println("\nBienvenido, " + usuario.getUsername());

            boolean activo = true;
            while (activo) {
                System.out.println("\n=================================");
                System.out.println("          MENU PRINCIPAL         ");
                System.out.println("=================================");
                System.out.println("[1] Tienda");
                System.out.println("[2] Matchmaking");
                System.out.println("[3] Salir");
                System.out.print("Opcion: ");

                int opcion = leerOpcion();
                out.writeObject(opcion);
                out.flush();

                switch (opcion) {
                    case 1 -> menuTienda(in, out);
                    case 2 -> menuMatchmaking(in, out);
                    case 3 -> {
                        in.readObject();
                        System.out.println("\nHasta luego, " + usuario.getUsername() + "!");
                        activo = false;
                    }
                    default -> System.out.println((String) in.readObject());
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error de conexion: " + e.getMessage());
        }
    }

    // TIENDA
    private static void menuTienda(ObjectInputStream in, ObjectOutputStream out) {
        try {
            boolean activo = true;
            while (activo) {
                System.out.println("\n=================================");
                System.out.println("            TIENDA               ");
                System.out.println("  Saldo: $" + String.format("%,d", usuario.getSaldo()));
                System.out.println("=================================");
                System.out.println("[1] Ver catalogo");
                System.out.println("[2] Comprar juego");
                System.out.println("[3] Ver mi biblioteca");
                System.out.println("[4] Cargar saldo");
                System.out.println("[5] Volver");
                System.out.print("Opcion: ");

                int opcion = leerOpcion();
                out.writeObject(opcion);
                out.flush();

                switch (opcion) {

                    case 1 -> {
                        // Ver catalogo
                        List<Juego> catalogo = (List<Juego>) in.readObject();
                        System.out.println("\n CATALOGO");
                        if (catalogo.isEmpty()) {
                            System.out.println("No hay juegos disponibles.");
                        } else {
                            catalogo.forEach(System.out::println);
                        }
                    }

                    case 2 -> {
                        // Comprar juego
                        List<Juego> catalogo = (List<Juego>) in.readObject();
                        System.out.println("\nJUEGOS DISPONIBLES");
                        if (catalogo.isEmpty()) {
                            System.out.println("No hay juegos disponibles para comprar.");
                            break;
                        }
                        catalogo.forEach(System.out::println);
                        System.out.print("ID del juego a comprar: ");
                        int idJuego = leerOpcion();
                        out.writeObject(idJuego);
                        out.flush();
                        String resultado = (String) in.readObject();
                        usuario = (Usuario) in.readObject();
                        if (resultado.equals(COMPRA_OK)) {
                            System.out.println(
                                    "Compra exitosa | Nuevo saldo: $" + String.format("%,d", usuario.getSaldo()));
                        } else {
                            System.out.println(
                                    "Compra fallida. Saldo actual: $" + String.format("%,d", usuario.getSaldo()));
                        }
                    }

                    case 3 -> { //
                        List<Juego> biblioteca = (List<Juego>) in.readObject();
                        System.out.println("\nBIBLIOTECA");
                        if (biblioteca.isEmpty()) {
                            System.out.println("No tienes juegos aun.");
                        } else {
                            biblioteca.forEach(System.out::println);
                        }
                    }

                    case 4 -> { // Cargar saldo
                        System.out.print("Monto a cargar: $");
                        int monto = leerOpcion();
                        out.writeObject(monto);
                        out.flush();
                        String resultado = (String) in.readObject();
                        if (resultado.equals(SALDO_OK)) {
                            usuario = (Usuario) in.readObject();
                            System.out.println(
                                    "Saldo cargado | Nuevo saldo: $" + String.format("%,d", usuario.getSaldo()));
                        }
                    }

                    case 5 -> {
                        // Volver
                        in.readObject();
                        activo = false;
                    }
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error en tienda: " + e.getMessage());
        }
    }

    // MATCHMAKING
    private static void menuMatchmaking(ObjectInputStream in, ObjectOutputStream out) {
        try {
            boolean activo = true;
            while (activo) {
                System.out.println("\n=================================");
                System.out.println("          MATCHMAKING            ");
                System.out.println("=================================");
                System.out.println("[1] Ver lobbies");
                System.out.println("[2] Crear lobby");
                System.out.println("[3] Unirse a lobby");
                System.out.println("[4] Volver");
                System.out.print("Opcion: ");

                int opcion = leerOpcion();
                out.writeObject(opcion);
                out.flush();

                switch (opcion) {

                    case 1 -> {
                        List<Lobby> lobbies = (List<Lobby>) in.readObject();
                        System.out.println("\nLOBBIES DISPONIBLES");
                        if (lobbies.isEmpty()) {
                            System.out.println("No hay lobbies activos.");
                        } else {
                            lobbies.forEach(System.out::println);
                        }
                    }

                    case 2 -> {
                        List<Juego> biblioteca = (List<Juego>) in.readObject();
                        if (biblioteca.isEmpty()) {
                            System.out.println("No tenes juegos en tu biblioteca. Compra uno primero.");
                            break;
                        }
                        System.out.println("\nElige el juego para el lobby:");
                        biblioteca.forEach(System.out::println);
                        System.out.print("ID del juego: ");
                        int idJuego = leerOpcion();
                        out.writeObject(idJuego);
                        out.flush();

                        Object respuesta = in.readObject();
                        if (respuesta instanceof String msg) {
                            System.out.println("Error: " + msg);
                        } else {
                            Lobby lobby = (Lobby) respuesta;
                            System.out.println("\nLobby creado: " + lobby);
                            System.out.println("Entrando al chat... (escribe /salir para salir)");
                            iniciarChat(in, out);
                            activo = false;
                        }
                    }

                    case 3 -> {
                        List<Lobby> lobbies = (List<Lobby>) in.readObject();
                        System.out.println("\nLOBBIES DISPONIBLES");
                        if (lobbies.isEmpty()) {
                            System.out.println("No hay lobbies activos.");
                            break;
                        }
                        lobbies.forEach(System.out::println);
                        System.out.print("ID del lobby: ");
                        int idLobby = leerOpcion();
                        out.writeObject(idLobby);
                        out.flush();

                        String resultado = (String) in.readObject();
                        switch (resultado) {
                            case UNIDO_OK -> {
                                Lobby lobby = (Lobby) in.readObject();
                                System.out.println("\nUnido a: " + lobby);
                                System.out.println("Entrando al chat... (escribe /salir para salir)");
                                iniciarChat(in, out);
                                activo = false;
                            }
                            case "NO_TIENES_JUEGO" ->
                                System.out.println("No tienes ese juego en tu biblioteca. Compralo primero.");
                            default ->
                                System.out.println("No se pudo unir al lobby (ya estás adentro o no existe).");
                        }
                    }

                    case 4 -> {
                        in.readObject();
                        activo = false;
                    }
                }
            }

        } catch (IOException | ClassNotFoundException e) {
            System.out.println("Error en matchmaking: " + e.getMessage());
        }
    }

    // CHAT
    private static void iniciarChat(ObjectInputStream in, ObjectOutputStream out) {
        // Hilo para recibir mensajes del servidor
        // La conexión es la misma que se reutiliza para el resto de la sesión
        String miDespedida = "[" + usuario.getUsername() + " salió del lobby]";
        Thread receptor = new Thread(() -> {
            try {
                while (true) {
                    String mensaje = (String) in.readObject();
                    System.out.println(mensaje);
                    if (mensaje.equals(miDespedida)) {
                        break;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("[Chat desconectado]");
            }
        });
        receptor.setDaemon(true);
        receptor.start();

        // Loop principal para enviar mensajes
        try {
            while (true) {
                String mensaje = scanner.nextLine();
                out.writeObject(mensaje);
                out.flush();
                if (mensaje.equalsIgnoreCase("/salir"))
                    break;
            }
        } catch (IOException e) {
            System.out.println("Error en chat: " + e.getMessage());
        }

        try {
            receptor.join(5000);
        } catch (InterruptedException ignored) {
        }
    }

    // UTILS
    private static int leerOpcion() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
