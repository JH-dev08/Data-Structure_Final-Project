package Interfaz;

import javafx.beans.property.*;

public class FilaComparativa {
    private final StringProperty solver;
    private final StringProperty estado;
    private final DoubleProperty costoObjetivo;
    private final DoubleProperty tiempoMs;
    private final IntegerProperty solucionesEncontradas;

    public FilaComparativa(String solver, String estado, double costoObjetivo, double tiempoMs, int soluciones) {
        this.solver = new SimpleStringProperty(solver);
        this.estado = new SimpleStringProperty(estado);
        this.costoObjetivo = new SimpleDoubleProperty(costoObjetivo);
        this.tiempoMs = new SimpleDoubleProperty(tiempoMs);
        this.solucionesEncontradas = new SimpleIntegerProperty(soluciones);
    }

    // Getters para las propiedades (requeridos por el TableView de JavaFX)
    public StringProperty solverProperty() { return solver; }
    public StringProperty estadoProperty() { return estado; }
    public DoubleProperty costoObjetivoProperty() { return costoObjetivo; }
    public DoubleProperty tiempoMsProperty() { return tiempoMs; }
    public IntegerProperty solucionesEncontradasProperty() { return solucionesEncontradas; }

    public String getSolver() { return solver.get(); }
    public double getCostoObjetivo() { return costoObjetivo.get(); }
    public void setCostoObjetivo(double v) { this.costoObjetivo.set(v); }
    public String getEstado() { return estado.get(); }
    public void setEstado(String v) { this.estado.set(v); }
    public double getTiempoMs() { return tiempoMs.get(); }
    public void setTiempoMs(double v) { this.tiempoMs.set(v); }
    public int getSolucionesEncontradas() { return solucionesEncontradas.get(); }
    public void setSolucionesEncontradas(int v) { this.solucionesEncontradas.set(v); }
}