// CARDENAS_ANDRES_010_HERRERA_JOSE_001_MORALES_JADER_028
package Interfaz;

import Modelo.ModeloPGraph;
import Optimizacion.*;
import Persistencia.*;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.*;

public class VentanaPrincipal {
    private final CargarPGraph archi = new CargarPGraph();
    private BorderPane root;
    private ModeloPGraph modeloActual;
    
    // Componentes de la UI
    private TextArea consola;
    private ComboBox<String> comboSolvers;
    private Button btnCargar;
    private Button btnEjecutar;
    private Button btnExportarJSON;
    
    // Variables globales existentes...
    private final List<ResultadoOptimizacion> historialResultados = new ArrayList<>(); // Lista de soluciones

    public VentanaPrincipal(Stage stage) {
        root = new BorderPane();
        root.setPadding(new Insets(10));

        // 1. Panel Izquierdo: Controles
        VBox panelIzquierdo = crearPanelControles(stage);
        root.setLeft(panelIzquierdo);

        // 2. Componente de Consola (panel central)
        consola = new TextArea();
        consola.setEditable(false);
        consola.setPromptText("Esperando carga de modelo...");
        
        // 3. Tablas y Gráficos (Panel derecho)
        VBox panelAnaliticaCompleto = PanelAnalitica.crearPanelDashboard();
        
        // 4. SplitPane: Permite al usuario arrastrar el tamaño (Separador)
        SplitPane splitPane = new SplitPane();
        
        splitPane.getItems().addAll(consola, panelAnaliticaCompleto);
        splitPane.setDividerPositions(0.3f); // 30% consola, 70% dashboard
        
        root.setCenter(splitPane);
    }

    private VBox crearPanelControles(Stage stage) {
        VBox vbox = new VBox(15); // Espaciado de 15px
        vbox.setPadding(new Insets(10));
        vbox.setPrefWidth(250);
        vbox.setStyle("-fx-background-color: #f4f4f4; -fx-border-color: #cccccc;");

        Label lblTitulo = new Label("Configuración PNS");
        lblTitulo.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Botón Cargar Archivo
        btnCargar = new Button("📂 Cargar Modelo .txt");
        btnCargar.setMaxWidth(Double.MAX_VALUE);
        btnCargar.setOnAction(e -> cargarArchivo(stage));
        
        // Botón Exportar Soluciones
        btnExportarJSON = new Button("💾 Exportar soluciones en .JSON");
        btnExportarJSON.setMaxWidth(Double.MAX_VALUE);
        btnExportarJSON.setDisable(true); // Se desactiva hasta que Gurobi termine
        btnExportarJSON.setOnAction(e -> exportarJSONAutomatico());

        // Selector de Solvers
        Label lblSolver = new Label("Seleccione el Solver:");
        comboSolvers = new ComboBox<>();
        comboSolvers.getItems().addAll("Apache Commons Math", "GLPK", "Gurobi", "CPLEX");
        comboSolvers.setValue("Gurobi"); // Valor por defecto
        comboSolvers.setMaxWidth(Double.MAX_VALUE);

        // Botón Ejecutar
        btnEjecutar = new Button("▶ Ejecutar Optimización");
        btnEjecutar.setMaxWidth(Double.MAX_VALUE);
        btnEjecutar.setDisable(true); // Desactivado hasta que se cargue un archivo
        btnEjecutar.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
        btnEjecutar.setOnAction(e -> ejecutarSolver());

        vbox.getChildren().addAll(lblTitulo, btnCargar, btnExportarJSON, lblSolver, comboSolvers, btnEjecutar);
        return vbox;
    }

    private void cargarArchivo(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Abrir archivo de modelo PNS");

        // Filtro para que solo muestre archivos de texto
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Archivos de Texto PNS (*.txt)", "*.txt")
        );

        // Abrir la ventana nativa de selección de archivos
        File archivo = fileChooser.showOpenDialog(stage);

        if (archivo != null) {
            try {
                consola.clear();
                consola.appendText("⏳ Leyendo archivo: " + archivo.getName() + "...\n");
                
                historialResultados.clear();
                PanelAnalitica.limpiarTodo();
                
                // ──> CONEXION DEL LECTOR
                modeloActual = archi.cargarModelo(archivo); 

                // Si la lectura fue exitosa, imprimimos las estadísticas en la consola central
                consola.appendText("✅ ¡Modelo cargado exitosamente!\n");
                consola.appendText("==================================\n");
                consola.appendText("📊 RESUMEN ESTRUCTURAL:\n");
                consola.appendText("• Materiales detectados: " + modeloActual.getMateriales().size() + "\n");
                consola.appendText("• Unidades operativas:   " + modeloActual.getUnidadesOperativas().size() + "\n");
                consola.appendText("• Relaciones de flujo:   " + modeloActual.getTasaFlujos().size() + "\n");
                consola.appendText("==================================\n");
                consola.appendText("💡 Listo para optimizar. Seleccione un solver y presione 'Ejecutar'.\n");

                // Habilitamos el botón de ejecución ya que hay datos válidos en memoria
                btnEjecutar.setDisable(false); 

            } catch (Exception ex) {
                // Error de archivo corrupto
                consola.appendText("\n❌ ERROR AL PROCESAR EL ARCHIVO:\n");
                consola.appendText(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                ex.printStackTrace(); // Ver el error real en la consola de NetBeans

                btnEjecutar.setDisable(true); // Mantenemos el botón apagado si falló
            }
        }
    }
    
