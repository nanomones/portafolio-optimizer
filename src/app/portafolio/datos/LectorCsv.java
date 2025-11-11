package app.portafolio.datos;

import app.portafolio.dominio.Activo;
import app.portafolio.util.MatrizCorrelacion;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;

/**
 * Lector de CSVs sin librerías externas.
 * - Usa ISO-8859-1 para tolerar acentos/soft hyphen como vienen en muchos CSV.
 * - Normaliza encabezados: minúsculas, sin espacios/acentos/guiones.
 */
public class LectorCsv {

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");

    /** Normaliza encabezados a una clave estable (min, sin diacríticos, solo a-z0-9). */
    private static String norm(String s) {
        if (s == null) return "";
        String t = s.replace("\uFEFF", "").replace("\u00AD", "").trim();   // BOM/soft hyphen
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        t = t.toLowerCase().replaceAll("[^a-z0-9]", "");
        return t;
    }

    /** Lee activos desde un CSV con tus encabezados reales. */
    public static List<Activo> leerActivos(String ruta) throws Exception {
        List<Activo> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(ruta), LATIN1)) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacío: " + ruta);
            String[] cols = header.split(",", -1);

            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < cols.length; i++) idx.put(norm(cols[i]), i);

            Integer iId     = idx.get("id");
            Integer iTicker = idx.get("ticker");
            Integer iSector = idx.get("sector");
            Integer iTipo   = idx.get("tipo");
            Integer iMinInv = idx.get("inversionminima");
            Integer iRetEsp = idx.get("retornoesperado");
            Integer iRiesgo = idx.get("riesgo");

            if (iId==null || iTicker==null || iSector==null || iTipo==null
                    || iMinInv==null || iRetEsp==null || iRiesgo==null) {
                throw new IllegalArgumentException("Encabezados requeridos no encontrados. Vistos (normalizados): " + idx.keySet());
            }

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] t = line.split(",", -1); // -1: preserva campos vacíos
                String id     = t[iId].trim();
                String ticker = t[iTicker].trim();
                String sector = t[iSector].trim();
                String tipo   = t[iTipo].trim();
                double minInv = parseDoubleSafe(t[iMinInv]);
                double ret    = parseDoubleSafe(t[iRetEsp]);
                double risk   = parseDoubleSafe(t[iRiesgo]);
                out.add(new Activo(id, ticker, sector, tipo, minInv, ret, risk));
            }
        }
        return out;
    }

    /** Lee matriz de correlación: 1ra fila = IDs de columnas; 1ra columna = ID de fila. */
    public static MatrizCorrelacion leerCorrelacion(String ruta) throws Exception {
        try (BufferedReader br = Files.newBufferedReader(Path.of(ruta), LATIN1)) {
            String header = br.readLine();
            if (header == null) throw new IllegalArgumentException("CSV vacío: " + ruta);
            String[] cols = header.split(",", -1);

            List<String> idsCol = new ArrayList<>();
            for (int c = 1; c < cols.length; c++) idsCol.add(cols[c].trim());

            Map<String, Map<String, Double>> m = new LinkedHashMap<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] t = line.split(",", -1);
                String rowId = t[0].trim();
                Map<String, Double> row = new LinkedHashMap<>();
                for (int c = 1; c < t.length && c-1 < idsCol.size(); c++) {
                    row.put(idsCol.get(c - 1), parseDoubleSafe(t[c]));
                }
                m.put(rowId, row);
            }
            return new MatrizCorrelacion(m);
        }
    }

    private static double parseDoubleSafe(String s) {
        String x = (s == null) ? "" : s.trim();
        if (x.isEmpty()) return 0.0;
        x = x.replace(',', '.');  // tolera coma decimal por si acaso
        return Double.parseDouble(x);
    }
}
