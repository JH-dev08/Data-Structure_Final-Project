package Optimizacion;

import Modelo.*;

import org.apache.commons.math4.legacy.optim.*;
import org.apache.commons.math4.legacy.optim.linear.*;
import org.apache.commons.math4.legacy.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math4.legacy.exception.TooManyIterationsException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimizadorApache {
    public static List<ResultadoOptimizacion> resolverModelo(ModeloPGraph modeloPNS) {
        List<ResultadoOptimizacion> solucionesDeEstaCorrida = new ArrayList<>();
        long tiempoInicio = System.currentTimeMillis();

        // Estructuras de datos locales para la traducción matemática
        List<LinearConstraint> restricciones = new ArrayList<>();
        List<String> nombresVariables = new ArrayList<>();
        Map<String, Integer> indiceUnidades = new HashMap<>();

        List<UnidadOperativa> unidades = modeloPNS.getUnidadesOperativas();
        int numVariables = unidades.size();
        double[] coeficientesObjetivo = new double[numVariables];

        // --- 1. CONSTRUCCIÓN DEL MODELO MATEMÁTICO ---
        // A. Definir Variables (Unidades Operativas) y Función Objetivo
        for (int i = 0; i < numVariables; i++) {
            UnidadOperativa ou = unidades.get(i);
            nombresVariables.add(ou.id());
            indiceUnidades.put(ou.id(), i);
            
            // Costo proporcional para el LP
            coeficientesObjetivo[i] = ou.costoProporcional();
            
            // Restricción de Capacidad: x_i <= Capacidad Maxima
            double[] coeficientesCapacidad = new double[numVariables];
            coeficientesCapacidad[i] = 1.0; 
            restricciones.add(new LinearConstraint(coeficientesCapacidad, Relationship.LEQ, ou.capacidadLimite()));
        }
        
        LinearObjectiveFunction funcionObjetivo = new LinearObjectiveFunction(coeficientesObjetivo, 0.0);

        // B. Restricciones de Conservación de Masa
        for (Material mat : modeloPNS.getMateriales()) {
            double[] balanceMasa = new double[numVariables];
            String matIdLimpio = mat.id().trim();
            String tipoMaterial = mat.tipo().trim().toLowerCase();

            for (TasaFlujo fr : modeloPNS.getTasaFlujos()) {
                if (fr.objetivo().trim().equals(matIdLimpio)) {
                    String ouId = fr.fuente().trim();
                    if (indiceUnidades.containsKey(ouId)) {
                        balanceMasa[indiceUnidades.get(ouId)] += fr.tasa();
                    }
                } else if (fr.fuente().trim().equals(matIdLimpio)) {
                    String ouId = fr.objetivo().trim();
                    if (indiceUnidades.containsKey(ouId)) {
                        balanceMasa[indiceUnidades.get(ouId)] -= fr.tasa();
                    }
                }
            }

            // Clasificación de la restricción
            if (tipoMaterial.equals("product")) {
                restricciones.add(new LinearConstraint(balanceMasa, Relationship.GEQ, mat.flujoMinimo()));
            } else if (tipoMaterial.equals("intermediate")) {
                restricciones.add(new LinearConstraint(balanceMasa, Relationship.GEQ, 0.0));
            }
        }
        
        // --- CÓDIGO PARA GENERAR EL ARCHIVO .LP MANUALMENTE ---
        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter("mi_modelo_apache.lp"))) {
            writer.println("Minimize");
            writer.print("  Obj: ");
            for (int i = 0; i < numVariables; i++) {
                writer.print(coeficientesObjetivo[i] + " x_" + nombresVariables.get(i) + (i < numVariables - 1 ? " + " : ""));
            }
            writer.println("\n\nSubject To");

            // Aquí imprimimos las restricciones que se guardaron en la lista
            int contador = 1;
            for (LinearConstraint c : restricciones) {
                writer.print("  Restriccion_" + contador + ": ");
                double[] coeffs = c.getCoefficients().toArray();
                boolean primero = true;
                for (int j = 0; j < coeffs.length; j++) {
                    if (coeffs[j] != 0) {
                        String signo = coeffs[j] > 0 ? (primero ? "" : " + ") : " - ";
                        writer.print(signo + Math.abs(coeffs[j]) + " x_" + nombresVariables.get(j));
                        primero = false;
                    }
                }

                // Mapeo visual del enum Relationship de Apache a símbolos de texto
                String relacion = "";
                if (c.getRelationship() == Relationship.GEQ) relacion = ">=";
                else if (c.getRelationship() == Relationship.LEQ) relacion = "<=";
                else if (c.getRelationship() == Relationship.EQ) relacion = "=";

                writer.println(" " + relacion + " " + c.getValue());
                contador++;
            }
            writer.println("\nEnd");
        } catch (java.io.IOException e) {
            System.out.println("No se pudo generar el archivo LP de Apache: " + e.getMessage());
        }
        // ------------------------------------------------------

        // --- 2. RESOLUCIÓN Y EXTRACCIÓN DE RESULTADOS ---
        try {
            SimplexSolver solver = new SimplexSolver();
            PointValuePair solucionOptima = solver.optimize(
                    new MaxIter(1000),
                    funcionObjetivo,
                    new LinearConstraintSet(restricciones),
                    GoalType.MINIMIZE,
                    new NonNegativeConstraint(true)
            );

            long tiempoMs = System.currentTimeMillis() - tiempoInicio;
            double[] valoresVariables = solucionOptima.getPoint();
            double costoFinalCalculado = 0.0;

            Map<String, Double> flujosActivos = new HashMap<>();
            
            for (int i = 0; i < valoresVariables.length; i++) {
                if (valoresVariables[i] > 1e-6) { 
                    String ouId = nombresVariables.get(i);
                    double nivelOperacion = valoresVariables[i];
                    
                    flujosActivos.put(ouId, nivelOperacion);
                    
                    // Cálculo manual de costos fijos
                    UnidadOperativa ou = unidades.get(i);
                    costoFinalCalculado += (nivelOperacion * ou.costoProporcional()) + ou.costoFijo();
                }
            }

            solucionesDeEstaCorrida.add(new ResultadoOptimizacion(
                    "Apache Commons Math 4", 
                    costoFinalCalculado, 
                    tiempoMs, 
                    flujosActivos, 
                    new HashMap<>(), 
                    "OPTIMAL", 
                    1 
            ));

        } catch (NoFeasibleSolutionException e) {
            solucionesDeEstaCorrida.add(crearResultadoError("INFEASIBLE", tiempoInicio));
        } catch (UnboundedSolutionException e) {
            solucionesDeEstaCorrida.add(crearResultadoError("UNBOUNDED", tiempoInicio));
        } catch (TooManyIterationsException e) {
            solucionesDeEstaCorrida.add(crearResultadoError("TIMEOUT", tiempoInicio));
        } catch (Exception e) {
            // Captura de seguridad para cualquier otro error imprevisto
            solucionesDeEstaCorrida.add(crearResultadoError("ERROR (" + e.getMessage() + ")", tiempoInicio));
        }

        return solucionesDeEstaCorrida;
    }

    private static ResultadoOptimizacion crearResultadoError(String estado, long tiempoInicio) {
        long tiempoMs = System.currentTimeMillis() - tiempoInicio;
        return new ResultadoOptimizacion(
                "Apache Commons Math 4", 0.0, tiempoMs, new HashMap<>(), new HashMap<>(), estado, 0
        );
    }
}