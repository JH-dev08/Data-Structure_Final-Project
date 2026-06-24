package Persistencia;

public class LectorExcepciones extends Exception {
    public LectorExcepciones(String message, int lineNumber) {
        super("Error de sintaxis en la línea " + lineNumber + ": " + message);
    }
}