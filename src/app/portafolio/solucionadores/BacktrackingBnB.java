package app.portafolio.solucionadores;

import app.portafolio.dominio.Activo;
import app.portafolio.dominio.Cliente;
import app.portafolio.dominio.ResultadoPortafolio;
import app.portafolio.util.MatrizCorrelacion;
import app.portafolio.util.RiesgoUtils;
import app.portafolio.util.Validador;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backtracking con Branch & Bound (poda), modo estudiante.
 * - Maximiza retorno cumpliendo perfil (con retMinUsado), mínimos, 100% dinero y 3..6 activos.
 * - Cota superior optimista de retorno.
 * - Valida cuotas mínimas por sector/tipo (si se pasan).
 * - Desempata por score = retorno - alpha*corr (para favorecer diversificación).
 */
public class BacktrackingBnB {

    private static final int K_MIN = 3;
    private static final int K_MAX = 6;
    private static final double ALPHA_CORR = 0.02;

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
                double s1 = a1.retornoEsperado() / Math.max(1e-9, a1.riesgo());
                double s2 = a2.retornoEsperado() / Math.max(1e-9, a2.riesgo());
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

                boolean okPerfil = (s <= cliente.perfil().riesgoMax() + 1e-9)
                                && (r + 1e-12 >= retMinUsado);
                boolean okCuotas = Validador.cumplenCuotasMinimas(elegidos, w, cuotasSector, cuotasTipo);

                if (okPerfil && okCuotas) {
                    boolean mejoraRet = (r > mejorRetorno);
                    boolean empateRetMejorScore = (Math.abs(r - mejorRetorno) < 1e-9 && score > mejorScore);
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
        if (cota <= mejorRetorno + 1e-12) return;

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
        for (int i = 0; i < elegidos.size(); i++) {
            Activo a = elegidos.get(i);
            double wmin = a.precioMinimo() / monto;
            sumaMin += wmin;
            retMin += wmin * a.retornoEsperado();
        }
        if (sumaMin > 1.0 + 1e-12) return 0.0;

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
        for (int i = 0; i < universo.size(); i++) {
            Activo a = universo.get(i);
            boolean okSector = sinSectores || matchesAny(a.sector(), prefSectores);
            boolean okTipo   = sinTipos || matchesAny(a.tipo(),   prefTipos);
            if (okSector && okTipo) resultado.add(a);
        }
        if (resultado.isEmpty()) resultado.addAll(universo);
        return resultado;
    }

    private static boolean matchesAny(String value, String[] opciones) {
        if (opciones == null || opciones.length == 0) return true;
        if (value == null) return false;
        for (int i = 0; i < opciones.length; i++) {
            String op = opciones[i];
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
        if (sumaMinimos > 1.0 + 1e-12) return null;

        double slack = Math.max(0.0, 1.0 - sumaMinimos);

        double[] base = new double[n];
        double sumaPosRet = 0.0;
        for (int i = 0; i < n; i++) {
            double r = Math.max(0.0, activos.get(i).retornoEsperado());
            base[i] = r;
            sumaPosRet += r;
        }
        if (sumaPosRet < 1e-12) {
            for (int i = 0; i < n; i++) base[i] = 1.0;
            sumaPosRet = n;
        }
        for (int i = 0; i < n; i++) {
            w[i] = w[i] + slack * (base[i] / sumaPosRet);
        }

        double s = 0.0;
        for (double x : w) s += x;
        if (Math.abs(s - 1.0) > 1e-9) {
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
