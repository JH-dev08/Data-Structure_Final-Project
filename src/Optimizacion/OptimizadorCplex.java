package Optimizacion;

import Modelo.*;
import ilog.concert.*;
import ilog.cplex.IloCplex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimizadorCplex {
    public static List<ResultadoOptimizacion> resolverModelo(ModeloPGraph modeloPNS) {
        List<ResultadoOptimizacion> solucionesDeEstaCorrida = new ArrayList<>();
        long tiempoInicio = System.currentTimeMillis();

        try {
            // Iniciar el entorno y el modelo vacío de CPLEX
            IloCplex cplex = new IloCplex();
            
            // Opcional: Si quieres desactivar la consola de CPLEX, descomenta la siguiente línea:
            // cplex.setOut(null);

            // Diccionarios o tipo de tabla hash para almacenar las variables matemáticas
            Map<String, IloIntVar> varUnidadesBinarias = new HashMap<>(); // Activación (y_i)
            Map<String, IloNumVar> varUnidadesContinuas = new HashMap<>(); // Capacidad usada (x_i)

            // Crear variables para las Unidades Operativas
            for (UnidadOperativa ou : modeloPNS.getUnidadesOperativas()) {
                // y_i: Variable binaria (1 si se construye/usa, 0 si no)
                IloIntVar y = cplex.boolVar("y_" + ou.id());
                varUnidadesBinarias.put(ou.id(), y);

                // x_i: Variable continua (nivel de operación entre 0 y límite)
                IloNumVar x = cplex.numVar(0.0, ou.capacidadLimite(), "x_" + ou.id());
                varUnidadesContinuas.put(ou.id(), x);

                // Restricción Lógica: x_i <= CapacidadMaxima * y_i
                // Reescrito para CPLEX: x_i - CapacidadMaxima * y_i <= 0
                IloLinearNumExpr exprCapacidad = cplex.linearNumExpr();
                exprCapacidad.addTerm(1.0, x);
                exprCapacidad.addTerm(-ou.capacidadLimite(), y);
                cplex.addLe(exprCapacidad, 0.0, "CapLogical_" + ou.id());
            }

            // Restricciones de Conservación de Masa
            for (Material mat : modeloPNS.getMateriales()) {
                IloLinearNumExpr balanceMasa = cplex.linearNumExpr();
                String matIdLimpio = mat.id().trim();
                String tipoMaterial = mat.tipo().trim().toLowerCase();

                for (TasaFlujo fr : modeloPNS.getTasaFlujos()) {
                    if (fr.objetivo().trim().equals(matIdLimpio)) {
                        String ouId = fr.fuente().trim();
                        if (varUnidadesContinuas.containsKey(ouId)) {
                            balanceMasa.addTerm(fr.tasa(), varUnidadesContinuas.get(ouId)); // Produce (+)
                        }
                    } else if (fr.fuente().trim().equals(matIdLimpio)) {
                        String ouId = fr.objetivo().trim();
                        if (varUnidadesContinuas.containsKey(ouId)) {
                            balanceMasa.addTerm(-fr.tasa(), varUnidadesContinuas.get(ouId)); // Consume (-)
                        }
                    }
                }

                // --- CLASIFICACIÓN DE RESTRICCIONES ---
                if (tipoMaterial.equals("product")) {
                    // La suma debe ser mayor o igual a la demanda del mercado
                    cplex.addGe(balanceMasa, mat.flujoMinimo(), "Demand_" + matIdLimpio);
                } 
                else if (tipoMaterial.equals("intermediate")) {
                    // Usamos GEQ (>= 0) basándonos en tu configuración validada
                    cplex.addGe(balanceMasa, 0.0, "BalanceInt_" + matIdLimpio);
                }
            }

            // Definir la Función Objetivo (Minimizar Costos)
            IloLinearNumExpr funcionObjetivo = cplex.linearNumExpr();
            for (UnidadOperativa ou : modeloPNS.getUnidadesOperativas()) {
                funcionObjetivo.addTerm(ou.costoFijo(), varUnidadesBinarias.get(ou.id()));
                funcionObjetivo.addTerm(ou.costoProporcional(), varUnidadesContinuas.get(ou.id()));
            }
            cplex.addMinimize(funcionObjetivo);

            // Exportar el modelo para verificar las ecuaciones
            cplex.exportModel("mi_modelo_cplex.lp");

            // Configurar el Pool de Soluciones (Múltiples soluciones)
            cplex.setParam(IloCplex.Param.MIP.Pool.Capacity, 4);   // Límite de soluciones a guardar
            cplex.setParam(IloCplex.Param.MIP.Pool.Replace, 2);    // 1 = reemplazo automatico, 2 = Reemplazar siempre la solucion de peor costo
            cplex.setParam(IloCplex.Param.MIP.Pool.Intensity, 4);  // 0 = Automático, 1 = Moderado, 2 = Alto, 3 = Agresivo, 4 = Enumeración máxima

            // Resolver (Usamos 'populate()' en vez de 'solve()' para extraer múltiples resultados)
            boolean exito = cplex.populate();
            long tiempoMs = System.currentTimeMillis() - tiempoInicio;

            // Extraer resultados
            if (exito) {
                IloCplex.Status status = cplex.getStatus();
                int solucionesEncontradas = cplex.getSolnPoolNsolns();

                for (int i = 0; i < solucionesEncontradas; i++) {
                    double costoSolucion = cplex.getObjValue(i); // Extraer del índice 'i'
                    String estadoSolucion = (i == 0 && status.equals(IloCplex.Status.Optimal)) ? "OPTIMAL" : "FEASIBLE";

                    Map<String, Double> flujosActivosSol = new HashMap<>();
                    for (String ouId : varUnidadesContinuas.keySet()) {
                        // Leer variable de la solución número 'i'
                        double nivelOperacion = cplex.getValue(varUnidadesContinuas.get(ouId), i);
                        
                        if (nivelOperacion > 0.0001) {
                            flujosActivosSol.put(ouId, nivelOperacion);
                        }
                    }
                    
                    // AGREGAR a la lista retornable.
                    solucionesDeEstaCorrida.add(new ResultadoOptimizacion(
                            "CPLEX",
                            costoSolucion,
                            tiempoMs,
                            flujosActivosSol,
                            new HashMap<>(),
                            estadoSolucion,
                            solucionesEncontradas
                    ));
                }
            } else {
                // Si CPLEX no encuentra ninguna solución (Infactible o Error)
                String estadoError = cplex.getStatus().toString();
                solucionesDeEstaCorrida.add(crearResultadoError(estadoError, tiempoInicio));
            }

            // Liberar memoria del solver obligatoriamente
            cplex.end();

        } catch (IloException e) {
            System.err.println("Error en la formulación de CPLEX: " + e.getMessage());
            solucionesDeEstaCorrida.add(crearResultadoError("ERROR_CPLEX", tiempoInicio));
        } catch (Exception e) {
            System.err.println("Error general ejecutando CPLEX: " + e.getMessage());
            solucionesDeEstaCorrida.add(crearResultadoError("ERROR", tiempoInicio));
        }

        return solucionesDeEstaCorrida;
    }

    // Capturar errores sin tumbar la aplicación.
    private static ResultadoOptimizacion crearResultadoError(String estado, long tiempoInicio) {
        long tiempoMs = System.currentTimeMillis() - tiempoInicio;
        return new ResultadoOptimizacion(
                "CPLEX", 0.0, tiempoMs, new HashMap<>(), new HashMap<>(), estado, 0
        );
    }
    
}
