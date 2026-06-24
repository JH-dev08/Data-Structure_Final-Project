package Interfaz;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

public class PanelAnalitica {
    // Listas observables globales para que se actualicen en tiempo real
    private static final ObservableList<FilaComparativa> datosTabla = FXCollections.observableArrayList();
    private static BarChart<String, Number> graficoTiempos;

    public static VBox crearPanelDashboard() {
        VBox contenedorGlobal = new VBox(15);
        contenedorGlobal.setPadding(new Insets(15));

        // --- 1. TÍTULO DEL PANEL ---
        Label titulo = new Label("📈 Análisis Comparativo de Solvers MILP");
        titulo.setStyle("-font-size: 18px; -fx-font-weight: bold;");

        // --- 2. CREACIÓN DE LA TABLA (Requisito: Tablas e Indicadores) ---
        TableView<FilaComparativa> tabla = new TableView<>(datosTabla);
        tabla.setPrefHeight(180);

        TableColumn<FilaComparativa, String> colSolver = new TableColumn<>("Solver");
        colSolver.setCellValueFactory(cellData -> cellData.getValue().solverProperty());

        TableColumn<FilaComparativa, String> colEstado = new TableColumn<>("Estado Solución");
        colEstado.setCellValueFactory(cellData -> cellData.getValue().estadoProperty());
        // Formato visual para el Estado (Indicador visual color Verde/Rojo)
        colEstado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equalsIgnoreCase("OPTIMAL")) {
                        setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    }
                }
            }
        });

        TableColumn<FilaComparativa, Number> colObjetivo = new TableColumn<>("Función Objetivo ($)");
        colObjetivo.setCellValueFactory(cellData -> cellData.getValue().costoObjetivoProperty());

        TableColumn<FilaComparativa, Number> colTiempo = new TableColumn<>("Tiempo (ms)");
        colTiempo.setCellValueFactory(cellData -> cellData.getValue().tiempoMsProperty());

        TableColumn<FilaComparativa, Number> colSoluciones = new TableColumn<>("Soluciones Encontradas");
        colSoluciones.setCellValueFactory(cellData -> cellData.getValue().solucionesEncontradasProperty());

        tabla.getColumns().addAll(colSolver, colEstado, colObjetivo, colTiempo, colSoluciones);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // --- 3. CREACIÓN DEL GRÁFICO DE TIEMPOS (Requisito: Gráficos) ---
        CategoryAxis ejeX = new CategoryAxis();
        ejeX.setLabel("Solvers");
        NumberAxis ejeY = new NumberAxis();
        ejeY.setLabel("Tiempo (ms)");

        graficoTiempos = new BarChart<>(ejeX, ejeY);
        graficoTiempos.setTitle("Comparativa de Rendimiento Computacional");
        graficoTiempos.setLegendVisible(false);
        graficoTiempos.setPrefHeight(250);

        XYChart.Series<String, Number> serieTiempos = new XYChart.Series<>();
        serieTiempos.setName("Tiempo");
        graficoTiempos.getData().add(serieTiempos);

        // Inicializamos los solvers base en la tabla y gráfico con valores 0 o vacíos
        inicializarSolversVacios();

        // Agregamos todo al contenedor
        contenedorGlobal.getChildren().addAll(titulo, new Label("Resumen de Métricas:"), tabla, graficoTiempos);
        return contenedorGlobal;
    }

    private static void inicializarSolversVacios() {
        // Cubrir con Platform.runLater para obligar a JavaFX a procesarlo de forma segura - evita congelamiento :(
        Platform.runLater(() -> {
            
            // 1. Tabla Comparativa
            datosTabla.clear();
            datosTabla.add(new FilaComparativa("Gurobi", "No ejecutado", 0.0, 0.0, 0));
            datosTabla.add(new FilaComparativa("Apache Commons Math", "No ejecutado", 0.0, 0.0, 0));
            datosTabla.add(new FilaComparativa("CPLEX", "No ejecutado", 0.0, 0.0, 0));
            datosTabla.add(new FilaComparativa("GLPK", "No ejecutado", 0.0, 0.0, 0));
            
            // 2. Gráfico de barras
            if (graficoTiempos != null && !graficoTiempos.getData().isEmpty()) {
                XYChart.Series<String, Number> serie = graficoTiempos.getData().get(0);
                
                if (serie.getData().isEmpty()) {
                    // Si el gráfico es nuevo y no tiene barras aún, las creamos
                    serie.getData().add(new XYChart.Data<>("Gurobi", 0.0));
                    serie.getData().add(new XYChart.Data<>("Apache Commons Math", 0.0));
                    serie.getData().add(new XYChart.Data<>("CPLEX", 0.0));
                    serie.getData().add(new XYChart.Data<>("GLPK", 0.0));
                } else {
                    // Si ya hay barras dibujadas, solo asignamos valores 0.0
                    for (XYChart.Data<String, Number> data : serie.getData()) {
                        data.setYValue(0.0);
                    }
                }
            }
        });
    }

    // Actualizar las tablas y graficos
    public static void registrarResultadoSolver(String nombreSolver, String estado, double funcionObjetivo, double tiempoMs, int soluciones) {
        // 1. Actualizar la Tabla
        for (FilaComparativa fila : datosTabla) {
            if (fila.getSolver().equalsIgnoreCase(nombreSolver)) {
                fila.setEstado(estado);
                fila.setCostoObjetivo(funcionObjetivo);
                fila.setTiempoMs(tiempoMs);
                fila.setSolucionesEncontradas(soluciones);
                break;
            }
        }

        // 2. Actualizar el Gráfico de Barras
        if (!graficoTiempos.getData().isEmpty()) {
            XYChart.Series<String, Number> serie = graficoTiempos.getData().get(0);
            for (XYChart.Data<String, Number> data : serie.getData()) {
                if (data.getXValue().equalsIgnoreCase(nombreSolver)) {
                    data.setYValue(tiempoMs);
                    break;
                }
            }
        }
        
        // 3. Analizar Diferencias Automáticamente.
        evaluarDiferenciasDeResultados();
    }

    private static void evaluarDiferenciasDeResultados() {
        // Aquí puedes comparar si los solvers que ya corrieron llegaron al mismo costo objetivo.
        // Si difieren, puedes mandar alertas a la consola o abrir un Label de advertencia.
        FilaComparativa gurobi = datosTabla.get(0);
        FilaComparativa commons_math = datosTabla.get(1);
        FilaComparativa cplex = datosTabla.get(2);
        FilaComparativa glpk = datosTabla.get(3);

        if (!gurobi.getEstado().equals("No ejecutado") && !commons_math.getEstado().equals("No ejecutado") 
                && !cplex.getEstado().equals("No ejecutado") && !glpk.getEstado().equals("No ejecutado")) {

            // Tolerancia para diferencias de redondeo entre solvers
            double tolerancia = 0.001;

            // Tomamos el de Gurobi como "referencia" y verificamos que nadie se aleje de él
            boolean difMath  = Math.abs(gurobi.getCostoObjetivo() - commons_math.getCostoObjetivo()) > tolerancia;
            boolean difCplex = Math.abs(gurobi.getCostoObjetivo() - cplex.getCostoObjetivo()) > tolerancia;
            boolean difGlpk  = Math.abs(gurobi.getCostoObjetivo() - glpk.getCostoObjetivo()) > tolerancia;

            if (difMath || difCplex || difGlpk) {
                System.out.println("⚠️ ALERTA: ¡Se encontraron funciones objetivo diferentes en algunos solvers!");
            }
        }
    }
    
    public static void limpiarTodo() {
        if (graficoTiempos != null && !graficoTiempos.getData().isEmpty()) {
            inicializarSolversVacios();
        }
    }
}