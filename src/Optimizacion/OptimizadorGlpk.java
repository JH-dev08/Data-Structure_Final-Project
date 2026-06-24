package Optimizacion;

import Modelo.*;

import org.gnu.glpk.GLPK;
import org.gnu.glpk.GLPKConstants;
import org.gnu.glpk.glp_prob;
import org.gnu.glpk.glp_iocp;
import org.gnu.glpk.SWIGTYPE_p_int;         // Libreria para crear arreglos y matrices propias del lenguaje C
import org.gnu.glpk.SWIGTYPE_p_double;      // Libreria para crear arreglos matrices propias del lenguaje C

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 *
 * @author TEOFILO
 */
public class OptimizadorGlpk {
   public static List<ResultadoOptimizacion> resolverModelo(ModeloPGraph modeloPNS) {
        List<ResultadoOptimizacion> solucionesDeEstaCorrida = new ArrayList<>();
        long tiempoInicio = System.currentTimeMillis();
        
        glp_prob lp = null;

        try {
            // Crear el entorno del problema GLPK
            lp = GLPK.glp_create_prob();
            GLPK.glp_set_prob_name(lp, "OptimizacionPNS_GLPK");
            GLPK.glp_set_obj_dir(lp, GLPKConstants.GLP_MIN);

            // Listas dinámicas para construir la matriz dispersa (Sparse Matrix)
            // IMPORTANTE: En GLPK el índice 0 se ignora, todo empieza en 1.
            List<Integer> listaFilas = new ArrayList<>();
            List<Integer> listaColumnas = new ArrayList<>();
            List<Double> listaValores = new ArrayList<>();
            
            // Insertar un valor fantasma (dummy) en el índice 0 para cumplir con la regla de GLPK
            listaFilas.add(0);
            listaColumnas.add(0);
            listaValores.add(0.0);

            List<UnidadOperativa> unidades = modeloPNS.getUnidadesOperativas();
            int numUnidades = unidades.size();
            
            // Diccionario para rastrear la columna de las variables continuas 'x'
            Map<String, Integer> indiceVarX = new HashMap<>();

            // --- CONFIGURACIÓN DE COLUMNAS (VARIABLES Y FUNCIÓN OBJETIVO) ---
            // Tenemos 2 tipos de variables por cada unidad: binarias (y) y continuas (x)
            int totalColumnas = numUnidades * 2;
            GLPK.glp_add_cols(lp, totalColumnas);

            for (int i = 0; i < numUnidades; i++) {
                UnidadOperativa ou = unidades.get(i);
                
                // Variable Binaria: y_i (Índices del 1 al numUnidades)
                int colY = i + 1; 
                GLPK.glp_set_col_name(lp, colY, "y_" + ou.id());
                GLPK.glp_set_col_kind(lp, colY, GLPKConstants.GLP_BV); // BV = Binary Variable
                GLPK.glp_set_obj_coef(lp, colY, ou.costoFijo());       // Costo fijo a minimizar

                // Variable Continua: x_i (Índices del numUnidades+1 al total)
                int colX = numUnidades + i + 1; 
                indiceVarX.put(ou.id(), colX);
                GLPK.glp_set_col_name(lp, colX, "x_" + ou.id());
                GLPK.glp_set_col_kind(lp, colX, GLPKConstants.GLP_CV); // CV = Continuous Variable
                // Límite (Bounds): De 0 hasta la capacidad máxima
                GLPK.glp_set_col_bnds(lp, colX, GLPKConstants.GLP_DB, 0.0, ou.capacidadLimite()); 
                GLPK.glp_set_obj_coef(lp, colX, ou.costoProporcional()); // Costo proporcional
            }

            // --- CONFIGURACIÓN DE FILAS (RESTRICCIONES) ---
            List<Material> materiales = modeloPNS.getMateriales();
            
            List<Material> materialesValidos = new ArrayList<>();
            for (Material mat : materiales) {
                String tipo = mat.tipo().trim().toLowerCase();
                if (tipo.equals("product") || tipo.equals("intermediate")) {
                    materialesValidos.add(mat);
                }
            }
            
            // Calculamos el número exacto de filas necesarias
            int numRestricciones = numUnidades + materialesValidos.size();
            GLPK.glp_add_rows(lp, numRestricciones);

            int filaActual = 1;

            // A. Restricciones Lógicas de Capacidad (x_i - Capacidad * y_i <= 0)
            for (int i = 0; i < numUnidades; i++) {
                UnidadOperativa ou = unidades.get(i);
                int colY = i + 1;
                int colX = numUnidades + i + 1;

                GLPK.glp_set_row_name(lp, filaActual, "CapLogical_" + ou.id());
                GLPK.glp_set_row_bnds(lp, filaActual, GLPKConstants.GLP_UP, 0.0, 0.0); 

                listaFilas.add(filaActual); listaColumnas.add(colX); listaValores.add(1.0);
                listaFilas.add(filaActual); listaColumnas.add(colY); listaValores.add(-ou.capacidadLimite());

                filaActual++;
            }

            // B. Restricciones de Balance de Masa (SOLO VALIDAS)
            for (Material mat : materialesValidos) {
                String matId = mat.id().trim();
                String tipo = mat.tipo().trim().toLowerCase();

                GLPK.glp_set_row_name(lp, filaActual, "Balance_" + matId);

                double limiteInferior = tipo.equals("product") ? mat.flujoMinimo() : 0.0;
                GLPK.glp_set_row_bnds(lp, filaActual, GLPKConstants.GLP_LO, limiteInferior, 0.0);

                for (TasaFlujo fr : modeloPNS.getTasaFlujos()) {
                    if (fr.objetivo().trim().equals(matId)) { 
                        if (indiceVarX.containsKey(fr.fuente().trim())) {
                            listaFilas.add(filaActual);
                            listaColumnas.add(indiceVarX.get(fr.fuente().trim()));
                            listaValores.add(fr.tasa());
                        }
                    } else if (fr.fuente().trim().equals(matId)) { 
                        if (indiceVarX.containsKey(fr.objetivo().trim())) {
                            listaFilas.add(filaActual);
                            listaColumnas.add(indiceVarX.get(fr.objetivo().trim()));
                            listaValores.add(-fr.tasa());
                        }
                    }
                }
                filaActual++;
            }

            // --- CARGAR MATRIZ Y EXPORTAR ---
            // Convertir las listas dinámicas a arreglos primitivos de Java
            int cantElementos = listaValores.size();

            // Estructuras de memoria nativa que SWIG y C puedan entender
            SWIGTYPE_p_int iaArr = GLPK.new_intArray(cantElementos);
            SWIGTYPE_p_int jaArr = GLPK.new_intArray(cantElementos);
            SWIGTYPE_p_double arArr = GLPK.new_doubleArray(cantElementos);

            // Asignar las estructuras nativas usando los métodos setitem de GLPK
            for (int i = 1; i < cantElementos; i++) { // Recordar que el índice 0 se ignora
                GLPK.intArray_setitem(iaArr, i, listaFilas.get(i));
                GLPK.intArray_setitem(jaArr, i, listaColumnas.get(i));
                GLPK.doubleArray_setitem(arArr, i, listaValores.get(i));
            }

            // Cargar la matriz matemática en GLPK (¡Ahora compilará perfectamente!)
            GLPK.glp_load_matrix(lp, cantElementos - 1, iaArr, jaArr, arArr);

            // Como GLPK ya copió internamente los datos a 'lp',
            // liberamos estos tres arreglos nativos inmediatamente para no causar fugas de memoria.
            GLPK.delete_intArray(iaArr);
            GLPK.delete_intArray(jaArr);
            GLPK.delete_doubleArray(arArr);
            
            // Crear archivo del modelo recibido.
            GLPK.glp_write_lp(lp, null, "mi_modelo_glpk.lp");
            
            // --- CONFIGURACIÓN DEL BUCLE PARA BUSCAR N SOLUCIONES ---
            int nSolucionesDeseadas = 4; // Número de soluciones "Top N" que quieres
            int solucionesEncontradas = 0;

            glp_iocp parametros = new glp_iocp();
            GLPK.glp_init_iocp(parametros);
            parametros.setPresolve(GLPKConstants.GLP_ON);
            parametros.setMsg_lev(GLPKConstants.GLP_MSG_OFF); // Silencioso para no saturar la consola en el bucle

            while (solucionesEncontradas < nSolucionesDeseadas) {
                // Resolver el modelo en su estado actual
                int resultadoSolver = GLPK.glp_intopt(lp, parametros);
                
                if (resultadoSolver != 0) break; // Error o ya no hay más soluciones posibles

                int estadoMIP = GLPK.glp_mip_status(lp);
                if (estadoMIP != GLPKConstants.GLP_OPT && estadoMIP != GLPKConstants.GLP_FEAS) {
                    break; // Paramos si el modelo se vuelve Incoherente / Infactible
                }

                // --- A. EXTRAER LA SOLUCIÓN ENCONTRADA ---
                double costoTotal = GLPK.glp_mip_obj_val(lp);
                
                // Las demás, aunque GLPK las vea óptimas para el modelo cortado, son subóptimas/factibles.
                String estadoSolucion;
                if (solucionesEncontradas == 0 && estadoMIP == GLPKConstants.GLP_OPT) {
                    estadoSolucion = "OPTIMAL";
                } else {
                    estadoSolucion = "FEASIBLE";
                }
                
                Map<String, Double> flujosActivos = new HashMap<>();
                List<Integer> columnasActivasEnEstaSolucion = new ArrayList<>();
                List<Integer> columnasInactivasEnEstaSolucion = new ArrayList<>();

                for (int i = 0; i < numUnidades; i++) {
                    UnidadOperativa ou = unidades.get(i);
                    int colY = i + 1; // Columna binaria
                    int colX = indiceVarX.get(ou.id()); // Columna continua
                    
                    double valorY = GLPK.glp_mip_col_val(lp, colY);
                    double flujoX = GLPK.glp_mip_col_val(lp, colX);

                    if (flujoX > 0.0001) {
                        flujosActivos.put(ou.id(), flujoX);
                    }

                    // Clasificamos qué decisiones binarias tomó el solver en esta ronda
                    if (valorY > 0.5) {
                        columnasActivasEnEstaSolucion.add(colY);
                    } else {
                        columnasInactivasEnEstaSolucion.add(colY);
                    }
                }

                long tiempoMs = System.currentTimeMillis() - tiempoInicio;
                solucionesEncontradas++;

                // Guardamos el resultado en nuestra lista
                solucionesDeEstaCorrida.add(new ResultadoOptimizacion(
                        "GLPK", costoTotal, tiempoMs, flujosActivos, new HashMap<>(), estadoSolucion, solucionesEncontradas
                ));

                // --- B. AGREGAR EL CORTE DE EXCLUSIÓN o RESTRICCION ---
                // Agregamos una nueva fila (restricción) al modelo sobre la marcha
                int nuevaFilaCorte = GLPK.glp_add_rows(lp, 1);
                GLPK.glp_set_row_name(lp, nuevaFilaCorte, "CorteExclusion_" + solucionesEncontradas);

                // Evita la combinación exacta actual imposibilitando que se repita.
                double limiteSuperiorCorte = columnasActivasEnEstaSolucion.size() - 1;
                GLPK.glp_set_row_bnds(lp, nuevaFilaCorte, GLPKConstants.GLP_UP, 0.0, limiteSuperiorCorte);

                // Crear los vectores de coordenadas para esta nueva restricción
                int totalElementosCorte = columnasActivasEnEstaSolucion.size() + columnasInactivasEnEstaSolucion.size();
                SWIGTYPE_p_int iaCorte = GLPK.new_intArray(totalElementosCorte + 1);
                SWIGTYPE_p_int jaCorte = GLPK.new_intArray(totalElementosCorte + 1);
                SWIGTYPE_p_double arCorte = GLPK.new_doubleArray(totalElementosCorte + 1);

                int idx = 1;
                for (int col : columnasActivasEnEstaSolucion) {
                    GLPK.intArray_setitem(iaCorte, idx, nuevaFilaCorte);
                    GLPK.intArray_setitem(jaCorte, idx, col);
                    GLPK.doubleArray_setitem(arCorte, idx, 1.0); // Coeficiente +1
                    idx++;
                }
                for (int col : columnasInactivasEnEstaSolucion) {
                    GLPK.intArray_setitem(iaCorte, idx, nuevaFilaCorte);
                    GLPK.intArray_setitem(jaCorte, idx, col);
                    GLPK.doubleArray_setitem(arCorte, idx, -1.0); // Coeficiente -1
                    idx++;
                }

                // Inyectar la fila de exclusión de manera directa a la matriz del modelo
                GLPK.glp_set_mat_row(lp, nuevaFilaCorte, totalElementosCorte, jaCorte, arCorte);

                // Liberar memoria del corte para la siguiente iteración
                GLPK.delete_intArray(iaCorte);
                GLPK.delete_intArray(jaCorte);
                GLPK.delete_doubleArray(arCorte);
            }
            
        } catch (Exception e) {
            System.err.println("Error estructurando GLPK: " + e.getMessage());
            solucionesDeEstaCorrida.add(crearResultadoError("ERROR", tiempoInicio));
            
        } finally {
            // ¡CRÍTICO en C / GLPK! Liberar memoria manualmente
            if (lp != null) {
                GLPK.glp_delete_prob(lp);
            }
        }

        return solucionesDeEstaCorrida;
    }

    // Método estático auxiliar para errores
    private static ResultadoOptimizacion crearResultadoError(String estado, long tiempoInicio) {
        long tiempoMs = System.currentTimeMillis() - tiempoInicio;
        return new ResultadoOptimizacion(
                "GLPK", 0.0, tiempoMs, new HashMap<>(), new HashMap<>(), estado, 0
        );
    }
}