    // CONEXION CON EL MODELO LOGICO MILP
    private void ejecutarSolver() {
        if (modeloActual == null) {
            consola.appendText("⚠️ Error: Primero debe cargar un modelo.\n");
            return;
        }

        String solverElegido = comboSolvers.getValue();
        try {
            consola.appendText("\n🚀 Iniciando optimización con " + solverElegido + "...\n");

            List<ResultadoOptimizacion> res = null;
            
            // Llamada al Solver Optimizador
            if ("Gurobi".equals(solverElegido)) {
                res = OptimizadorGurobi.resolverModelo(modeloActual);
            } else if("Apache Commons Math".equals(solverElegido)) {
                res = OptimizadorApache.resolverModelo(modeloActual);
            } else if("CPLEX".equals(solverElegido)) {
                res = OptimizadorCplex.resolverModelo(modeloActual);
            } else if("GLPK".equals(solverElegido)) {
                res = OptimizadorGlpk.resolverModelo(modeloActual);
            }
            
            if(res != null){
                this.historialResultados.addAll(res);
                // Activamos el botón de exportación
                btnExportarJSON.setDisable(false);
                consola.appendText("✅ " + solverElegido + " terminó. Se guardaron " + res.size() + " solucion(es).\n");

                if (!res.isEmpty() || !"OPTIMAL".equals(res.get(0).estadoTexto())) {
                    ResultadoOptimizacion mejorSolucion = res.get(0);

                    consola.appendText("✅ ¡Solución Óptima Encontrada!\n");
                    consola.appendText("==================================\n");
                    consola.appendText(String.format("💰 Costo Total:      %.2f\n", res.get(0).costoTotal()));
                    consola.appendText(String.format("⏱ Tiempo de Solución: %.2f ms\n", res.get(0).tiempoEjecucionMs()));
                    consola.appendText("==================================\n");
                    consola.appendText("⚙ UNIDADES ACTIVAS (Nivel de Operación):\n");

                    // Imprimir las unidades que el Solver decidió encender (binario en 1)
                    res.get(0).flujosActivos().forEach((id, nivel) -> {
                        consola.appendText(String.format(" • %s procesando %.2f unidades\n", id, nivel));
                    });

                    PanelAnalitica.registrarResultadoSolver(
                        solverElegido, 
                        mejorSolucion.estadoTexto(), 
                        mejorSolucion.costoTotal(), 
                        mejorSolucion.tiempoEjecucionMs(), 
                        res.size()
                    );
                } else {
                    consola.appendText("❌ ¡Solución Óptima No Encontrada!\n");
                }
            } else {
                consola.appendText("❌ ¡No se encontró ninguna solución!\n");
            }
            return;
        } catch (Exception ex) {
            consola.appendText("❌ Error en la optimización:\n" + ex.getMessage() + "\n");
        }
        
        consola.appendText("\n⚠️ Solver " + solverElegido + " aún no implementado.\n");
    }
    
    private void exportarJSONAutomatico() {
        // Validamos que al menos se haya ejecutado un solver
        if (this.historialResultados.isEmpty()) {
            consola.appendText("⚠️ No hay ninguna ejecución en el historial para exportar.\n");
            return;
        }

        try {
            // 1. Crear o identificar la carpeta "data"
            File carpetaData1 = new File("data//Soluciones_Optimas");
            File carpetaData2 = new File("data//Soluciones_Factibles");
            if (!carpetaData1.exists()) {
                carpetaData1.mkdirs();
            }
            if (!carpetaData2.exists()) {
                carpetaData2.mkdirs();
            }

            // 2. Separar las soluciones en dos listas distintas
            List<ResultadoOptimizacion> solucionesOptimas = new ArrayList<>();
            List<ResultadoOptimizacion> solucionesFactibles = new ArrayList<>();

            for (ResultadoOptimizacion res : historialResultados) {
                // Si el estado es OPTIMAL, va a la primera lista
                if ("OPTIMAL".equalsIgnoreCase(res.estadoTexto())) {
                    solucionesOptimas.add(res);
                } else {
                    // Cualquier otro estado (TIMEOUT, INFEASIBLE, etc.) va a la segunda
                    solucionesFactibles.add(res);
                }
            }

            // 3. Marca de tiempo única para los archivos de esta exportación
            String timestamp = String.valueOf(System.currentTimeMillis());
            boolean exportacionExitosa = false;

            consola.appendText("\n📊 Generando reporte...\n");

            // 4. Guardar archivo de Soluciones Óptimas (Si hay alguna)
            if (!solucionesOptimas.isEmpty()) {
                File archivoOptimas = new File(carpetaData1, "soluciones_optimas_" + timestamp + ".json");
                ExportarSoluciones.exportarJSON(solucionesOptimas, archivoOptimas);
                consola.appendText("   ✅ Óptimas exportadas (" + solucionesOptimas.size() + "): " + archivoOptimas.getName() + "\n");
                exportacionExitosa = true;
            }

            // 5. Guardar archivo de Soluciones Factibles (Si hay alguna)
            if (!solucionesFactibles.isEmpty()) {
                File archivoFactibles = new File(carpetaData2, "soluciones_factibles_" + timestamp + ".json");
                ExportarSoluciones.exportarJSON(solucionesFactibles, archivoFactibles);
                consola.appendText("   ⚠️ Factibles/Otras exportadas (" + solucionesFactibles.size() + "): " + archivoFactibles.getName() + "\n");
                exportacionExitosa = true;
            }

            if (exportacionExitosa) {
                consola.appendText("📁 ¡Exportación automática completada con éxito en la carpeta /data!\n");
            }

        } catch (Exception e) {
            consola.appendText("\n❌ Error al exportar los archivos JSON: " + e.getMessage() + "\n");
        }
    }

    public BorderPane getRoot() {
        return root;
    }
}