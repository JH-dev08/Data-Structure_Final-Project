package Persistencia;

import Optimizacion.ResultadoOptimizacion;
import java.io.*;
import java.util.List;
import java.util.Map;

public class ExportarSoluciones {
    public static void exportarJSON(List<ResultadoOptimizacion> lista, File archivo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(archivo))) {
            writer.write("[\n");

            for (int i = 0; i < lista.size(); i++) {
                ResultadoOptimizacion res = lista.get(i);

                writer.write("  {\n");
                writer.write("    \"informacionEjecucion\": {\n");
                writer.write("      \"solverUtilizado\": \"" + res.solverUsado() + "\",\n");
                writer.write("      \"tiempoEjecucionMs\": " + res.tiempoEjecucionMs() + "\n");
                writer.write("      \"estadoSolucion\": \"" + res.estadoTexto() + "\",\n");
                writer.write("    },\n");
                writer.write("    \"solucion\": {\n");
                writer.write("      \"valorObjetivo\": " + res.costoTotal() + ",\n");

                // Bloque de variables activas
                writer.write("      \"variablesActivas\": {\n");
                int count = 0;
                int total = res.flujosActivos().size();
                for (Map.Entry<String, Double> entry : res.flujosActivos().entrySet()) {
                    count++;
                    writer.write("        \"" + entry.getKey() + "\": " + entry.getValue());
                    if (count < total) {
                        writer.write(",\n");
                    } else {
                        writer.write("\n");
                    }
                }
                writer.write("      }\n");
                writer.write("    }\n");
                writer.write("  }");

                // Si no es el último elemento, agregamos una coma para separar los objetos del array
                if (i < lista.size() - 1) {
                    writer.write(",\n");
                } else {
                    writer.write("\n");
                }
            }

            writer.write("]\n"); // 🌟 Cerramos el arreglo JSON
        }
    }   
}