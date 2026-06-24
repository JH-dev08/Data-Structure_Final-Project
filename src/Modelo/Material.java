package Modelo;

public record Material(String id, String tipo, double flujoMinimo) {}
// puede ser de tipo --> A: raw_material, B: intermediate, C: product

/* 
    La declaracion "record" genera automaticamente --> campos privados y finales, constructor
    metodos getter (), por ejemplo:     material_1.tipo()
    Sobrescribe automáticamente equals(), hashCode() y toString().
*/