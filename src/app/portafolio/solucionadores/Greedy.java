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
 * Greedy (codicioso) simple, modo estudiante
 * - Filtra por preferencias (blandas).
 * - Ordena por retorno/riesgo.
 * - Agrega activos maximizando score = retorno - alpha*corrProm (penalización suave).
 * - Asigna pesos: mínimos + reparto del resto por retorno esperado.
 * - Valida: perfil (con retMinUsado), mínimos, 3..6, y cuotas por sector/tipo (si se pasan).
 */
public class Greedy {

    private static final int K_MIN = 3;
    private static final int K_MAX = 6;
    private static final double ALPHA_CORR = 0.02; // penalización suave por correlación

    public static ResultadoPortafolio construir(List<Activo> universo,
                                                MatrizCorrelacion rho,
                                                Cliente cliente,
                                                Map<String, Double> cuotaSector,
                                                Map<String, Double> cuotaTipo,
                                                double retMinUsado) {
        // 1) Preferencias blandas
        List<Activo> candidatos = filtrarPorPreferencias(universo, cliente);

        // 2) Orden
        candidatos.sort(new Comparator<Activo>() {
            @Override public int compare(Activo a1, Activo a2) {
                double s1 = a1.retornoEsperado() / Math.max(1e-9, a1.riesgo());
                double s2 = a2.retornoEsperado() / Math.max(1e-9, a2.riesgo());
                return Double.compare(s2, s1);
            }
        });

        // 3) Mejor hallado
        List<Activo> elegidos = new ArrayList<>();
        double[] mejoresPesos = null;
        double mejorRetorno = -1.0;
        double mejorRiesgo = Double.POSITIVE_INFINITY;
        double mejorCorr = 1.0;
        double mejorScore = -1.0;

        // 4) Selección
        while (elegidos.size() < K_MAX) {
            Activo mejorCandidato = null;
            double[] pesosCandidato = null;
            double retCandidato = -1.0;
            double riesgoCandidato = Double.POSITIVE_INFINITY;
            double corrCandidato = 1.0;
            double scoreCandidato = -1.0;

            for (int i = 0; i < candidatos.size(); i++) {
                Activo a = candidatos.get(i);
                if (elegidos.contains(a)) continue;

                List<Activo> tentativa = new ArrayList<>(elegidos);
                tentativa.add(a);

                double[] w = asignarPesos(tentativa, cliente.montoTotal());
                if (w == null) continue;

                double r = RiesgoUtils.retorno(tentativa, w);
                double s = RiesgoUtils.riesgo(tentativa, w, rho);
                double c = RiesgoUtils.correlacionPromedio(tentativa, rho);
                double score = r - ALPHA_CORR * c;

                boolean cumplePerfil = (s <= cliente.perfil().riesgoMax() + 1e-9)
                                     && (r + 1e-12 >= retMinUsado);
                boolean cumpleCard = (tentativa.size() <= K_MAX);
                boolean cumpleCuotas = Validador.cumplenCuotasMinimas(tentativa, w, cuotaSector, cuotaTipo);

                if (cumplePerfil && cumpleCard && cumpleCuotas) {
                    boolean mejoraScore = (score > scoreCandidato);
                    boolean empateScoreMenosRiesgo = (Math.abs(score - scoreCandidato) < 1e-9 && s < riesgoCandidato);
                    if (mejoraScore || empateScoreMenosRiesgo) {
                        mejorCandidato = a;
                        pesosCandidato = w;
                        retCandidato = r;
                        riesgoCandidato = s;
                        corrCandidato = c;
                        scoreCandidato = score;
                    }
                }
            }

            if (mejorCandidato == null) break;

            elegidos.add(mejorCandidato);
            mejoresPesos = pesosCandidato;
            mejorRetorno = retCandidato;
            mejorRiesgo = riesgoCandidato;
            mejorCorr = corrCandidato;
            mejorScore = scoreCandidato;
        }

        // 5) Plan B si faltan activos para llegar a K_MIN
        if (mejoresPesos == null || elegidos.size() < K_MIN) {
            elegidos.clear();
            for (int i = 0; i < Math.min(K_MIN, candidatos.size()); i++) {
                elegidos.add(candidatos.get(i));
            }
            double[] w = asignarPesos(elegidos, cliente.montoTotal());
            if (w != null) {
                double r = RiesgoUtils.retorno(elegidos, w);
                double s = RiesgoUtils.riesgo(elegidos, w, rho);
                double c = RiesgoUtils.correlacionPromedio(elegidos, rho);
                boolean okPerfil = (s <= cliente.perfil().riesgoMax() + 1e-9)
                                && (r + 1e-12 >= retMinUsado);
                boolean okCuotas = Validador.cumplenCuotasMinimas(elegidos, w, cuotaSector, cuotaTipo);
                if (okPerfil && okCuotas) {
                    mejoresPesos = w;
                    mejorRetorno = r;
                    mejorRiesgo = s;
                    mejorCorr = c;
                    mejorScore = r - ALPHA_CORR * c;
                }
            }
        }

        if (mejoresPesos == null) return null;

        String comentario = generarComentario(elegidos, mejorRetorno, mejorRiesgo, mejorCorr, cliente, retMinUsado);
        return new ResultadoPortafolio(elegidos, mejoresPesos, mejorRetorno, mejorRiesgo, mejorCorr, comentario);
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

    private static String generarComentario(List<Activo> as, double r, double s, double c, Cliente cli, double retMinUsado) {
        StringBuilder sb = new StringBuilder();
        sb.append("El portafolio cumple el perfil ").append(cli.perfil());
        sb.append(" (retorno >= ").append(String.format(Locale.US, "%.2f%%", retMinUsado * 100));
        sb.append(", riesgo <= ").append(String.format(Locale.US, "%.2f%%", cli.perfil().riesgoMax() * 100)).append("). ");
        sb.append("Se priorizaron activos con buen retorno relativo y se evitó alta correlación cuando fue posible. ");
        if (cli.sectoresPreferidos() != null && cli.sectoresPreferidos().length > 0) sb.append("Sectores preferidos respetados. ");
        if (cli.tiposPreferidos() != null && cli.tiposPreferidos().length > 0) sb.append("Tipos preferidos respetados. ");
        sb.append(String.format(Locale.US, "(r=%.3f, sigma=%.3f, corrProm=%.3f)", r, s, c));
        return sb.toString();
    }
}
