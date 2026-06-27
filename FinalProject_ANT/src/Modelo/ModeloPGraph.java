package Modelo;

import java.util.ArrayList;
import java.util.List;

// El modelo esta compuesto por: Materiales, Unidades operativas y cantidades de flujo

public class ModeloPGraph {
    private final List<Material> materiales = new ArrayList<>();
    private final List<UnidadOperativa> unidadesOperativas = new ArrayList<>();
    private final List<TasaFlujo> tasaFlujos = new ArrayList<>();

    // Métodos para agregar
    public void addMaterial(Material m) { materiales.add(m); }
    public void addUnidadOperativa(UnidadOperativa u) { unidadesOperativas.add(u); }
    public void addTasaFlujo(TasaFlujo t) { tasaFlujos.add(t); }

    // Getters
    public List<Material> getMateriales() { return materiales; }
    public List<UnidadOperativa> getUnidadesOperativas() { return unidadesOperativas; }
    public List<TasaFlujo> getTasaFlujos() { return tasaFlujos; }
}