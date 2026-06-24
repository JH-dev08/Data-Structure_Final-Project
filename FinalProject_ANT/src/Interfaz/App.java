package Interfaz;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        // Instanciamos nuestra vista principal
        VentanaPrincipal vista = new VentanaPrincipal(primaryStage);
        
        // Configuracion de escena (tamaño de la ventana)
        Scene scene = new Scene(vista.getRoot(), 1000, 600);
        
        primaryStage.setTitle("Optimizador PNS - MILP Solvers");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args); // Arranca el hilo de JavaFX
    }
}