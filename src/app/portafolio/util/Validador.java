package app.portafolio.util;

import app.portafolio.dominio.Activo;
import app.portafolio.dominio.PerfilCliente;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Validador {
    private static final double EPS = 1e-6;

    // Suma de pesos = 1 (100% del dinero)
    public static boolean pesosUsanTodoElDinero(double[] pesos) {
        double s = 0.0;
        for (double p : pesos) s += p;
        return Math.abs(s - 1.0) <= EPS;
    }

    // Cada activo respeta su monto mínimo (peso * montoTotal >= mínimo)
    public static boolean cumplenMinimos(List<Activo> activos, double[] pesos, double montoTotal) {
        for (int i = 0; i < activos.size(); i++) {
            double monto = pesos[i] * montoTotal;
            if (monto + 1e-9 < activos.get(i).precioMinimo()) return false;
        }
        return true;
    }

    // Entre 3 y 6 activos
    public static boolean cumpleCardinalidad(int n) {
        return n >= 3 && n <= 6;
    }

    // Preferencias “blandas”: si hay sectores/tipos preferidos,
    // todos los activos elegidos deben pertenecer a esas listas.
    // Si no se pasan preferencias, se acepta todo.
    public static boolean cumplePreferencias(List<Activo> activos, String[] sectores, String[] tipos) {
        boolean sinSectores = (sectores == null || sectores.length == 0);
        boolean sinTipos    = (tipos == null || tipos.length == 0);
        if (sinSectores && sinTipos) return true;

        for (Activo a : activos) {
            boolean okSector = sinSectores;
            boolean okTipo   = sinTipos;

            if (!okSector) {
                for (String s : sectores) {
                    if (a.sector().equalsIgnoreCase(s)) { okSector = true; break; }
                }
            }
            if (!okTipo) {
                for (String t : tipos) {
                    if (a.tipo().equalsIgnoreCase(t)) { okTipo = true; break; }
                }
            }
            if (!(okSector && okTipo)) return false;
        }
        return true;
    }

    // Chequeo de perfil (con matriz): riesgo <= máx y retorno >= mín del perfil
    // (Este método no usa “retMin deseado”; eso se controla en los solucionadores
    // comparando contra retMinUsado = max(perfilMin, deseado))
    public static boolean cumplePerfil(List<Activo> activos, double[] pesos,
                                    PerfilCliente perfil, MatrizCorrelacion rho) {
        double r = RiesgoUtils.retorno(activos, pesos);
        double s = RiesgoUtils.riesgo(activos, pesos, rho);
        return s <= perfil.riesgoMax() + 1e-9 && r + 1e-12 >= perfil.retornoMin();
    }

    // **Nuevo**: cuotas mínimas por sector y/o tipo (restricciones “duras”)
    // Ejemplos:
    //   cuotaSector = {"Tecnologia": 0.30, "Energia": 0.20}
    //   cuotaTipo   = {"CEDEAR": 0.20, "ETF": 0.20}
    // Si los mapas son null o vacíos, no restringen.
    public static boolean cumplenCuotasMinimas(List<Activo> activos, double[] pesos,
                                            Map<String, Double> cuotaSector,
                                            Map<String, Double> cuotaTipo) {
        // Acumulo pesos por sector y por tipo
        Map<String, Double> acumSector = new HashMap<>();
        Map<String, Double> acumTipo   = new HashMap<>();

        for (int i = 0; i < activos.size(); i++) {
            Activo a = activos.get(i);
            double w = pesos[i];

            if (a.sector() != null && !a.sector().isBlank()) {
                acumSector.put(a.sector(), acumSector.getOrDefault(a.sector(), 0.0) + w);
            }
            if (a.tipo() != null && !a.tipo().isBlank()) {
                acumTipo.put(a.tipo(),     acumTipo.getOrDefault(a.tipo(), 0.0)   + w);
            }
        }

        // Verifico cuotas por sector
        if (cuotaSector != null && !cuotaSector.isEmpty()) {
            for (Map.Entry<String, Double> e : cuotaSector.entrySet()) {
                String sector = e.getKey();
                double req = e.getValue();             // p.ej. 0.30 = 30%
                double have = acumSector.getOrDefault(sector, 0.0);
                if (have + 1e-9 < req) return false;
            }
        }

        // Verifico cuotas por tipo
        if (cuotaTipo != null && !cuotaTipo.isEmpty()) {
            for (Map.Entry<String, Double> e : cuotaTipo.entrySet()) {
                String tipo = e.getKey();
                double req = e.getValue();
                double have = acumTipo.getOrDefault(tipo, 0.0);
                if (have + 1e-9 < req) return false;
            }
        }

        return true;
    }
}

