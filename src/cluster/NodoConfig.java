package cluster;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Una entrada de la membresía estática del cluster (config/peers.conf):
 * id,host,puertoCliente,puertoPeer
 */
public class NodoConfig {

    private final int id;
    private final String host;
    private final int puertoCliente;
    private final int puertoPeer;

    public NodoConfig(int id, String host, int puertoCliente, int puertoPeer) {
        this.id = id;
        this.host = host;
        this.puertoCliente = puertoCliente;
        this.puertoPeer = puertoPeer;
    }

    public int getId() {
        return id;
    }

    public String getHost() {
        return host;
    }

    public int getPuertoCliente() {
        return puertoCliente;
    }

    public int getPuertoPeer() {
        return puertoPeer;
    }

    @Override
    public String toString() {
        return "Nodo{" + id + ", " + host + ", cliente=" + puertoCliente + ", peer=" + puertoPeer + "}";
    }

    public static List<NodoConfig> cargarDesde(String path) {
        List<NodoConfig> nodos = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                String[] partes = linea.split(",");
                if (partes.length != 4) {
                    throw new IllegalArgumentException("Línea inválida en " + path + ": " + linea);
                }
                int id = Integer.parseInt(partes[0].trim());
                String host = partes[1].trim();
                int puertoCliente = Integer.parseInt(partes[2].trim());
                int puertoPeer = Integer.parseInt(partes[3].trim());
                nodos.add(new NodoConfig(id, host, puertoCliente, puertoPeer));
            }
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer la configuración de peers: " + path, e);
        }
        return nodos;
    }
}
