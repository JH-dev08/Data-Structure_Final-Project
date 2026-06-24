package Persistencia;

import Modelo.UnidadOperativa;
import Modelo.TasaFlujo;
import Modelo.Material;
import Modelo.ModeloPGraph;
import java.io.*;
import java.util.regex.*;

public class CargarPGraph {
    private enum Section { NONE, MATERIALS, OPERATING_UNITS, FLOW_RATES }           // Categorizar linea de texto en el Modelo

    public ModeloPGraph loadModel(File file) throws IOException, LectorExcepciones {
        ModeloPGraph model = new ModeloPGraph();
        
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;
            Section currentSection = Section.NONE;                                  // Inicialmente no se tiene ninguna categoria

            while ((line = br.readLine()) != null) {
                lineNumber++;
                line = line.trim();                                                 // Elimina espacios en blanco al inicio y final

                // Ignorar líneas vacías o comentarios si existieran
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // Cambios de sección detectados por las etiquetas base
                if (line.equals("materials:")) {
                    currentSection = Section.MATERIALS;
                    continue;
                } else if (line.equals("operating_units:")) {
                    currentSection = Section.OPERATING_UNITS;
                    continue;
                } else if (line.equals("material_to_operating_unit_flow_rates:")) {
                    currentSection = Section.FLOW_RATES;
                    continue;
                }

                // Procesamiento según la sección activa
                switch (currentSection) {
                    case MATERIALS -> parseMaterial(line, model, lineNumber);
                    case OPERATING_UNITS -> parseOperatingUnit(line, model, lineNumber);
                    case FLOW_RATES -> parseFlowRate(line, model, lineNumber);
                    default -> throw new LectorExcepciones("Datos huérfanos fuera de una sección válida", lineNumber);
                }
            }
        }
        return model;
    }

    private void parseMaterial(String line, ModeloPGraph model, int lineNum) throws LectorExcepciones {
        // Ejemplo esperado: A: raw_material o D: product, flow_rate_lower_bound=10
        String[] parts = line.split(":", 2);
        if (parts.length < 2) throw new LectorExcepciones("Formato de material inválido", lineNum);

        String id = parts[0].trim();
        String details = parts[1].trim();

        String type = details.split(",")[0].trim(); // raw_material, intermediate, product
        double lowerBound = 0.0;

        // Extraer atributos opcionales mediante expresiones regulares si existen
        if (details.contains("flow_rate_lower_bound")) {
            lowerBound = extractDoubleAttribute(details, "flow_rate_lower_bound", lineNum);
        }

        // Se instancia el objeto del dominio y se añade al modelo cargado en memoria
        model.addMaterial(new Material(id, type, lowerBound));
    }
    
    private double extractDoubleAttribute(String text, String attributeName, int lineNum) throws LectorExcepciones {
        try {
            // Buscamos el patrón "nombreAtributo=numero"
            String regex = attributeName + "\\s*=\\s*([0-9]*\\.?[0-9]+)";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
            java.util.regex.Matcher matcher = pattern.matcher(text);
            
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
            return 0.0;
        } catch (NumberFormatException e) {
            throw new LectorExcepciones("Error al leer el número del atributo " + attributeName, lineNum);
        }
    }

    private void parseOperatingUnit(String line, ModeloPGraph model, int lineNum) throws LectorExcepciones {
        // Ejemplo esperado: O1: capacity_upper_bound=1000, fix_cost=4, proportional_cost=2
        String[] parts = line.split(":", 2);
        if (parts.length < 2) throw new LectorExcepciones("Formato de unidad operativa inválido", lineNum);

        String id = parts[0].trim();
        String details = parts[1].trim();

        double capacity = extractDoubleAttribute(details, "capacity_upper_bound", lineNum);
        double fixCost = extractDoubleAttribute(details, "fix_cost", lineNum);
        double propCost = extractDoubleAttribute(details, "proportional_cost", lineNum);

        model.addUnidadOperativa(new UnidadOperativa(id, capacity, fixCost, propCost));
    }

    private void parseFlowRate(String line, ModeloPGraph model, int lineNum) throws LectorExcepciones {
        // Ejemplo esperado: (A, O1): 5.5 o (O1, D): 1.0
        Pattern pattern = Pattern.compile("\\(([^,]+),\\s*([^\\)]+)\\):\\s*([\\d\\.]+)");
        Matcher matcher = pattern.matcher(line);

        if (!matcher.find()) {
            throw new LectorExcepciones("Formato de relación de flujo incorrecto. Se esperaba (Nodo1, Nodo2): valor", lineNum);
        }

        String sourceId = matcher.group(1).trim();
        String targetId = matcher.group(2).trim();
        double rate = Double.parseDouble(matcher.group(3));

        model.addTasaFlujo(new TasaFlujo(sourceId, targetId, rate));
    }
}