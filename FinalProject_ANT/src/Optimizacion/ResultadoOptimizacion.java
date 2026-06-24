package Optimizacion;

import java.util.Map;

public record ResultadoOptimizacion(
        String solverUsado,
        double costoTotal,
        double tiempoEjecucionMs,
        Map<String, Double> flujosActivos,
        Map<String, Double> unidadesActivas,
        String estadoTexto,
        int solucionesEncontradas
) {}