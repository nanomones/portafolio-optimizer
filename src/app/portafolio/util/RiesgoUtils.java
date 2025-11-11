package app.portafolio.util;

import app.portafolio.dominio.Activo;
import java.util.List;

public class RiesgoUtils {

    // Retorno total: sum_i w[i] * r_i
    public static double retorno(List<Activo> activos, double[] w) {
        double r = 0.0;
        for (int i = 0; i < activos.size(); i++) r += w[i] * activos.get(i).retornoEsperado();
        return r;
    }

    // σ_p = sqrt( sum_{i,j} w_i * w_j * σ_i * ρ_ij * σ_j )
    public static double riesgo(List<Activo> activos, double[] w, MatrizCorrelacion rho) {
        int n = activos.size();
        double var = 0.0;
        for (int i = 0; i < n; i++) {
            double si = activos.get(i).riesgo();
            for (int j = 0; j < n; j++) {
                double sj = activos.get(j).riesgo();
                double rij = rho.get(activos.get(i).id(), activos.get(j).id());
                var += w[i] * w[j] * si * rij * sj;
            }
        }
        return Math.sqrt(Math.max(0.0, var));
    }

    public static double correlacionPromedio(List<Activo> activos, MatrizCorrelacion rho) {
        int n = activos.size();
        if (n < 2) return 0.0;
        double s = 0.0; int c = 0;
        for (int i = 0; i < n; i++)
            for (int j = i+1; j < n; j++) { s += rho.get(activos.get(i).id(), activos.get(j).id()); c++; }
        return s / c;
    }
}
