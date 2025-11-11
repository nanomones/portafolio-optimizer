package app.portafolio.solucionadores;

import app.portafolio.dominio.Activo;
import app.portafolio.dominio.Cliente;
import app.portafolio.dominio.ResultadoPortafolio;
import app.portafolio.util.MatrizCorrelacion;
import app.portafolio.util.RiesgoUtils;
import app.portafolio.util.Validador;
import java.util.*;

/**
 * Alternativas simples a partir del óptimo:
 * - Respetan TODAS las restricciones del cliente
 * - Nunca superan el retorno del óptimo (por diseño)
 * - Alt 1: retorno apenas menor al óptimo
 * - Alt 2: menor correlación promedio sin superar el retorno del óptimo
 *
 * Implementación simple (estudiante): swaps y agregar 1 activo (si hay lugar) con pesos iguales.
 */
public class Alternativos {

    /** Valida TODAS las restricciones pedidas por la cátedra. */
    private static boolean validaTodo(List<Activo> sel, double[] w,
                                      Cliente cliente,
                                      MatrizCorrelacion mc,
                                      Map<String,Double> cuotaSector,
                                      Map<String,Double> cuotaTipo,
                                      double retMinUsado,
                                      double montoTotal) {
        if (!Validador.cumpleCardinalidad(sel.size())) return false;
        if (!Validador.pesosUsanTodoElDinero(w)) return false;
        if (!Validador.cumplenMinimos(sel, w, montoTotal)) return false;
        if (!Validador.cumplePreferencias(sel, cliente.sectoresPreferidos(), cliente.tiposPreferidos())) return false;
        if (!Validador.cumplenCuotasMinimas(sel, w, cuotaSector, cuotaTipo)) return false;

        double r = RiesgoUtils.retorno(sel, w);
        double s = RiesgoUtils.riesgo(sel, w, mc);

        if (s > cliente.perfil().riesgoMax() + 1e-9) return false;
        if (r + 1e-12 < retMinUsado) return false;
        return true;
    }

    /** Construye el ResultadoPortafolio usando el constructor que tenés (con r, sigma, corrProm). */
    private static ResultadoPortafolio armarResultado(List<Activo> sel, double[] w,
                                                      MatrizCorrelacion mc, String comentario) {
        double r = RiesgoUtils.retorno(sel, w);
        double s = RiesgoUtils.riesgo(sel, w, mc);
        double c = RiesgoUtils.correlacionPromedio(sel, mc);
        return new ResultadoPortafolio(sel, w, r, s, c, comentario);
    }

    /** Alternativa 1: retorno apenas menor al óptimo (nunca lo supera). */
    public static ResultadoPortafolio generar1(ResultadoPortafolio optimo,
                                               List<Activo> universo,
                                               MatrizCorrelacion mc,
                                               Cliente cliente,
                                               Map<String,Double> cuotaSector,
                                               Map<String,Double> cuotaTipo,
                                               double retMinUsado) {
        if (optimo == null) return null;

        double rOpt = RiesgoUtils.retorno(optimo.activos(), optimo.pesos());
        final double DELTA = 0.005;        // 0.5% por debajo del óptimo
        final double R_MAX = rOpt - DELTA; // nunca superar rOpt

        if (R_MAX < retMinUsado + 1e-6) {
            // El óptimo ya está pegado al mínimo → no hay margen para alternativa “apenas menor”
            return null;
        }

        List<Activo> mejorSel = null;
        double[] mejorW = null;
        double mejorR = -1;

        // 1) Swaps de a un activo, pesos iguales
        for (int i = 0; i < optimo.activos().size(); i++) {
            for (Activo cand : universo) {
                if (optimo.activos().contains(cand)) continue;
                List<Activo> nueva = new ArrayList<>(optimo.activos());
                nueva.set(i, cand);

                int n = nueva.size();
                double[] w = new double[n];
                Arrays.fill(w, 1.0 / n);

                if (!validaTodo(nueva, w, cliente, mc, cuotaSector, cuotaTipo, retMinUsado, cliente.montoTotal()))
                    continue;

                double r = RiesgoUtils.retorno(nueva, w);
                // Queremos r <= R_MAX pero lo más cerca posible de rOpt
                if (r <= R_MAX + 1e-12 && r > mejorR) {
                    mejorR = r;
                    mejorSel = nueva;
                    mejorW = w;
                }
            }
        }

        // 2) Si no hay swap bueno, probamos AGREGAR un activo (si hay lugar)
        if (mejorSel == null && optimo.activos().size() < 6) {
            for (Activo cand : universo) {
                if (optimo.activos().contains(cand)) continue;
                List<Activo> nueva = new ArrayList<>(optimo.activos());
                nueva.add(cand);

                int n = nueva.size();
                double[] w = new double[n];
                Arrays.fill(w, 1.0 / n);

                if (!validaTodo(nueva, w, cliente, mc, cuotaSector, cuotaTipo, retMinUsado, cliente.montoTotal()))
                    continue;

                double r = RiesgoUtils.retorno(nueva, w);
                if (r <= R_MAX + 1e-12 && r > mejorR) {
                    mejorR = r;
                    mejorSel = nueva;
                    mejorW = w;
                }
            }
        }

        if (mejorSel == null) return null;

        String comentario = "Alternativa 1: retorno apenas menor al óptimo, respetando todas las restricciones.";
        return armarResultado(mejorSel, mejorW, mc, comentario);
    }

