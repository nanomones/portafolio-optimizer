package app.portafolio.util;

import java.util.*;

public class MatrizCorrelacion {
    private final Map<String, Map<String, Double>> m;

    public MatrizCorrelacion(Map<String, Map<String, Double>> matriz) {
        this.m = matriz;
    }

    public double get(String a, String b) {
        var row = m.get(a);
        if (row == null) throw new IllegalArgumentException("Falta fila para: " + a);
        var v = row.get(b);
        if (v == null) throw new IllegalArgumentException("Falta columna para: " + b + " en fila " + a);
        return v;
    }

    /** IDs presentes como filas (clave externa del mapa). */
    public Set<String> idsFila() {
        return Collections.unmodifiableSet(m.keySet());
    }

    /** IDs presentes como columnas (tomados de la primera fila). */
    public Set<String> idsColumna() {
        if (m.isEmpty()) return Set.of();
        var first = m.values().iterator().next();
        return Collections.unmodifiableSet(first.keySet());
    }

    /** Verifica simetría y diagonal = 1.0 con tolerancia numérica. */
    public boolean esSimetricaConDiagonalUno() {
        for (String i : m.keySet()) {
            if (Math.abs(get(i,i) - 1.0) > 1e-9) return false;
            for (String j : m.keySet()) {
                if (Math.abs(get(i,j) - get(j,i)) > 1e-9) return false;
            }
        }
        return true;
    }
}
