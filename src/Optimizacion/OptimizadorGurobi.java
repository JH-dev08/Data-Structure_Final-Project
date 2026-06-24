package Optimizacion;

import Modelo.*;
import com.gurobi.gurobi.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class OptimizadorGurobi {
    public static List<ResultadoOptimizacion> resolverModelo(ModeloPGraph modeloPNS) {
        try {
            // Iniciar el entorno y el modelo vacío de Gurobi
            GRBEnv env = new GRBEnv(true);
            env.set("LogFile", ""); // gurobi_pns.log
            env.start();
            GRBModel modeloGurobi = new GRBModel(env);
            modeloGurobi.set(GRB.StringAttr.ModelName, "OptimizacionPNS");

            // Diccionarios para almacenar las variables matemáticas
            Map<String, GRBVar> varUnidadesBinarias = new HashMap<>(); // Activación (0 o 1)
            Map<String, GRBVar> varUnidadesContinuas = new HashMap<>(); // Capacidad usada

            // Crear variables para las Unidades Operativas (Operating Units)
            for (UnidadOperativa ou : modeloPNS.getUnidadesOperativas()) {
                // y_i: Variable binaria (1 si la unidad se construye/usa, 0 si no)
                GRBVar y = modeloGurobi.addVar(0.0, 1.0, 0.0, GRB.BINARY, "y_" + ou.id());
                varUnidadesBinarias.put(ou.id(), y);

                // x_i: Variable continua (nivel de operación entre 0 y la capacidad máxima)
                GRBVar x = modeloGurobi.addVar(0.0, ou.capacidadLimite(), 0.0, GRB.CONTINUOUS, "x_" + ou.id());
                varUnidadesContinuas.put(ou.id(), x);

                // Restricción Lógica: El nivel de operación 'c' solo puede ser > 0 si 'y' es 1
                // c_i <= CapacidadMaxima * y_i
                GRBLinExpr exprCapacidad = new GRBLinExpr();
                exprCapacidad.addTerm(ou.capacidadLimite(), y);
                modeloGurobi.addConstr(x, GRB.LESS_EQUAL, exprCapacidad, "CapLogical_" + ou.id());
            }

            // Agregar Restricciones de Conservación de Masa en los Materiales
            for (Material mat : modeloPNS.getMateriales()) {
                GRBLinExpr balanceMasa = new GRBLinExpr();
                String matIdLimpio = mat.id().trim();
                String tipoMaterial = mat.tipo().trim().toLowerCase(); // Aseguramos minúsculas

                for (TasaFlujo fr : modeloPNS.getTasaFlujos()) {
                    // Si el material es el OBJETIVO del flujo, la unidad lo PRODUCE (Suma)
                    if (fr.objetivo().trim().equals(matIdLimpio)) {
                        String ouId = fr.fuente().trim();
                        GRBVar varOU = varUnidadesContinuas.get(ouId);
                        if (varOU != null) {
                            balanceMasa.addTerm(fr.tasa(), varOU);
                        }
                    }
                    // Si el material es la FUENTE del flujo, la unidad lo CONSUME (Resta)
                    else if (fr.fuente().trim().equals(matIdLimpio)) {
                        String ouId = fr.objetivo().trim();
                        GRBVar varOU = varUnidadesContinuas.get(ouId);
                        if (varOU != null) {
                            balanceMasa.addTerm(-fr.tasa(), varOU);
                        }
                    }
                }

                // --- CLASIFICACIÓN DE RESTRICCIONES SEGÚN EL TIPO ---
                if (tipoMaterial.equals("product")) {
                    // Los productos finales deben cumplir obligatoriamente con la demanda del mercado
                    modeloGurobi.addConstr(balanceMasa, GRB.GREATER_EQUAL, mat.flujoMinimo(), "Demand_" + matIdLimpio);
                } 
                else if (tipoMaterial.equals("intermediate")) {
                    // Conservación estricta de masa: No se puede acumular ni faltar material intermedio (Neto = 0)
                    modeloGurobi.addConstr(balanceMasa, GRB.GREATER_EQUAL, 0.0, "BalanceInt_" + matIdLimpio);
                } 
                else if (tipoMaterial.equals("raw_material")) {
                    // Las materias primas NO llevan restricción de balance mínimo interno.
                }
            }
            
            // Definir la Función Objetivo (Minimizar Costos Fijos + Costos Proporcionales)
            GRBLinExpr funcionObjetivo = new GRBLinExpr();
            for (UnidadOperativa ou : modeloPNS.getUnidadesOperativas()) {
                funcionObjetivo.addTerm(ou.costoFijo(), varUnidadesBinarias.get(ou.id()));
                funcionObjetivo.addTerm(ou.costoProporcional(), varUnidadesContinuas.get(ou.id()));
            }
            modeloGurobi.setObjective(funcionObjetivo, GRB.MINIMIZE);

            // Optimizar el modelo y Crear archivo del modelo recibido.
            modeloGurobi.write("mi_modelo_gurobi.lp");
            
            // Configurar la búsqueda de múltiples soluciones (guardar OPTIMAS y SUBOPTIMAS)
            // PoolSearchMode = 1 -> el algoritmo se detiene al saber la optima
            // PoolSearchMode = 2 hace el esfuerzo extra por encontrar las 'n' mejores soluciones (PoolSolutions)
            modeloGurobi.set(GRB.IntParam.PoolSearchMode, 2);

            // PoolSolutions es el límite máximo de soluciones a guardar en memoria (ej. 10, 50, o 100)
            modeloGurobi.set(GRB.IntParam.PoolSolutions, 4); // 1 optima y 3 subobtimas.

            // Ahora sí, optimizamos
            modeloGurobi.optimize();

            // Extraer resultados si se encontró una solución óptima
            int status = modeloGurobi.get(GRB.IntAttr.Status);
            int solucionesEncontradas = modeloGurobi.get(GRB.IntAttr.SolCount);
            double tiempoMs = modeloGurobi.get(GRB.DoubleAttr.Runtime) * 1000.0; // Convertir a milisegundos
            
            // Lista para guardar todos los resultados.
            List<ResultadoOptimizacion> solucionesDeEstaCorrida = new ArrayList<>();
            
            if (solucionesEncontradas > 0) {
                // Recorremos el Pool de Soluciones de Gurobi
                for (int i = 0; i < solucionesEncontradas; i++) {

                    // 1. Le decimos a Gurobi qué solución específica queremos leer (0 es la mejor, 1 en adelante son factibles)
                    modeloGurobi.set(GRB.IntParam.SolutionNumber, i);

                    // 2. Extraemos el costo de ESTA solución en particular usando PoolObjVal (¡No ObjVal!)
                    double costoSolucion = modeloGurobi.get(GRB.DoubleAttr.PoolObjVal);

                    // 3. Asignamos el estado: La solución 0 es OPTIMAL (si el solver terminó bien), el resto son FEASIBLE (Factibles)
                    String estadoSolucion = (i == 0 && status == 2) ? "OPTIMAL" : "FEASIBLE";

                    // 4. Extraemos las variables para ESTA solución usando Xn (¡Súper importante, no usar X!)
                    Map<String, Double> flujosActivosSol = new HashMap<>();
                    for (String ouId : varUnidadesContinuas.keySet()) {
                        // Se usa GRB.DoubleAttr.Xn para leer la solución 'i' del Pool
                        double nivelOperacion = varUnidadesContinuas.get(ouId).get(GRB.DoubleAttr.Xn); 
                        if (nivelOperacion > 0.0001) {
                            flujosActivosSol.put(ouId, nivelOperacion);
                        }
                    }

                    // 5. Agregamos el resultado a nuestra lista
                    solucionesDeEstaCorrida.add(new ResultadoOptimizacion(
                        "Gurobi", 
                        costoSolucion, 
                        tiempoMs, 
                        flujosActivosSol, 
                        new HashMap<>(), // Aquí pondrías tu extracción de unidades si aplica
                        estadoSolucion, 
                        solucionesEncontradas
                    ));
                }
            } else {
                // Si no encontró nada (INFEASIBLE, UNBOUNDED, etc.)
                String estadoError = (status == 3) ? "INFEASIBLE" : "ERROR (" + status + ")";
                solucionesDeEstaCorrida.add(new ResultadoOptimizacion(
                    "Gurobi", 0.0, tiempoMs, new HashMap<>(), new HashMap<>(), estadoError, 0
                ));
            }
            
            modeloGurobi.dispose();
            env.dispose();

            // Retornar la LISTA de resultados.
            return solucionesDeEstaCorrida;
            
        } catch (GRBException e) {
            throw new RuntimeException("Error interno en Gurobi: " + e.getMessage(), e);
        }
    }
}