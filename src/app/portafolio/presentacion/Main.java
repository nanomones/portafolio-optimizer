package app.portafolio.presentacion;

import app.portafolio.datos.LectorCsv;
import app.portafolio.dominio.Activo;
import app.portafolio.dominio.Cliente;
import app.portafolio.dominio.PerfilCliente;
import app.portafolio.dominio.ResultadoPortafolio;
import app.portafolio.solucionadores.Alternativos;
import app.portafolio.solucionadores.BacktrackingBnB;
import app.portafolio.solucionadores.Greedy;
import app.portafolio.util.MatrizCorrelacion;
import app.portafolio.util.RiesgoUtils;
import app.portafolio.util.Validador;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    // ===== Helpers simples =====
    private static String readLine(Scanner sc, String prompt) {
        System.out.print(prompt);
        String s = sc.nextLine();
        return (s == null) ? "" : s.trim();
    }

    private static double parseDoubleSafe(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            s = s.replace(',', '.');
            return Double.parseDouble(s.trim());
        } catch (Exception ignored) {
            System.out.println("  Aviso: no pude leer ese número, uso " + def);
            return def;
        }
    }

    private static PerfilCliente parsePerfilSafe(String s, List<String> perfilesDisponibles) {
        if (s == null) return PerfilCliente.MODERADO;
        try {
            return PerfilCliente.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            System.out.println("  Aviso: perfil inválido. Usando MODERADO. Perfiles válidos: " + perfilesDisponibles);
            return PerfilCliente.MODERADO;
        }
    }

    private static String[] parseListaFiltrada(String s, Set<String> disponibles, String etiqueta) {
        if (s == null || s.isBlank()) return new String[0];
        String[] items = s.split(",");
        List<String> out = new ArrayList<>();
        for (String raw : items) {
            String v = raw.trim();
            if (v.isEmpty()) continue;
            boolean ok = false;
            for (String d : disponibles) {
                if (d.equalsIgnoreCase(v)) {
                    out.add(d); // guardo con el nombre tal como está en la data
                    ok = true;
                    break;
                }
            }
            if (!ok) System.out.println("  Aviso: " + etiqueta + " \"" + v + "\" no existe. Se ignora.");
        }
        return out.toArray(new String[0]);
    }

    private static Map<String, Double> parseCuotasFiltradas(String s, Set<String> disponibles, String etiqueta) {
        if (s == null || s.isBlank()) return null;
        Map<String,Double> m = new LinkedHashMap<>();
        String[] pares = s.split(",");
        for (String p : pares) {
            String[] kv = p.split(":");
            if (kv.length != 2) {
                System.out.println("  Aviso: formato inválido \"" + p + "\". Ej: Tecnologia:0.30");
                continue;
            }
            String claveRaw = kv[0].trim();
            String valorRaw = kv[1].trim();

            String claveOK = null;
            for (String d : disponibles) {
                if (d.equalsIgnoreCase(claveRaw)) { claveOK = d; break; }
            }
            if (claveOK == null) {
                System.out.println("  Aviso: " + etiqueta + " \"" + claveRaw + "\" no existe. Se ignora.");
                continue;
            }

            double v = parseDoubleSafe(valorRaw, -1);
            if (v < 0 || v > 1) {
                System.out.println("  Aviso: valor fuera de rango [0..1] para \"" + claveOK + "\". Se ignora.");
                continue;
            }
            m.put(claveOK, v);
        }
        if (m.isEmpty()) return null;

        double suma = 0.0;
        for (double x : m.values()) suma += x;
        if (suma > 1.0 + 1e-9) {
            System.out.println("  Aviso: la suma de cuotas de " + etiqueta + " supera 100% (" +
                    String.format(Locale.US,"%.2f%%", suma*100) + "). Puede no haber solución.");
        }
        return m;
    }

    public static void main(String[] args) {
        try {
            // ====== Cargar data ======
            Path base = Paths.get("data");
            String activosCsv = base.resolve("activos_financieros_reales.csv").toString();
            String matrizCsv  = base.resolve("matriz_correlaciones.csv").toString();

            List<Activo> activos = LectorCsv.leerActivos(activosCsv);
            MatrizCorrelacion mc = LectorCsv.leerCorrelacion(matrizCsv);

            System.out.println("Activos cargados: " + activos.size());
            System.out.println("Matriz OK (simétrica y diagonal=1): " + mc.esSimetricaConDiagonalUno());

            // Detectar sectores y tipos disponibles
            Set<String> sectoresSet = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            Set<String> tiposSet    = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (Activo a : activos) {
                if (a.sector() != null && !a.sector().isBlank()) sectoresSet.add(a.sector());
                if (a.tipo()   != null && !a.tipo().isBlank())   tiposSet.add(a.tipo());
            }
            List<String> sectoresDisponibles = new ArrayList<>(sectoresSet);
            List<String> tiposDisponibles    = new ArrayList<>(tiposSet);

            // Perfiles disponibles desde el enum
            List<String> perfilesDisponibles = new ArrayList<>();
            for (PerfilCliente p : PerfilCliente.values()) perfilesDisponibles.add(p.name());
            Collections.sort(perfilesDisponibles);

            // ====== Entrada por consola ======
            Scanner sc = new Scanner(System.in);
            System.out.println();
            System.out.println("=== CONFIGURAR CLIENTE ===");

            double montoTotal;
            while (true) {
                String sm = readLine(sc, "Monto total a invertir (ej 100000): ");
                montoTotal = parseDoubleSafe(sm, -1);
                if (montoTotal > 0) break;
                System.out.println("  Por favor ingresá un monto positivo.");
            }

            System.out.println("Perfiles disponibles: " + perfilesDisponibles);
            PerfilCliente perfil = parsePerfilSafe(
                    readLine(sc, "Perfil (exacto, ej MODERADO/AGRESIVO/MODERADAMENTE_CONSERVADOR/...): "),
                    perfilesDisponibles
            );

            double retornoMinDeseado = parseDoubleSafe(
                    readLine(sc, "Retorno minimo deseado (ej 0.18 para 18%, o 0 si no): "),
                    0.0
            );
            if (retornoMinDeseado < 0) {
                System.out.println("  Aviso: retorno mínimo deseado negativo no tiene sentido. Uso 0.");
                retornoMinDeseado = 0.0;
            }

            System.out.println("Sectores disponibles: " + sectoresDisponibles);
            String[] sectoresPref = parseListaFiltrada(
                    readLine(sc, "Sectores preferidos (coma separada, vacio para ninguno): "),
                    sectoresSet, "Sector"
            );

            System.out.println("Tipos disponibles: " + tiposDisponibles);
            String[] tiposPref = parseListaFiltrada(
                    readLine(sc, "Tipos preferidos (coma separada, vacio para ninguno): "),
                    tiposSet, "Tipo"
            );

            System.out.println("Cuotas minimas por SECTOR (usar claves de la lista de sectores).");
            System.out.println("Formato: Tecnologia:0.30,Energia:0.20  (o vacio para no exigir)");
            Map<String,Double> cuotaSector = parseCuotasFiltradas(
                    readLine(sc, ""), sectoresSet, "Sector"
            );

            System.out.println("Cuotas minimas por TIPO (usar claves de la lista de tipos).");
            System.out.println("Formato: CEDEAR:0.20,ETF:0.20  (o vacio para no exigir)");
            Map<String,Double> cuotaTipo = parseCuotasFiltradas(
                    readLine(sc, ""), tiposSet, "Tipo"
            );

            // Plazo fijo (según cátedra)
            int plazoAnios = 1;

            // Construir cliente y retorno mínimo efectivo
            Cliente cliente = new Cliente(montoTotal, perfil, sectoresPref, tiposPref);
            double retMinUsado = Math.max(perfil.retornoMin(), retornoMinDeseado);

            // Mostrar datos del cliente
            System.out.println("\nCliente:");
            System.out.println(" - Monto = $" + String.format("%,.2f", montoTotal));
            System.out.println(" - Perfil = " + perfil);
            System.out.println(" - Plazo = " + plazoAnios + " año");
            System.out.println(" - Retorno mínimo usado = " + String.format(Locale.US, "%.2f%%", retMinUsado * 100));
            if (sectoresPref.length > 0) System.out.println(" - Sectores preferidos = " + Arrays.toString(sectoresPref));
            if (tiposPref.length > 0)    System.out.println(" - Tipos preferidos    = " + Arrays.toString(tiposPref));
            if (cuotaSector != null)     System.out.println(" - Cuotas por sector   = " + cuotaSector);
            if (cuotaTipo != null)       System.out.println(" - Cuotas por tipo     = " + cuotaTipo);

            // ====== Demo mínima opcional ======
            if (activos.size() >= 3) {
                List<Activo> subset = activos.subList(0, 3);
                double[] w = { 1.0/3, 1.0/3, 1.0/3 };

                double r = RiesgoUtils.retorno(subset, w);
                double s = RiesgoUtils.riesgo(subset, w, mc);
                double c = RiesgoUtils.correlacionPromedio(subset, mc);

                System.out.printf(Locale.US,
                        "Demo 3-activos  r=%.4f  sigma=%.4f  corrProm=%.4f%n", r, s, c);

                System.out.println("Checks demo:");
                System.out.println(" - Usa 100% dinero: " + Validador.pesosUsanTodoElDinero(w));
                System.out.println(" - Mínimos:          " + Validador.cumplenMinimos(subset, w, cliente.montoTotal()));
                System.out.println(" - Cardinalidad:     " + Validador.cumpleCardinalidad(subset.size()));
                System.out.println(" - Preferencias:     " + Validador.cumplePreferencias(subset,
                        cliente.sectoresPreferidos(), cliente.tiposPreferidos()));
                System.out.println(" - Perfil:           " + Validador.cumplePerfil(subset, w, cliente.perfil(), mc));
            } else {
                System.out.println("No hay suficientes activos para la demo.");
            }

            // ====== GREEDY ======
            ResultadoPortafolio greedy = Greedy.construir(activos, mc, cliente, cuotaSector, cuotaTipo, retMinUsado);
            if (greedy == null) {
                System.out.println("\nGreedy: no encontró un portafolio válido para las restricciones dadas.");
            } else {
                System.out.println("\n=== GREEDY - Portafolio candidato ===");
                System.out.print(greedy.resumen(montoTotal));
                double[] w = greedy.pesos();
                System.out.println("Checks Greedy:");
                System.out.println(" - Usa 100% dinero: " + Validador.pesosUsanTodoElDinero(w));
                System.out.println(" - Mínimos:          " + Validador.cumplenMinimos(greedy.activos(), w, montoTotal));
                System.out.println(" - Cardinalidad:     " + Validador.cumpleCardinalidad(greedy.activos().size()));
                System.out.println(" - Preferencias:     " + Validador.cumplePreferencias(greedy.activos(),
                        cliente.sectoresPreferidos(), cliente.tiposPreferidos()));
                System.out.println(" - Perfil:           " + Validador.cumplePerfil(greedy.activos(), w, cliente.perfil(), mc));
            }

            // ====== ÓPTIMO (Backtracking + B&B) ======
            ResultadoPortafolio optimo = BacktrackingBnB.optimizar(activos, mc, cliente, cuotaSector, cuotaTipo, retMinUsado);
            if (optimo == null) {
                System.out.println("\nBacktrackingBnB: no encontró un portafolio válido.");
            } else {
                System.out.println("\n=== ÓPTIMO (Backtracking + B&B) ===");
                System.out.print(optimo.resumen(montoTotal));
                double[] w = optimo.pesos();
                System.out.println("Checks Óptimo:");
                System.out.println(" - Usa 100% dinero: " + Validador.pesosUsanTodoElDinero(w));
                System.out.println(" - Mínimos:          " + Validador.cumplenMinimos(optimo.activos(), w, montoTotal));
                System.out.println(" - Cardinalidad:     " + Validador.cumpleCardinalidad(optimo.activos().size()));
                System.out.println(" - Preferencias:     " + Validador.cumplePreferencias(optimo.activos(),
                        cliente.sectoresPreferidos(), cliente.tiposPreferidos()));
                System.out.println(" - Perfil:           " + Validador.cumplePerfil(optimo.activos(), w, cliente.perfil(), mc));

                // ====== Alternativos (respetan TODO y no superan retorno del óptimo) ======
                ResultadoPortafolio alt1 = Alternativos.generar1(optimo, activos, mc, cliente, cuotaSector, cuotaTipo, retMinUsado);
                ResultadoPortafolio alt2 = Alternativos.generar2(optimo, activos, mc, cliente, cuotaSector, cuotaTipo, retMinUsado);

                if (alt1 != null) {
                    System.out.println("\n=== Alternativo 1 ===");
                    System.out.print(alt1.resumen(montoTotal));
                    double[] w1 = alt1.pesos();
                    System.out.println("Checks Alt1:");
                    System.out.println(" - Usa 100% dinero: " + Validador.pesosUsanTodoElDinero(w1));
                    System.out.println(" - Mínimos:          " + Validador.cumplenMinimos(alt1.activos(), w1, montoTotal));
                    System.out.println(" - Cardinalidad:     " + Validador.cumpleCardinalidad(alt1.activos().size()));
                    System.out.println(" - Preferencias:     " + Validador.cumplePreferencias(alt1.activos(),
                            cliente.sectoresPreferidos(), cliente.tiposPreferidos()));
                    System.out.println(" - Perfil:           " + Validador.cumplePerfil(alt1.activos(), w1, cliente.perfil(), mc));
                } else {
                    System.out.println("\nAlternativo 1: no se pudo generar (no hubo opción válida cercana al óptimo).");
                }

                if (alt2 != null) {
                    System.out.println("\n=== Alternativo 2 ===");
                    System.out.print(alt2.resumen(montoTotal));
                    double[] w2 = alt2.pesos();
                    System.out.println("Checks Alt2:");
                    System.out.println(" - Usa 100% dinero: " + Validador.pesosUsanTodoElDinero(w2));
                    System.out.println(" - Mínimos:          " + Validador.cumplenMinimos(alt2.activos(), w2, montoTotal));
                    System.out.println(" - Cardinalidad:     " + Validador.cumpleCardinalidad(alt2.activos().size()));
                    System.out.println(" - Preferencias:     " + Validador.cumplePreferencias(alt2.activos(),
                            cliente.sectoresPreferidos(), cliente.tiposPreferidos()));
                    System.out.println(" - Perfil:           " + Validador.cumplePerfil(alt2.activos(), w2, cliente.perfil(), mc));
                } else {
                    System.out.println("\nAlternativo 2: no se pudo generar (no hubo opción válida con menor correlación).");
                }
            }

            // ====== Portafolio final ======
            ResultadoPortafolio finalista = (optimo != null ? optimo : greedy);
            if (finalista != null) {
                System.out.println("\n=== PORTAFOLIO FINAL PARA EL CLIENTE ===");
                System.out.print(finalista.resumen(montoTotal));
            } else {
                System.out.println("\nNo se pudo construir ningún portafolio válido.");
            }

        } catch (Exception e) {
            System.out.println("Error al ejecutar: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
