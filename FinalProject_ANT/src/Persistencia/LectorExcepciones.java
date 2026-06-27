package Persistencia;

public class LectorExcepciones extends Exception {
    public LectorExcepciones(String mensaje, int numLine) {
        super("Error de sintaxis en la línea " + numLine + ": " + mensaje);
    }
}