package app.portafolio.solucionadores;

import app.portafolio.dominio.Activo;
import app.portafolio.dominio.Cliente;
import app.portafolio.dominio.ResultadoPortafolio;
import app.portafolio.util.MatrizCorrelacion;
import app.portafolio.util.RiesgoUtils;
import app.portafolio.util.Validador;

import java.util.*;

/**
 * BacktrackingBnB: busca combinaciones con poda
 * para evitar explorar ramas que no mejoran o ayudan a la solución.
 */
public class BacktrackingBnB {

    // =======================  CONSTANTES (para legibilidad)  =======================

    /** Mínimo y máximo de activos por portafolio (cardinalidad). */
    private static final int K_MIN = 3;
    private static final int K_MAX = 6;

    /** Penalización usada en el score: r - α·corr */
    private static final double ALPHA_CORR = 0.02;

    /** Tolerancias numéricas para comparaciones de double. */
    private static final double EPSILON_RIESGO   = 1e-9;
    private static final double EPSILON_RETORNO  = 1e-12;
    private static final double EPSILON_SCORE    = 1e-9;
    private static final double EPSILON_SUM_PESOS = 1e-9;

    /** Umbral de suma de mínimos permitido (para evitar >1.0). */
    private static final double UMBRAL_MINIMOS = 1.0 + 1e-12;

    /** Valor mínimo considerado "positivo" para retorno acumulado. */
    private static final double RETORNO_MIN_POSITIVO = 1e-12;

    // ===============================================================================

    private static ResultadoPortafolio mejor;
    private static double mejorRetorno;
    private static double mejorScore;

    private static Map<String, Double> cuotasSector;
    private static Map<String, Double> cuotasTipo;
    private static double retMinUsado;

    public static ResultadoPortafolio optimizar(List<Activo> universo,
                                                MatrizCorrelacion rho,
                                                Cliente cliente,
                                                Map<String, Double> cuotaSector,
                                                Map<String, Double> cuotaTipo,
                                                double retMinUsadoParam) {

        cuotasSector = cuotaSector;
        cuotasTipo   = cuotaTipo;
        retMinUsado  = retMinUsadoParam;

        List<Activo> candidatos = filtrarPorPreferencias(universo, cliente);

        candidatos.sort(new Comparator<Activo>() {
            @Override public int compare(Activo a1, Activo a2) {
                double s1 = a1.retornoEsperado() / Math.max(EPSILON_RIESGO, a1.riesgo());
                double s2 = a2.retornoEsperado() / Math.max(EPSILON_RIESGO, a2.riesgo());
                return Double.compare(s2, s1);
            }
        });

        mejor = null;
        mejorRetorno = -1.0;
        mejorScore = -1.0;

        backtrack(0, new ArrayList<Activo>(), candidatos, rho, cliente);
        return mejor;
    }

    private static void backtrack(int idx,
                                  List<Activo> elegidos,
                                  List<Activo> candidatos,
                                  MatrizCorrelacion rho,
                                  Cliente cliente) {

        if (elegidos.size() >= K_MIN && elegidos.size() <= K_MAX) {
            double[] w = asignarPesos(elegidos, cliente.montoTotal());
            if (w != null) {
                double r = RiesgoUtils.retorno(elegidos, w);
                double s = RiesgoUtils.riesgo(elegidos, w, rho);
                double c = RiesgoUtils.correlacionPromedio(elegidos, rho);
                double score = r - ALPHA_CORR * c;

                boolean okPerfil = (s <= cliente.perfil().riesgoMax() + EPSILON_RIESGO)
                                && (r + EPSILON_RETORNO >= retMinUsado);
                boolean okCuotas = Validador.cumplenCuotasMinimas(elegidos, w, cuotasSector, cuotasTipo);

                if (okPerfil && okCuotas) {
                    boolean mejoraRet = (r > mejorRetorno);
                    boolean empateRetMejorScore = (Math.abs(r - mejorRetorno) < EPSILON_SCORE && score > mejorScore);
                    if (mejoraRet || empateRetMejorScore) {
                        mejorRetorno = r;
                        mejorScore = score;
                        String comentario = generarComentario(elegidos, r, s, c, cliente, retMinUsado);
                        mejor = new ResultadoPortafolio(elegidos, w, r, s, c, comentario);
                    }
                }
            }
        }

        if (elegidos.size() == K_MAX || idx == candidatos.size()) return;

        double cota = cotaSuperiorRetorno(elegidos, idx, candidatos, cliente);
        if (cota <= mejorRetorno + EPSILON_RETORNO) return;

        Activo actual = candidatos.get(idx);
        elegidos.add(actual);
        backtrack(idx + 1, elegidos, candidatos, rho, cliente);
        elegidos.remove(elegidos.size() - 1);

        backtrack(idx + 1, elegidos, candidatos, rho, cliente);
    }

