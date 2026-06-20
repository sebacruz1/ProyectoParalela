package coordinacion;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/*
    Log por nodo a logs/node<id>.log, formato:
    [yyyy-MM-ddTHH:mm:ss.SSS][node=<id>][lamport=<N>] evento
 */
public class NodoLogger {

    private static final DateTimeFormatter FORMATO_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final int nodeId;
    private final PrintWriter writer;

    public NodoLogger(int nodeId) {
        this.nodeId = nodeId;
        try {
            Files.createDirectories(Paths.get("logs"));
            this.writer = new PrintWriter(new FileWriter("logs/node" + nodeId + ".log", true), true);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo abrir el log del nodo " + nodeId, e);
        }
    }

    public synchronized void log(int lamport, String evento) {
        String linea = "[" + LocalDateTime.now().format(FORMATO_TS) + "]"
                + "[node=" + nodeId + "]"
                + "[lamport=" + lamport + "] "
                + evento;
        writer.println(linea);
        System.out.println(linea);
    }

    public void log(String evento) {
        log(-1, evento);
    }
}
