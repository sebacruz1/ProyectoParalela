package carga;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Calcula throughput/latencia/p95/tasa de error a partir de Metricas y los
 * reporta por consola y CSV.
 */
public class ReporteCarga {

    public static void imprimirYExportar(Metricas metricas, long duracionMs, long mensajesCoordinacion,
            String csvPath) {
        List<Long> latencias = new ArrayList<>(metricas.getLatenciasMs());
        Collections.sort(latencias);

        long exitosas = metricas.getExitosas();
        long rechazadas = metricas.getRechazadas();
        long errores = metricas.getErrores();
        long total = exitosas + rechazadas + errores;

        double duracionSeg = duracionMs / 1000.0;
        double throughput = duracionSeg > 0 ? total / duracionSeg : 0;
        double latenciaProm = latencias.isEmpty() ? 0
                : latencias.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = percentil95(latencias);
        double tasaError = total == 0 ? 0 : (100.0 * errores / total);

        System.out.println();
        System.out.println("================ RESULTADOS DE LA PRUEBA DE CARGA ================");
        System.out.printf("Duración:                  %.1f s%n", duracionSeg);
        System.out.printf("Peticiones totales:        %d%n", total);
        System.out.printf("  Exitosas:                %d%n", exitosas);
        System.out.printf("  Rechazadas (negocio):    %d%n", rechazadas);
        System.out.printf("  Errores (red/excepción): %d%n", errores);
        System.out.printf("Throughput:                %.2f req/s%n", throughput);
        System.out.printf("Latencia promedio:         %.2f ms%n", latenciaProm);
        System.out.printf("Latencia p95:              %d ms%n", p95);
        System.out.printf("Tasa de error:             %.2f%%%n", tasaError);
        System.out.printf("Mensajes de coordinación:  %d (Bully + Ricart-Agrawala, todo el cluster)%n",
                mensajesCoordinacion);
        System.out.println("====================================================================");
        System.out.println();

        try {
            Path destino = Paths.get(csvPath);
            if (destino.getParent() != null) {
                Files.createDirectories(destino.getParent());
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(destino))) {
                w.println("metrica,valor");
                w.println("duracion_s," + duracionSeg);
                w.println("total," + total);
                w.println("exitosas," + exitosas);
                w.println("rechazadas," + rechazadas);
                w.println("errores," + errores);
                w.println("throughput_req_s," + throughput);
                w.println("latencia_prom_ms," + latenciaProm);
                w.println("latencia_p95_ms," + p95);
                w.println("tasa_error_pct," + tasaError);
                w.println("mensajes_coordinacion," + mensajesCoordinacion);
            }
            System.out.println("CSV exportado a: " + destino.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("No se pudo exportar el CSV: " + e.getMessage());
        }
    }

    private static long percentil95(List<Long> latenciasOrdenadas) {
        if (latenciasOrdenadas.isEmpty()) {
            return 0;
        }
        int indice = (int) Math.ceil(0.95 * latenciasOrdenadas.size()) - 1;
        indice = Math.max(0, Math.min(indice, latenciasOrdenadas.size() - 1));
        return latenciasOrdenadas.get(indice);
    }
}