    // Cálculo de cota optimista (segura)
    private static double cotaSuperiorRetorno(List<Activo> elegidos,
                                              int idx,
                                              List<Activo> candidatos,
                                              Cliente cliente) {
        double monto = cliente.montoTotal();

        double sumaMin = 0.0;
        double retMin = 0.0;
        for (Activo a : elegidos) {
            double wmin = a.precioMinimo() / monto;
            sumaMin += wmin;
            retMin += wmin * a.retornoEsperado();
        }
        if (sumaMin > UMBRAL_MINIMOS) return 0.0;

        double maxRetRestante = 0.0;
        for (int i = idx; i < candidatos.size(); i++) {
            double r = candidatos.get(i).retornoEsperado();
            if (r > maxRetRestante) maxRetRestante = r;
        }
        double slack = Math.max(0.0, 1.0 - sumaMin);
        return retMin + slack * maxRetRestante;
    }

    // ==== helpers ====

    private static List<Activo> filtrarPorPreferencias(List<Activo> universo, Cliente cliente) {
        String[] prefSectores = cliente.sectoresPreferidos();
        String[] prefTipos = cliente.tiposPreferidos();

        boolean sinSectores = (prefSectores == null || prefSectores.length == 0);
        boolean sinTipos = (prefTipos == null || prefTipos.length == 0);

        if (sinSectores && sinTipos) return new ArrayList<>(universo);

        List<Activo> resultado = new ArrayList<>();
        for (Activo a : universo) {
            boolean okSector = sinSectores || matchesAny(a.sector(), prefSectores);
            boolean okTipo   = sinTipos || matchesAny(a.tipo(), prefTipos);
            if (okSector && okTipo) resultado.add(a);
        }
        if (resultado.isEmpty()) resultado.addAll(universo);
        return resultado;
    }

    private static boolean matchesAny(String value, String[] opciones) {
        if (opciones == null || opciones.length == 0) return true;
        if (value == null) return false;
        for (String op : opciones) {
            if (op != null && value.equalsIgnoreCase(op)) return true;
        }
        return false;
    }

    private static double[] asignarPesos(List<Activo> activos, double montoTotal) {
        int n = activos.size();
        double[] w = new double[n];

        double sumaMinimos = 0.0;
        for (int i = 0; i < n; i++) {
            double wi = activos.get(i).precioMinimo() / montoTotal;
            w[i] = wi;
            sumaMinimos += wi;
        }
        if (sumaMinimos > UMBRAL_MINIMOS) return null;

        double slack = Math.max(0.0, 1.0 - sumaMinimos);

        double[] base = new double[n];
        double sumaPosRet = 0.0;
        for (int i = 0; i < n; i++) {
            double r = Math.max(0.0, activos.get(i).retornoEsperado());
            base[i] = r;
            sumaPosRet += r;
        }
        if (sumaPosRet < RETORNO_MIN_POSITIVO) {
            for (int i = 0; i < n; i++) base[i] = 1.0;
            sumaPosRet = n;
        }
        for (int i = 0; i < n; i++) {
            w[i] = w[i] + slack * (base[i] / sumaPosRet);
        }

        double s = 0.0;
        for (double x : w) s += x;
        if (Math.abs(s - 1.0) > EPSILON_SUM_PESOS) {
            for (int i = 0; i < n; i++) w[i] /= s;
        }
        return w;
    }

    private static String generarComentario(List<Activo> lista, double r, double s, double c, Cliente cli, double retMinUsado) {
        return "Óptimo por Backtracking con poda. Perfil " + cli.perfil()
                + ". Cumple retorno mínimo (>= " + String.format(Locale.US, "%.2f%%", retMinUsado * 100)
                + ") y riesgo máximo (<= " + String.format(Locale.US, "%.2f%%", cli.perfil().riesgoMax() * 100)
                + "). " + String.format(Locale.US, "(r=%.3f, sigma=%.3f, corrProm=%.3f)", r, s, c);
    }
}