    /** Alternativa 2: menor correlación promedio sin superar retorno del óptimo. */
    public static ResultadoPortafolio generar2(ResultadoPortafolio optimo,
                                               List<Activo> universo,
                                               MatrizCorrelacion mc,
                                               Cliente cliente,
                                               Map<String,Double> cuotaSector,
                                               Map<String,Double> cuotaTipo,
                                               double retMinUsado) {
        if (optimo == null) return null;

        double rOpt = RiesgoUtils.retorno(optimo.activos(), optimo.pesos());
        final double R_MAX = rOpt - 1e-6; // no superar al óptimo

        List<Activo> mejorSel = null;
        double[] mejorW = null;
        double mejorCorr = Double.POSITIVE_INFINITY;

        // 1) Swaps de a uno (pesos iguales): bajar corrProm manteniendo r <= rOpt
        for (int i = 0; i < optimo.activos().size(); i++) {
            for (Activo cand : universo) {
                if (optimo.activos().contains(cand)) continue;
                List<Activo> nueva = new ArrayList<>(optimo.activos());
                nueva.set(i, cand);

                int n = nueva.size();
                double[] w = new double[n];
                Arrays.fill(w, 1.0 / n);

                if (!validaTodo(nueva, w, cliente, mc, cuotaSector, cuotaTipo, retMinUsado, cliente.montoTotal()))
                    continue;

                double r = RiesgoUtils.retorno(nueva, w);
                if (r > R_MAX) continue;

                double corr = RiesgoUtils.correlacionPromedio(nueva, mc);
                if (corr < mejorCorr - 1e-9) {
                    mejorCorr = corr;
                    mejorSel = nueva;
                    mejorW = w;
                }
            }
        }

        // 2) Si no hubo swap útil, probamos AGREGAR un activo (si hay lugar)
        if (mejorSel == null && optimo.activos().size() < 6) {
            for (Activo cand : universo) {
                if (optimo.activos().contains(cand)) continue;
                List<Activo> nueva = new ArrayList<>(optimo.activos());
                nueva.add(cand);

                int n = nueva.size();
                double[] w = new double[n];
                Arrays.fill(w, 1.0 / n);

                if (!validaTodo(nueva, w, cliente, mc, cuotaSector, cuotaTipo, retMinUsado, cliente.montoTotal()))
                    continue;

                double r = RiesgoUtils.retorno(nueva, w);
                if (r > R_MAX) continue;

                double corr = RiesgoUtils.correlacionPromedio(nueva, mc);
                if (corr < mejorCorr - 1e-9) {
                    mejorCorr = corr;
                    mejorSel = nueva;
                    mejorW = w;
                }
            }
        }

        if (mejorSel == null) return null;

        String comentario = "Alternativa 2: menor correlación promedio (más diversificación) sin superar el retorno del óptimo.";
        return armarResultado(mejorSel, mejorW, mc, comentario);
    }
}
