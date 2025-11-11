package app.portafolio.util;

import app.portafolio.dominio.Activo;
import java.util.List;

public class RiesgoUtils {

    // =======================  CONSTANTES  =======================
    /** Valor mínimo permitido de varianza (evita negativos por redondeo). */
    private static final double VARIANZA_MINIMA = 0.0;

    /** Tamaño mínimo de lista para calcular correlación promedio. */
    private static final int MIN_ACTIVOS_CORRELACION = 2;

    // ============================================================

    /** Retorno total: sum_i w[i] * r_i */
    public static double retorno(List<Activo> activos, double[] w) {
        double retornoTotal = 0.0;
        for (int i = 0; i < activos.size(); i++) {
            retornoTotal += w[i] * activos.get(i).retornoEsperado();
        }
        return retornoTotal;
    }

    /** Riesgo total σ_p = sqrt( sum_{i,j} w_i * w_j * σ_i * ρ_ij * σ_j ) */
    public static double riesgo(List<Activo> activos, double[] w, MatrizCorrelacion rho) {
        int n = activos.size();
        double varianza = 0.0;

        for (int i = 0; i < n; i++) {
            double si = activos.get(i).riesgo();
            for (int j = 0; j < n; j++) {
                double sj = activos.get(j).riesgo();
                double rij = rho.get(activos.get(i).id(), activos.get(j).id());
                varianza += w[i] * w[j] * si * rij * sj;
            }
        }
        return Math.sqrt(Math.max(VARIANZA_MINIMA, varianza));
    }

    /** Correlación promedio entre los activos del portafolio. */
    public static double correlacionPromedio(List<Activo> activos, MatrizCorrelacion rho) {
        int n = activos.size();
        if (n < MIN_ACTIVOS_CORRELACION) return 0.0;

        double sumaCorrelaciones = 0.0;
        int cantidad = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                sumaCorrelaciones += rho.get(activos.get(i).id(), activos.get(j).id());
                cantidad++;
            }
        }

        return sumaCorrelaciones / cantidad;
    }
}
