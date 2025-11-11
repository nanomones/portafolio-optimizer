package app.portafolio.dominio;

import java.util.List;
import java.util.Locale;

public class ResultadoPortafolio {
    private final List<Activo> activos;
    private final double[] pesos;
    private final double retorno;
    private final double riesgo;
    private final double corrProm;
    private final String comentario;

    public ResultadoPortafolio(List<Activo> activos, double[] pesos, double retorno,
                            double riesgo, double corrProm, String comentario) {
        this.activos = List.copyOf(activos);
        this.pesos = pesos.clone();
        this.retorno = retorno;
        this.riesgo = riesgo;
        this.corrProm = corrProm;
        this.comentario = comentario;
    }

    public List<Activo> activos() { return activos; }
    public double[] pesos() { return pesos.clone(); }
    public double retorno() { return retorno; }
    public double riesgo() { return riesgo; }
    public double corrProm() { return corrProm; }
    public String comentario() { return comentario; }

    public String resumen(double montoTotal) {
        StringBuilder sb = new StringBuilder();
        sb.append("Portafolio (").append(activos.size()).append(" activos)\n");
        double costoTotal = 0.0;
        for (int i = 0; i < activos.size(); i++) {
            Activo a = activos.get(i);
            double w = pesos[i];
            double monto = w * montoTotal;
            costoTotal += monto;
            sb.append(String.format(Locale.US,
                    " - %-10s sector=%-12s tipo=%-14s peso=%6.2f%% monto=$%,.2f r=%.3f sigma=%.3f%n",
                    a.ticker(), a.sector(), a.tipo(), w * 100.0, monto, a.retornoEsperado(), a.riesgo()));
        }
        sb.append(String.format(Locale.US, "Retorno total=%.3f   Riesgo total(sigma)=%.3f   Corr.prom=%.3f%n",
                retorno, riesgo, corrProm));
        sb.append(String.format(Locale.US, "Costo total=$%,.2f%n", costoTotal));
        sb.append("Comentario: ").append(comentario).append("\n");
        return sb.toString();
    }
}

